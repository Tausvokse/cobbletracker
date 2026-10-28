package com.cobbletracker.mod.config;

public record WorldBossSettings(
        boolean enabled,
        int range,
        String bossBarColor,
        String bossBarStyle) {

    public static WorldBossSettings defaults() {
        return new WorldBossSettings(true, 128, "RED", "NOTCHED_10");
    }
}
