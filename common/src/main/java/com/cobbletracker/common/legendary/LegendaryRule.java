package com.cobbletracker.common.legendary;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Legacy legendary spawn conditions; biome tags are resolved by the caller. */
public record LegendaryRule(
        String speciesId,
        double weight,
        Set<String> biomes,
        Set<String> dimensions,
        String time,
        Boolean raining,
        Boolean thundering,
        Integer minY,
        Integer maxY) {

    public LegendaryRule {
        speciesId = speciesId == null ? "" : speciesId.trim().toLowerCase(Locale.ROOT);
        if (!speciesId.isEmpty() && !speciesId.contains(":")) speciesId = "cobblemon:" + speciesId;
        weight = weight <= 0 || !Double.isFinite(weight) ? 1.0 : weight;
        biomes = biomes == null ? Set.of() : Set.copyOf(biomes);
        dimensions = dimensions == null ? Set.of() : Set.copyOf(dimensions);
        String t = time == null ? "any" : time.trim().toLowerCase(Locale.ROOT);
        time = t.equals("day") || t.equals("night") ? t : "any";
    }

    /** Tests world context using biome tags resolved by the caller. */
    public boolean matches(String biomeId, Set<String> matchingTags, String dimensionId, boolean day,
            boolean isRaining, boolean isThundering, int y) {
        if (!biomes.isEmpty() && !biomeFits(biomeId, matchingTags)) {
            return false;
        }
        if (!dimensions.isEmpty() && (dimensionId == null
                || !dimensions.contains(dimensionId.toLowerCase(Locale.ROOT)))) {
            return false;
        }
        if (time.equals("day") && !day) {
            return false;
        }
        if (time.equals("night") && day) {
            return false;
        }
        if (raining != null && raining != isRaining) {
            return false;
        }
        if (thundering != null && thundering != isThundering) {
            return false;
        }
        if (minY != null && y < minY) {
            return false;
        }
        return maxY == null || y <= maxY;
    }

    private boolean biomeFits(String biomeId, Set<String> matchingTags) {
        String id = biomeId == null ? "" : biomeId.toLowerCase(Locale.ROOT);
        for (String entry : biomes) {
            if (entry.startsWith("#")) {
                if (matchingTags != null && matchingTags.contains(entry)) {
                    return true;
                }
            } else if (entry.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The {@code #tag} entries this rule refers to, for the caller to resolve against the biome registry. */
    public List<String> tagEntries() {
        return biomes.stream().filter(b -> b.startsWith("#")).toList();
    }

    /** A short human-readable summary of the conditions, for {@code /checklegendary}. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (!biomes.isEmpty()) {
            sb.append(String.join(", ", biomes.stream().map(LegendaryRule::pretty).toList()));
        }
        if (!time.equals("any")) {
            append(sb, time);
        }
        if (raining != null) {
            append(sb, raining ? "rain" : "no rain");
        }
        if (thundering != null) {
            append(sb, thundering ? "thunder" : "no thunder");
        }
        if (minY != null && maxY != null) {
            append(sb, "Y " + minY + "-" + maxY);
        } else if (minY != null) {
            append(sb, "Y " + minY + "+");
        } else if (maxY != null) {
            // Use an explicit comparison for a single height bound.
            append(sb, "Y up to " + maxY);
        }
        return sb.length() == 0 ? "anywhere" : sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(part);
    }

    private static String pretty(String entry) {
        String s = entry.startsWith("#") ? entry.substring(1) : entry;
        s = s.contains(":") ? s.substring(s.indexOf(':') + 1) : s;
        return s.startsWith("is_") ? s.substring(3).replace('_', ' ') : s.replace('_', ' ');
    }
}
