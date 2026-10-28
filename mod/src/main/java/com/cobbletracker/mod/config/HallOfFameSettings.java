package com.cobbletracker.mod.config;

import java.util.List;

public record HallOfFameSettings(
        boolean enabled,
        boolean broadcastServerFirst,
        List<String> categories) {

    public static HallOfFameSettings defaults() {
        return new HallOfFameSettings(true, true, List.of("legendaries", "mythicals", "ancients", "ultrabeasts", "shinies"));
    }
}
