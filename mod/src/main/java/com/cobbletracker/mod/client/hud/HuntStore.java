package com.cobbletracker.mod.client.hud;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.config.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Atomic client-side persistence for the personal hunt list. */
public final class HuntStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final int MAX_ENTRIES = 4096;

    private static final Object WRITE_LOCK = new Object();
    private static final java.util.concurrent.atomic.AtomicLong WRITE_SEQ = new java.util.concurrent.atomic.AtomicLong();
    /** Guarded by {@link #WRITE_LOCK}. */
    private static long lastWritten;
    private static volatile boolean writable = true;

    private HuntStore() {
    }

    public static Path file() {
        return ConfigManager.dir().resolve("hunt.json");
    }

    public static Set<String> load() {
        synchronized (WRITE_LOCK) {
            lastWritten = WRITE_SEQ.incrementAndGet();
            return loadLocked();
        }
    }

    private static Set<String> loadLocked() {
        Set<String> out = new LinkedHashSet<>();
        Path file = file();
        writable = true;
        try {
            if (!Files.exists(file)) {
                return out;
            }
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                throw new IllegalStateException("Expected a hunt object");
            }
            JsonElement hunted = root.getAsJsonObject().get("hunted");
            if (hunted == null || !hunted.isJsonArray()) {
                throw new IllegalStateException("Expected a hunted array");
            }
            JsonArray array = hunted.getAsJsonArray();
            boolean damaged = false;
            for (JsonElement element : array) {
                if (out.size() >= MAX_ENTRIES) {
                    damaged = true;
                    break;
                }
                if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    damaged = true;
                    continue;
                }
                String path = element.getAsString().trim().toLowerCase(Locale.ROOT);
                if (!path.isEmpty()) {
                    out.add(path);
                } else {
                    damaged = true;
                }
            }
            if (damaged) {
                Path backup = file.resolveSibling("hunt.json.damaged-" + System.currentTimeMillis() + ".bak");
                Files.copy(file, backup);
                CobbleTracker.LOGGER.warn("Preserved damaged hunt list in {}", backup.getFileName());
            }
        } catch (Exception e) {
            writable = false;
            CobbleTracker.LOGGER.warn("Couldn't read hunt.json - preserving the existing file", e);
            out.clear();
        }
        return out;
    }

    /** Serializes on the caller thread and writes the snapshot asynchronously. */
    public static void save(Set<String> hunted) {
        if (!writable) {
            return;
        }
        JsonArray array = new JsonArray();
        for (String path : hunted) {
            array.add(path);
        }
        JsonObject root = new JsonObject();
        root.add("hunted", array);
        String json = GSON.toJson(root);
        long seq = WRITE_SEQ.incrementAndGet();
        java.util.concurrent.CompletableFuture.runAsync(() -> write(json, seq));
    }

    // Screen-close and debounced writes may overlap; preserve sequence order.
    private static void write(String json, long seq) {
        synchronized (WRITE_LOCK) {
            if (!writable || seq < lastWritten) {
                return;
            }
            lastWritten = seq;
            writeLocked(json);
        }
    }

    private static void writeLocked(String json) {
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("hunt.json.tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            CobbleTracker.LOGGER.error("Failed to write hunt.json", e);
        }
    }
}
