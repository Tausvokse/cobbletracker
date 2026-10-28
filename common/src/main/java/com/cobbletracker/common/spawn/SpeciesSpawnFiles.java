package com.cobbletracker.common.spawn;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SpeciesSpawnFiles {

    private SpeciesSpawnFiles() {
    }

    public static List<SpeciesSpawnDefinition> read(Path folder, Set<String> groups) throws IOException {
        List<SpeciesSpawnDefinition> definitions = new ArrayList<>();
        Set<String> species = new HashSet<>();
        try (var files = Files.list(folder)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                try {
                    if (Files.size(path) > 8 * 1024 * 1024) {
                        throw new IllegalArgumentException("File exceeds 8 MiB");
                    }
                    try (JsonReader reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
                        reader.setLenient(false);
                        JsonElement root = value(reader, 0);
                        if (!root.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) {
                            throw new IllegalArgumentException("Expected one JSON object");
                        }
                        SpeciesSpawnDefinition definition = SpeciesSpawnDefinition.parse(root.getAsJsonObject());
                        if (!species.add(definition.species())) {
                            throw new IllegalArgumentException("Duplicate species " + definition.species());
                        }
                        if (!groups.contains(definition.group())) {
                            throw new IllegalArgumentException("Unknown spawn group " + definition.group());
                        }
                        definitions.add(definition);
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException(path.getFileName() + ": " + e.getMessage(), e);
                }
            }
        }
        return List.copyOf(definitions);
    }

    private static JsonElement value(JsonReader reader, int depth) throws IOException {
        if (depth > 128) {
            throw new IllegalArgumentException("JSON nesting exceeds 128 levels");
        }
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) {
                        throw new IllegalArgumentException("Duplicate JSON key " + name);
                    }
                    object.add(name, value(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) {
                    array.add(value(reader, depth + 1));
                }
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IllegalArgumentException("Unexpected JSON token " + reader.peek());
        };
    }
}
