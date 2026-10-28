package com.cobbletracker.mod.placeholder;

import com.cobbletracker.mod.config.ConfigManager;
import com.cobbletracker.mod.spawning.SpeciesSpawnEngine;
import com.cobbletracker.common.tracker.TrackerStore;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class PlaceholderValues {
    private static volatile Map<String, String> values = Map.of();

    private PlaceholderValues() { }

    public static void refresh(ConfigManager config, TrackerStore store, SpeciesSpawnEngine engine) {
        Map<String, String> next = new HashMap<>();
        for (String group : config.spawnGroups().keySet()) {
            var snapshot = engine.snapshot(group);
            next.put("spawn_chance " + group, percent(snapshot.chance()));
            next.put("spawn_interval " + group, duration(snapshot.intervalTicks()));
            next.put("spawn_next " + group, snapshot.enabled() ? duration(snapshot.remainingTicks()) : "disabled");
            next.put("spawn_status " + group, snapshot.state().name().toLowerCase(Locale.ROOT));
            next.put("spawn_age " + group, snapshot.ageTicks() < 0 ? "" : duration(snapshot.ageTicks()));
            var roundedShares = com.cobbletracker.common.spawn.WeightedSpeciesOdds.percentages(
                    snapshot.speciesShares().entrySet().stream()
                            .map(entry -> new com.cobbletracker.common.spawn.WeightedSpeciesOdds.Share(entry.getKey(), entry.getValue())).toList());
            String chances = roundedShares.stream()
                    .map(entry -> display(entry.species()) + " - " + String.format(Locale.ROOT, "%.2f%%", entry.percent()))
                    .collect(Collectors.joining(", "));
            next.put("spawn_chances " + group, snapshot.state() == SpeciesSpawnEngine.SearchState.CALCULATING
                    ? "Calculating..." : snapshot.state() == SpeciesSpawnEngine.SearchState.FAILED ? "Unavailable" : chances);
            snapshot.speciesShares().forEach((species, share) -> {
                next.put("species_chance " + group + " " + species, percent(share));
                next.put("species_chance " + group + " " + display(species).toLowerCase(Locale.ROOT), percent(share));
            });
        }
        for (String category : store.categories()) {
            var records = store.category(category);
            if (!records.isEmpty()) next.put("last_species " + category, records.getFirst().species());
        }
        values = Map.copyOf(next);
    }

    public static String resolve(String key, String argument) {
        String args = argument == null || argument.isBlank() ? "legendary" : argument.trim().toLowerCase(Locale.ROOT);
        Map<String, String> snapshot = values;
        String value = snapshot.get(key + " " + args);
        if (value != null) return value;
        if (!key.equals("species_chance")) return "";
        String group = args.split("\\s+", 2)[0];
        String state = snapshot.get("spawn_status " + group);
        return "ready".equals(state) || "refreshing".equals(state) ? "0.00" : "";
    }

    public static void clear() { values = Map.of(); }

    private static String percent(double value) { return String.format(Locale.ROOT, "%.2f", value * 100); }
    private static String display(String species) {
        String path = species.substring(species.indexOf(':') + 1);
        return path.isEmpty() ? species : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
    private static String duration(long ticks) {
        long seconds = Math.max(0, (ticks + 19) / 20);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}
