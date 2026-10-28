package com.cobbletracker.mod.config;

public record MinimapSettings(
        boolean enabled,
        boolean xaero,
        boolean voxelmap,
        boolean journeymap,
        boolean useBeamColor) {

    public static MinimapSettings defaults() {
        return new MinimapSettings(true, true, true, true, true);
    }
}
