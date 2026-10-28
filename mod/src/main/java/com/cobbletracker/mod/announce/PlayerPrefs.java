package com.cobbletracker.mod.announce;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.common.notify.NotificationPreference;
import com.cobbletracker.mod.config.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Persists non-default per-player notification settings. */
public final class PlayerPrefs {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final NotificationPreference DEFAULT = NotificationPreference.DEFAULT;

    private static final Map<UUID, Map<String, NotificationPreference>> PREFS = new ConcurrentHashMap<>();

    private static volatile boolean dirty;
    private static volatile boolean writable = true;

    private PlayerPrefs() {
    }

    public static Path file() {
        return ConfigManager.dir().resolve("prefs.json");
    }

    public static NotificationPreference get(UUID player, String categoryId) {
        if (player == null || categoryId == null) {
            return DEFAULT;
        }
        Map<String, NotificationPreference> byCategory = PREFS.get(player);
        return byCategory == null ? DEFAULT : byCategory.getOrDefault(categoryId, DEFAULT);
    }

    public static void set(UUID player, String categoryId, NotificationPreference pref) {
        if (player == null || categoryId == null || pref == null) {
            return;
        }
        if (pref.isDefault()) {
            Map<String, NotificationPreference> byCategory = PREFS.get(player);
            if (byCategory != null && byCategory.remove(categoryId) != null) {
                if (byCategory.isEmpty()) {
                    PREFS.remove(player);
                }
                dirty = true;
            }
            return;
        }
        PREFS.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(categoryId, pref);
        dirty = true;
    }


    public static void load() {
        synchronized (WRITE_LOCK) {
            lastWritten = WRITE_SEQ.incrementAndGet();
            loadLocked();
        }
    }

    private static void loadLocked() {
        PREFS.clear();
        dirty = false;
        writable = true;
        Path file = file();
        try {
            if (!Files.exists(file)) {
                return;
            }
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                throw new IllegalStateException("Expected a preferences object");
            }
            boolean damaged = false;
            for (Map.Entry<String, JsonElement> playerEntry : root.getAsJsonObject().entrySet()) {
                if (!playerEntry.getValue().isJsonObject()) {
                    damaged = true;
                    continue;
                }
                UUID player;
                try {
                    player = UUID.fromString(playerEntry.getKey());
                } catch (IllegalArgumentException e) {
                    damaged = true;
                    CobbleTracker.LOGGER.warn("Skipping prefs for unreadable player id '{}'", playerEntry.getKey());
                    continue;
                }
                Map<String, NotificationPreference> byCategory = new ConcurrentHashMap<>();
                for (Map.Entry<String, JsonElement> catEntry : playerEntry.getValue().getAsJsonObject().entrySet()) {
                    if (!catEntry.getValue().isJsonObject()) {
                        damaged = true;
                        continue;
                    }
                    try {
                        JsonObject o = catEntry.getValue().getAsJsonObject();
                        NotificationPreference pref = new NotificationPreference(
                                !o.has("enabled") || o.get("enabled").getAsBoolean(),
                                o.has("radius") ? o.get("radius").getAsInt() : 0,
                                o.has("mute-sound") && o.get("mute-sound").getAsBoolean(),
                                !o.has("chat") || o.get("chat").getAsBoolean(),
                                !o.has("title") || o.get("title").getAsBoolean(),
                                !o.has("actionbar") || o.get("actionbar").getAsBoolean());
                        if (!pref.isDefault()) {
                            byCategory.put(catEntry.getKey(), pref);
                        }
                    } catch (RuntimeException e) {
                        damaged = true;
                        CobbleTracker.LOGGER.warn("Skipping invalid preferences for player {} category '{}'",
                                player, catEntry.getKey());
                    }
                }
                if (!byCategory.isEmpty()) {
                    PREFS.put(player, byCategory);
                }
            }
            if (damaged) {
                Path backup = file.resolveSibling("prefs.json.damaged-" + System.currentTimeMillis() + ".bak");
                Files.copy(file, backup);
                CobbleTracker.LOGGER.warn("Preserved damaged preferences in {}", backup.getFileName());
            }
        } catch (Exception e) {
            writable = false;
            CobbleTracker.LOGGER.error("Failed to read prefs.json - preserving the existing file", e);
        }
    }

    // Capture the snapshot on the server thread; perform file I/O asynchronously.
    public static void saveIfDirty() {
        if (!dirty || !writable) {
            return;
        }
        dirty = false;
        Path destination = file();
        String json = serialise();
        long seq = WRITE_SEQ.incrementAndGet();
        CompletableFuture.runAsync(() -> write(destination, json, seq));
    }

    public static void save() {
        if (!writable) {
            return;
        }
        dirty = false;
        write(file(), serialise(), WRITE_SEQ.incrementAndGet());
    }

    private static String serialise() {
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, Map<String, NotificationPreference>> playerEntry : PREFS.entrySet()) {
            JsonObject byCategory = new JsonObject();
            for (Map.Entry<String, NotificationPreference> catEntry : playerEntry.getValue().entrySet()) {
                NotificationPreference pref = catEntry.getValue();
                JsonObject o = new JsonObject();
                o.addProperty("enabled", pref.enabled());
                o.addProperty("radius", pref.radius());
                o.addProperty("mute-sound", pref.muteSound());
                o.addProperty("chat", pref.chat());
                o.addProperty("title", pref.title());
                o.addProperty("actionbar", pref.actionbar());
                byCategory.add(catEntry.getKey(), o);
            }
            if (byCategory.size() > 0) {
                root.add(playerEntry.getKey().toString(), byCategory);
            }
        }
        return GSON.toJson(root);
    }

    // The sequence fence prevents delayed snapshots from overwriting a newer load or save.
    private static void write(Path file, String json, long seq) {
        synchronized (WRITE_LOCK) {
            if (!writable || seq < lastWritten) {
                return;
            }
            try {
                Files.createDirectories(file.getParent());
                Path temp = file.resolveSibling("prefs.json.tmp");
                Files.writeString(temp, json, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                lastWritten = seq;
            } catch (Exception e) {
                CobbleTracker.LOGGER.error("Failed to write prefs.json", e);
            }
        }
    }

    private static final Object WRITE_LOCK = new Object();
    private static final java.util.concurrent.atomic.AtomicLong WRITE_SEQ = new java.util.concurrent.atomic.AtomicLong();
    private static long lastWritten;

    public static void clear() {
        synchronized (WRITE_LOCK) {
            lastWritten = WRITE_SEQ.incrementAndGet();
            PREFS.clear();
            dirty = false;
            writable = true;
        }
    }
}
