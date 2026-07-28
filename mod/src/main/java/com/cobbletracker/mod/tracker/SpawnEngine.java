package com.cobbletracker.mod.tracker;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobbletracker.common.tracker.PokemonSnapshot;
import com.cobbletracker.common.tracker.SpawnSettings;
import com.cobbletracker.common.tracker.TargetKind;
import com.cobbletracker.common.tracker.TrackerCategory;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.config.ConfigManager;
import com.cobbletracker.mod.event.CobblemonSnapshots;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * CobbleTracker's own scheduled spawner, driven by each category's {@code spawn:} block. Once per
 * {@code interval-ticks} it tries to spawn one:
 *
 * <ul>
 *   <li>With {@code distribute-among-players}, one {@code chance} roll is made for the whole server and a
 *   random eligible player becomes the anchor, so the rate doesn't scale with player count. Set it false
 *   for an independent roll per player.</li>
 *   <li>A player just used as anchor is skipped for {@code player-cooldown-ticks}, so spawns spread out
 *   instead of always landing on whoever rolled first.</li>
 * </ul>
 *
 * Cobblemon calls are guarded; a spawn is recorded and announced under its own category via
 * {@link TrackerServer#recordForCategory}. Derived species pools are cached and cleared on reload.
 */
public final class SpawnEngine {

    /** Bearings tried before giving up on placing a spawn near the anchor. */
    private static final int PLACEMENT_ATTEMPTS = 6;

    private final Random random = new Random();
    private final Map<String, Integer> tickCounters = new HashMap<>();
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();
    private final Map<String, List<String>> derivedPools = new HashMap<>();

    /** Clears cached derived pools + counters after a config reload. */
    public void reset() {
        tickCounters.clear();
        derivedPools.clear();
        cooldowns.clear();
    }

    public void tick(MinecraftServer server, ConfigManager config, TrackerServer tracker) {
        for (TrackerCategory category : config.registry().categories()) {
            if (category.kind() != TargetKind.POKEMON || !category.enabled()) {
                continue;
            }
            SpawnSettings settings = category.spawn();
            if (!settings.enabled()) {
                continue;
            }
            int count = tickCounters.merge(category.id(), 1, Integer::sum);
            if (count < settings.intervalTicks()) {
                continue;
            }
            tickCounters.put(category.id(), 0);
            try {
                attempt(server, category, settings, tracker);
            } catch (Throwable t) {
                CobbleTracker.LOGGER.error("Spawn attempt failed for category {}", category.id(), t);
            }
        }
    }

    private void attempt(MinecraftServer server, TrackerCategory category, SpawnSettings settings,
            TrackerServer tracker) {
        long now = server.getTickCount();
        List<ServerPlayer> eligible = eligiblePlayers(server, category, settings, now);
        if (eligible.isEmpty()) {
            return;
        }
        if (settings.distributeAmongPlayers()) {
            // One server-wide roll → one anchor player (fair regardless of player count).
            if (random.nextDouble() > settings.chance()) {
                return;
            }
            spawnFor(eligible.get(random.nextInt(eligible.size())), category, settings, tracker, now);
        } else {
            // Independent roll per player.
            for (ServerPlayer player : eligible) {
                if (random.nextDouble() <= settings.chance()) {
                    spawnFor(player, category, settings, tracker, now);
                }
            }
        }
    }

    /**
     * Players this category may anchor a spawn on: alive, actually playing, in a dimension the category
     * tracks, and not still on cooldown from the last time they anchored one.
     */
    private List<ServerPlayer> eligiblePlayers(MinecraftServer server, TrackerCategory category,
            SpawnSettings settings, long now) {
        Map<UUID, Long> cd = cooldowns.computeIfAbsent(category.id(), k -> new HashMap<>());
        cd.entrySet().removeIf(e -> now - e.getValue() >= settings.playerCooldownTicks());
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            // A category limited to certain dimensions must not spawn outside them, or the spawn is
            // recorded under a category that would never have matched it.
            if (!category.allowsDimension(player.level().dimension().location().toString())) {
                continue;
            }
            if (cd.containsKey(player.getUUID())) {
                continue;
            }
            out.add(player);
        }
        return out;
    }

    private void spawnFor(ServerPlayer player, TrackerCategory category, SpawnSettings settings,
            TrackerServer tracker, long now) {
        List<String> pool = poolFor(category, settings);
        if (pool.isEmpty()) {
            CobbleTracker.LOGGER.warn("Spawn category {} has no candidate species", category.id());
            return;
        }
        String species = pool.get(random.nextInt(pool.size()));
        int level = settings.minLevel() == settings.maxLevel()
                ? settings.minLevel()
                : settings.minLevel() + random.nextInt(settings.maxLevel() - settings.minLevel() + 1);

        ServerLevel world = player.serverLevel();
        BlockPos pos = findSpawnPos(world, player.blockPosition(), settings);
        if (pos == null) {
            return;
        }

        String spec = species + " level=" + level + (settings.shiny() ? " shiny=yes" : "");
        UUID entityId = null;
        try {
            PokemonProperties properties = PokemonProperties.Companion.parse(spec, " ", "=");
            PokemonEntity entity = properties.createEntity(world, null);
            entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            // We record this spawn under THIS category below, so tell the ADD detector to skip it (it would
            // otherwise re-record it under whatever category first matches). Must precede addFreshEntity,
            // which fires EntityEvent.ADD synchronously.
            entityId = entity.getUUID();
            tracker.suppressDetection(entityId);
            if (!world.addFreshEntity(entity)) {
                tracker.releaseDetection(entityId);
                return;
            }
            // Anchor cooldown only on a real spawn.
            cooldowns.computeIfAbsent(category.id(), k -> new HashMap<>()).put(player.getUUID(), now);

            Pokemon pokemon = entity.getPokemon();
            PokemonSnapshot snapshot = CobblemonSnapshots.of(pokemon);
            String biome = CobblemonSnapshots.biomeId(world, pos);
            tracker.recordForCategory(category, snapshot, pokemon.getUuid(), entity.getUUID(),
                    pos.getX(), pos.getY(), pos.getZ(), world.dimension().location().toString(), biome);
            CobbleTracker.LOGGER.debug("Spawned {} (lvl {}) for category {} near {}",
                    species, level, category.id(), player.getGameProfile().getName());
        } catch (Throwable t) {
            if (entityId != null) {
                tracker.releaseDetection(entityId);
            }
            CobbleTracker.LOGGER.error("Failed to spawn {} for category {}", spec, category.id(), t);
        }
    }

    /**
     * A surface position a random bearing + distance from the anchor. A few bearings are tried before
     * giving up, so one unloaded or void column doesn't waste the whole interval's roll.
     */
    private BlockPos findSpawnPos(ServerLevel world, BlockPos anchor, SpawnSettings settings) {
        for (int i = 0; i < PLACEMENT_ATTEMPTS; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = settings.minDistance() + random.nextInt(settings.maxDistance() - settings.minDistance() + 1);
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * dist);
            if (!world.isLoaded(new BlockPos(x, world.getMinBuildHeight() + 1, z))) {
                continue;
            }
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= world.getMinBuildHeight()) {
                continue; // nothing but air all the way down - don't drop a Pokémon into the void
            }
            return new BlockPos(x, y, z);
        }
        return null;
    }

    private List<String> poolFor(TrackerCategory category, SpawnSettings settings) {
        if (!settings.speciesPool().isEmpty()) {
            return settings.speciesPool();
        }
        List<String> cached = derivedPools.get(category.id());
        if (cached != null) {
            return cached;
        }
        List<String> derived = derivePool(category);
        // Only cache a usable pool: an empty one usually means Cobblemon's registry wasn't ready yet, and
        // caching that would leave the category permanently unable to spawn.
        if (!derived.isEmpty()) {
            derivedPools.put(category.id(), derived);
        }
        return derived;
    }

    /** Derives candidate species from the category's spec labels; empty label → all implemented species. */
    private List<String> derivePool(TrackerCategory category) {
        String spec = category.spec() == null ? "" : category.spec().toLowerCase(Locale.ROOT);
        String label = spec.contains("islegendary") ? "legendary"
                : spec.contains("ismythical") ? "mythical"
                : spec.contains("isultrabeast") ? "ultra_beast"
                : null;
        List<String> ids = new ArrayList<>();
        try {
            for (Species sp : PokemonSpecies.getImplemented()) {
                if (label == null || containsIgnoreCase(sp.getLabels(), label)) {
                    ids.add(sp.getResourceIdentifier().toString());
                }
            }
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Failed to derive species pool for {}", category.id(), t);
        }
        return ids;
    }

    private static boolean containsIgnoreCase(Collection<String> labels, String label) {
        for (String l : labels) {
            if (l.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }
}
