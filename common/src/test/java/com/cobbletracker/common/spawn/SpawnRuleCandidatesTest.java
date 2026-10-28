package com.cobbletracker.common.spawn;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SpawnRuleCandidatesTest {
    @Test
    void eligibleBlockCountDoesNotIncreaseRuleWeight() {
        var one = new SpawnRuleCandidates<Integer>();
        var many = new SpawnRuleCandidates<Integer>();
        one.offer(0, 2, 0);
        for (int i = 0; i < 1000; i++) many.offer(i, 2, 0.5);
        assertEquals(one.meanWeight(), many.meanWeight());
        assertEquals(1000, many.count());
    }

    @Test
    void nativeMultipliersContributeTheirMeanRatherThanSum() {
        var candidates = new SpawnRuleCandidates<String>();
        candidates.offer("ordinary", 1, 0);
        candidates.offer("boosted", 3, 0);
        candidates.offer("blocked", 0, 0);
        assertEquals(2, candidates.meanWeight());
        assertEquals(2, candidates.count());
        assertEquals("boosted", candidates.selected());
    }

    @Test
    void weightedReservoirUsesExactBoundaries() {
        var retained = new SpawnRuleCandidates<String>();
        retained.offer("first", 1, 0);
        retained.offer("second", 3, 0.75);
        assertEquals("first", retained.selected());
        var replaced = new SpawnRuleCandidates<String>();
        replaced.offer("first", 1, 0);
        replaced.offer("second", 3, Math.nextDown(0.75));
        assertEquals("second", replaced.selected());
    }

    @Test
    void extremeFiniteWeightsDoNotOverflow() {
        var candidates = new SpawnRuleCandidates<Integer>();
        candidates.offer(0, Double.MIN_VALUE, 0);
        candidates.offer(1, Double.MAX_VALUE, 0.5);
        candidates.offer(2, Double.MAX_VALUE, 0.9);
        assertTrue(Double.isFinite(candidates.meanWeight()));
        assertEquals(Double.MAX_VALUE * (2.0 / 3), candidates.meanWeight(), Double.MAX_VALUE * 1e-15);
        assertEquals(1, candidates.selected());
    }

    @Test
    void emptyAndInvalidInputsCannotInventCandidates() {
        var candidates = new SpawnRuleCandidates<String>();
        candidates.offer("bad", Double.NaN, 0);
        candidates.offer("bad", Double.POSITIVE_INFINITY, 0);
        candidates.offer("bad", -1, 0);
        assertTrue(candidates.isEmpty());
        assertNull(candidates.selected());
        assertEquals(0, candidates.meanWeight());
        assertThrows(IllegalArgumentException.class, () -> candidates.offer("bad", 1, 1));
    }
}
