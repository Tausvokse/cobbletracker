package com.cobbletracker.common.spawn;

import static org.junit.jupiter.api.Assertions.*;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class VerticalSpawnSearchTest {
    @Test
    void undergroundPlayerNeverFallsBackToTheSurface() {
        assertEquals(OptionalDouble.empty(), VerticalSpawnSearch.findSurface(-34, 16, -64, 319,
                y -> y == 89 ? 90 : Double.NaN));
        assertEquals(OptionalDouble.of(-33), VerticalSpawnSearch.findSurface(-34, 16, -64, 319,
                y -> y == -34 || y == 89 ? y + 1 : Double.NaN));
    }

    @Test
    void includesBothLimitsAndHandlesZeroDistance() {
        assertEquals(OptionalDouble.of(-50), VerticalSpawnSearch.findSurface(-34, 16, -64, 319,
                y -> y == -51 ? -50 : Double.NaN));
        assertEquals(OptionalDouble.of(-18), VerticalSpawnSearch.findSurface(-34, 16, -64, 319,
                y -> y == -19 ? -18 : Double.NaN));
        assertEquals(OptionalDouble.empty(), VerticalSpawnSearch.findSurface(-34, 16, -64, 319,
                y -> y == -18 ? -17 : Double.NaN));
        assertEquals(OptionalDouble.of(-34), VerticalSpawnSearch.findSurface(-34, 0, -64, 319, y -> y + 1));
    }

    @Test
    void checksOnlyBuildableHeightsAndPrefersTheNearestPosition() {
        assertEquals(OptionalDouble.of(-63), VerticalSpawnSearch.findSurface(-64, 16, -64, 319, y -> {
            assertTrue(y >= -64 && y <= 319);
            return y + 1;
        }));
        assertEquals(OptionalDouble.of(69), VerticalSpawnSearch.findSurface(70, 16, -64, 319,
                y -> y == 68 || y == 84 ? y + 1 : Double.NaN));
    }

    @Test
    void partialSurfacesRespectActualPlayerHeightAndInclusiveLimits() {
        assertEquals(OptionalDouble.of(90.375), VerticalSpawnSearch.findSurface(90, 16, -64, 319,
                y -> y == 90 ? 90.375 : Double.NaN));
        assertEquals(OptionalDouble.of(80.5), VerticalSpawnSearch.findSurface(64.5, 16, -64, 319,
                y -> y == 80 ? 80.5 : Double.NaN));
        assertEquals(OptionalDouble.of(48.5), VerticalSpawnSearch.findSurface(64.5, 16, -64, 319,
                y -> y == 48 ? 48.5 : Double.NaN));
        assertEquals(OptionalDouble.empty(), VerticalSpawnSearch.findSurface(64.5, 16, -64, 319,
                y -> y == 80 ? 80.5625 : Double.NaN));
        assertEquals(OptionalDouble.empty(), VerticalSpawnSearch.findSurface(64.5, 16, -64, 319,
                y -> y == 48 ? 48.4375 : Double.NaN));
        assertEquals(OptionalDouble.of(64.5), VerticalSpawnSearch.findSurface(64.5, 0, -64, 319,
                y -> y == 64 ? 64.5 : Double.NaN));
    }

    @Test
    void blockedSurfaceCanYieldToAnotherFloorWithoutLeavingTheHeightRange() {
        assertEquals(OptionalDouble.of(-30), VerticalSpawnSearch.findSurface(-34, 16, -64, 319,
                y -> y == -31 ? -30 : Double.NaN));
        assertEquals(OptionalDouble.empty(), VerticalSpawnSearch.findSurface(-34, 16, -64, 319,
                y -> y == 89 ? 90 : Double.NaN));
    }
}
