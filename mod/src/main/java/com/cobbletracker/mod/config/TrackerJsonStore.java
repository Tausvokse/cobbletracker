package com.cobbletracker.mod.config;

import com.cobbletracker.common.tracker.SpawnRecord;
import com.cobbletracker.common.tracker.TrackerStore;
import com.cobbletracker.mod.CobbleTracker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * Reads and writes the {@link TrackerStore} as config/cobbletracker/tracker.json, laid out as
 * category -> uuid -> record. IO is guarded so a corrupt or locked file doesn't take tracking down;
 * writes are whole-file and debounced by {@link com.cobbletracker.mod.tracker.TrackerServer}.
 */
public final class TrackerJsonStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TrackerJsonStore() {
    }

    public static Path file() {
        return ConfigManager.dir().resolve("tracker.json");
    }

    /** Reads tracker.json into the store (no trimming; caller resorts). Missing file is a no-op. */
    public static void loadInto(TrackerStore store) {
        Path file = file();
        try {
            if (!Files.exists(file)) {
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String category : root.keySet()) {
                if (!root.get(category).isJsonObject()) {
                    continue;
                }
                JsonObject entries = root.getAsJsonObject(category);
                for (String uuid : entries.keySet()) {
                    try {
                        store.put(read(category, uuid, entries.getAsJsonObject(uuid)));
                    } catch (Exception e) {
                        CobbleTracker.LOGGER.warn("Skipping bad tracker record {}/{}: {}", category, uuid, e.getMessage());
                    }
                }
            }
            store.resort();
        } catch (Exception e) {
            CobbleTracker.LOGGER.error("Failed to read tracker.json", e);
        }
    }

    /**
     * Writes the whole store to tracker.json. The file goes out to a sibling temp file first and is then
     * moved into place, so a crash or a pulled plug mid-write leaves the previous history intact instead
     * of a half-written file nothing can parse.
     */
    public static void save(TrackerStore store) {
        try {
            JsonObject root = new JsonObject();
            for (String category : store.categories()) {
                JsonObject entries = new JsonObject();
                // Persist oldest→newest so a reload preserves eviction order.
                List<SpawnRecord> records = store.category(category);
                for (int i = records.size() - 1; i >= 0; i--) {
                    SpawnRecord r = records.get(i);
                    entries.add(r.id().toString(), write(r));
                }
                root.add(category, entries);
            }
            Path file = file();
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("tracker.json.tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            CobbleTracker.LOGGER.error("Failed to write tracker.json", e);
        }
    }

    private static SpawnRecord read(String category, String uuid, JsonObject o) {
        UUID catcher = o.has("catcher-uuid") && !o.get("catcher-uuid").isJsonNull()
                ? UUID.fromString(o.get("catcher-uuid").getAsString()) : null;
        String catcherName = o.has("catcher-name") && !o.get("catcher-name").isJsonNull()
                ? o.get("catcher-name").getAsString() : null;
        return new SpawnRecord(
                UUID.fromString(uuid),
                category,
                str(o, "species"),
                str(o, "species-id"),
                o.has("spawn-time") ? o.get("spawn-time").getAsLong() : 0L,
                str(o, "world"),
                o.has("x") ? o.get("x").getAsInt() : 0,
                o.has("y") ? o.get("y").getAsInt() : 0,
                o.has("z") ? o.get("z").getAsInt() : 0,
                str(o, "biome"),
                o.has("shiny") && o.get("shiny").getAsBoolean(),
                o.has("caught") && o.get("caught").getAsBoolean(),
                catcher,
                catcherName,
                str(o, "outcome"),
                str(o, "outcome-by"));
    }

    private static JsonObject write(SpawnRecord r) {
        JsonObject o = new JsonObject();
        o.addProperty("species", r.species());
        o.addProperty("species-id", r.speciesId());
        o.addProperty("spawn-time", r.spawnTime());
        o.addProperty("world", r.world());
        o.addProperty("x", r.x());
        o.addProperty("y", r.y());
        o.addProperty("z", r.z());
        o.addProperty("biome", r.biome());
        o.addProperty("shiny", r.shiny());
        o.addProperty("caught", r.caught());
        o.addProperty("outcome", r.outcome());
        o.addProperty("outcome-by", r.outcomeBy());
        if (r.catcherUuid() != null) {
            o.addProperty("catcher-uuid", r.catcherUuid().toString());
        } else {
            o.add("catcher-uuid", com.google.gson.JsonNull.INSTANCE);
        }
        if (r.catcherName() != null) {
            o.addProperty("catcher-name", r.catcherName());
        } else {
            o.add("catcher-name", com.google.gson.JsonNull.INSTANCE);
        }
        return o;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}
