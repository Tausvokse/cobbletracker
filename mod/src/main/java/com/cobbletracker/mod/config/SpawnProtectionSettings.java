package com.cobbletracker.mod.config;

public record SpawnProtectionSettings(
        boolean enabled,
        int gracePeriodSeconds,
        boolean notifyOthers) {

    public static SpawnProtectionSettings defaults() {
        return new SpawnProtectionSettings(true, 60, true);
    }
}
