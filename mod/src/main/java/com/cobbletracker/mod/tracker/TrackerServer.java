package com.cobbletracker.mod.tracker;

import com.cobbletracker.common.tracker.PokemonSnapshot;
import com.cobbletracker.common.tracker.SpawnContext;
import com.cobbletracker.common.tracker.SpawnDetails;
import com.cobbletracker.common.tracker.SpawnRecord;
import com.cobbletracker.common.tracker.TrackerCategory;
import com.cobbletracker.common.tracker.TrackerStore;
import com.cobbletracker.common.util.Obfuscation;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.announce.AnnouncementService;
import com.cobbletracker.mod.announce.DiscordWebhook;
import com.cobbletracker.mod.announce.PlayerPrefs;
import com.cobbletracker.common.notify.NotificationPreference;
import com.cobbletracker.mod.config.BeamSettings;
import com.cobbletracker.mod.config.ConfigManager;
import com.cobbletracker.mod.config.GeneralSettings;
import com.cobbletracker.mod.config.MinimapSettings;
import com.cobbletracker.mod.config.NotificationConfig;
import com.cobbletracker.mod.config.TrackerJsonStore;
import com.cobbletracker.mod.legendary.LegendaryReport;
import com.cobbletracker.mod.minimap.MinimapWaypoints;
import com.cobbletracker.mod.net.C2S_MinimapSupportPacket;
import com.cobbletracker.mod.net.PlatformNetworking;
import com.cobbletracker.mod.net.S2C_ClientSettingsPacket;
import com.cobbletracker.mod.net.S2C_OpenAdminPacket;
import com.cobbletracker.mod.net.S2C_SpawnEndedPacket;
import com.cobbletracker.mod.net.S2C_SyncHistoryPacket;
import com.cobbletracker.mod.net.S2C_TrackedSpawnPacket;
import com.cobbletracker.mod.text.Msg;
import com.cobbletracker.mod.spawning.SpeciesSpawnEngine;
import com.cobbletracker.mod.placeholder.PlaceholderValues;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Server-owned encounter history, active tracking and debounced persistence. */
public final class TrackerServer {

    private static volatile TrackerServer instance;

    private final MinecraftServer server;
    private volatile ConfigManager config;
    private final TrackerStore store = new TrackerStore();
    private final SpawnEngine spawnEngine = new SpawnEngine();
    private final SpeciesSpawnEngine speciesSpawnEngine;
    private final Map<UUID, Long> lastSpawnReport = new LinkedHashMap<>();
    private final Map<UUID, Long> lastForcedSpawn = new LinkedHashMap<>();
    private final Set<UUID> pendingPreferenceSync = ConcurrentHashMap.newKeySet();
    /** Minimap mods each connected client advertised (via {@link C2S_MinimapSupportPacket}). */
    private final Map<UUID, C2S_MinimapSupportPacket> minimapSupport = new ConcurrentHashMap<>();
    /** Bounded, insertion-ordered live encounters keyed by Pokemon UUID. */
    private final Map<UUID, LiveSpawn> liveSpawns = Collections.synchronizedMap(new LinkedHashMap<>());
    /** Pokémon UUIDs we've already seen or recorded, with bounded FIFO eviction so chunk reloads are deduplicated. */
    private final Map<UUID, Boolean> seenPokemon = Collections.synchronizedMap(new LinkedHashMap<>(2048, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
            return size() > 16384;
        }
    });
    /** Entity UUIDs the custom {@link SpawnEngine} spawned; the ADD detector skips these (engine records them). */
    private final Set<UUID> suppressedDetection = ConcurrentHashMap.newKeySet();
    /** Per-player history synchronization rate limit. */
    private final Map<UUID, Long> lastSyncMs = new ConcurrentHashMap<>();
    private final Map<UUID, String> clientDimensions = new LinkedHashMap<>();

    /** A tracked spawn still in the world, watched for its fate (mutable bookkeeping). */
    private static final class LiveSpawn {
        final UUID entityId;
        final String categoryId;
        final String species;
        final int color;
        final SpawnRecord record;
        final long spawnedAtMs = System.currentTimeMillis();
        String dimension;
        int lastX;
        int lastY;
        int lastZ;
        boolean everSeen;
        int missingTicks;

        LiveSpawn(UUID entityId, String categoryId, String species, int color, SpawnRecord record) {
            this.record = record;
            this.entityId = entityId;
            this.categoryId = categoryId;
            this.species = species;
            this.color = color;
        }
    }

    /** Witness distance distinguishing despawn from chunk unloading. */
    private static final int DESPAWN_WITNESS_RANGE = 160;
    /** Absolute cap (ms) on how long a live spawn is tracked before it's dropped silently, freeing memory. */
    private static final long LIVE_SPAWN_MAX_AGE_MS = 30 * 60 * 1000L;
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
        this.speciesSpawnEngine = new SpeciesSpawnEngine(server);
        if (!config.readable() || !speciesSpawnEngine.reload(config.spawnGroups(), config.speciesSpawns())) {
            CobbleTracker.LOGGER.error("Custom spawning is inactive because the configuration is invalid");
        }
        Msg.setPrefix(config.general().chatPrefix());
        TrackerJsonStore.loadInto(store);
        PlayerPrefs.load();
        applyStorageCaps();
        for (String cat : store.categories()) {
            for (SpawnRecord r : store.category(cat)) {
                seenPokemon.put(r.id(), Boolean.TRUE);
            }
        }
    }

    /** Apply configured history caps after loading. Retain records for removed categories on disk. */
    private void applyStorageCaps() {
        boolean trimmed = false;
        for (TrackerCategory category : config.registry().categories()) {
            trimmed |= store.trimTo(category.id(), category.maxStored());
        }
        if (trimmed) {
            dirty = true;
        }
    }

    public static void start(MinecraftServer server) {
        DiscordWebhook.start();
        com.cobbletracker.mod.protection.SpawnProtectionModule.register();
        com.cobbletracker.mod.hof.HallOfFameModule.load();
        instance = new TrackerServer(server);
        CobbleTracker.LOGGER.info("CobbleTracker ready - {} tracker categories loaded",
                instance.config.registry().categories().size());
    }

    public static void stop() {
        TrackerServer running = instance;
        instance = null;
        if (running != null) {
            running.speciesSpawnEngine.close();
            TrackerJsonStore.save(running.store);
            PlayerPrefs.save();
        }
        com.cobbletracker.mod.boss.WorldBossModule.clearAll();
        com.cobbletracker.mod.protection.SpawnProtectionModule.clearAll();
        com.cobbletracker.mod.hof.HallOfFameModule.save();
        PlayerPrefs.clear();
        com.cobbletracker.mod.event.CobblemonBridge.clear();
        PlaceholderValues.clear();
        com.cobbletracker.mod.placeholder.TabPlaceholders.clear();
        DiscordWebhook.stop();
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
        speciesSpawnEngine.playerLeft(playerId);
        com.cobbletracker.mod.protection.SpawnProtectionModule.playerLeft(playerId);
        clientDimensions.remove(playerId);
        lastSpawnReport.remove(playerId);
        lastForcedSpawn.remove(playerId);
        pendingPreferenceSync.remove(playerId);
        minimapSupport.remove(playerId);
        lastSyncMs.remove(playerId);
    }

    /** Server post-tick: run the custom spawner and flush persistence when dirty (debounced). */
    public void tick(MinecraftServer server) {
        com.cobbletracker.mod.event.CobblemonBridge.tick(server);
        if (server.getTickCount() % 10 == 0) {
            for (UUID playerId : List.copyOf(pendingPreferenceSync)) {
                pendingPreferenceSync.remove(playerId);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) refreshClient(player);
            }
        }
        // The custom spawner runs every tick; it tracks its own per-category interval + fairness.
        spawnEngine.tick(server, config, this);
        speciesSpawnEngine.tick();
        if (server.getTickCount() % 20 == 0) PlaceholderValues.refresh(config, store, speciesSpawnEngine);
        if (server.getTickCount() % 20 == 0) com.cobbletracker.mod.protection.SpawnProtectionModule.tick(config.spawnProtection());
        if (server.getTickCount() % 20 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                String previous = clientDimensions.get(player.getUUID());
                if (previous != null && !previous.equals(player.level().dimension().location().toString())) {
                    refreshClient(player);
                }
            }
        }
        if (server.getTickCount() % 100 == 0) com.cobbletracker.mod.placeholder.TabPlaceholders.register(config);
        com.cobbletracker.mod.boss.WorldBossModule.tick(server, config.worldBoss());
        if (!liveSpawns.isEmpty() && server.getTickCount() % 20 == 0) {
            watchLiveSpawns(server);
        }
        if (dirty) {
            if (saveCooldown <= 0) {
                TrackerJsonStore.saveAsync(store);
                dirty = false;
                saveCooldown = 200; // at most once per ~10s
            } else {
                saveCooldown--;
            }
        }
        // Preference persistence has its own dirty state, independent of spawn history.
        if (server.getTickCount() % 200 == 0) {
            PlayerPrefs.saveIfDirty();
        }
    }

    /** Idempotent entity-add detection; skips engine-recorded and previously seen Pokemon UUIDs. */
    public void recordSpawn(PokemonSnapshot snapshot, UUID pokemonId, UUID entityId,
            int x, int y, int z, String world, String biome, SpawnContext context) {
        recordSpawn(snapshot, pokemonId, entityId, x, y, z, world, biome, context, CommandHooks.captureOrigin());
    }

    public void recordSpawn(PokemonSnapshot snapshot, UUID pokemonId, UUID entityId,
            int x, int y, int z, String world, String biome, SpawnContext context,
            com.cobbletracker.common.tracker.CommandHookGuard.Origin origin) {
        if (suppressedDetection.remove(entityId)) {
            return; // the SpawnEngine spawned this and records it under its own category
        }
        if (seenPokemon.put(pokemonId, Boolean.TRUE) != null) {
            restoreTrackedSpawn(snapshot, pokemonId, entityId, x, y, z, world, biome, context);
            return; // already recorded once - this is a chunk reload / re-add of the same Pokémon
        }
        config.registry().firstMatch(snapshot, context)
                .ifPresent(category -> recordInternal(category, snapshot, pokemonId, entityId, x, y, z, world, biome, origin));
    }

    public boolean needsLiveRestore(UUID pokemonId) {
        return !liveSpawns.containsKey(pokemonId) && store.findById(pokemonId)
                .filter(record -> !record.caught() && record.outcome().isEmpty()).isPresent();
    }

    private void restoreTrackedSpawn(PokemonSnapshot snapshot, UUID pokemonId, UUID entityId,
            int x, int y, int z, String world, String biome, SpawnContext context) {
        if (!needsLiveRestore(pokemonId)) return;
        SpawnRecord record = store.findById(pokemonId).orElse(null);
        if (record == null) return;
        TrackerCategory category = config.registry().byId(record.category()).orElse(null);
        if (category == null || !category.enabled() || !category.allowsDimension(world)
                || category.isBlacklisted(snapshot.speciesName()) || category.isBlacklisted(snapshot.speciesPath())
                || !config.registry().matches(category, snapshot, context)) return;
        SpawnRecord current = new SpawnRecord(record.id(), record.category(), record.species(), record.speciesId(),
                record.spawnTime(), world, x, y, z, biome, record.shiny(), record.caught(), record.catcherUuid(),
                record.catcherName(), record.outcome(), record.outcomeBy(), record.details(), record.traits());
        broadcastTrackedSpawn(category, current, entityId, AnnouncementService.recipients(server,
                config.notification(category.id()), current));
    }

    /** Reject known or engine-owned entities before building a snapshot. */
    public boolean isNewSpawn(UUID pokemonId, UUID entityId) {
        if (suppressedDetection.contains(entityId)) {
            return true;
        }
        return !seenPokemon.containsKey(pokemonId);
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
        recordInternal(category, snapshot, pokemonId, entityId, x, y, z, world, biome, CommandHooks.captureOrigin());
    }

    private void recordInternal(TrackerCategory category, PokemonSnapshot snapshot, UUID pokemonId, UUID entityId,
            int x, int y, int z, String world, String biome,
            com.cobbletracker.common.tracker.CommandHookGuard.Origin origin) {
        seenPokemon.put(pokemonId, Boolean.TRUE);

        List<String> traits = new ArrayList<>();
        if (snapshot.shiny()) traits.add("shiny");
        if (snapshot.boss()) traits.add("boss");
        if (snapshot.legendary()) traits.add("legendary");
        if (snapshot.mythical()) traits.add("mythic");
        if (snapshot.ultraBeast()) traits.add("ultra-beast");
        if (snapshot.ancient()) traits.add("ancient");
        if (snapshot.starter()) traits.add("starter");
        if (snapshot.alolan()) traits.add("alolan");
        if (snapshot.hisuian()) traits.add("hisuian");
        if (snapshot.galarian()) traits.add("galarian");
        if (snapshot.paldean()) traits.add("paldean");
        String traitsStr = String.join(",", traits);

        SpawnRecord record = new SpawnRecord(pokemonId, category.id(), snapshot.speciesName(), snapshot.speciesId(),
                Instant.now().getEpochSecond(), world, x, y, z, biome, snapshot.shiny(), false, null, null, "", "",
                new SpawnDetails(snapshot.level(), snapshot.gender(), snapshot.nature(), snapshot.ability(),
                        snapshot.ivs(), snapshot.evs()),
                traitsStr);
        store.record(record, category.maxStored());
        dirty = true;

        
        com.cobbletracker.mod.boss.WorldBossModule.onBossSpawn(snapshot, pokemonId, entityId, world, config.worldBoss());

        
        ServerPlayer nearestPlayer = findNearestPlayer(world, x, y, z);
        com.cobbletracker.mod.protection.SpawnProtectionModule.protect(pokemonId, nearestPlayer, config.spawnProtection());

        NotificationConfig note = config.notification(category.id());
        List<ServerPlayer> recipients = AnnouncementService.recipients(server, note, record);
        AnnouncementService.announce(config.general(), note, record, recipients);

        
        if (note.enabled()) {
            boolean exact = !config.general().hideExactPosition();
            DiscordWebhook.post(note.discordWebhook(), note.discordTemplate(), record,
                    Obfuscation.maybeObfuscate(record.x(), exact), record.y(),
                    Obfuscation.maybeObfuscate(record.z(), exact),
                    config.discordIntegration(), category.color());
        }
        broadcastTrackedSpawn(category, record, entityId, recipients);
        CommandHooks.onSpawn(server, category, record, origin);
        CobbleTracker.LOGGER.debug("Recorded {} under '{}' at {},{},{} (biome {})",
                record.species(), category.id(), x, y, z, record.biome());
    }

    private ServerPlayer findNearestPlayer(String world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        ServerPlayer nearest = null;
        double bestDistSq = Double.MAX_VALUE;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension().location().toString().equals(world)) {
                double d2 = player.blockPosition().distSqr(pos);
                if (d2 <= 100 * 100 && d2 < bestDistSq) {
                    bestDistSq = d2;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    /** Track the encounter independently of history retention and send indicators to eligible recipients. */
    private void broadcastTrackedSpawn(TrackerCategory category, SpawnRecord record, UUID entityId,
            List<ServerPlayer> recipients) {
        LiveSpawn live = new LiveSpawn(entityId, category.id(), record.species(), category.color(), record);
        live.dimension = record.world();
        live.lastX = record.x();
        live.lastY = record.y();
        live.lastZ = record.z();
        List<Map.Entry<UUID, LiveSpawn>> evicted = new ArrayList<>();
        synchronized (liveSpawns) {
            liveSpawns.put(record.id(), live);
            Iterator<Map.Entry<UUID, LiveSpawn>> oldest = liveSpawns.entrySet().iterator();
            while (liveSpawns.size() > MAX_LIVE_SPAWNS && oldest.hasNext()) {
                Map.Entry<UUID, LiveSpawn> dropped = oldest.next();
                evicted.add(Map.entry(dropped.getKey(), dropped.getValue()));
                oldest.remove();
            }
        }
        for (var dropped : evicted) {
            com.cobbletracker.mod.boss.WorldBossModule.onBossEnded(dropped.getKey());
            com.cobbletracker.mod.protection.SpawnProtectionModule.clearProtection(dropped.getKey());
            endSpawn(dropped.getValue().entityId);
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

    /** Sends a manual historical waypoint using the client-advertised format and coordinate policy. */
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
            if (!historyVisibleTo(player, player.hasPermissions(2), r)) {
                player.sendSystemMessage(Msg.warn("That spawn is outside your permitted tracking area."));
                return;
            }
            boolean hide = config.general().hideExactPosition();
            int wx = Obfuscation.maybeObfuscate(r.x(), player.hasPermissions(2) || !hide);
            int wz = Obfuscation.maybeObfuscate(r.z(), player.hasPermissions(2) || !hide);
            int color = config.registry().byId(r.category()).map(TrackerCategory::color).orElse(0xFFFFAA00);
            boolean sent = MinimapWaypoints.send(player, caps, config.minimap(), r.species(), color,
                    wx, r.y(), wz, r.world());
            if (!sent) {
                
                player.sendSystemMessage(Msg.warn("Waypoints are turned off for your minimap on this server."));
            }
        }, () -> player.sendSystemMessage(Msg.warn("That spawn is no longer tracked.")));
    }

    /** Client-ready handshake: record map support, synchronize policy and replay active indicators. */
    public void clientReady(ServerPlayer player, C2S_MinimapSupportPacket caps) {
        minimapSupport.put(player.getUUID(), caps);
        clientDimensions.putIfAbsent(player.getUUID(), "");
        CobbleTracker.LOGGER.debug("Client ready: {} (xaero={} voxelmap={} journeymap={})",
                player.getGameProfile().getName(), caps.xaero(), caps.voxelmap(), caps.journeymap());
        refreshClient(player, false);
    }

    /** Replay eligible live indicators after the client-ready handshake, not during initial join. */
    private void resendLiveSpawns(ServerPlayer player) {
        if (!PlatformNetworking.canReach(player)) {
            return;
        }
        List<Map.Entry<UUID, LiveSpawn>> snapshot;
        synchronized (liveSpawns) {
            snapshot = new ArrayList<>(liveSpawns.entrySet());
        }
        if (snapshot.isEmpty()) {
            return;
        }
        GeneralSettings g = config.general();
        BeamSettings beam = config.beam();
        String playerDimension = player.level().dimension().location().toString();
        snapshot.sort(java.util.Comparator.comparingDouble(entry -> {
            LiveSpawn live = entry.getValue();
            double dx = live.lastX - player.getX(), dz = live.lastZ - player.getZ();
            return dx * dx + dz * dz;
        }));
        int sent = 0;
        for (Map.Entry<UUID, LiveSpawn> entry : snapshot) {
            LiveSpawn live = entry.getValue();
            if (!playerDimension.equals(live.dimension)) continue;
            TrackerCategory category = config.registry().byId(live.categoryId).orElse(null);
            if (category == null || !category.enabled()) {
                continue; // its category was removed from the config since it spawned
            }
            NotificationConfig note = config.notification(live.categoryId);
            BlockPos at = new BlockPos(live.lastX, live.lastY, live.lastZ);
            if (!AnnouncementService.reaches(note, live.categoryId, live.dimension, at, player)) {
                continue;
            }
            int x = Obfuscation.maybeObfuscate(live.lastX, !g.hideExactPosition());
            int z = Obfuscation.maybeObfuscate(live.lastZ, !g.hideExactPosition());
            try {
                PlatformNetworking.sendToPlayer(player, new S2C_TrackedSpawnPacket(live.entityId,
                        live.categoryId, live.species, category.color(), x, live.lastY, z, live.dimension,
                        beam.enabled(), beam.radius(), beam.durationTicks(), beam.height()));
                sent++;
                if (sent >= 24) break;
            } catch (Throwable t) {
                CobbleTracker.LOGGER.debug("Beam resend failed for {}", player.getGameProfile().getName(), t);
            }
        }
        if (sent > 0) {
            CobbleTracker.LOGGER.debug("Re-sent {} live beam(s) to {}", sent, player.getGameProfile().getName());
        }
    }

    /** A tracked Pokémon was captured. Records the catcher, announces it and stops watching it. */
    public void markCaught(UUID pokemonId, UUID catcher, String catcherName) {
        LiveSpawn live = liveSpawns.remove(pokemonId);
        SpawnRecord captured = store.markCaught(pokemonId, catcher, catcherName).orElse(null);
        if (captured == null && live != null && store.findById(pokemonId).isEmpty()) {
            captured = live.record.asCaught(catcher, catcherName);
        }
        if (captured != null) {
            SpawnRecord r = captured;
            dirty = true;
            // History eviction must not discard the capture hooks of an encounter still being watched.
            config.registry().byId(r.category())
                    .ifPresent(category -> CommandHooks.onCatch(server, category, r, catcherName));

            
            com.cobbletracker.mod.hof.HallOfFameModule.checkAndRecord(r, catcher, catcherName, config.hallOfFame(), server);

            
            NotificationConfig note = config.notification(r.category());
            if (note.enabled()) {
                int color = config.registry().byId(r.category()).map(TrackerCategory::color).orElse(0xFFFFAA00);
                DiscordWebhook.postOutcome(note.discordWebhook(), r, "caught", catcherName, config.discordIntegration(), color);
            }
        }
        com.cobbletracker.mod.boss.WorldBossModule.onBossEnded(pokemonId);
        com.cobbletracker.mod.protection.SpawnProtectionModule.clearProtection(pokemonId);
        if (live != null) {
            String who = catcherName == null || catcherName.isBlank() ? "someone" : catcherName;
            AnnouncementService.lifecycle(lifecycleRecipients(pokemonId, live), live.categoryId, live.color, live.species,
                    "was caught by " + who + "!");
            endSpawn(live.entityId);
        }
    }

    /** A tracked Pokémon fainted (defeated in battle / killed). Announces and stops watching it. */
    public void markFainted(UUID pokemonId, String killerName) {
        LiveSpawn live = liveSpawns.remove(pokemonId);
        // Persist outcomes even after live tracking expires or is evicted.
        outcomeRecord(pokemonId, live, "defeated", killerName == null ? "" : killerName).ifPresent(r -> {
            dirty = true;
            NotificationConfig note = config.notification(r.category());
            if (note.enabled()) {
                int color = config.registry().byId(r.category()).map(TrackerCategory::color).orElse(0xFFFFAA00);
                DiscordWebhook.postOutcome(note.discordWebhook(), r, "defeated", killerName, config.discordIntegration(), color);
            }
        });
        com.cobbletracker.mod.boss.WorldBossModule.onBossEnded(pokemonId);
        com.cobbletracker.mod.protection.SpawnProtectionModule.clearProtection(pokemonId);
        if (live != null) {
            String phrase = killerName == null || killerName.isBlank()
                    ? "was defeated!" : "was defeated by " + killerName + "!";
            AnnouncementService.lifecycle(lifecycleRecipients(pokemonId, live), live.categoryId, live.color, live.species, phrase);
            endSpawn(live.entityId);
        }
    }

    private java.util.Optional<SpawnRecord> outcomeRecord(UUID pokemonId, LiveSpawn live, String outcome, String by) {
        var updated = store.markOutcome(pokemonId, outcome, by);
        if (updated.isPresent() || live == null || store.findById(pokemonId).isPresent()) return updated;
        return java.util.Optional.of(live.record.withOutcome(outcome, by));
    }

    /**
     * Use the original announcement audience; fall back to current notification policy if history is
     * unavailable.
     */
    private List<ServerPlayer> lifecycleRecipients(UUID recordId, LiveSpawn live) {
        NotificationConfig note = config.notification(live.categoryId);
        return store.findById(recordId)
                .map(r -> AnnouncementService.recipients(server, note, r))
                .orElseGet(() -> AnnouncementService.recipients(server, note, live.categoryId, live.dimension,
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

    /** Only nearby witnesses establish despawn; unloaded chunks remain tracked until expiry. */
    private void watchLiveSpawns(MinecraftServer server) {
        long now = System.currentTimeMillis();
        // Do not hold the tracking monitor while sending packets or announcements.
        List<Map.Entry<UUID, LiveSpawn>> snapshot;
        synchronized (liveSpawns) {
            snapshot = new ArrayList<>(liveSpawns.entrySet());
        }
        List<UUID> finished = new ArrayList<>();
        Set<String> changedDimensions = new HashSet<>();
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
                if (found instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pokemon
                        && !com.cobbletracker.mod.event.CobblemonBridge.isWild(pokemon)) {
                    com.cobbletracker.mod.boss.WorldBossModule.onBossEnded(pokemonId);
                    com.cobbletracker.mod.protection.SpawnProtectionModule.clearProtection(pokemonId);
                    endSpawn(live.entityId);
                    finished.add(pokemonId);
                    continue;
                }
                live.everSeen = true;
                live.missingTicks = 0;
                String dimension = found.level().dimension().location().toString();
                if (!dimension.equals(live.dimension)) {
                    changedDimensions.add(live.dimension);
                    changedDimensions.add(dimension);
                }
                live.dimension = dimension;
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
            // An unwitnessed disappearance may be a chunk unload.
            if (!live.everSeen) {
                live.missingTicks += 20;
                if (live.missingTicks >= 200) {
                    endSpawn(live.entityId);
                    finished.add(pokemonId); // never actually saw it; give up quietly
                }
                continue;
            }
            if (playerNear(server, live)) {
                live.missingTicks += 20;
                if (live.missingTicks >= 60) {
                    outcomeRecord(pokemonId, live, "despawned", "").ifPresent(r -> {
                        dirty = true;
                        NotificationConfig note = config.notification(r.category());
                        if (note.enabled()) {
                            int color = config.registry().byId(r.category()).map(TrackerCategory::color).orElse(0xFFFFAA00);
                            DiscordWebhook.postOutcome(note.discordWebhook(), r, "despawned", "",
                                    config.discordIntegration(), color);
                        }
                    });
                    com.cobbletracker.mod.boss.WorldBossModule.onBossEnded(pokemonId);
                    com.cobbletracker.mod.protection.SpawnProtectionModule.clearProtection(pokemonId);
                    AnnouncementService.lifecycle(lifecycleRecipients(pokemonId, live), live.categoryId, live.color, live.species,
                            "despawned.");
                    endSpawn(live.entityId);
                    finished.add(pokemonId);
                }
            } else {
                live.missingTicks = 0; // out of everyone's range - just an unloaded chunk
            }
        }
        if (!finished.isEmpty()) {
            finished.forEach(com.cobbletracker.mod.boss.WorldBossModule::onBossEnded);
            finished.forEach(com.cobbletracker.mod.protection.SpawnProtectionModule::clearProtection);
            synchronized (liveSpawns) {
                finished.forEach(liveSpawns::remove);
            }
        }
        if (!changedDimensions.isEmpty()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (clientDimensions.containsKey(player.getUUID())
                        && changedDimensions.contains(player.level().dimension().location().toString())) refreshClient(player);
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

    /** Atomically replaces effective configuration after validation; returns false on rejection. */
    public boolean reload() {
        ConfigManager loaded = ConfigManager.load();
        if (!loaded.readable()) {
            CobbleTracker.LOGGER.error("Keeping the previous CobbleTracker configuration - the files on disk "
                    + "could not be parsed");
            return false;
        }
        if (!speciesSpawnEngine.reload(loaded.spawnGroups(), loaded.speciesSpawns())) return false;
        config = loaded;
        Msg.setPrefix(config.general().chatPrefix());
        applyStorageCaps();
        spawnEngine.reset();
        if (!config.spawnProtection().enabled()) com.cobbletracker.mod.protection.SpawnProtectionModule.clearAll();
        refreshBosses();
        refreshClients();
        CobbleTracker.LOGGER.info("CobbleTracker config reloaded - {} categories",
                config.registry().categories().size());
        return true;
    }

    private void refreshBosses() {
        com.cobbletracker.mod.boss.WorldBossModule.clearAll();
        if (!config.worldBoss().enabled()) return;
        for (LiveSpawn live : liveSpawns.values()) {
            if (config.registry().byId(live.categoryId).filter(TrackerCategory::enabled).isEmpty()) continue;
            for (ServerLevel level : server.getAllLevels()) {
                if (!(level.getEntity(live.entityId) instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pokemon)
                        || !com.cobbletracker.mod.event.CobblemonBridge.isWild(pokemon)) continue;
                try {
                    var snapshot = com.cobbletracker.mod.event.CobblemonSnapshots.of(pokemon.getPokemon());
                    com.cobbletracker.mod.boss.WorldBossModule.onBossSpawn(snapshot, pokemon.getPokemon().getUuid(),
                            pokemon.getUUID(), level.dimension().location().toString(), config.worldBoss());
                } catch (RuntimeException | LinkageError failure) {
                    CobbleTracker.LOGGER.warn("Could not restore boss visuals for {}", live.entityId, failure);
                }
                break;
            }
        }
    }

    public boolean managesCategory(TrackerCategory category) {
        return speciesSpawnEngine.managesCategory(category);
    }

    public boolean isReplacedSpecies(String species) {
        return speciesSpawnEngine.isReplacedSpecies(species);
    }

    public void sendSpawnReport(ServerPlayer player, String group) {
        if (allowSpawnReport(player)) speciesSpawnEngine.sendReport(player, group);
    }

    private boolean allowSpawnReport(ServerPlayer player) {
        long now = System.nanoTime();
        Long previous = lastSpawnReport.get(player.getUUID());
        if (previous != null && now - previous < 5_000_000_000L) return false;
        lastSpawnReport.put(player.getUUID(), now);
        return true;
    }

    private void refreshClients() {
        for (ServerPlayer player : AnnouncementService.onlinePlayers(server)) {
            refreshClient(player);
        }
    }

    private void refreshClient(ServerPlayer player) {
        refreshClient(player, true);
    }

    private void refreshClient(ServerPlayer player, boolean resetBeams) {
        if (!PlatformNetworking.canReach(player)) return;
        try {
            PlatformNetworking.sendToPlayer(player, new S2C_ClientSettingsPacket(config.hunt().enabled(), resetBeams, config.minimap()));
            resendLiveSpawns(player);
            clientDimensions.put(player.getUUID(), player.level().dimension().location().toString());
        } catch (RuntimeException | LinkageError failure) {
            CobbleTracker.LOGGER.debug("Could not refresh client visuals for {}", player.getGameProfile().getName(), failure);
        }
    }

    

    /** Sends the player the full spawn-history payload for the tracker GUI (All tab). */
    public boolean syncTo(ServerPlayer player) {
        return syncTo(player, "");
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

    /** Sends recent legendary history without coordinates; no client mod required. */
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

    /** Sends rate-limited history filtered by player visibility and coordinate policy. */
    public boolean syncTo(ServerPlayer player, String focusCategory) {
        long now = System.currentTimeMillis();
        Long last = lastSyncMs.get(player.getUUID());
        if (last != null && now - last < SYNC_COOLDOWN_MS) {
            return false;
        }
        lastSyncMs.put(player.getUUID(), now);

        boolean operator = player.hasPermissions(2);
        boolean exact = operator || !config.general().hideExactPosition();
        List<S2C_SyncHistoryPacket.Category> categories = new ArrayList<>();
        for (TrackerCategory category : config.registry().categories()) {
            NotificationPreference pref = PlayerPrefs.get(player.getUUID(), category.id());
            categories.add(new S2C_SyncHistoryPacket.Category(category.id(), category.displayName(),
                    category.color(), pref.enabled(), pref.muteSound(), pref.radius(),
                    config.notification(category.id()).enabled(),
                    (pref.chat() ? 1 : 0) | (pref.title() ? 2 : 0) | (pref.actionbar() ? 4 : 0)));
        }
        List<S2C_SyncHistoryPacket.Entry> entries = new ArrayList<>();
        Map<String, Integer> caughtByPlayer = new LinkedHashMap<>();
        for (SpawnRecord r : store.all()) {
            // store.all() is newest-first, so the cap drops the oldest history rather than the newest.
            if (entries.size() < MAX_SYNC_ENTRIES && historyVisibleTo(player, operator, r)) {
                SpawnDetails d = r.details();
                entries.add(new S2C_SyncHistoryPacket.Entry(r.category(), r.species(), r.speciesId(), r.spawnTime(),
                        r.world(), Obfuscation.maybeObfuscate(r.x(), exact), r.y(),
                        Obfuscation.maybeObfuscate(r.z(), exact), r.biome(), r.shiny(), r.caught(),
                        r.catcherName() == null ? "" : r.catcherName(),
                        d.level(), d.gender(), d.nature(), d.ability(), d.ivs(), d.evs()));
            }
            // Position-free capture counts remain server-wide.
            if (r.caught() && r.catcherName() != null && !r.catcherName().isBlank()) {
                caughtByPlayer.merge(r.catcherName(), 1, Integer::sum);
            }
        }
        List<S2C_SyncHistoryPacket.Stat> stats = new ArrayList<>();
        caughtByPlayer.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .forEach(e -> stats.add(new S2C_SyncHistoryPacket.Stat(e.getKey(), e.getValue())));

        var payload = new S2C_SyncHistoryPacket(categories.stream().limit(128).toList(), entries, stats,
                focusCategory == null ? "" : focusCategory);
        net.minecraft.network.FriendlyByteBuf measured = new net.minecraft.network.FriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer());
        try {
            while (true) {
                measured.clear();
                S2C_SyncHistoryPacket.STREAM_CODEC.encode(measured, payload);
                if (measured.readableBytes() <= 900_000) break;
                if (entries.isEmpty()) {
                    player.sendSystemMessage(Msg.warn("Tracker category names exceed the history packet limit."));
                    return false;
                }
                entries.subList(Math.max(0, entries.size() / 2), entries.size()).clear();
            }
        } finally {
            measured.release();
        }
        PlatformNetworking.sendToPlayer(player, payload);
        return true;
    }

    /** Filters history by current category and audience policy; operators bypass the radius filter. */
    private boolean historyVisibleTo(ServerPlayer player, boolean operator, SpawnRecord r) {
        if (config.registry().byId(r.category()).isEmpty()) {
            return false;
        }
        if (operator) {
            return true;
        }
        NotificationConfig notification = config.notification(r.category());
        int radius = notification.broadcastRadius();
        if (radius <= 0 && !notification.playToAll()) radius = 128;
        if (radius <= 0) {
            return true;
        }
        if (!player.level().dimension().location().toString().equals(r.world())) {
            return false;
        }
        return player.blockPosition().distSqr(new BlockPos(r.x(), r.y(), r.z())) <= (long) radius * radius;
    }

    /** Spawns a shiny Pikachu through normal detection for the operator preview command. */
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
            // Normal entity-add detection records and announces this spawn.
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

    /** Reports legendary availability and the active scheduler countdown in chat. */
    public void sendLegendaryCheck(ServerPlayer player) {
        if (!allowSpawnReport(player)) return;
        if (speciesSpawnEngine.managesGroup("legendary")) speciesSpawnEngine.sendReport(player, "legendary");
        else LegendaryReport.send(player, config, spawnEngine, server.getTickCount());
    }

    /** Attempts a contextual legendary spawn immediately; returns null on success. */
    public String forceLegendarySpawn(ServerPlayer player) {
        long now = System.nanoTime();
        Long previous = lastForcedSpawn.get(player.getUUID());
        if (previous != null && now - previous < 5_000_000_000L) return "Wait five seconds between forced spawn attempts.";
        lastForcedSpawn.put(player.getUUID(), now);
        if (speciesSpawnEngine.managesGroup("legendary")) return speciesSpawnEngine.spawnNow(player, "legendary");
        String categoryId = legendaryCategoryId();
        if (categoryId.isEmpty()) {
            return "No legendary tracker category is configured.";
        }
        TrackerCategory category = config.registry().byId(categoryId).orElse(null);
        if (category == null || !category.enabled()) {
            return "The legendary tracker category is disabled.";
        }
        return spawnEngine.spawnNow(player, config, category, this);
    }

    

    /** Sends clickable personal notification settings in chat. */
    public void sendNotifyReport(ServerPlayer player) {
        List<TrackerCategory> categories = config.registry().categories();
        if (categories.isEmpty()) {
            player.sendSystemMessage(Msg.warn("This server has no tracker categories configured."));
            return;
        }
        player.sendSystemMessage(Msg.parse(Msg.prefix())
                .append(Component.literal("Your spawn notifications")
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
        for (TrackerCategory category : categories) {
            NotificationPreference pref = PlayerPrefs.get(player.getUUID(), category.id());
            boolean serverOff = !config.notification(category.id()).enabled();
            MutableComponent line = Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(category.displayName()).withStyle(Style.EMPTY
                            .withColor(TextColor.fromRgb(category.color())).withBold(true)))
                    .append(Component.literal(" "))
                    .append(Component.literal(pref.enabled() ? "[ON]" : "[OFF]").withStyle(Style.EMPTY
                            .withColor(pref.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/cobbletracker notify " + category.id() + (pref.enabled() ? " off" : " on")))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to turn " + (pref.enabled() ? "off" : "on"))))));
            if (pref.muteSound()) {
                line.append(Component.literal(" (muted)").withStyle(ChatFormatting.GRAY));
            }
            if (pref.radius() > 0) {
                line.append(Component.literal(" (within " + pref.radius() + "m)").withStyle(ChatFormatting.GRAY));
            }
            if (serverOff) {
                
                line.append(Component.literal(" - off server-wide").withStyle(ChatFormatting.DARK_GRAY));
            }
            player.sendSystemMessage(line);
        }
    }

    /** True if the id names a configured category (so the command can reject typos rather than store them). */
    public boolean hasCategory(String categoryId) {
        return config.registry().byId(categoryId).isPresent();
    }

    /** Category ids, for command suggestions. */
    public List<String> categoryIds() {
        List<String> out = new ArrayList<>();
        for (TrackerCategory c : config.registry().categories()) {
            out.add(c.id());
        }
        return out;
    }

    /** Applies one change to the caller's preferences and confirms it in chat. */
    public void setNotifyPref(ServerPlayer player, String categoryId, NotificationPreference pref) {
        NotificationPreference previous = PlayerPrefs.get(player.getUUID(), categoryId);
        if (!previous.equals(pref)) {
            PlayerPrefs.set(player.getUUID(), categoryId, pref);
            if (previous.enabled() != pref.enabled() || previous.radius() != pref.radius()) {
                pendingPreferenceSync.add(player.getUUID());
            }
        }
        String name = config.registry().byId(categoryId).map(TrackerCategory::displayName).orElse(categoryId);
        
        StringBuilder extra = new StringBuilder();
        if (pref.muteSound()) {
            extra.append(", sound muted");
        }
        if (!pref.chat()) extra.append(", chat off");
        if (!pref.title()) extra.append(", title off");
        if (!pref.actionbar()) extra.append(", actionbar off");
        if (pref.radius() > 0) {
            extra.append(", within ").append(pref.radius()).append(" blocks");
        }
        player.sendSystemMessage(Msg.success("Notifications for " + name + " are "
                + (pref.enabled() ? "on" : "off") + extra + "."));
    }

    

    /**
     * Builds the in-memory admin panel. Beam parameters are configured on disk; minimap state includes
     * client support.
     */
    public void openAdmin(ServerPlayer player) {
        GeneralSettings g = config.general();
        MinimapSettings m = config.minimap();
        C2S_MinimapSupportPacket caps = minimapSupport.get(player.getUUID());
        List<S2C_OpenAdminPacket.Setting> settings = new ArrayList<>();
        settings.add(setting("hide-exact-position", "Hide exact position", g.hideExactPosition()));
        settings.add(setting("show-title", "Show title on screen", g.showTitle()));
        settings.add(setting("minimap-enabled", "Minimap waypoints", m.enabled()));
        settings.add(minimapSetting("minimap-xaero", "Xaero's Minimap", m.xaero(),
                caps != null && caps.xaero()));
        settings.add(minimapSetting("minimap-voxelmap", "VoxelMap", m.voxelmap(),
                caps != null && caps.voxelmap()));
        settings.add(minimapSetting("minimap-journeymap", "JourneyMap", m.journeymap(),
                caps != null && caps.journeymap()));
        settings.add(setting("minimap-use-beam-color", "Minimap dot uses beam colour", m.useBeamColor()));
        PlatformNetworking.sendToPlayer(player, new S2C_OpenAdminPacket(settings));
    }

    /** Shows effective format availability for this client; changes apply server-wide. */
    private static S2C_OpenAdminPacket.Setting minimapSetting(String key, String label, boolean enabled,
            boolean installed) {
        String suffix = installed ? "" : " (not installed)";
        return setting(key, label + suffix, enabled && installed);
    }

    /** Applies bounded client-supplied settings in memory. Disk configuration remains unchanged. */
    public void applyAdmin(String key, String value) {
        GeneralSettings g = config.general();
        MinimapSettings m = config.minimap();
        switch (key) {
            case "hide-exact-position" ->
                    config = config.withGeneral(new GeneralSettings(g.chatPrefix(), parseBool(value), g.showTitle(),
                            g.showSpawnLocationInChat()));
            case "show-title" ->
                    config = config.withGeneral(new GeneralSettings(g.chatPrefix(), g.hideExactPosition(),
                            parseBool(value), g.showSpawnLocationInChat()));
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
        refreshClients();
    }

    private static S2C_OpenAdminPacket.Setting setting(String key, String label, boolean value) {
        return new S2C_OpenAdminPacket.Setting(key, label, Boolean.toString(value), "bool");
    }

    private static boolean parseBool(String v) {
        return v != null && Boolean.parseBoolean(v.trim());
    }

}
