package com.cobbletracker.mod.tracker;

import com.cobbletracker.common.tracker.PokemonSnapshot;
import com.cobbletracker.common.tracker.SpawnRecord;
import com.cobbletracker.common.tracker.TrackerCategory;
import com.cobbletracker.common.tracker.TrackerStore;
import com.cobbletracker.common.util.Obfuscation;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.announce.AnnouncementService;
import com.cobbletracker.mod.config.BeamSettings;
import com.cobbletracker.mod.config.ConfigManager;
import com.cobbletracker.mod.config.GeneralSettings;
import com.cobbletracker.mod.config.MinimapSettings;
import com.cobbletracker.mod.config.NotificationConfig;
import com.cobbletracker.mod.config.TrackerJsonStore;
import com.cobbletracker.mod.minimap.MinimapWaypoints;
import com.cobbletracker.mod.net.C2S_MinimapSupportPacket;
import com.cobbletracker.mod.net.PlatformNetworking;
import com.cobbletracker.mod.net.S2C_OpenAdminPacket;
import com.cobbletracker.mod.net.S2C_SpawnEndedPacket;
import com.cobbletracker.mod.net.S2C_SyncHistoryPacket;
import com.cobbletracker.mod.net.S2C_TrackedSpawnPacket;
import com.cobbletracker.mod.text.Msg;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server-side state: the loaded {@link ConfigManager}, the in-memory {@link TrackerStore} (mirrored to
 * tracker.json), the live spawns we're watching, and the debounced save. Built on server start, dropped
 * on stop. Other code reaches it through {@link #get()} / {@link #ifRunning}.
 */
public final class TrackerServer {

    private static volatile TrackerServer instance;

    private final MinecraftServer server;
    private volatile ConfigManager config;
    private final TrackerStore store = new TrackerStore();
    private final SpawnEngine spawnEngine = new SpawnEngine();
    /** Minimap mods each connected client advertised (via {@link C2S_MinimapSupportPacket}). */
    private final Map<UUID, C2S_MinimapSupportPacket> minimapSupport = new ConcurrentHashMap<>();
    /**
     * Live tracked spawns, keyed by Pokémon UUID, so we can report caught / defeated / despawned. Ordered
     * by insertion and capped, because a category with a wide-open spec matches every Pokémon on the
     * server and this would otherwise grow with the spawn rate.
     */
    private final Map<UUID, LiveSpawn> liveSpawns = Collections.synchronizedMap(new LinkedHashMap<>());
    /** Pokémon UUIDs we've already recorded, so a chunk reload re-firing {@code EntityEvent.ADD} is ignored. */
    private final Set<UUID> seenPokemon = ConcurrentHashMap.newKeySet();
    /** Entity UUIDs the custom {@link SpawnEngine} spawned; the ADD detector skips these (engine records them). */
    private final Set<UUID> suppressedDetection = ConcurrentHashMap.newKeySet();
    /** Last time each player asked for their history, to stop a misbehaving client hammering the sync. */
    private final Map<UUID, Long> lastSyncMs = new ConcurrentHashMap<>();

    /** A tracked spawn still in the world, watched for its fate (mutable bookkeeping). */
    private static final class LiveSpawn {
        final UUID entityId;
        final String categoryId;
        final String species;
        final int color;
        final long spawnedAtMs = System.currentTimeMillis();
        String dimension;
        int lastX;
        int lastY;
        int lastZ;
        boolean everSeen;
        int missingTicks;

        LiveSpawn(UUID entityId, String categoryId, String species, int color) {
            this.entityId = entityId;
            this.categoryId = categoryId;
            this.species = species;
            this.color = color;
        }
    }

    /** How near (blocks) a player must be to a vanished spawn's last position for it to count as a real
     *  despawn rather than the chunk simply unloading because everyone flew away. */
    private static final int DESPAWN_WITNESS_RANGE = 160;
    /** Absolute cap (ms) on how long a live spawn is tracked before it's dropped silently, freeing memory. */
    private static final long LIVE_SPAWN_MAX_AGE_MS = 30 * 60 * 1000L;
    /** Bound on the dedup set; cleared wholesale if it ever grows past this (a re-announce then is harmless). */
    private static final int SEEN_CAP = 8192;
    /** Most spawns we'll put in one history packet, so a huge tracker.json can't blow the packet limit. */
    private static final int MAX_SYNC_ENTRIES = 2000;
    /** Minimum gap between two history syncs for the same player. */
    private static final long SYNC_COOLDOWN_MS = 500L;
    /** Most spawns watched for their fate at once; the oldest is dropped to make room. */
    private static final int MAX_LIVE_SPAWNS = 512;

    private boolean dirty;
    private int saveCooldown;

    private TrackerServer(MinecraftServer server) {
        this.server = server;
        this.config = ConfigManager.load();
        TrackerJsonStore.loadInto(store);
    }

    public static void start(MinecraftServer server) {
        instance = new TrackerServer(server);
        CobbleTracker.LOGGER.info("CobbleTracker ready - {} tracker categories loaded",
                instance.config.registry().categories().size());
    }

    public static void stop() {
        TrackerServer running = instance;
        instance = null;
        if (running != null) {
            TrackerJsonStore.save(running.store);
        }
    }

    public static TrackerServer get() {
        return instance;
    }

    public static void ifRunning(Consumer<TrackerServer> action) {
        TrackerServer i = instance;
        if (i != null) {
            action.accept(i);
        }
    }

    public ConfigManager config() {
        return config;
    }

    public TrackerStore store() {
        return store;
    }

    /** Forgets everything we were holding for a player who just left. */
    public void playerLeft(UUID playerId) {
        minimapSupport.remove(playerId);
        lastSyncMs.remove(playerId);
    }

    /** Server post-tick: run the custom spawner and flush persistence when dirty (debounced). */
    public void tick(MinecraftServer server) {
        // The custom spawner runs every tick; it tracks its own per-category interval + fairness.
        spawnEngine.tick(server, config, this);
        if (!liveSpawns.isEmpty() && server.getTickCount() % 20 == 0) {
            watchLiveSpawns(server);
        }
        if (dirty) {
            if (saveCooldown <= 0) {
                TrackerJsonStore.save(store);
                dirty = false;
                saveCooldown = 200; // at most once per ~10s
            } else {
                saveCooldown--;
            }
        }
    }

    /**
     * Records a spawn if it matches a category, then fires the announcement. Called for every Pokémon that
     * enters a server level (via {@code EntityEvent.ADD}), so it must be idempotent: entities the custom
     * spawner already handled are suppressed, and a Pokémon we've already recorded (e.g. its chunk just
     * reloaded) is ignored via {@link #seenPokemon}. Exact coords are stored.
     */
    public void recordSpawn(PokemonSnapshot snapshot, UUID pokemonId, UUID entityId,
            int x, int y, int z, String world, String biome) {
        if (suppressedDetection.remove(entityId)) {
            return; // the SpawnEngine spawned this and records it under its own category
        }
        if (seenPokemon.contains(pokemonId)) {
            return; // already recorded once - this is a chunk reload / re-add of the same Pokémon
        }
        config.registry().firstMatch(snapshot, world)
                .ifPresent(category -> recordInternal(category, snapshot, pokemonId, entityId, x, y, z, world, biome));
    }

    /** Marks a Pokémon spawned by the {@link SpawnEngine} so the ADD detector skips it (engine records it). */
    public void suppressDetection(UUID entityId) {
        if (suppressedDetection.size() > 256) {
            suppressedDetection.clear(); // bound it; a stray add slipping through just gets recorded normally
        }
        suppressedDetection.add(entityId);
    }

    /** Drops a suppression again when the spawn it was meant for never made it into the world. */
    public void releaseDetection(UUID entityId) {
        suppressedDetection.remove(entityId);
    }

    /** Records a spawn under a specific category (used by the {@link SpawnEngine}, bypassing the matcher). */
    public void recordForCategory(TrackerCategory category, PokemonSnapshot snapshot, UUID pokemonId, UUID entityId,
            int x, int y, int z, String world, String biome) {
        recordInternal(category, snapshot, pokemonId, entityId, x, y, z, world, biome);
    }

    private void recordInternal(TrackerCategory category, PokemonSnapshot snapshot, UUID pokemonId, UUID entityId,
            int x, int y, int z, String world, String biome) {
        // Remember it so a chunk reload re-firing the add event doesn't re-announce the same Pokémon.
        if (seenPokemon.size() > SEEN_CAP) {
            seenPokemon.clear();
        }
        seenPokemon.add(pokemonId);
        SpawnRecord record = new SpawnRecord(pokemonId, category.id(), snapshot.speciesName(), snapshot.speciesId(),
                Instant.now().getEpochSecond(), world, x, y, z, biome, snapshot.shiny(), false, null, null, "", "");
        store.record(record, category.maxStored());
        dirty = true;
        NotificationConfig note = config.notification(category.id());
        List<ServerPlayer> recipients = AnnouncementService.recipients(server, note, record);
        AnnouncementService.announce(config.general(), note, record, recipients);
        broadcastTrackedSpawn(category, record, entityId, recipients);
        CobbleTracker.LOGGER.debug("Recorded {} under '{}' at {},{},{} (biome {})",
                record.species(), category.id(), x, y, z, record.biome());
    }

    /**
     * Starts watching a recorded spawn and pushes the beam packet to everyone the announcement reached.
     * The packet carries the beam config; the client decides whether the entity is in its loaded chunks
     * and in range. Watching happens even with nobody to notify - the spawn is real either way, and we
     * still want its catch/defeat/despawn on the record. Minimap waypoints aren't pushed automatically:
     * a player clicks the "Create Waypoint" link in chat ({@link #dropWaypoint}) when they want one.
     */
    private void broadcastTrackedSpawn(TrackerCategory category, SpawnRecord record, UUID entityId,
            List<ServerPlayer> recipients) {
        LiveSpawn live = new LiveSpawn(entityId, category.id(), record.species(), category.color());
        live.dimension = record.world();
        live.lastX = record.x();
        live.lastY = record.y();
        live.lastZ = record.z();
        synchronized (liveSpawns) {
            liveSpawns.put(record.id(), live);
            Iterator<Map.Entry<UUID, LiveSpawn>> oldest = liveSpawns.entrySet().iterator();
            while (liveSpawns.size() > MAX_LIVE_SPAWNS && oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
        if (recipients.isEmpty()) {
            return;
        }
        GeneralSettings g = config.general();
        BeamSettings beam = config.beam();
        int x = Obfuscation.maybeObfuscate(record.x(), !g.hideExactPosition());
        int y = record.y();
        int z = Obfuscation.maybeObfuscate(record.z(), !g.hideExactPosition());
        S2C_TrackedSpawnPacket packet = new S2C_TrackedSpawnPacket(entityId, category.id(), record.species(),
                category.color(), x, y, z, record.world(), beam.enabled(), beam.radius(), beam.durationTicks(),
                beam.height());
        for (ServerPlayer player : recipients) {
            if (!PlatformNetworking.canReach(player)) {
                continue;
            }
            try {
                PlatformNetworking.sendToPlayer(player, packet);
            } catch (Throwable t) {
                CobbleTracker.LOGGER.debug("Beam push failed for {}", player.getGameProfile().getName(), t);
            }
        }
    }

    /**
     * Drops a minimap waypoint for a past spawn onto one player's map, using whichever minimap they
     * advertised. Fired when a player clicks the "Create Waypoint" link in a spawn announcement. Position
     * honours hide-exact-position, same as the announcement did.
     */
    public void dropWaypoint(ServerPlayer player, UUID recordId) {
        if (!config.minimap().enabled()) {
            player.sendSystemMessage(Msg.warn("Minimap waypoints are disabled."));
            return;
        }
        C2S_MinimapSupportPacket caps = minimapSupport.get(player.getUUID());
        if (caps == null || !caps.any()) {
            player.sendSystemMessage(Msg.warn(
                    "No supported minimap detected - install Xaero's, VoxelMap or JourneyMap."));
            return;
        }
        store.findById(recordId).ifPresentOrElse(r -> {
            boolean hide = config.general().hideExactPosition();
            int wx = Obfuscation.maybeObfuscate(r.x(), !hide);
            int wz = Obfuscation.maybeObfuscate(r.z(), !hide);
            int color = config.registry().byId(r.category()).map(TrackerCategory::color).orElse(0xFFFFAA00);
            boolean sent = MinimapWaypoints.send(player, caps, config.minimap(), r.species(), color,
                    wx, r.y(), wz, r.world());
            if (!sent) {
                // Their minimap is one the server has switched off, so nothing went out.
                player.sendSystemMessage(Msg.warn("Waypoints are turned off for your minimap on this server."));
            }
        }, () -> player.sendSystemMessage(Msg.warn("That spawn is no longer tracked.")));
    }

    /** Records a client's advertised minimap support; replaced on relog, dropped when they leave. */
    public void setMinimapSupport(ServerPlayer player, C2S_MinimapSupportPacket caps) {
        minimapSupport.put(player.getUUID(), caps);
        CobbleTracker.LOGGER.debug("Minimap support for {}: xaero={} voxelmap={} journeymap={}",
                player.getGameProfile().getName(), caps.xaero(), caps.voxelmap(), caps.journeymap());
    }

    /** A tracked Pokémon was captured. Records the catcher, announces it and stops watching it. */
    public void markCaught(UUID pokemonId, UUID catcher, String catcherName) {
        store.markCaught(pokemonId, catcher, catcherName).ifPresent(r -> dirty = true);
        LiveSpawn live = liveSpawns.remove(pokemonId);
        if (live != null) {
            String who = catcherName == null || catcherName.isBlank() ? "someone" : catcherName;
            AnnouncementService.lifecycle(lifecycleRecipients(pokemonId, live), live.color, live.species,
                    "was caught by " + who + "!");
            endSpawn(live.entityId);
        }
    }

    /** A tracked Pokémon fainted (defeated in battle / killed). Announces and stops watching it. */
    public void markFainted(UUID pokemonId, String killerName) {
        LiveSpawn live = liveSpawns.remove(pokemonId);
        if (live != null) {
            store.markOutcome(pokemonId, "defeated", killerName == null ? "" : killerName).ifPresent(r -> dirty = true);
            String phrase = killerName == null || killerName.isBlank()
                    ? "was defeated!" : "was defeated by " + killerName + "!";
            AnnouncementService.lifecycle(lifecycleRecipients(pokemonId, live), live.color, live.species, phrase);
            endSpawn(live.entityId);
        }
    }

    /**
     * Who hears about a tracked spawn's fate: the same players the original announcement went to, so a
     * radius-limited category doesn't suddenly tell the whole server. If the record has already aged out
     * of the history, the spawn's last known position stands in for it.
     */
    private List<ServerPlayer> lifecycleRecipients(UUID recordId, LiveSpawn live) {
        NotificationConfig note = config.notification(live.categoryId);
        return store.findById(recordId)
                .map(r -> AnnouncementService.recipients(server, note, r))
                .orElseGet(() -> AnnouncementService.recipients(server, note, live.dimension,
                        live.lastX, live.lastY, live.lastZ));
    }

    /** Tells every client to drop the beam for this entity now (its spawn has definitively ended). */
    private void endSpawn(UUID entityId) {
        S2C_SpawnEndedPacket packet = new S2C_SpawnEndedPacket(entityId);
        for (ServerPlayer player : AnnouncementService.onlinePlayers(server)) {
            if (!PlatformNetworking.canReach(player)) {
                continue;
            }
            try {
                PlatformNetworking.sendToPlayer(player, packet);
            } catch (Throwable ignored) {
                // best-effort beam teardown; the client keep-alive grace cleans up anyway
            }
        }
    }

    /**
     * Finds tracked spawns that have actually left the world and reports/tears them down. Something only
     * counts as despawned if it's gone while a player is still near its last position. If everyone flew
     * away its chunk just unloaded, so we keep watching (no false "despawned", no beam teardown) and the
     * beam comes back when a player returns. Very old entries are dropped quietly to bound memory.
     */
    private void watchLiveSpawns(MinecraftServer server) {
        long now = System.currentTimeMillis();
        // Take a snapshot to walk: the announcements and packets below send over the network, and that is
        // no place to be holding the map's monitor.
        List<Map.Entry<UUID, LiveSpawn>> snapshot;
        synchronized (liveSpawns) {
            snapshot = new ArrayList<>(liveSpawns.entrySet());
        }
        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, LiveSpawn> entry : snapshot) {
            UUID pokemonId = entry.getKey();
            LiveSpawn live = entry.getValue();
            Entity found = null;
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(live.entityId);
                if (e != null) {
                    found = e;
                    break;
                }
            }
            if (found != null) {
                live.everSeen = true;
                live.missingTicks = 0;
                live.dimension = found.level().dimension().location().toString();
                live.lastX = (int) Math.round(found.getX());
                live.lastY = (int) Math.round(found.getY());
                live.lastZ = (int) Math.round(found.getZ());
                continue;
            }
            if (now - live.spawnedAtMs >= LIVE_SPAWN_MAX_AGE_MS) {
                endSpawn(live.entityId);
                finished.add(pokemonId);
                continue;
            }
            // Gone this sweep. Only call it a despawn if someone was close enough to witness it; otherwise
            // its chunk just unloaded, so keep watching and the beam returns when a player comes back.
            if (!live.everSeen) {
                live.missingTicks += 20;
                if (live.missingTicks >= 200) {
                    finished.add(pokemonId); // never actually saw it; give up quietly
                }
                continue;
            }
            if (playerNear(server, live)) {
                live.missingTicks += 20;
                if (live.missingTicks >= 60) {
                    store.markOutcome(pokemonId, "despawned", "").ifPresent(r -> dirty = true);
                    AnnouncementService.lifecycle(lifecycleRecipients(pokemonId, live), live.color, live.species,
                            "despawned.");
                    endSpawn(live.entityId);
                    finished.add(pokemonId);
                }
            } else {
                live.missingTicks = 0; // out of everyone's range - just an unloaded chunk
            }
        }
        if (!finished.isEmpty()) {
            synchronized (liveSpawns) {
                finished.forEach(liveSpawns::remove);
            }
        }
    }

    /** True if any player is within {@link #DESPAWN_WITNESS_RANGE} of the spawn's last known position. */
    private static boolean playerNear(MinecraftServer server, LiveSpawn live) {
        BlockPos pos = new BlockPos(live.lastX, live.lastY, live.lastZ);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().location().toString().equals(live.dimension)) {
                continue;
            }
            if (player.blockPosition().distSqr(pos) <= (long) DESPAWN_WITNESS_RANGE * DESPAWN_WITNESS_RANGE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reloads config.yml + announcements.yml from disk, rebuilding the category registry. A file that
     * won't parse leaves the running config alone: taking every tracker down because of one stray
     * character is worse than carrying on with what already worked. Returns false in that case so the
     * caller can tell the operator to go and look at the log.
     */
    public boolean reload() {
        ConfigManager loaded = ConfigManager.load();
        if (!loaded.readable()) {
            CobbleTracker.LOGGER.error("Keeping the previous CobbleTracker configuration - the files on disk "
                    + "could not be parsed");
            return false;
        }
        config = loaded;
        spawnEngine.reset();
        CobbleTracker.LOGGER.info("CobbleTracker config reloaded - {} categories",
                config.registry().categories().size());
        return true;
    }

    // ---- client sync -----------------------------------------------------

    /** Sends the player the full spawn-history payload for the tracker GUI (All tab). */
    public void syncTo(ServerPlayer player) {
        syncTo(player, "");
    }

    /** The category id of the first legendary tracker (for {@code /lastlegend}), or "" if none. */
    public String legendaryCategoryId() {
        for (TrackerCategory c : config.registry().categories()) {
            String spec = c.spec() == null ? "" : c.spec().toLowerCase(Locale.ROOT);
            if (c.id().toLowerCase(Locale.ROOT).contains("legend") || spec.contains("islegendary")) {
                return c.id();
            }
        }
        return "";
    }

    /**
     * Prints a short chat summary of recent legendary spawns: the species, how long ago it turned up, and
     * whoever caught or beat it. No coordinates, no distance - just what happened. Works without the
     * client mod.
     */
    public void sendLegendReport(ServerPlayer player) {
        String catId = legendaryCategoryId();
        List<SpawnRecord> records = catId.isEmpty() ? List.of() : store.category(catId);
        if (records.isEmpty()) {
            player.sendSystemMessage(Msg.info("No legendary spawns recorded yet."));
            return;
        }
        int color = config.registry().byId(catId).map(TrackerCategory::color).orElse(0xFFFF3333);
        player.sendSystemMessage(Msg.parse(Msg.prefix())
                .append(Component.literal("Recent legendary spawns")
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
        long nowSec = Instant.now().getEpochSecond();
        int limit = Math.min(records.size(), 10);
        for (int i = 0; i < limit; i++) {
            SpawnRecord r = records.get(i);
            MutableComponent line = Component.literal(" • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(r.species()).withStyle(Style.EMPTY
                            .withColor(TextColor.fromRgb(color)).withBold(true)))
                    .append(Component.literal(" spawned " + ago(nowSec - r.spawnTime()) + " ago")
                            .withStyle(ChatFormatting.GRAY));
            String status = legendStatus(r);
            if (!status.isEmpty()) {
                line.append(Component.literal(" - " + status).withStyle(ChatFormatting.YELLOW));
            }
            player.sendSystemMessage(line);
        }
    }

    private static String legendStatus(SpawnRecord r) {
        if (r.caught() || "caught".equals(r.outcome())) {
            String by = !r.outcomeBy().isBlank() ? r.outcomeBy()
                    : (r.catcherName() == null ? "" : r.catcherName());
            return by.isBlank() ? "caught" : "caught by " + by;
        }
        return switch (r.outcome()) {
            case "defeated" -> r.outcomeBy().isBlank() ? "defeated" : "defeated by " + r.outcomeBy();
            case "despawned" -> "despawned";
            default -> "";
        };
    }

    private static String ago(long seconds) {
        long s = Math.max(0, seconds);
        if (s < 60) {
            return s + "s";
        }
        if (s < 3600) {
            return (s / 60) + "m";
        }
        if (s < 86400) {
            return (s / 3600) + "h";
        }
        return (s / 86400) + "d";
    }

    /**
     * Sends the player the spawn-history payload, optionally focusing a category tab. Rate-limited per
     * player: the GUI only needs this on open, so a client asking faster than that is either broken or
     * trying to make us rebuild the whole history in a loop.
     */
    public void syncTo(ServerPlayer player, String focusCategory) {
        long now = System.currentTimeMillis();
        Long last = lastSyncMs.get(player.getUUID());
        if (last != null && now - last < SYNC_COOLDOWN_MS) {
            return;
        }
        lastSyncMs.put(player.getUUID(), now);

        List<S2C_SyncHistoryPacket.Category> categories = new ArrayList<>();
        for (TrackerCategory category : config.registry().categories()) {
            categories.add(new S2C_SyncHistoryPacket.Category(category.id(), category.displayName(), category.color()));
        }
        List<S2C_SyncHistoryPacket.Entry> entries = new ArrayList<>();
        Map<String, Integer> caughtByPlayer = new LinkedHashMap<>();
        for (SpawnRecord r : store.all()) {
            // store.all() is newest-first, so the cap drops the oldest history rather than the newest.
            if (entries.size() < MAX_SYNC_ENTRIES) {
                entries.add(new S2C_SyncHistoryPacket.Entry(r.category(), r.species(), r.speciesId(), r.spawnTime(),
                        r.world(), r.x(), r.y(), r.z(), r.biome(), r.shiny(), r.caught(),
                        r.catcherName() == null ? "" : r.catcherName()));
            }
            if (r.caught() && r.catcherName() != null && !r.catcherName().isBlank()) {
                caughtByPlayer.merge(r.catcherName(), 1, Integer::sum);
            }
        }
        List<S2C_SyncHistoryPacket.Stat> stats = new ArrayList<>();
        caughtByPlayer.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .forEach(e -> stats.add(new S2C_SyncHistoryPacket.Stat(e.getKey(), e.getValue())));

        PlatformNetworking.sendToPlayer(player,
                new S2C_SyncHistoryPacket(categories, entries, stats, focusCategory == null ? "" : focusCategory));
    }

    /**
     * Spawns a real shiny Pikachu a few blocks from the caller ({@code /ct fakehit}) and lets it run
     * through the normal detection path, so it exercises the whole chain the way a real shiny would: the
     * chat announcement, the beam, and the catch/faint/despawn reports. Returns false if it couldn't be
     * placed.
     */
    public boolean sendTestBeam(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        BlockPos p = player.blockPosition();
        int tx = p.getX() + 6;
        int tz = p.getZ() + 3;
        int ty = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);
        try {
            var props = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(
                    "pikachu shiny=yes", " ", "=");
            com.cobblemon.mod.common.entity.pokemon.PokemonEntity entity = props.createEntity(world, null);
            entity.moveTo(tx + 0.5, ty, tz + 0.5, 0f, 0f);
            // Not suppressed: EntityEvent.ADD records + announces it like any real shiny spawn.
            if (!world.addFreshEntity(entity)) {
                return false;
            }
            CobbleTracker.LOGGER.debug("fakehit spawned a shiny Pikachu for {}", player.getGameProfile().getName());
            return true;
        } catch (Throwable t) {
            CobbleTracker.LOGGER.debug("Test spawn failed", t);
            return false;
        }
    }

    // ---- admin -----------------------------------------------------------

    public void openAdmin(ServerPlayer player) {
        GeneralSettings g = config.general();
        BeamSettings b = config.beam();
        MinimapSettings m = config.minimap();
        List<S2C_OpenAdminPacket.Setting> settings = new ArrayList<>();
        settings.add(setting("hide-exact-position", "Hide exact position", g.hideExactPosition()));
        settings.add(setting("show-title", "Show title on screen", g.showTitle()));
        settings.add(setting("beam-enabled", "Spawn beam", b.enabled()));
        settings.add(setting("beam-radius", "Beam radius (0=auto)", b.radius()));
        settings.add(setting("beam-duration-seconds", "Beam grace (s)", b.durationSeconds()));
        settings.add(setting("beam-height", "Beam height", b.height()));
        settings.add(setting("minimap-enabled", "Minimap waypoints", m.enabled()));
        settings.add(setting("minimap-xaero", "Xaero's Minimap", m.xaero()));
        settings.add(setting("minimap-voxelmap", "VoxelMap", m.voxelmap()));
        settings.add(setting("minimap-journeymap", "JourneyMap", m.journeymap()));
        settings.add(setting("minimap-use-beam-color", "Minimap dot uses beam colour", m.useBeamColor()));
        PlatformNetworking.sendToPlayer(player, new S2C_OpenAdminPacket(settings));
    }

    /**
     * Applies one admin edit in-memory (effective immediately). Disk config is unchanged until reload.
     * Values arrive from a client packet, so every one is clamped to a range the rest of the mod can
     * actually work with.
     */
    public void applyAdmin(String key, String value) {
        GeneralSettings g = config.general();
        BeamSettings b = config.beam();
        MinimapSettings m = config.minimap();
        switch (key) {
            case "hide-exact-position" ->
                    config = config.withGeneral(new GeneralSettings(g.chatPrefix(), parseBool(value), g.showTitle()));
            case "show-title" ->
                    config = config.withGeneral(new GeneralSettings(g.chatPrefix(), g.hideExactPosition(),
                            parseBool(value)));
            case "beam-enabled" ->
                    config = config.withBeam(new BeamSettings(parseBool(value), b.radius(), b.durationSeconds(),
                            b.height()));
            case "beam-radius" ->
                    config = config.withBeam(new BeamSettings(b.enabled(), parseInt(value, b.radius()),
                            b.durationSeconds(), b.height()));
            case "beam-duration-seconds" ->
                    config = config.withBeam(new BeamSettings(b.enabled(), b.radius(),
                            parseInt(value, b.durationSeconds()), b.height()));
            case "beam-height" ->
                    config = config.withBeam(new BeamSettings(b.enabled(), b.radius(), b.durationSeconds(),
                            parseInt(value, b.height())));
            case "minimap-enabled" -> config = config.withMinimap(new MinimapSettings(
                    parseBool(value), m.xaero(), m.voxelmap(), m.journeymap(), m.useBeamColor()));
            case "minimap-xaero" -> config = config.withMinimap(new MinimapSettings(
                    m.enabled(), parseBool(value), m.voxelmap(), m.journeymap(), m.useBeamColor()));
            case "minimap-voxelmap" -> config = config.withMinimap(new MinimapSettings(
                    m.enabled(), m.xaero(), parseBool(value), m.journeymap(), m.useBeamColor()));
            case "minimap-journeymap" -> config = config.withMinimap(new MinimapSettings(
                    m.enabled(), m.xaero(), m.voxelmap(), parseBool(value), m.useBeamColor()));
            case "minimap-use-beam-color" -> config = config.withMinimap(new MinimapSettings(
                    m.enabled(), m.xaero(), m.voxelmap(), m.journeymap(), parseBool(value)));
            default -> CobbleTracker.LOGGER.debug("Ignoring unknown admin setting '{}'", key);
        }
    }

    private static S2C_OpenAdminPacket.Setting setting(String key, String label, boolean value) {
        return new S2C_OpenAdminPacket.Setting(key, label, Boolean.toString(value), "bool");
    }

    private static S2C_OpenAdminPacket.Setting setting(String key, String label, int value) {
        return new S2C_OpenAdminPacket.Setting(key, label, Integer.toString(value), "int");
    }

    private static boolean parseBool(String v) {
        return v != null && Boolean.parseBoolean(v.trim());
    }

    private static int parseInt(String v, int fallback) {
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
