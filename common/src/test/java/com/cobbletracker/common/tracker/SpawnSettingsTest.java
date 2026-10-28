package com.cobbletracker.common.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;


class SpawnSettingsTest {

    private static SpawnSettings with(int minDistance, int maxDistance, int minLevel, int maxLevel) {
        return new SpawnSettings(true, 100, 0.5, true, 0, minDistance, maxDistance, minLevel, maxLevel,
                false, List.of(), SpawnSettings.Mode.AUTO, true);
    }

    @Test
    void invertedDistanceRangeIsCorrected() {
        SpawnSettings s = with(80, 20, 10, 20);
        assertTrue(s.maxDistance() >= s.minDistance());
        assertTrue(s.maxDistance() - s.minDistance() + 1 > 0);
    }

    @Test
    void invertedLevelRangeIsCorrected() {
        SpawnSettings s = with(10, 20, 90, 5);
        assertTrue(s.maxLevel() >= s.minLevel());
        assertTrue(s.maxLevel() - s.minLevel() + 1 > 0);
    }

    @Test
    void levelsStayInsidePokemonRange() {
        SpawnSettings low = with(10, 20, -40, 0);
        assertEquals(1, low.minLevel());
        assertTrue(low.maxLevel() >= 1);

        SpawnSettings high = with(10, 20, 500, 900);
        assertEquals(100, high.minLevel());
        assertEquals(100, high.maxLevel());
    }

    @Test
    void intervalNeverDropsToZero() {
        
        assertEquals(1, new SpawnSettings(true, 0, 0.5, true, 0, 1, 2, 1, 2, false, List.of(), SpawnSettings.Mode.AUTO, true).intervalTicks());
        assertEquals(1, new SpawnSettings(true, -5, 0.5, true, 0, 1, 2, 1, 2, false, List.of(), SpawnSettings.Mode.AUTO, true).intervalTicks());
    }

    @Test
    void chanceIsAProbability() {
        assertEquals(1.0, chance(7.5));
        assertEquals(0.0, chance(-3.0));
        assertEquals(0.25, chance(0.25));
        assertEquals(0.0, chance(Double.NaN));
        assertEquals(1.0, chance(Double.POSITIVE_INFINITY));
        assertEquals(0.0, chance(Double.NEGATIVE_INFINITY));
    }

    private static double chance(double raw) {
        return new SpawnSettings(true, 1, raw, true, 0, 1, 2, 1, 2, false, List.of(), SpawnSettings.Mode.AUTO, true).chance();
    }

    @Test
    void speciesPoolIsCopiedAndNullSafe() {
        List<String> source = new java.util.ArrayList<>(List.of("cobblemon:rayquaza"));
        SpawnSettings s = new SpawnSettings(true, 1, 0.5, true, 0, 1, 2, 1, 2, false, source, SpawnSettings.Mode.AUTO, true);
        source.clear();
        assertEquals(1, s.speciesPool().size());
        assertTrue(new SpawnSettings(true, 1, 0.5, true, 0, 1, 2, 1, 2, false, null, SpawnSettings.Mode.AUTO, true).speciesPool().isEmpty());
    }

    @Test
    void speciesAliasesStillMatchContextRules() {
        SpawnSettings pool = new SpawnSettings(true, 1, 1, true, 0, 1, 2, 1, 2, false,
                List.of(" Rayquaza ", "cobblemon:rayquaza", "other:Mew"), SpawnSettings.Mode.ALWAYS, true);
        var rule = new com.cobbletracker.common.legendary.LegendaryRule("rayquaza", 1, null, null,
                "night", null, null, null, null);
        assertEquals(List.of("cobblemon:rayquaza", "other:mew"), pool.speciesPool());
        assertTrue(pool.speciesPool().contains(rule.speciesId()));
    }
}
