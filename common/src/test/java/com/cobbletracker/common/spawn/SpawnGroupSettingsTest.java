package com.cobbletracker.common.spawn;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SpawnGroupSettingsTest {

    @Test
    void defaultsUseOneSharedHourlyThirtyFivePercentRoll() {
        SpawnGroupSettings settings = SpawnGroupSettings.legendaryDefaults();
        assertTrue(settings.enabled());
        assertEquals(72_000, settings.intervalTicks());
        assertEquals(0.35, settings.chance());
        assertTrue(settings.succeeds(0));
        assertTrue(settings.succeeds(0.349999));
        assertFalse(settings.succeeds(0.35));
    }

    @Test
    void probabilityEndpointsAreExact() {
        assertFalse(new SpawnGroupSettings(true, 1, 0).succeeds(0));
        assertTrue(new SpawnGroupSettings(true, 1, 1).succeeds(Math.nextDown(1.0)));
    }

    @Test
    void malformedSettingsCannotReachScheduler() {
        for (double chance : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1}) {
            assertThrows(IllegalArgumentException.class, () -> new SpawnGroupSettings(true, 1, chance));
        }
        assertThrows(IllegalArgumentException.class, () -> new SpawnGroupSettings(true, 0, 0.35));
        assertThrows(IllegalArgumentException.class, () -> SpawnGroupSettings.legendaryDefaults().succeeds(1));
    }
}
