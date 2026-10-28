package com.cobbletracker.common.spawn;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SpawnAreaCoverageTest {
    @Test
    void coversEveryColumnOfPossibleNativeZonesWithoutDuplicateCells() {
        Random random = new Random(417762340L);
        for (double[] player : new double[][] {{0, 0}, {-2449.5, 304.5}, {-0.01, -0.99}, {1234.75, -987.25}}) {
            for (int size : new int[] {7, 8, 16}) {
                var coverage = new SpawnAreaCoverage(player[0], player[1], 16, 64, size);
                var cells = new HashSet<SpawnAreaCoverage.Cell>();
                coverage.forEach(cell -> assertTrue(cells.add(cell), "Duplicate cell"));
                for (int sample = 0; sample < 200; sample++) {
                    double radius = sample < 2 ? sample == 0 ? 16 : 64 : 16 + random.nextDouble() * 48;
                    double angle = random.nextDouble() * Math.PI * 2;
                    int lowX = (int) Math.floor(player[0] + Math.cos(angle) * radius - size / 2.0);
                    int lowZ = (int) Math.floor(player[1] + Math.sin(angle) * radius - size / 2.0);
                    for (int x = lowX; x < lowX + size; x++) {
                        for (int z = lowZ; z < lowZ + size; z++) {
                            assertTrue(coverage.allowsColumn(x, z), "Native column excluded");
                            assertTrue(cells.contains(new SpawnAreaCoverage.Cell(
                                    Math.floorDiv(x, size) * size, Math.floorDiv(z, size) * size, size)),
                                    "Native column has no scan cell");
                        }
                    }
                }
            }
        }
    }

    @Test
    void excludesUnreachableColumnsAndHonorsTheInnerRadius() {
        var coverage = new SpawnAreaCoverage(0, 0, 16, 64, 8);
        assertFalse(coverage.allowsColumn(0, 0));
        assertFalse(coverage.allowsColumn(100, 0));
        assertFalse(coverage.allowsColumn(64, 64));
        assertTrue(coverage.allowsColumn(60, 0));
        assertTrue(coverage.allowsColumn(-64, -1));
    }

    @Test
    void zeroRadiusMatchesTheOneNativeZoneIncludingNegativeCoordinates() {
        var coverage = new SpawnAreaCoverage(-0.25, -0.75, 0, 0, 8);
        for (int x = -7; x <= 6; x++) {
            for (int z = -7; z <= 6; z++) {
                assertEquals(x >= -5 && x <= 2 && z >= -5 && z <= 2, coverage.allowsColumn(x, z));
            }
        }
    }

    @Test
    void iteratorIsRepeatableAndStopsAtTheEnd() {
        var coverage = new SpawnAreaCoverage(-2449.5, 304.5, 16, 64, 8);
        var first = coverage.iterator();
        var second = coverage.iterator();
        int count = 0;
        while (first.hasNext()) {
            assertTrue(first.hasNext());
            assertEquals(first.next(), second.next());
            count++;
        }
        assertFalse(second.hasNext());
        assertThrows(java.util.NoSuchElementException.class, first::next);
        assertTrue(count > 100 && count < 400);
    }

    @Test
    void changingWorkTileSizeDoesNotChangeTheSearchDomain() {
        var reference = new SpawnAreaCoverage(-2449.5, 304.5, 16, 64, 8);
        for (int tileSize : new int[] {3, 4, 8, 16}) {
            var coverage = new SpawnAreaCoverage(-2449.5, 304.5, 16, 64, 8, tileSize);
            var cells = new HashSet<SpawnAreaCoverage.Cell>();
            coverage.forEach(cell -> assertTrue(cells.add(cell)));
            for (int x = -2520; x <= -2380; x++) {
                for (int z = 234; z <= 375; z++) {
                    boolean allowed = reference.allowsColumn(x, z);
                    assertEquals(allowed, coverage.allowsColumn(x, z));
                    if (allowed) {
                        assertTrue(cells.contains(new SpawnAreaCoverage.Cell(
                                Math.floorDiv(x, tileSize) * tileSize,
                                Math.floorDiv(z, tileSize) * tileSize, tileSize)));
                    }
                }
            }
        }
    }

    @Test
    void splittingUnavailableTilesPreservesEveryColumnWithoutOverlap() {
        for (int x : new int[] {-32, -16, 0, 16}) {
            for (int z : new int[] {-32, -16, 0, 16}) {
                var parent = new SpawnAreaCoverage.Cell(x, z, 16);
                var columns = new HashSet<String>();
                for (var child : parent.split()) {
                    assertEquals(8, child.size());
                    for (int px = child.x(); px < child.x() + child.size(); px++) {
                        for (int pz = child.z(); pz < child.z() + child.size(); pz++) {
                            assertTrue(px >= x && px < x + 16 && pz >= z && pz < z + 16);
                            assertTrue(columns.add(px + ":" + pz), "Overlapping fallback columns");
                        }
                    }
                }
                assertEquals(256, columns.size(), "Fallback lost reachable columns");
            }
        }
        assertThrows(IllegalStateException.class, () -> new SpawnAreaCoverage.Cell(0, 0, 3).split());
        assertThrows(IllegalStateException.class, () -> new SpawnAreaCoverage.Cell(0, 0, 1).split());
    }
}
