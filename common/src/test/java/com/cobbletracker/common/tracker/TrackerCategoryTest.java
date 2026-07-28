package com.cobbletracker.common.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TrackerCategoryTest {

    private static TrackerCategory category(Set<String> blacklist, Set<String> dimensions, int maxStored) {
        return new TrackerCategory("legends", "Legends", "isLegendary:true", 0xFFFF3333, maxStored,
                blacklist, dimensions, TargetKind.POKEMON, SpawnSettings.disabled(), true);
    }

    @Test
    void emptyDimensionFilterAllowsEverywhere() {
        TrackerCategory c = category(Set.of(), Set.of(), 10);
        assertTrue(c.allowsDimension("minecraft:overworld"));
        assertTrue(c.allowsDimension("some_mod:weird_place"));
        assertTrue(c.allowsDimension(null));
    }

    @Test
    void dimensionFilterIsCaseInsensitiveAndExclusive() {
        TrackerCategory c = category(Set.of(), Set.of("minecraft:the_nether"), 10);
        assertTrue(c.allowsDimension("minecraft:the_nether"));
        assertTrue(c.allowsDimension("MINECRAFT:THE_NETHER"));
        assertFalse(c.allowsDimension("minecraft:overworld"));
        assertFalse(c.allowsDimension(null));
    }

    @Test
    void blacklistIsCaseInsensitiveAndNullSafe() {
        TrackerCategory c = category(Set.of("magikarp"), Set.of(), 10);
        assertTrue(c.isBlacklisted("Magikarp"));
        assertTrue(c.isBlacklisted("magikarp"));
        assertFalse(c.isBlacklisted("gyarados"));
        assertFalse(c.isBlacklisted(null));
    }

    @Test
    void negativeHistoryCapIsTreatedAsKeepingNothing() {
        assertEquals(0, category(Set.of(), Set.of(), -7).maxStored());
    }
}
