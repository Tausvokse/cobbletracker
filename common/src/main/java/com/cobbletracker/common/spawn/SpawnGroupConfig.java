package com.cobbletracker.common.spawn;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SpawnGroupConfig {

    private SpawnGroupConfig() {
    }

    public static Map<String, SpawnGroupSettings> parse(Object configured) {
        Map<String, SpawnGroupSettings> groups = new LinkedHashMap<>();
        groups.put("legendary", SpawnGroupSettings.legendaryDefaults());
        if (configured == null) {
            return Map.copyOf(groups);
        }
        if (!(configured instanceof Map<?, ?> entries)) {
            throw new IllegalArgumentException("spawn-groups must be a map");
        }
        Set<String> seen = new HashSet<>();
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            String id = String.valueOf(entry.getKey()).trim().toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9_.-]+") || !seen.add(id)) {
                throw new IllegalArgumentException("Invalid or duplicate spawn group: " + id);
            }
            if (!(entry.getValue() instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException("Spawn group " + id + " must be a map");
            }
            Object enabled = values.containsKey("enabled") ? values.get("enabled") : Boolean.TRUE;
            Object interval = values.containsKey("interval-ticks") ? values.get("interval-ticks") : 72_000;
            Object chance = values.containsKey("chance") ? values.get("chance") : 0.35;
            if (!(enabled instanceof Boolean) || !(interval instanceof Number ticks)
                    || !Double.isFinite(ticks.doubleValue()) || ticks.doubleValue() != ticks.intValue()
                    || !(chance instanceof Number)) {
                throw new IllegalArgumentException("Spawn group " + id
                        + " requires boolean enabled, integer interval-ticks and numeric chance");
            }
            groups.put(id, new SpawnGroupSettings((Boolean) enabled, ((Number) interval).intValue(),
                    ((Number) chance).doubleValue()));
        }
        return Map.copyOf(groups);
    }
}
