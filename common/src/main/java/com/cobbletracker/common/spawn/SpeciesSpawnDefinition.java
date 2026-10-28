package com.cobbletracker.common.spawn;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record SpeciesSpawnDefinition(String species, boolean enabled, SpeciesSpawnMode mode,
        String group, List<JsonObject> spawns) {

    public SpeciesSpawnDefinition {
        species = Objects.requireNonNull(species, "species").trim().toLowerCase(Locale.ROOT);
        if (!species.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("species must be a namespaced identifier");
        }
        Objects.requireNonNull(mode, "mode");
        group = Objects.requireNonNull(group, "group").trim().toLowerCase(Locale.ROOT);
        if (!group.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("group must be a nonempty identifier");
        }
        spawns = Objects.requireNonNull(spawns, "spawns").stream().map(JsonObject::deepCopy).toList();
        if (enabled && spawns.isEmpty()) {
            throw new IllegalArgumentException("enabled species requires at least one native spawn entry");
        }
    }

    @Override
    public List<JsonObject> spawns() {
        return spawns.stream().map(JsonObject::deepCopy).toList();
    }

    public static SpeciesSpawnDefinition parse(JsonObject root) {
        String species = requiredString(root, "species");
        JsonElement enabled = root.get("enabled");
        if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("enabled must be a boolean");
        }
        JsonElement entries = root.get("spawns");
        if (entries == null || !entries.isJsonArray()) {
            throw new IllegalArgumentException("spawns must be an array of native spawn objects");
        }
        java.util.ArrayList<JsonObject> spawns = new java.util.ArrayList<>();
        for (JsonElement entry : entries.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                throw new IllegalArgumentException("every spawn entry must be a native spawn object");
            }
            spawns.add(entry.getAsJsonObject());
        }
        return new SpeciesSpawnDefinition(species, enabled.getAsBoolean(),
                SpeciesSpawnMode.parse(requiredString(root, "mode")), requiredString(root, "group"), spawns);
    }

    private static String requiredString(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(key + " must be a nonempty string");
        }
        return value.getAsString();
    }
}
