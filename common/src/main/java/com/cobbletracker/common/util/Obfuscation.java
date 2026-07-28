package com.cobbletracker.common.util;

/**
 * Coordinate rounding for the {@code hide-exact-position} option. With it on, announced and beamed
 * positions are moved to the centre of their chunk, so players get a heading rather than a pinpoint.
 * Pure integer math, applied the same way on the announcement, beam and waypoint paths.
 */
public final class Obfuscation {

    private Obfuscation() {
    }

    /** Rounds a world coordinate to the center of its 16-block chunk column ({@code chunk*16 + 8}). */
    public static int toChunkCenter(int coord) {
        return (Math.floorDiv(coord, 16) * 16) + 8;
    }

    /** Applies {@link #toChunkCenter} only when exact positions are not allowed. */
    public static int maybeObfuscate(int coord, boolean allowExact) {
        return allowExact ? coord : toChunkCenter(coord);
    }
}
