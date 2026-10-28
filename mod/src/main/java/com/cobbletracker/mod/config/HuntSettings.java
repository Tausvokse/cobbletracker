package com.cobbletracker.mod.config;

public record HuntSettings(boolean enabled) {

    public static HuntSettings defaults() {
        return new HuntSettings(true);
    }
}
