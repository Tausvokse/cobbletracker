package com.cobbletracker.common.tracker;

import java.util.Locale;

/** Optional encounter traits. Older records load as EMPTY; missing IV totals use -1. */
public record SpawnDetails(
        int level,
        String gender,
        String nature,
        String ability,
        String ivs,
        String evs) {

    public static final SpawnDetails EMPTY = new SpawnDetails(0, "", "", "", "", "");

    public SpawnDetails {
        level = Math.max(0, level);
        gender = clean(gender);
        nature = clean(nature);
        ability = clean(ability);
        ivs = ivs == null ? "" : ivs.trim();
        evs = evs == null ? "" : evs.trim();
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /** True when there is nothing worth showing (an older record, or a Pokémon we couldn't read). */
    public boolean isEmpty() {
        return level <= 0 && gender.isEmpty() && nature.isEmpty() && ability.isEmpty() && ivs.isEmpty();
    }

    /** The IV total, parsed back out of {@link #ivs}; -1 when there are no IVs to add up. */
    public int ivTotal() {
        return sum(ivs);
    }

    /** How many IVs are at the 31 maximum; -1 when there are no IVs to count. */
    public int perfectIvs() {
        if (ivs.isEmpty()) {
            return -1;
        }
        int perfect = 0;
        for (String part : ivs.split("/")) {
            try {
                if (Integer.parseInt(part.trim()) >= 31) {
                    perfect++;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed IV values in hand-edited history.
            }
        }
        return perfect;
    }

    private static int sum(String slashed) {
        if (slashed.isEmpty()) {
            return -1;
        }
        int total = 0;
        for (String part : slashed.split("/")) {
            try {
                total += Integer.parseInt(part.trim());
            } catch (NumberFormatException ignored) {
                
            }
        }
        return total;
    }

    /** The gender as a single symbol for a cramped GUI row; {@code ""} for genderless or unknown. */
    public String genderSymbol() {
        return switch (gender) {
            case "male" -> "♂";
            case "female" -> "♀";
            default -> "";
        };
    }
}
