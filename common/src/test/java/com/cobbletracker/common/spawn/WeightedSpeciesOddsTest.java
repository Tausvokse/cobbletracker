package com.cobbletracker.common.spawn;

import static org.junit.jupiter.api.Assertions.*;
import com.cobbletracker.common.spawn.WeightedSpeciesOdds.Share;
import com.cobbletracker.common.spawn.WeightedSpeciesOdds.Weight;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeightedSpeciesOddsTest {

    @Test
    void duplicateEntriesContributeToOneSpeciesShare() {
        List<Share> shares = WeightedSpeciesOdds.normalize(List.of(
                new Weight("a", 1), new Weight("a", 2), new Weight("b", 1)));
        assertEquals(List.of(new Share("a", 0.75), new Share("b", 0.25)), shares);
    }

    @Test
    void eligiblePlayerRulePairsUseTheirGlobalWeights() {
        List<Share> shares = WeightedSpeciesOdds.aggregatePlayers(List.of(
                List.of(new Weight("mew", 1)), List.of(new Weight("latias", 2))));
        assertEquals(List.of(new Share("latias", 2.0 / 3), new Share("mew", 1.0 / 3)), shares);
    }

    @Test
    void unequalPlayerTotalsAreNotNormalizedIndependently() {
        List<Share> shares = WeightedSpeciesOdds.aggregatePlayers(List.of(
                List.of(new Weight("mew", 1)),
                List.of(new Weight("latias", 2), new Weight("lugia", 3)), List.of()));
        assertEquals(List.of(new Share("lugia", 0.5), new Share("latias", 1.0 / 3),
                new Share("mew", 1.0 / 6)), shares);
    }

    @Test
    void duplicateSpeciesAcrossPlayersAndRulesKeepAllGlobalWeight() {
        List<Share> shares = WeightedSpeciesOdds.aggregatePlayers(List.of(
                List.of(new Weight("mew", 1), new Weight("latias", 1)),
                List.of(new Weight("mew", 2), new Weight("mew", 1))));
        assertEquals(List.of(new Share("mew", 0.8), new Share("latias", 0.2)), shares);
    }

    @Test
    void globalPlayerWeightsRemainFiniteWithoutOverflow() {
        List<Share> shares = WeightedSpeciesOdds.aggregatePlayers(List.of(
                List.of(new Weight("mew", Double.MAX_VALUE)),
                List.of(new Weight("latias", Double.MAX_VALUE), new Weight("mew", Double.MAX_VALUE))));
        assertEquals(List.of(new Share("mew", 2.0 / 3), new Share("latias", 1.0 / 3)), shares);
        assertTrue(shares.stream().allMatch(share -> Double.isFinite(share.share())));
    }

    @Test
    void unusableWeightsDoNotPoisonValidChoices() {
        List<Share> shares = WeightedSpeciesOdds.normalize(List.of(new Weight("a", 1),
                new Weight("bad", Double.NaN), new Weight("inf", Double.POSITIVE_INFINITY),
                new Weight("zero", 0), new Weight("negative", -1)));
        assertEquals(List.of(new Share("a", 1)), shares);
        assertTrue(WeightedSpeciesOdds.normalize(List.of()).isEmpty());
        assertTrue(WeightedSpeciesOdds.aggregatePlayers(List.of(List.of())).isEmpty());
    }

    @Test
    void hugeWeightsRemainFiniteWithoutOverflow() {
        List<Share> shares = WeightedSpeciesOdds.normalize(List.of(new Weight("a", Double.MAX_VALUE),
                new Weight("a", Double.MAX_VALUE), new Weight("b", Double.MAX_VALUE)));
        assertEquals(2.0 / 3, shares.get(0).share(), 1e-12);
        assertEquals(1.0, shares.stream().mapToDouble(Share::share).sum(), 1e-12);
    }

    @Test
    void samplerUsesTheSameSortedSharesAndExactBoundaries() {
        List<Share> shares = WeightedSpeciesOdds.normalize(List.of(new Weight("a", 1), new Weight("b", 3)));
        assertEquals("b", WeightedSpeciesOdds.choose(shares, 0));
        assertEquals("b", WeightedSpeciesOdds.choose(shares, Math.nextDown(0.75)));
        assertEquals("a", WeightedSpeciesOdds.choose(shares, 0.75));
        assertEquals("a", WeightedSpeciesOdds.choose(shares, Math.nextDown(1.0)));
        assertNull(WeightedSpeciesOdds.choose(List.of(), 0));
    }

    @Test
    void displayedPercentagesTotalExactlyOneHundredWithoutChangingOrder() {
        List<Share> shares = WeightedSpeciesOdds.normalize(List.of(
                new Weight("a", 1), new Weight("b", 1), new Weight("c", 1)));
        var percentages = WeightedSpeciesOdds.percentages(shares);
        assertEquals(List.of("a", "b", "c"), percentages.stream().map(WeightedSpeciesOdds.Percent::species).toList());
        assertEquals(10_000, percentages.stream().mapToInt(WeightedSpeciesOdds.Percent::basisPoints).sum());
        assertEquals(33.34, percentages.getFirst().percent());
        assertEquals(33.33, percentages.getLast().percent());
        assertTrue(WeightedSpeciesOdds.percentages(List.of()).isEmpty());
    }
}
