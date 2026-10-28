package com.cobbletracker.common.spawn;

public record SpawnGroupSettings(boolean enabled, int intervalTicks, double chance) {

    public SpawnGroupSettings {
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("interval-ticks must be positive");
        }
        if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("chance must be a finite probability between 0 and 1");
        }
    }

    public static SpawnGroupSettings legendaryDefaults() {
        return new SpawnGroupSettings(true, 72_000, 0.35);
    }

    public boolean succeeds(double roll) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) {
            throw new IllegalArgumentException("roll must be between 0 inclusive and 1 exclusive");
        }
        return roll < chance;
    }
}
