package com.cobbletracker.mod.hof;

import com.cobbletracker.common.tracker.SpawnRecord;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.config.ConfigManager;
import com.cobbletracker.mod.config.HallOfFameSettings;
import com.cobbletracker.mod.text.Msg;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class HallOfFameModule {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public record HofEntry(
            String speciesId,
            String speciesName,
            UUID catcherUuid,
            String catcherName,
            long timestamp,
            int level,
            boolean shiny,
            String ivs) {
    }

    private static final Map<String, HofEntry> ENTRIES = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;
    private static volatile boolean writable = true;
    private static final Object WRITE_LOCK = new Object();
    private static final AtomicLong WRITE_SEQUENCE = new AtomicLong();
    private static long lastWritten;

    private HallOfFameModule() {
    }

    public static Path file() {
        return ConfigManager.dir().resolve("hall_of_fame.json");
    }

    public static synchronized void load() {
        synchronized (WRITE_LOCK) {
            lastWritten = WRITE_SEQUENCE.incrementAndGet();
            loadLocked();
        }
    }

    private static void loadLocked() {
        ENTRIES.clear();
        loaded = true;
        writable = true;
        Path path = file();
        if (!Files.exists(path)) {
            return;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            boolean damaged = false;
            for (String key : root.keySet()) {
                try {
                    JsonObject o = root.getAsJsonObject(key);
                    String speciesName = text(o, "speciesName", key);
                    String catcher = text(o, "catcherUuid", "");
                    UUID uuid = catcher.isBlank() ? null : UUID.fromString(catcher);
                    String name = text(o, "catcherName", "Unknown");
                    long time = o.has("timestamp") ? o.get("timestamp").getAsLong() : 0L;
                    int level = o.has("level") ? o.get("level").getAsInt() : 0;
                    boolean shiny = o.has("shiny") && o.get("shiny").getAsBoolean();
                    String ivs = text(o, "ivs", "");
                    ENTRIES.put(key, new HofEntry(key, speciesName, uuid, name, time, level, shiny, ivs));
                } catch (RuntimeException e) {
                    damaged = true;
                    CobbleTracker.LOGGER.warn("Skipping invalid Hall of Fame record '{}'", key);
                }
            }
            if (damaged) {
                Path backup = path.resolveSibling("hall_of_fame.json.damaged-" + System.currentTimeMillis() + ".bak");
                Files.copy(path, backup);
                CobbleTracker.LOGGER.warn("Preserved damaged Hall of Fame in {}", backup.getFileName());
            }
            CobbleTracker.LOGGER.info("Loaded {} Hall of Fame records", ENTRIES.size());
        } catch (Exception e) {
            writable = false;
            CobbleTracker.LOGGER.error("Failed to load hall_of_fame.json", e);
        }
    }

    public static synchronized void save() {
        if (writable) {
            write(file(), snapshot(), WRITE_SEQUENCE.incrementAndGet());
        }
    }

    private static void saveAsync() {
        if (!writable) {
            return;
        }
        Path destination = file();
        String json = snapshot();
        long sequence = WRITE_SEQUENCE.incrementAndGet();
        CompletableFuture.runAsync(() -> write(destination, json, sequence));
    }

    private static String snapshot() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, HofEntry> e : ENTRIES.entrySet()) {
            HofEntry entry = e.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("speciesName", entry.speciesName());
            o.addProperty("catcherUuid", entry.catcherUuid() == null ? null : entry.catcherUuid().toString());
            o.addProperty("catcherName", entry.catcherName());
            o.addProperty("timestamp", entry.timestamp());
            o.addProperty("level", entry.level());
            o.addProperty("shiny", entry.shiny());
            o.addProperty("ivs", entry.ivs());
            root.add(e.getKey(), o);
        }
        return GSON.toJson(root);
    }

    private static String text(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }

    private static void write(Path path, String json, long sequence) {
        synchronized (WRITE_LOCK) {
            if (!writable || sequence < lastWritten) {
                return;
            }
            try {
                Files.createDirectories(path.getParent());
                Path tmp = path.resolveSibling("hall_of_fame.json.tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
                lastWritten = sequence;
            } catch (Exception e) {
                CobbleTracker.LOGGER.error("Failed to save hall_of_fame.json", e);
            }
        }
    }

    public static void checkAndRecord(SpawnRecord record, UUID catcherUuid, String catcherName,
            HallOfFameSettings settings, MinecraftServer server) {
        if (!settings.enabled() || catcherUuid == null) {
            return;
        }
        if (!loaded) {
            load();
        }
        if (!settings.categories().contains(record.category())) {
            return;
        }
        String key = record.speciesId();
        HofEntry entry = new HofEntry(key, record.species(), catcherUuid,
                catcherName == null || catcherName.isBlank() ? "Unknown" : catcherName,
                Instant.now().getEpochSecond(), record.details().level(), record.shiny(), record.details().ivs());
        if (ENTRIES.putIfAbsent(key, entry) != null) {
            return;
        }
        saveAsync();

        if (settings.broadcastServerFirst()) {
            MutableComponent msg = Msg.parse(
                    "<gold><bold>★ HALL OF FAME ★</bold></gold> <yellow><bold>" + Msg.esc(entry.catcherName())
                    + "</bold></yellow> <white>achieved the</white> <gold><bold>Server First</bold></gold> <white>catch for</white> <aqua><bold>"
                    + (record.shiny() ? "✨ Shiny " : "") + Msg.esc(entry.speciesName()) + "</bold></aqua><white>!</white>");
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(msg);
            }
        }
    }

    public static void display(ServerPlayer player) {
        if (!loaded) {
            load();
        }
        if (ENTRIES.isEmpty()) {
            player.sendSystemMessage(Msg.parse("<gold><bold>=== CobbleTracker Hall of Fame ===</bold></gold>"));
            player.sendSystemMessage(Msg.parse("<gray>No server records claimed yet! Be the first to catch a legendary or rare Pokémon.</gray>"));
            return;
        }
        player.sendSystemMessage(Msg.parse("<gold><bold>=== CobbleTracker Hall of Fame (Server Firsts) ===</bold></gold>"));
        List<HofEntry> list = new ArrayList<>(ENTRIES.values());
        list.sort(Comparator.comparingLong(HofEntry::timestamp).reversed());
        for (HofEntry e : list) {
            String date = DATE_FMT.format(Instant.ofEpochSecond(e.timestamp()));
            String shiny = e.shiny() ? "<yellow>✨ </yellow>" : "";
            player.sendSystemMessage(Msg.parse(
                    " <dark_gray>•</dark_gray> " + shiny + "<aqua><bold>" + Msg.esc(e.speciesName()) + "</bold></aqua> "
                    + "<gray>[Lv." + e.level() + "]</gray> <white>→</white> <yellow>" + Msg.esc(e.catcherName()) + "</yellow> "
                    + "<dark_gray>(" + date + ")</dark_gray>"));
        }
    }
}
