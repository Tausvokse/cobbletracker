package com.cobbletracker.common.spawn;

import java.util.Locale;

public enum SpeciesSpawnMode {
    REPLACE,
    APPEND;

    public static SpeciesSpawnMode parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("mode is required (replace or append)");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "replace" -> REPLACE;
            case "append" -> APPEND;
            default -> throw new IllegalArgumentException("mode must be replace or append");
        };
    }
}
