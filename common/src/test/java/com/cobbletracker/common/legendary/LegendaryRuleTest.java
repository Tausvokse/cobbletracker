package com.cobbletracker.common.legendary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class LegendaryRuleTest {

    private static LegendaryRule rule(Set<String> biomes, String time, Boolean raining, Integer minY) {
        return new LegendaryRule("cobblemon:rayquaza", 1.0, biomes, Set.of(), time, raining, null, minY, null);
    }

    @Test
    void emptyBiomesMeansAnywhere() {
        LegendaryRule r = rule(Set.of(), "any", null, null);
        assertTrue(r.matches("minecraft:desert", Set.of(), "minecraft:overworld", true, false, false, 64));
        assertTrue(r.matches("minecraft:ocean", Set.of(), "minecraft:the_nether", false, true, true, -20));
    }

    @Test
    void plainBiomeIdMustMatchExactly() {
        LegendaryRule r = rule(Set.of("minecraft:jungle"), "any", null, null);
        assertTrue(r.matches("minecraft:jungle", Set.of(), "minecraft:overworld", true, false, false, 64));
        assertFalse(r.matches("minecraft:desert", Set.of(), "minecraft:overworld", true, false, false, 64));
    }

    @Test
    void tagEntriesComeFromTheCallerBecauseTagsNeedTheRegistry() {
        LegendaryRule r = rule(Set.of("#cobblemon:is_mountain"), "any", null, null);
        
        assertTrue(r.matches("minecraft:jagged_peaks", Set.of("#cobblemon:is_mountain"),
                "minecraft:overworld", true, false, false, 200));
        
        assertFalse(r.matches("minecraft:jagged_peaks", Set.of(), "minecraft:overworld", true, false, false, 200));
    }

    @Test
    void timeOfDayIsHonoured() {
        assertTrue(rule(Set.of(), "day", null, null)
                .matches("b", Set.of(), "d", true, false, false, 64));
        assertFalse(rule(Set.of(), "day", null, null)
                .matches("b", Set.of(), "d", false, false, false, 64));
        assertTrue(rule(Set.of(), "night", null, null)
                .matches("b", Set.of(), "d", false, false, false, 64));
    }

    @Test
    void weatherIsTriState() {
        
        assertTrue(rule(Set.of(), "any", null, null).matches("b", Set.of(), "d", true, true, false, 64));
        assertTrue(rule(Set.of(), "any", null, null).matches("b", Set.of(), "d", true, false, false, 64));
        assertTrue(rule(Set.of(), "any", Boolean.TRUE, null).matches("b", Set.of(), "d", true, true, false, 64));
        assertFalse(rule(Set.of(), "any", Boolean.TRUE, null).matches("b", Set.of(), "d", true, false, false, 64));
        assertFalse(rule(Set.of(), "any", Boolean.FALSE, null).matches("b", Set.of(), "d", true, true, false, 64));
    }

    @Test
    void heightBandIsInclusive() {
        LegendaryRule r = rule(Set.of(), "any", null, 120);
        assertTrue(r.matches("b", Set.of(), "d", true, false, false, 120));
        assertFalse(r.matches("b", Set.of(), "d", true, false, false, 119));
    }

    @Test
    void unusableWeightFallsBackToOne() {
        assertEquals(1.0, new LegendaryRule("x", 0, Set.of(), Set.of(), "any", null, null, null, null).weight());
        assertEquals(1.0, new LegendaryRule("x", -3, Set.of(), Set.of(), "any", null, null, null, null).weight());
        assertEquals(2.5, new LegendaryRule("x", 2.5, Set.of(), Set.of(), "any", null, null, null, null).weight());
        assertEquals(1.0, new LegendaryRule("x", Double.POSITIVE_INFINITY, Set.of(), Set.of(), "any", null, null, null, null).weight());
        assertEquals(1.0, new LegendaryRule("x", Double.NaN, Set.of(), Set.of(), "any", null, null, null, null).weight());
    }

    @Test
    void describeRendersASingleHeightBoundReadably() {
        
        assertEquals("Y up to 40",
                new LegendaryRule("x", 1, Set.of(), Set.of(), "any", null, null, null, 40).describe());
        assertEquals("Y 120+",
                new LegendaryRule("x", 1, Set.of(), Set.of(), "any", null, null, 120, null).describe());
        assertEquals("Y 10-40",
                new LegendaryRule("x", 1, Set.of(), Set.of(), "any", null, null, 10, 40).describe());
        assertEquals("anywhere",
                new LegendaryRule("x", 1, Set.of(), Set.of(), "any", null, null, null, null).describe());
    }

    @Test
    void unknownTimeWordIsTreatedAsAny() {
        
        assertEquals("any", new LegendaryRule("x", 1, Set.of(), Set.of(), "dusk", null, null, null, null).time());
    }
}
