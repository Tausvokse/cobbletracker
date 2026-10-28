package com.cobbletracker.mod.config;

import static org.junit.jupiter.api.Assertions.*;
import com.cobbletracker.common.tracker.SpawnDetails;
import com.cobbletracker.common.tracker.SpawnRecord;
import com.cobbletracker.common.tracker.TrackerStore;
import com.cobbletracker.mod.platform.PlatformServices;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackerJsonStoreTest {
    @TempDir Path temporaryConfig;

    @BeforeEach
    void installTemporaryPlatform() throws Exception {
        PlatformServices.install(new PlatformServices.Bridge() {
            public Path configDirectory() { return temporaryConfig; }
            public boolean isModLoaded(String id) { return false; }
        });
        Files.createDirectories(ConfigManager.dir());
        TrackerJsonStore.loadInto(new TrackerStore());
    }

    private SpawnRecord record() {
        return new SpawnRecord(UUID.randomUUID(), "shinies", "Pikachu", "cobblemon:pikachu", 100,
                "minecraft:overworld", 1, 64, -2, "minecraft:plains", true, false,
                null, null, "", "", SpawnDetails.EMPTY);
    }

    @Test
    void corruptRootIsNeverOverwrittenByAnAutomaticSave() throws Exception {
        String damaged = "{unfinished";
        Files.writeString(TrackerJsonStore.file(), damaged);
        TrackerStore store = new TrackerStore();
        TrackerJsonStore.loadInto(store);
        store.record(record(), 10);
        TrackerJsonStore.save(store);
        assertEquals(damaged, Files.readString(TrackerJsonStore.file()));
    }

    @Test
    void partialDamageKeepsGoodRecordsAndPreservesOriginalBeforeRepair() throws Exception {
        TrackerStore original = new TrackerStore();
        SpawnRecord good = record();
        original.record(good, 10);
        TrackerJsonStore.save(original);
        JsonObject root = JsonParser.parseString(Files.readString(TrackerJsonStore.file())).getAsJsonObject();
        root.getAsJsonObject("shinies").add("invalid-uuid", new JsonObject());
        String damaged = root.toString();
        Files.writeString(TrackerJsonStore.file(), damaged);
        TrackerStore loaded = new TrackerStore();
        TrackerJsonStore.loadInto(loaded);
        assertEquals(good, loaded.findById(good.id()).orElseThrow());
        try (var backups = Files.list(ConfigManager.dir())) {
            Path backup = backups.filter(p -> p.getFileName().toString().startsWith("tracker.json.damaged-")).findFirst().orElseThrow();
            assertEquals(damaged, Files.readString(backup));
        }
        TrackerJsonStore.save(loaded);
        assertFalse(Files.readString(TrackerJsonStore.file()).contains("invalid-uuid"));
    }
    @Test
    void pendingSaveCannotReplaceUnreadableHistoryLoadedAfterItWasQueued() throws Exception {
        var lock = TrackerJsonStore.class.getDeclaredField("WRITE_LOCK");
        lock.setAccessible(true);
        String damaged = "{unfinished-after-queued-save";
        synchronized (lock.get(null)) {
            TrackerStore previous = new TrackerStore();
            previous.record(record(), 10);
            TrackerJsonStore.saveAsync(previous);
            Files.writeString(TrackerJsonStore.file(), damaged);
            TrackerJsonStore.loadInto(new TrackerStore());
        }
        assertTrue(java.util.concurrent.ForkJoinPool.commonPool()
                .awaitQuiescence(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(damaged, Files.readString(TrackerJsonStore.file()));
    }

    @Test
    void pendingSaveCannotRollBackSuccessfullyReloadedHistory() throws Exception {
        var lock = TrackerJsonStore.class.getDeclaredField("WRITE_LOCK");
        lock.setAccessible(true);
        synchronized (lock.get(null)) {
            TrackerStore previous = new TrackerStore();
            previous.record(record(), 10);
            TrackerJsonStore.saveAsync(previous);
            Files.writeString(TrackerJsonStore.file(), "{}");
            TrackerJsonStore.loadInto(new TrackerStore());
        }
        assertTrue(java.util.concurrent.ForkJoinPool.commonPool()
                .awaitQuiescence(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals("{}", Files.readString(TrackerJsonStore.file()));
    }

}
