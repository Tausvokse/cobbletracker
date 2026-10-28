package com.cobbletracker.mod.spawning;

import com.cobblemon.mod.common.api.spawning.SpawnLoader;
import com.cobblemon.mod.common.api.spawning.SpawnDetailPresets;
import com.cobblemon.mod.common.api.spawning.condition.AppendageCondition;
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition;
import com.cobblemon.mod.common.api.spawning.condition.CompositeSpawningCondition;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.multiplier.WeightMultiplier;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

final class NativeSpawnSchema {
    private NativeSpawnSchema() {}

    static void validatePresets(JsonObject json) {
        if (json.has("preset")) preset(json.get("preset"));
        if (json.has("presets")) {
            JsonElement presets = json.get("presets");
            if (!presets.isJsonArray()) throw new IllegalArgumentException("presets must be an array");
            presets.getAsJsonArray().forEach(NativeSpawnSchema::preset);
        }
    }

    private static void preset(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Spawn preset must be a string");
        }
        String name = value.getAsString();
        ResourceLocation id = ResourceLocation.parse(name.contains(":") ? name : "cobblemon:" + name);
        if (!SpawnDetailPresets.INSTANCE.getPresets().containsKey(id)) {
            throw new IllegalArgumentException("Unknown native spawn preset: " + name);
        }
    }

    static void validate(JsonObject json, SpawnDetail detail) {
        Set<String> fields = fields(detail.getClass());
        fields.addAll(Set.of("type", "context", "preset", "presets", "condition", "anticondition", "weightMultiplier"));
        check(json, fields, detail.getId());
        conditions(json, detail, detail.getId());
        if (json.has("compositeCondition")) {
            JsonElement value = json.get("compositeCondition");
            if (!value.isJsonObject()) throw new IllegalArgumentException("compositeCondition must be an object");
            JsonObject composite = value.getAsJsonObject();
            check(composite, fields(CompositeSpawningCondition.class), detail.getId() + ".compositeCondition");
            conditions(composite, detail, detail.getId() + ".compositeCondition");
        }
        for (String key : new String[] {"weightMultiplier", "weightMultipliers"}) {
            JsonElement value = json.get(key);
            if (value == null) continue;
            if (value.isJsonArray()) {
                for (JsonElement multiplier : value.getAsJsonArray()) multiplier(multiplier, detail, key);
            } else multiplier(value, detail, key);
        }
    }

    private static void conditions(JsonObject json, SpawnDetail detail, String path) {
        for (String key : new String[] {"condition", "conditions", "anticondition", "anticonditions"}) {
            JsonElement value = json.get(key);
            if (value == null) continue;
            if (value.isJsonArray()) {
                for (JsonElement condition : value.getAsJsonArray()) condition(condition, detail, path + "." + key);
            } else {
                condition(value, detail, path + "." + key);
            }
        }
    }

    private static void multiplier(JsonElement value, SpawnDetail detail, String path) {
        if (!value.isJsonObject()) throw new IllegalArgumentException(path + " must contain multiplier objects");
        JsonObject json = value.getAsJsonObject();
        Set<String> fields = fields(WeightMultiplier.class);
        fields.addAll(Set.of("condition", "anticondition"));
        check(json, fields, detail.getId() + "." + path);
        if (json.has("multiplier")) {
            float multiplier = json.get("multiplier").getAsFloat();
            if (!Float.isFinite(multiplier) || multiplier < 0) {
                throw new IllegalArgumentException(path + ".multiplier must be a finite nonnegative number");
            }
        }
        conditions(json, detail, detail.getId() + "." + path);
    }

    private static void condition(JsonElement value, SpawnDetail detail, String path) {
        if (!value.isJsonObject()) throw new IllegalArgumentException(path + " must contain condition objects");
        JsonObject json = value.getAsJsonObject();
        String type = json.has("type") ? json.get("type").getAsString()
                : detail.getSpawnablePositionType().getDefaultCondition();
        Class<? extends SpawningCondition<?>> clazz = SpawningCondition.Companion.getByName(type);
        if (clazz == null) throw new IllegalArgumentException("Unknown condition type: " + type);
        SpawningCondition<?> parsed = SpawnLoader.INSTANCE.getGson().fromJson(json, clazz);
        Set<String> fields = fields(clazz);
        fields.add("type");
        for (Class<?> appendage : AppendageCondition.Companion.getAppendages(parsed)) {
            fields.addAll(fields(appendage));
        }
        check(json, fields, path);
    }

    private static Set<String> fields(Class<?> type) {
        Set<String> result = new HashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                        || Modifier.isTransient(field.getModifiers())) continue;
                SerializedName name = field.getAnnotation(SerializedName.class);
                if (name == null) result.add(field.getName());
                else {
                    result.add(name.value());
                    result.addAll(java.util.List.of(name.alternate()));
                }
            }
        }
        return result;
    }

    private static void check(JsonObject json, Set<String> allowed, String path) {
        for (String key : json.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown native spawn field: " + path + "." + key);
        }
    }
}
