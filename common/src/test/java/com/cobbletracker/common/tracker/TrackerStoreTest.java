package com.cobbletracker.common.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrackerStoreTest {

    private static SpawnRecord rec(String cat, String species, long time) {
        return new SpawnRecord(UUID.randomUUID(), cat, species, "cobblemon:" + species.toLowerCase(),
                time, "minecraft:overworld", 0, 64, 0, "minecraft:plains", false, false, null, null, "", "",
                SpawnDetails.EMPTY);
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

        
        store.markCaught(r.id(), UUID.randomUUID(), "Ash");
        var afterCatch = store.markOutcome(r.id(), "despawned", "");
        assertTrue(afterCatch.isEmpty());
        assertEquals("caught", store.findById(r.id()).orElseThrow().outcome());
        assertTrue(store.findById(r.id()).orElseThrow().caught());
    }

    @Test
    void duplicateCaptureCannotTriggerRewardsOrReplaceTheCatcher() {
        TrackerStore store = new TrackerStore();
        SpawnRecord record = rec("shinies", "Pikachu", 10);
        store.record(record, 10);
        UUID originalCatcher = UUID.randomUUID();
        assertTrue(store.markCaught(record.id(), originalCatcher, "Ash").isPresent());

        assertTrue(store.markCaught(record.id(), UUID.randomUUID(), "Other").isEmpty());
        assertEquals(originalCatcher, store.findById(record.id()).orElseThrow().catcherUuid());
    }

    @Test
    void duplicateDefeatCannotTriggerAnotherOutcomeAnnouncement() {
        TrackerStore store = new TrackerStore();
        SpawnRecord record = rec("legends", "Rayquaza", 10);
        store.record(record, 10);
        assertTrue(store.markOutcome(record.id(), "defeated", "Alex").isPresent());

        assertTrue(store.markOutcome(record.id(), "defeated", "Alex").isEmpty());
        assertTrue(store.markOutcome(record.id(), "despawned", "").isEmpty());
        assertEquals("defeated", store.findById(record.id()).orElseThrow().outcome());
    }

    @Test
    void trimToAppliesALoweredCapToAlreadyLoadedHistory() {
        TrackerStore store = new TrackerStore();
        
        store.put(rec("legends", "A", 1));
        store.put(rec("legends", "B", 2));
        store.put(rec("legends", "C", 3));
        store.resort();

        assertTrue(store.trimTo("legends", 2));
        var list = store.category("legends");
        assertEquals(2, list.size());
        assertEquals("C", list.get(0).species());
        assertFalse(list.stream().anyMatch(r -> r.species().equals("A")));

        
        assertFalse(store.trimTo("legends", 2));
        assertFalse(store.trimTo("nosuch", 0));
    }

    @Test
    void trimToZeroDropsTheCategoryEntirely() {
        TrackerStore store = new TrackerStore();
        store.put(rec("legends", "A", 1));
        assertTrue(store.trimTo("legends", 0));
        assertTrue(store.category("legends").isEmpty());
        assertFalse(store.categories().contains("legends"));
    }

    @Test
    void resortOrdersBySpawnTimeForCorrectEviction() {
        TrackerStore store = new TrackerStore();
        
        store.put(rec("legends", "C", 3));
        store.put(rec("legends", "A", 1));
        store.put(rec("legends", "B", 2));
        store.resort();
        store.record(rec("legends", "D", 4), 3); 

        var list = store.category("legends");
        assertEquals(3, list.size());
        assertFalse(list.stream().anyMatch(r -> r.species().equals("A")));
    }
}
