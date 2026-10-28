package com.cobbletracker.mod.legendary;

import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobbletracker.common.legendary.LegendaryRule;
import com.cobbletracker.common.tracker.SpawnContext;
import com.cobbletracker.common.tracker.TrackerCategory;
import com.cobbletracker.mod.CobbleTracker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/** Natural tier coverage and contextual species selection for the legacy scheduler. */
public final class LegendaryDirector {

    private LegendaryDirector() {
    }

    /** Natural-pool coverage cache TTL in milliseconds. */
    private static final long POOL_CACHE_MS = 15_000L;

    private static volatile long poolScannedAt;
    private static volatile Set<String> poolLabels = Set.of();

    /** Drops the cached pool verdict, so the next check re-reads it (used after a config/datapack reload). */
    public static void invalidate() {
        poolScannedAt = 0;
        poolLabels = Set.of();
    }

    /** Tests natural coverage for label-based tiers; null and non-label categories are not covered. */
    public static boolean coversTier(String label) {
        return label != null && labelsInPool().contains(label);
    }

    /** The species label this category's spec is about, or null if it isn't a label-based tier. */
    public static String tierLabel(TrackerCategory category) {
        String spec = category.spec() == null ? "" : category.spec().toLowerCase(Locale.ROOT);
        // Negated labels do not establish tier coverage.
        if (spec.contains("!islegendary") || spec.contains("!ismythical") || spec.contains("!isultrabeast")) {
            return null;
        }
        if (spec.contains("islegendary")) {
            return "legendary";
        }
        if (spec.contains("ismythical")) {
            return "mythical";
        }
        if (spec.contains("isultrabeast")) {
            return "ultra_beast";
        }
        return null;
    }

    /** Species labels that appear on at least one entry of Cobblemon's live world spawn pool. */
    private static Set<String> labelsInPool() {
        long now = System.currentTimeMillis();
        if (now - poolScannedAt < POOL_CACHE_MS) {
            return poolLabels;
        }
        poolScannedAt = now;
        Set<String> found = new HashSet<>();
        try {
            for (SpawnDetail detail : CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL().getDetails()) {
                for (Species species : SpawnSpecies.of(detail)) {
                    for (String label : species.getLabels()) {
                        String l = label.toLowerCase(Locale.ROOT);
                        if (l.equals("legendary") || l.equals("mythical") || l.equals("ultra_beast")
                                || l.equals("ultrabeast")) {
                            found.add(l.equals("ultrabeast") ? "ultra_beast" : l);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // An unavailable natural pool does not disable legacy scheduling.
            CobbleTracker.LOGGER.debug("Couldn't scan Cobblemon's spawn pool for tier coverage", t);
            poolLabels = Set.of();
            return poolLabels;
        }
        poolLabels = Set.copyOf(found);
        return poolLabels;
    }

    

    /** Weighted selection at the candidate position; null when no rule matches. */
    public static String pickSpecies(Holder<Biome> biomeHolder, SpawnContext context,
            List<LegendaryRule> rules, Collection<String> allowed, Random random) {
        List<Candidate> fitting = fitting(biomeHolder, context, rules, allowed);
        if (fitting.isEmpty()) {
            return null;
        }
        double roll = random.nextDouble();
        for (Candidate candidate : fitting) {
            roll -= candidate.share();
            if (roll <= 0) {
                return candidate.rule().speciesId();
            }
        }
        return fitting.get(fitting.size() - 1).rule().speciesId();
    }

    /** A rule that fits here, with its share of the pick. */
    public record Candidate(LegendaryRule rule, double share) {
    }

    /** Normalized matching rules shared by placement and reporting. */
    public static List<Candidate> fitting(Holder<Biome> biomeHolder, SpawnContext context,
            List<LegendaryRule> rules, Collection<String> allowed) {
        List<LegendaryRule> matched = new ArrayList<>();
        double total = 0;
        for (LegendaryRule rule : rules) {
            if (!allowed.isEmpty() && !allowed.contains(rule.speciesId())) {
                continue; // outside the category's pool / blacklisted
            }
            Set<String> tags = resolveTags(biomeHolder, rule);
            if (rule.matches(context.biome(), tags, context.dimension(), context.day(),
                    context.raining(), context.thundering(), context.y())) {
                matched.add(rule);
                total += rule.weight();
            }
        }
        if (matched.isEmpty() || total <= 0) {
            return List.of();
        }
        List<Candidate> out = new ArrayList<>(matched.size());
        for (LegendaryRule rule : matched) {
            out.add(new Candidate(rule, rule.weight() / total));
        }
        out.sort((a, b) -> Double.compare(b.share(), a.share()));
        return out;
    }

    /** Which of a rule's {@code #tag} biome entries the spawn position's biome actually carries. */
    private static Set<String> resolveTags(Holder<Biome> holder, LegendaryRule rule) {
        List<String> tagEntries = rule.tagEntries();
        if (tagEntries.isEmpty() || holder == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String entry : tagEntries) {
            try {
                ResourceLocation id = ResourceLocation.parse(entry.substring(1));
                if (holder.is(TagKey.create(Registries.BIOME, id))) {
                    out.add(entry);
                }
            } catch (Throwable ignored) {
                // Invalid tags do not match.
            }
        }
        return out;
    }

}
