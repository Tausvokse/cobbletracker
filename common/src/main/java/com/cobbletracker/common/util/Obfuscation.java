package com.cobbletracker.common.util;

/** Rounds hidden coordinates to chunk centers for announcement and indicator output. */
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
