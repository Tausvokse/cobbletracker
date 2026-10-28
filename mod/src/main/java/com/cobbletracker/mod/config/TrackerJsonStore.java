package com.cobbletracker.mod.config;

import com.cobbletracker.common.tracker.SpawnDetails;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Atomic whole-file persistence for encounter history. */
public final class TrackerJsonStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object WRITE_LOCK = new Object();
    private static final AtomicLong WRITE_SEQUENCE = new AtomicLong();
    private static long lastWritten;
    private static volatile boolean writable = true;

    private TrackerJsonStore() {
    }

    public static Path file() {
        return ConfigManager.dir().resolve("tracker.json");
    }

    // Loading invalidates snapshots queued before this file was read.
    public static void loadInto(TrackerStore store) {
        synchronized (WRITE_LOCK) {
            lastWritten = WRITE_SEQUENCE.incrementAndGet();
            loadLocked(store);
        }
    }

    private static void loadLocked(TrackerStore store) {
        Path file = file();
        writable = true;
        try {
            if (!Files.exists(file)) {
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean damaged = false;
            for (String category : root.keySet()) {
                if (!root.get(category).isJsonObject()) {
                    damaged = true;
                    continue;
                }
                JsonObject entries = root.getAsJsonObject(category);
                for (String uuid : entries.keySet()) {
                    try {
                        store.put(read(category, uuid, entries.getAsJsonObject(uuid)));
                    } catch (Exception e) {
                        damaged = true;
                        CobbleTracker.LOGGER.warn("Skipping bad tracker record {}/{}: {}", category, uuid, e.getMessage());
                    }
                }
            }
            if (damaged) {
                preserveDamagedFile(file);
            }
            store.resort();
        } catch (Exception e) {
            writable = false;
            CobbleTracker.LOGGER.error("Failed to read tracker.json", e);
        }
    }

    public static void save(TrackerStore store) {
        if (writable) {
            write(file(), snapshot(store), WRITE_SEQUENCE.incrementAndGet());
        }
    }

    public static void saveAsync(TrackerStore store) {
        if (!writable) {
            return;
        }
        Path destination = file();
        String json = snapshot(store);
        long sequence = WRITE_SEQUENCE.incrementAndGet();
        CompletableFuture.runAsync(() -> write(destination, json, sequence));
    }

    private static String snapshot(TrackerStore store) {
        JsonObject root = new JsonObject();
        for (String category : store.categories()) {
            JsonObject entries = new JsonObject();
            List<SpawnRecord> records = store.category(category);
            for (int i = records.size() - 1; i >= 0; i--) {
                SpawnRecord r = records.get(i);
                entries.add(r.id().toString(), write(r));
            }
            root.add(category, entries);
        }
        return GSON.toJson(root);
    }

    private static void preserveDamagedFile(Path file) throws java.io.IOException {
        Path backup = file.resolveSibling("tracker.json.damaged-" + System.currentTimeMillis() + ".bak");
        Files.copy(file, backup);
        CobbleTracker.LOGGER.warn("Preserved damaged tracker history in {}", backup.getFileName());
    }

    // Serialize replacement writes and reject snapshots older than the latest load or save.
    private static void write(Path file, String json, long sequence) {
        synchronized (WRITE_LOCK) {
            if (!writable || sequence < lastWritten) {
                return;
            }
            try {
                Files.createDirectories(file.getParent());
                Path temp = file.resolveSibling("tracker.json.tmp");
                Files.writeString(temp, json, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                lastWritten = sequence;
            } catch (Exception e) {
                CobbleTracker.LOGGER.error("Failed to write tracker.json", e);
            }
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
                str(o, "outcome-by"),
                readDetails(o),
                str(o, "traits"));
    }

    private static SpawnDetails readDetails(JsonObject o) {
        if (!o.has("details") || !o.get("details").isJsonObject()) {
            return SpawnDetails.EMPTY;
        }
        JsonObject d = o.getAsJsonObject("details");
        return new SpawnDetails(
                d.has("level") ? d.get("level").getAsInt() : 0,
                str(d, "gender"),
                str(d, "nature"),
                str(d, "ability"),
                str(d, "ivs"),
                str(d, "evs"));
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
        if (!r.traits().isEmpty()) {
            o.addProperty("traits", r.traits());
        }
        if (!r.details().isEmpty()) {
            JsonObject d = new JsonObject();
            d.addProperty("level", r.details().level());
            d.addProperty("gender", r.details().gender());
            d.addProperty("nature", r.details().nature());
            d.addProperty("ability", r.details().ability());
            d.addProperty("ivs", r.details().ivs());
            if (!r.details().evs().isEmpty()) {
                d.addProperty("evs", r.details().evs());
            }
            o.add("details", d);
        }
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
