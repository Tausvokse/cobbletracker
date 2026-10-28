package com.cobbletracker.common.spawn;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpeciesSpawnFilesTest {

    @TempDir
    Path folder;

    private static final String EXAMPLE = """
            {"species":"cobblemon:mew","enabled":true,"mode":"replace","group":"legendary",
             "spawns":[{"id":"mew-test","pokemon":"mew","weight":1.5,
               "condition":{"biomes":["#cobblemon:is_jungle"]},"future-native-property":{"value":true}}]}
            """;

    @Test
    void nativeConditionsAndUnknownNativeFieldsRemainIntact() throws Exception {
        Files.writeString(folder.resolve("mew_spawn.json"), EXAMPLE);
        SpeciesSpawnDefinition definition = SpeciesSpawnFiles.read(folder, Set.of("legendary")).getFirst();
        assertEquals("cobblemon:mew", definition.species());
        assertEquals(SpeciesSpawnMode.REPLACE, definition.mode());
        assertTrue(definition.spawns().getFirst().getAsJsonObject("future-native-property").get("value").getAsBoolean());
        assertEquals("#cobblemon:is_jungle", definition.spawns().getFirst().getAsJsonObject("condition")
                .getAsJsonArray("biomes").get(0).getAsString());
    }

    @Test
    void duplicateSpeciesAndUnknownGroupsRejectTheWholeLoad() throws Exception {
        Files.writeString(folder.resolve("a.json"), EXAMPLE);
        Files.writeString(folder.resolve("b.json"), EXAMPLE);
        assertThrows(IllegalArgumentException.class, () -> SpeciesSpawnFiles.read(folder, Set.of("legendary")));
        Files.delete(folder.resolve("b.json"));
        assertThrows(IllegalArgumentException.class, () -> SpeciesSpawnFiles.read(folder, Set.of("other")));
    }

    @Test
    void malformedJsonAndDuplicateKeysAreRejected() throws Exception {
        for (String json : List.of(EXAMPLE + "{}", EXAMPLE.replace("\"enabled\":true", "\"enabled\":true,\"enabled\":false"),
                EXAMPLE.replace("\"weight\":1.5", "\"weight\":NaN"), "[]", "{bad}")) {
            Files.writeString(folder.resolve("mew.json"), json);
            assertThrows(IllegalArgumentException.class, () -> SpeciesSpawnFiles.read(folder, Set.of("legendary")));
        }
    }

    @Test
    void invalidDefinitionFieldsAreRejected() {
        for (String json : List.of(EXAMPLE.replace("\"enabled\":true", "\"enabled\":\"true\""),
                EXAMPLE.replace("\"replace\"", "\"auto\""), EXAMPLE.replace("\"spawns\":[", "\"spawns\":[7,"),
                EXAMPLE.replace("cobblemon:mew", "mew"))) {
            assertThrows(IllegalArgumentException.class, () -> SpeciesSpawnDefinition.parse(JsonParser.parseString(json).getAsJsonObject()));
        }
    }

    @Test
    void definitionsAreDefensiveSnapshots() {
        JsonObject root = JsonParser.parseString(EXAMPLE).getAsJsonObject();
        SpeciesSpawnDefinition definition = SpeciesSpawnDefinition.parse(root);
        root.getAsJsonArray("spawns").get(0).getAsJsonObject().addProperty("weight", 99);
        definition.spawns().getFirst().addProperty("weight", 88);
        assertEquals(1.5, definition.spawns().getFirst().get("weight").getAsDouble());
        assertThrows(UnsupportedOperationException.class, () -> definition.spawns().clear());
    }

    @Test
    void disabledExampleMayHaveNoNativeEntries() {
        SpeciesSpawnDefinition definition = SpeciesSpawnDefinition.parse(JsonParser.parseString("""
                {"species":"cobblemon:mew","enabled":false,"mode":"append","group":"legendary","spawns":[]}
                """).getAsJsonObject());
        assertFalse(definition.enabled());
        assertTrue(definition.spawns().isEmpty());
    }
}
