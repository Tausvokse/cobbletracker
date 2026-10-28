package com.cobbletracker.mod.tracker;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobbletracker.common.tracker.PokemonSnapshot;
import com.cobbletracker.common.spawn.VerticalSpawnSearch;
import com.cobbletracker.common.tracker.SpawnContext;
import com.cobbletracker.common.tracker.SpawnSettings;
import com.cobbletracker.common.tracker.SpecParser;
import com.cobbletracker.common.tracker.TrackerCategory;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.config.ConfigManager;
import com.cobbletracker.mod.event.CobblemonSnapshots;
import com.cobbletracker.mod.legendary.LegendaryDex;
import com.cobbletracker.mod.legendary.LegendaryDirector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Legacy category scheduler with optional server-wide rolls and per-player anchor cooldowns. */
public final class SpawnEngine {

    /** Bearings tried before giving up on placing a spawn near the anchor. */
    private static final int PLACEMENT_ATTEMPTS = 6;

    private final Random random = new Random();
    private final Map<String, Integer> tickCounters = new HashMap<>();
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();
    private final Map<String, List<String>> pools = new HashMap<>();
    /** Unsatisfiable categories already warned about. */
    private final Set<String> warnedUnmatched = new HashSet<>();
    /** Last scheduling decision, used to avoid repeated logging. */
    private final Map<String, Boolean> lastRunDecision = new HashMap<>();
    /** Cached label-based tier, or NO_TIER; absent entries have not been computed. */
    private final Map<String, String> tierLabels = new HashMap<>();

    /** Sentinel for "this category has no label-based tier", so absent still means "not computed yet". */
    private static final String NO_TIER = "";

    /** Clears cached pools + counters after a config reload. */
    public void reset() {
        tickCounters.clear();
        pools.clear();
        cooldowns.clear();
        warnedUnmatched.clear();
        lastRunDecision.clear();
        tierLabels.clear();
        // Reload invalidates natural-pool coverage as well as category settings.
        LegendaryDirector.invalidate();
        LegendaryDex.invalidate();
    }

    public void tick(MinecraftServer server, ConfigManager config, TrackerServer tracker) {
        for (TrackerCategory category : config.registry().categories()) {
            if (!category.enabled() || tracker.managesCategory(category)) {
                continue;
            }
            SpawnSettings settings = category.spawn();
            if (!settings.enabled() || !shouldRun(category, settings)) {
                continue;
            }
            int count = tickCounters.merge(category.id(), 1, Integer::sum);
            if (count < settings.intervalTicks()) {
                continue;
            }
            tickCounters.put(category.id(), 0);
            try {
                attempt(server, config, category, settings, tracker);
            } catch (Throwable t) {
                CobbleTracker.LOGGER.error("Spawn attempt failed for category {}", category.id(), t);
            }
        }
    }

    /** AUTO defers to matching natural tier entries; ALWAYS runs and NEVER disables scheduling. */
    private boolean shouldRun(TrackerCategory category, SpawnSettings settings) {
        boolean run = switch (settings.mode()) {
            // Cache tier labels; NO_TIER distinguishes no tier from an uncomputed entry.
            case AUTO -> !LegendaryDirector.coversTier(tierLabel(category));
            case ALWAYS -> true;
            case NEVER -> false;
        };
        Boolean previous = lastRunDecision.get(category.id());
        if (previous == null || previous != run) {
            lastRunDecision.put(category.id(), run);
            if (run) {
                CobbleTracker.LOGGER.info("Spawner for '{}' is active (mode {})", category.id(), settings.mode());
            } else {
                CobbleTracker.LOGGER.info("Spawner for '{}' is standing down: {}", category.id(),
                        settings.mode() == SpawnSettings.Mode.NEVER
                                ? "mode is 'never'"
                                : "Cobblemon's own spawn data already covers this tier (mode 'auto')");
            }
        }
        return run;
    }

    /** The category's tier label, computed once and cached. Null when it isn't a label-based tier. */
    private String tierLabel(TrackerCategory category) {
        String cached = tierLabels.computeIfAbsent(category.id(), id -> {
            String label = LegendaryDirector.tierLabel(category);
            return label == null ? NO_TIER : label;
        });
        return NO_TIER.equals(cached) ? null : cached;
    }

    /** Ticks left before this category's next attempt, or -1 if its spawner isn't running. */
    public int ticksUntilAttempt(TrackerCategory category) {
        SpawnSettings settings = category.spawn();
        if (!category.enabled() || !settings.enabled() || !Boolean.TRUE.equals(lastRunDecision.get(category.id()))) {
            return -1;
        }
        return Math.max(0, settings.intervalTicks() - tickCounters.getOrDefault(category.id(), 0));
    }

    /** Ticks left on a player's anchor cooldown for this category, or 0 if they're eligible now. */
    public long playerCooldownRemaining(TrackerCategory category, UUID player, long nowTicks) {
        Map<UUID, Long> cd = cooldowns.get(category.id());
        if (cd == null) {
            return 0;
        }
        Long started = cd.get(player);
        if (started == null) {
            return 0;
        }
        return Math.max(0, category.spawn().playerCooldownTicks() - (nowTicks - started));
    }

    private void attempt(MinecraftServer server, ConfigManager config, TrackerCategory category,
            SpawnSettings settings, TrackerServer tracker) {
        long now = server.getTickCount();
        List<ServerPlayer> eligible = eligiblePlayers(server, category, settings, now);
        if (eligible.isEmpty()) {
            return;
        }
        if (settings.distributeAmongPlayers()) {
            // One shared roll selects one anchor.
            if (random.nextDouble() >= settings.chance()) {
                return;
            }
            spawnFor(eligible.get(random.nextInt(eligible.size())), config, category, settings, tracker, now, true);
        } else {
            
            for (ServerPlayer player : eligible) {
                if (random.nextDouble() < settings.chance()) {
                    spawnFor(player, config, category, settings, tracker, now, true);
                }
            }
        }
    }

    /** Alive, non-spectator players in allowed dimensions without an active anchor cooldown. */
    private List<ServerPlayer> eligiblePlayers(MinecraftServer server, TrackerCategory category,
            SpawnSettings settings, long now) {
        Map<UUID, Long> cd = cooldowns.computeIfAbsent(category.id(), k -> new HashMap<>());
        cd.entrySet().removeIf(e -> now - e.getValue() >= settings.playerCooldownTicks());
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            
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

    /** Returns null on successful placement, otherwise a failure reason. */
    private String spawnFor(ServerPlayer player, ConfigManager config, TrackerCategory category,
            SpawnSettings settings, TrackerServer tracker, long now, boolean applyCooldown) {
        List<String> pool = poolFor(category, settings);
        if (applyCooldown) pool = pool.stream().filter(species -> !tracker.isReplacedSpecies(species)).toList();
        if (pool.isEmpty()) {
            CobbleTracker.LOGGER.warn("Spawn category {} has no candidate species", category.id());
            return "That category has no candidate species.";
        }
        ServerLevel world = player.serverLevel();
        // Evaluate conditions at the candidate position, which may differ from the player biome.
        Vec3 pos = null;
        SpawnContext context = null;
        String species = null;
        String lastBiome = "";
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS && species == null; attempt++) {
            Vec3 candidate = findSpawnPos(world, player.position(), settings);
            if (candidate == null) {
                continue;
            }
            BlockPos block = BlockPos.containing(candidate);
            Holder<Biome> holder = world.getBiome(block);
            String biome = holder.unwrapKey().map(k -> k.location().toString()).orElse("");
            lastBiome = biome;
            SpawnContext ctx = new SpawnContext(biome, world.dimension().location().toString(),
                    world.getDayTime(), world.isRaining(), world.isThundering(), block.getY());
            String chosen = chooseSpecies(holder, ctx, config, settings, pool);
            if (chosen != null) {
                pos = candidate;
                context = ctx;
                species = chosen;
            }
        }
        if (pos == null) {
            
            CobbleTracker.LOGGER.debug("No legendary rule fits near {} (last biome {}) - skipping this attempt",
                    player.getGameProfile().getName(), lastBiome);
            return "Nothing fits around here right now - check /checklegendary for what this spot allows.";
        }
        int level = settings.minLevel() == settings.maxLevel()
                ? settings.minLevel()
                : settings.minLevel() + random.nextInt(settings.maxLevel() - settings.minLevel() + 1);

        // An explicit shiny category requirement takes precedence over the optional spawn flag.
        boolean shiny = settings.shiny() || Boolean.TRUE.equals(SpecParser.expectation(category.spec(), "isShiny"));
        String spec = species + " level=" + level + (shiny ? " shiny=yes" : "");
        UUID entityId = null;
        try {
            PokemonProperties properties = PokemonProperties.Companion.parse(spec, " ", "=");
            PokemonEntity entity = properties.createEntity(world, null);
            if (entity == null) {
                return "Cobblemon could not build entity for spec: " + spec;
            }
            Pokemon pokemon = entity.getPokemon();
            if (pokemon == null) {
                entity.discard();
                return "Spawned entity has no Pokemon data.";
            }
            PokemonSnapshot snapshot = CobblemonSnapshots.of(pokemon);
            // Direct category recording bypasses matching, so validate before insertion.
            if (!config.registry().matches(category, snapshot, context)) {
                warnUnmatched(category, species, spec);
                entity.discard();
                return "What the spawner built doesn't satisfy the category's own spec - see the log.";
            }
            if (!canPlace(world, entity, pos)) {
                String selectedSpecies = species;
                List<String> candidates = pool;
                pos = findSpawnPos(world, player.position(), settings, at -> {
                    BlockPos block = BlockPos.containing(at);
                    Holder<Biome> holder = world.getBiome(block);
                    SpawnContext ctx = contextAt(world, block);
                    if (!config.registry().matches(category, snapshot, ctx)) return false;
                    if (usesContextRules(config, settings, candidates)
                            && LegendaryDirector.fitting(holder, ctx, config.legendaryRules(), candidates).stream()
                                    .noneMatch(candidate -> candidate.rule().speciesId().equals(selectedSpecies))) return false;
                    return canPlace(world, entity, at);
                });
                if (pos == null) {
                    entity.discard();
                    return "No space for this Pokemon within the configured horizontal and vertical spawn limits.";
                }
                context = contextAt(world, BlockPos.containing(pos));
            }
            entity.setYRot(random.nextFloat() * 360f);
            // Suppress ADD detection before insertion; addFreshEntity fires it synchronously.
            entityId = entity.getUUID();
            tracker.suppressDetection(entityId);
            if (!world.addFreshEntity(entity)) {
                tracker.releaseDetection(entityId);
                entity.discard();
                return "The world refused to accept the spawn there.";
            }
            // Manual attempts do not consume the scheduled anchor cooldown.
            if (applyCooldown) {
                cooldowns.computeIfAbsent(category.id(), k -> new HashMap<>()).put(player.getUUID(), now);
            }

            tracker.recordForCategory(category, snapshot, pokemon.getUuid(), entity.getUUID(),
                    BlockPos.containing(pos).getX(), BlockPos.containing(pos).getY(), BlockPos.containing(pos).getZ(),
                    context.dimension(), context.biome());
            CobbleTracker.LOGGER.debug("Spawned {} (lvl {}) for category {} near {}",
                    species, level, category.id(), player.getGameProfile().getName());
            return null;
        } catch (Throwable t) {
            if (entityId != null) {
                tracker.releaseDetection(entityId);
            }
            CobbleTracker.LOGGER.error("Failed to spawn {} for category {}", spec, category.id(), t);
            return "The spawn failed - see the server log.";
        }
    }

    /** Attempts placement without a chance roll, interval or anchor cooldown; returns null on success. */
    public String spawnNow(ServerPlayer player, ConfigManager config, TrackerCategory category,
            TrackerServer tracker) {
        try {
            return spawnFor(player, config, category, category.spawn(), tracker,
                    player.server.getTickCount(), false);
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Forced spawn failed for category {}", category.id(), t);
            return "The spawn failed - see the server log.";
        }
    }

    public List<String> candidatePool(TrackerCategory category) {
        return List.copyOf(poolFor(category, category.spawn()));
    }

    public static boolean usesContextRules(ConfigManager config, SpawnSettings settings, List<String> pool) {
        return settings.contextAware() && rulesCover(config, pool);
    }

    /** Selects from contextual legacy rules, or the flat category pool when no rules cover it. */
    private String chooseSpecies(Holder<Biome> biomeHolder, SpawnContext context, ConfigManager config,
            SpawnSettings settings, List<String> pool) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        if (usesContextRules(config, settings, pool)) {
            String chosen = LegendaryDirector.pickSpecies(biomeHolder, context, config.legendaryRules(),
                    pool, random);
            if (chosen != null) {
                return chosen;
            }
            // No contextual rule matches this position.
            return null;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /** True if any legendaries.yml rule names a species this category could actually spawn. */
    private static boolean rulesCover(ConfigManager config, List<String> pool) {
        for (var rule : config.legendaryRules()) {
            if (pool.contains(rule.speciesId())) {
                return true;
            }
        }
        return false;
    }

    /** Warns once when generated traits cannot satisfy the category spec. */
    private void warnUnmatched(TrackerCategory category, String species, String spec) {
        if (!warnedUnmatched.add(category.id())) {
            return;
        }
        CobbleTracker.LOGGER.warn("Spawner for '{}' built \"{}\" but the category's own spec (\"{}\") rejects it, "
                + "so nothing was placed - give the category a species-pool it can satisfy, relax the spec, or "
                + "turn its spawn block off. First mismatch was {}.", category.id(), spec, category.spec(), species);
    }

    private static SpawnContext contextAt(ServerLevel world, BlockPos pos) {
        String biome = world.getBiome(pos).unwrapKey().map(key -> key.location().toString()).orElse("");
        return new SpawnContext(biome, world.dimension().location().toString(), world.getDayTime(),
                world.isRaining(), world.isThundering(), pos.getY());
    }

    private static boolean canPlace(ServerLevel world, PokemonEntity entity, Vec3 pos) {
        entity.moveTo(pos.x, pos.y, pos.z, entity.getYRot(), 0f);
        AABB box = entity.getBoundingBox();
        return box.minY >= world.getMinBuildHeight() && box.maxY <= world.getMaxBuildHeight()
                && world.getWorldBorder().isWithinBounds(box)
                && world.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
                        BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                && world.noCollision(entity);
    }

    private Vec3 findSpawnPos(ServerLevel world, Vec3 anchor, SpawnSettings settings) {
        return findSpawnPos(world, anchor, settings, at -> true);
    }

    private Vec3 findSpawnPos(ServerLevel world, Vec3 anchor, SpawnSettings settings, Predicate<Vec3> suitable) {
        for (int i = 0; i < PLACEMENT_ATTEMPTS; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = settings.minDistance() + random.nextInt(settings.maxDistance() - settings.minDistance() + 1);
            int x = (int) Math.floor(anchor.x) + (int) Math.round(Math.cos(angle) * dist);
            int z = (int) Math.floor(anchor.z) + (int) Math.round(Math.sin(angle) * dist);
            if (!world.isLoaded(new BlockPos(x, world.getMinBuildHeight() + 1, z))) {
                continue;
            }
            var height = VerticalSpawnSearch.findSurface(anchor.y, settings.maxVerticalDistance(),
                    world.getMinBuildHeight(), world.getMaxBuildHeight() - 1, y -> {
                        BlockPos floor = new BlockPos(x, y, z);
                        var state = world.getBlockState(floor);
                        double top = Double.NaN;
                        for (AABB box : state.getCollisionShape(world, floor).toAabbs()) {
                            if (box.minX <= 0.5 && box.maxX >= 0.5 && box.minZ <= 0.5 && box.maxZ >= 0.5) {
                                top = Double.isNaN(top) ? box.maxY : Math.max(top, box.maxY);
                            }
                        }
                        if (Double.isNaN(top) && world.getFluidState(floor).is(FluidTags.WATER)) top = 1;
                        if (Double.isNaN(top)) return Double.NaN;
                        double feetY = y + top;
                        if (Math.abs(feetY - anchor.y) > settings.maxVerticalDistance()) return Double.NaN;
                        Vec3 feet = new Vec3(x + 0.5, feetY, z + 0.5);
                        AABB clearance = new AABB(x + 0.2, feetY, z + 0.2, x + 0.8, feetY + 2, z + 0.8);
                        if (clearance.maxY > world.getMaxBuildHeight()
                                || !world.getWorldBorder().isWithinBounds(clearance) || !world.noCollision(clearance)
                                || world.getFluidState(BlockPos.containing(feet)).is(FluidTags.LAVA)
                                || world.getFluidState(BlockPos.containing(feet.x, feet.y + 1, feet.z)).is(FluidTags.LAVA)
                                || !suitable.test(feet)) return Double.NaN;
                        return feetY;
                    });
            if (height.isPresent()) {
                return new Vec3(x + 0.5, height.getAsDouble(), z + 0.5);
            }
        }
        return null;
    }

    private List<String> poolFor(TrackerCategory category, SpawnSettings settings) {
        List<String> cached = pools.get(category.id());
        if (cached != null) {
            return cached;
        }
        List<String> source = settings.speciesPool().isEmpty() ? derivePool(category) : settings.speciesPool();
        List<String> usable = withoutBlacklisted(category, source);
        // Do not cache an empty pool while the species registry is still initializing.
        if (!usable.isEmpty()) {
            pools.put(category.id(), usable);
        }
        return usable;
    }

    /** Apply the blacklist before selecting species; direct category recording bypasses it. */
    private static List<String> withoutBlacklisted(TrackerCategory category, List<String> ids) {
        List<String> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            if (!category.isBlacklisted(id) && !category.isBlacklisted(path)) {
                out.add(id);
            }
        }
        return out;
    }

    /** Derives candidate species from the category's spec labels; empty label → all implemented species. */
    private List<String> derivePool(TrackerCategory category) {
        String spec = category.spec() == null ? "" : category.spec().toLowerCase(Locale.ROOT);
        String label = spec.contains("islegendary") ? "legendary"
                : spec.contains("ismythical") ? "mythical"
                : spec.contains("isultrabeast") ? "ultra_beast"
                : spec.contains("isancient") ? "ancient"
                : spec.contains("isstarter") ? "starter"
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
