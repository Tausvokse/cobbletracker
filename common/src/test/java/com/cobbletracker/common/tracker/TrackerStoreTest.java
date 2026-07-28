package com.cobbletracker.common.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrackerStoreTest {

    private static SpawnRecord rec(String cat, String species, long time) {
        return new SpawnRecord(UUID.randomUUID(), cat, species, "cobblemon:" + species.toLowerCase(),
                time, "minecraft:overworld", 0, 64, 0, "minecraft:plains", false, false, null, null, "", "");
    }

    @Test
    void fifoEvictionKeepsNewestUpToCap() {
        TrackerStore store = new TrackerStore();
        store.record(rec("legends", "A", 1), 3);
        store.record(rec("legends", "B", 2), 3);
        store.record(rec("legends", "C", 3), 3);
        store.record(rec("legends", "D", 4), 3);

        var list = store.category("legends");
        assertEquals(3, list.size());
        // newest first: D, C, B ; A evicted
        assertEquals("D", list.get(0).species());
        assertEquals("B", list.get(2).species());
        assertFalse(list.stream().anyMatch(r -> r.species().equals("A")));
    }

    @Test
    void markCaughtUpdatesRecord() {
        TrackerStore store = new TrackerStore();
        SpawnRecord r = rec("shinies", "Pikachu", 10);
        store.record(r, 10);
        UUID catcher = UUID.randomUUID();
        var updated = store.markCaught(r.id(), catcher, "Ash");
        assertTrue(updated.isPresent());
        assertTrue(updated.get().caught());
        assertEquals("Ash", updated.get().catcherName());
        assertTrue(store.category("shinies").get(0).caught());
    }

    @Test
    void markOutcomeRecordsDefeatButNotOverCapture() {
        TrackerStore store = new TrackerStore();
        SpawnRecord r = rec("legends", "Rayquaza", 5);
        store.record(r, 10);

        var defeated = store.markOutcome(r.id(), "defeated", "Alex");
        assertTrue(defeated.isPresent());
        assertEquals("defeated", defeated.get().outcome());
        assertEquals("Alex", defeated.get().outcomeBy());

        // A capture wins: once caught, a later outcome call must not overwrite it.
        store.markCaught(r.id(), UUID.randomUUID(), "Ash");
        var afterCatch = store.markOutcome(r.id(), "despawned", "");
        assertTrue(afterCatch.isPresent());
        assertEquals("caught", afterCatch.get().outcome());
        assertTrue(afterCatch.get().caught());
    }

    @Test
    void resortOrdersBySpawnTimeForCorrectEviction() {
        TrackerStore store = new TrackerStore();
        // loaded out of order
        store.put(rec("legends", "C", 3));
        store.put(rec("legends", "A", 1));
        store.put(rec("legends", "B", 2));
        store.resort();
        store.record(rec("legends", "D", 4), 3); // cap 3 → evict oldest (A)

        var list = store.category("legends");
        assertEquals(3, list.size());
        assertFalse(list.stream().anyMatch(r -> r.species().equals("A")));
    }
}
