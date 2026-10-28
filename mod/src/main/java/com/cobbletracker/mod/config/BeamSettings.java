package com.cobbletracker.mod.config;

public record BeamSettings(boolean enabled, int radius, int durationSeconds, int height) {

    public static BeamSettings defaults() {
        return new BeamSettings(true, 0, 600, 512);
    }

    public BeamSettings {
        radius = clamp(radius, 0, 2048);
        durationSeconds = clamp(durationSeconds, 1, 86_400);
        height = clamp(height, 16, 4096);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    public int durationTicks() {
        return durationSeconds * 20;
    }
}
