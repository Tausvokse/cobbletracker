package com.cobbletracker.mod.legendary;

import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.spawning.CobblemonSpawnBridge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

/** Reads legendary availability from Cobblemon's loaded world spawn pool, including datapack entries. */
public final class LegendaryDex {

    private LegendaryDex() {
    }

    /** Species labels that make a Pokémon "legendary" for this report. */
    private static final Set<String> LEGENDARY_LABELS = Set.of("legendary", "mythical", "ultra_beast", "ultrabeast");

    /** Legendary index cache TTL in milliseconds. */
    private static final long INDEX_TTL_MS = 15_000L;

    private static volatile long indexedAt;
    private static volatile List<Indexed> cachedLegendaries = List.of();
    private static volatile int cachedPoolSize = -1;

    /** One legendary spawn entry, judged against a specific position. */
    public record Entry(
            String speciesId,
            String speciesName,
            String bucket,
            float weight,
            boolean eligible,
            List<String> blockers,
            boolean partiallyChecked,
            double chance) {

        public Entry {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }

    /** What {@link #report} found, plus the context it was judged in. */
    public record Report(
            String dimension,
            String biome,
            boolean day,
            boolean raining,
            boolean thundering,
            List<Entry> entries,
            boolean poolAvailable) {

        public Report {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        /** True when the loaded natural pool has no legendary entries. */
        public boolean empty() {
            return entries.isEmpty();
        }

        public List<Entry> eligible() {
            return entries.stream().filter(Entry::eligible).toList();
        }
    }

    /** Everything about the place being judged, read once so the sweep doesn't re-query the world per entry. */
    private record Env(
            Holder<Biome> biomeHolder,
            String biome,
            String dimension,
            int y,
            long dayTime,
            boolean raining,
            boolean thundering,
            int light,
            boolean canSeeSky,
            int moonPhase) {
    }

    /** Evaluates availability and bucket-relative chances at the player position. */
    public static Report report(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        String dimension = level.dimension().location().toString();
        String biome = biomeId(level, pos);
        long dayTime = level.getDayTime() % 24000L;
        boolean day = dayTime < 12000L;
        boolean raining = level.isRaining();
        boolean thundering = level.isThundering();

        List<SpawnDetail> details;
        try {
            details = CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL().getDetails();
        } catch (Throwable t) {
            CobbleTracker.LOGGER.debug("Cobblemon spawn pool unavailable", t);
            return new Report(dimension, biome, day, raining, thundering, List.of(), false);
        }

        Env env = new Env(safeBiomeHolder(level, pos), biome, dimension, pos.getY(), dayTime, raining,
                thundering, safeLight(level, pos), safeCanSeeSky(level, pos), safeMoonPhase(level));

        List<Indexed> index = index(details);
        Map<String, Float> bucketWeights = bucketWeights();
        // The chance denominator includes all eligible entries in each bucket.
        Map<String, Double> eligibleWeightByBucket = new HashMap<>();
        for (SpawnDetail detail : details) {
            if ((detail instanceof PokemonSpawnDetail || detail instanceof PokemonHerdSpawnDetail) && fitsHere(detail, env)) {
                eligibleWeightByBucket.merge(bucketName(detail), (double) Math.max(0f, detail.getWeight()),
                        Double::sum);
            }
        }

        // Merge entries by species; eligible chances add together.
        Map<String, Entry> bySpecies = new LinkedHashMap<>();
        for (Indexed indexed : index) {
            SpawnDetail detail = indexed.detail();
            boolean herd = detail instanceof PokemonHerdSpawnDetail;
            Judgement judgement = judge(detail, env);
            String bucket = bucketName(detail);
            double chance = 0;
            if (judgement.eligible() && !herd) {
                double bucketShare = bucketShare(bucketWeights, bucket);
                double inBucket = eligibleWeightByBucket.getOrDefault(bucket, 0.0);
                chance = inBucket <= 0 ? 0 : bucketShare * (Math.max(0f, detail.getWeight()) / inBucket);
            }
            String id = indexed.species().getResourceIdentifier().toString();
            Entry candidate = new Entry(id, indexed.species().getName(), bucket, detail.getWeight(),
                    judgement.eligible(), judgement.blockers(), judgement.partiallyChecked() || herd, chance);
            bySpecies.merge(id, candidate, LegendaryDex::better);
        }
        List<Entry> entries = new ArrayList<>(bySpecies.values());
        
        entries.sort(Comparator.comparingInt((Entry e) -> e.eligible() ? 0 : 1)
                .thenComparing(Comparator.comparingDouble(Entry::chance).reversed())
                .thenComparingInt(e -> e.blockers().size())
                .thenComparing(Entry::speciesName));
        return new Report(dimension, biome, day, raining, thundering, entries, true);
    }

    /** Which of two entries for the same species to show: eligible beats blocked, fewer blockers beats more. */
    private static Entry better(Entry a, Entry b) {
        if (a.eligible() && b.eligible()) {
            return new Entry(a.speciesId(), a.speciesName(), a.bucket(), a.weight(), true, List.of(),
                    a.partiallyChecked() || b.partiallyChecked(), a.chance() + b.chance());
        }
        if (a.eligible()) {
            return a;
        }
        if (b.eligible()) {
            return b;
        }
        return a.blockers().size() <= b.blockers().size() ? a : b;
    }

    

    /** A legendary pool entry paired with the species it resolved to, so neither is looked up twice. */
    private record Indexed(SpawnDetail detail, Species species) {
    }

    /** Caches species resolution; invalidated by TTL, pool size changes and reload. */
    private static List<Indexed> index(List<SpawnDetail> details) {
        long now = System.currentTimeMillis();
        if (now - indexedAt < INDEX_TTL_MS && cachedPoolSize == details.size()) {
            return cachedLegendaries;
        }
        List<Indexed> legendaries = new ArrayList<>();
        for (SpawnDetail detail : details) {
            for (Species resolved : SpawnSpecies.of(detail)) {
                if (isLegendary(resolved)) legendaries.add(new Indexed(detail, resolved));
            }
        }
        cachedLegendaries = List.copyOf(legendaries);
        cachedPoolSize = details.size();
        indexedAt = now;
        return cachedLegendaries;
    }

    /** Drops the cached index, so the next report re-reads the pool (used after a datapack reload). */
    public static void invalidate() {
        indexedAt = 0;
        cachedPoolSize = -1;
        cachedLegendaries = List.of();
    }

    

    /** The verdict on one spawn detail: whether it fits here, and if not, the human-readable reasons. */
    private record Judgement(boolean eligible, List<String> blockers, boolean partiallyChecked) {
    }

    /** Short-circuit eligibility check without collecting failure descriptions. */
    private static boolean fitsHere(SpawnDetail detail, Env env) {
        if (!biomeAllowed(detail, env)) {
            return false;
        }
        List<SpawningCondition<?>> conditions = detail.getConditions();
        if (conditions != null && !conditions.isEmpty()) {
            boolean any = false;
            for (SpawningCondition<?> condition : conditions) {
                if (fits(condition, env, null)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        List<SpawningCondition<?>> anti = detail.getAnticonditions();
        if (anti != null) {
            for (SpawningCondition<?> condition : anti) {
                if (fits(condition, env, null)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Judgement judge(SpawnDetail detail, Env env) {
        Set<String> blockers = new LinkedHashSet<>();
        boolean partial = false;

        if (!biomeAllowed(detail, env)) {
            // Use Cobblemon's resolved biome set before evaluating conditions.
            blockers.add("wrong biome");
        }

        List<SpawningCondition<?>> conditions = detail.getConditions();
        if (conditions != null && !conditions.isEmpty()) {
            Set<String> best = null;
            boolean anySatisfied = false;
            for (SpawningCondition<?> condition : conditions) {
                Set<String> reasons = new LinkedHashSet<>();
                boolean ok = fits(condition, env, reasons);
                partial |= usesUncheckableFields(condition);
                if (ok) {
                    anySatisfied = true;
                    break;
                }
                // Report the alternative with the fewest unmet conditions.
                if (best == null || reasons.size() < best.size()) {
                    best = reasons;
                }
            }
            if (!anySatisfied) {
                // Evaluation errors must add a blocker; an empty blocker list means eligible.
                if (best == null || best.isEmpty()) {
                    blockers.add("conditions not met");
                } else {
                    blockers.addAll(best);
                }
            }
        }

        List<SpawningCondition<?>> anti = detail.getAnticonditions();
        if (anti != null) {
            for (SpawningCondition<?> condition : anti) {
                partial |= usesUncheckableFields(condition);
                if (fits(condition, env, null)) {
                    blockers.add("excluded here");
                    break;
                }
            }
        }

        return new Judgement(blockers.isEmpty(), new ArrayList<>(blockers), partial);
    }

    /** Uses the resolved biome union. An empty set defers to per-condition checks. */
    private static boolean biomeAllowed(SpawnDetail detail, Env env) {
        Set<ResourceLocation> valid = detail.getValidBiomes();
        if (valid == null || valid.isEmpty()) {
            return true;
        }
        for (ResourceLocation id : valid) {
            if (id.toString().equals(env.biome())) {
                return true;
            }
        }
        return false;
    }

    /** Check each alternative's biome set with its other conditions, not the detail-level union. */
    private static boolean fits(SpawningCondition<?> c, Env env, Set<String> reasons) {
        boolean ok = true;
        try {
            if (!conditionBiomeFits(c, env)) {
                ok = false;
                if (!note(reasons, "wrong biome")) {
                    return false;
                }
            }
            if (c.getTimeRange() != null && !c.getTimeRange().contains((int) env.dayTime())) {
                ok = false;
                if (!note(reasons, env.dayTime() < 12000L ? "needs night" : "needs day")) {
                    return false;
                }
            }
            if (c.isRaining() != null && c.isRaining() != env.raining()) {
                ok = false;
                if (!note(reasons, c.isRaining() ? "needs rain" : "needs clear weather")) {
                    return false;
                }
            }
            if (c.isThundering() != null && c.isThundering() != env.thundering()) {
                ok = false;
                if (!note(reasons, c.isThundering() ? "needs a thunderstorm" : "needs no thunderstorm")) {
                    return false;
                }
            }
            if (c.getMinY() != null && env.y() < c.getMinY()) {
                ok = false;
                if (!note(reasons, "needs Y >= " + Math.round(c.getMinY()))) {
                    return false;
                }
            }
            if (c.getMaxY() != null && env.y() > c.getMaxY()) {
                ok = false;
                if (!note(reasons, "needs Y <= " + Math.round(c.getMaxY()))) {
                    return false;
                }
            }
            if (c.getDimensions() != null && !c.getDimensions().isEmpty()
                    && !containsId(c.getDimensions(), env.dimension())) {
                ok = false;
                if (!note(reasons, "wrong dimension")) {
                    return false;
                }
            }
            if (c.getCanSeeSky() != null && c.getCanSeeSky() != env.canSeeSky()) {
                ok = false;
                if (!note(reasons, c.getCanSeeSky() ? "needs open sky" : "needs to be under cover")) {
                    return false;
                }
            }
            if (c.getMoonPhase() != null && !c.getMoonPhase().contains(env.moonPhase())) {
                ok = false;
                if (!note(reasons, "needs a different moon phase")) {
                    return false;
                }
            }
            if (c.getMinLight() != null && env.light() < c.getMinLight()) {
                ok = false;
                if (!note(reasons, "needs more light")) {
                    return false;
                }
            }
            if (c.getMaxLight() != null && env.light() > c.getMaxLight()) {
                ok = false;
                if (!note(reasons, "needs less light")) {
                    return false;
                }
            }
            return ok;
        } catch (Throwable t) {
            CobbleTracker.LOGGER.debug("Couldn't evaluate a spawn condition", t);
            return false;
        }
    }

    /** Collects a blocker, or signals short-circuit evaluation when no collector is provided. */
    private static boolean note(Set<String> reasons, String reason) {
        if (reasons == null) {
            return false;
        }
        reasons.add(reason);
        return true;
    }

    private static boolean conditionBiomeFits(SpawningCondition<?> c, Env env) {
        Set<RegistryLikeCondition<Biome>> biomes = c.getBiomes();
        if (biomes == null || biomes.isEmpty()) {
            return true;
        }
        Holder<Biome> holder = env.biomeHolder();
        if (holder == null) {
            return true; // can't tell; don't invent a blocker
        }
        for (RegistryLikeCondition<Biome> condition : biomes) {
            if (condition.fits(holder)) {
                return true;
            }
        }
        return false;
    }

    /** Structures, markers and slime chunks need a real SpawnablePosition, so they go unchecked here. */
    private static boolean usesUncheckableFields(SpawningCondition<?> c) {
        try {
            return notEmpty(c.getStructures()) || notEmpty(c.getMarkers()) || c.isSlimeChunk() != null;
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean containsId(List<ResourceLocation> ids, String wanted) {
        for (ResourceLocation id : ids) {
            if (id.toString().equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    

    private static boolean isLegendary(Species species) {
        try {
            for (String label : species.getLabels()) {
                if (LEGENDARY_LABELS.contains(label.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // labels unavailable on this build - fall through to "not legendary"
        }
        return false;
    }

    private static String bucketName(SpawnDetail detail) {
        try {
            return CobblemonSpawnBridge.bucketName(detail);
        } catch (Throwable t) {
            return "?";
        }
    }

    /** Bucket name → configured weight, from Cobblemon's own spawner config. */
    private static Map<String, Float> bucketWeights() {
        Map<String, Float> out = new HashMap<>();
        try {
            out.putAll(CobblemonSpawnBridge.worldBucketWeights());
        } catch (Throwable t) {
            CobbleTracker.LOGGER.debug("Couldn't read Cobblemon's spawn buckets", t);
        }
        return out;
    }

    /** Returns zero when bucket weights are unavailable. */
    private static double bucketShare(Map<String, Float> weights, String bucket) {
        if (weights.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (float w : weights.values()) {
            total += Math.max(0f, w);
        }
        if (total <= 0) {
            return 0;
        }
        return Math.max(0f, weights.getOrDefault(bucket, 0f)) / total;
    }

    private static String biomeId(ServerLevel level, BlockPos pos) {
        try {
            return level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("");
        } catch (Throwable t) {
            return "";
        }
    }

    private static Holder<Biome> safeBiomeHolder(ServerLevel level, BlockPos pos) {
        try {
            return level.getBiome(pos);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int safeLight(ServerLevel level, BlockPos pos) {
        try {
            return level.getMaxLocalRawBrightness(pos);
        } catch (Throwable t) {
            return 15;
        }
    }

    private static boolean safeCanSeeSky(ServerLevel level, BlockPos pos) {
        try {
            return level.canSeeSky(pos);
        } catch (Throwable t) {
            return true;
        }
    }

    private static int safeMoonPhase(ServerLevel level) {
        try {
            return level.getMoonPhase();
        } catch (Throwable t) {
            return 0;
        }
    }
}
