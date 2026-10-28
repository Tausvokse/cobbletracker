package com.cobbletracker.common.tracker;

import java.util.Locale;

/** Normalized biome, dimension, weather and height context. World time is reduced to 0..23999. */
public record SpawnContext(
        String biome,
        String dimension,
        long dayTime,
        boolean raining,
        boolean thundering,
        int y) {

    public SpawnContext {
        biome = biome == null ? "" : biome.toLowerCase(Locale.ROOT);
        dimension = dimension == null ? "" : dimension.toLowerCase(Locale.ROOT);
        dayTime = ((dayTime % 24000L) + 24000L) % 24000L;
    }

    /** Context-dependent tokens reject EMPTY rather than assuming world conditions. */
    public static final SpawnContext EMPTY = new SpawnContext("", "", 0, false, false, 0);

    /** True when this carries no real information (so context tokens should decline rather than guess). */
    public boolean isEmpty() {
        return biome.isEmpty() && dimension.isEmpty();
    }

    /** Minecraft's day runs 0-11999; night is 12000-23999. */
    public boolean day() {
        return dayTime < 12000L;
    }

    /** The bare biome path with no namespace, e.g. {@code jungle}. */
    public String biomePath() {
        return biome.contains(":") ? biome.substring(biome.indexOf(':') + 1) : biome;
    }
}
