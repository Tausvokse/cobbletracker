package com.cobbletracker.common.spawn;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public final class SpawnAreaCoverage implements Iterable<SpawnAreaCoverage.Cell> {
    public record Cell(int x, int z, int size) {
        public List<Cell> split() {
            if (size < 2 || size % 2 != 0) {
                throw new IllegalStateException("Cell subdivision requires an even size of at least 2");
            }
            int half = size / 2;
            int middleX = Math.addExact(x, half);
            int middleZ = Math.addExact(z, half);
            return List.of(new Cell(x, z, half), new Cell(middleX, z, half),
                    new Cell(x, middleZ, half), new Cell(middleX, middleZ, half));
        }
    }

    private final double x;
    private final double z;
    private final double minimum;
    private final double maximum;
    private final int size;
    private final int tileSize;
    private final double half;
    private final int firstX;
    private final int firstZ;
    private final int lastX;
    private final int lastZ;

    public SpawnAreaCoverage(double x, double z, double minimum, double maximum, int size) {
        this(x, z, minimum, maximum, size, size);
    }

    public SpawnAreaCoverage(double x, double z, double minimum, double maximum, int size, int tileSize) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(minimum)
                || !Double.isFinite(maximum) || minimum < 0 || maximum < minimum || size < 1 || tileSize < 1) {
            throw new IllegalArgumentException("Invalid spawn coverage dimensions");
        }
        this.x = x;
        this.z = z;
        this.minimum = minimum;
        this.maximum = maximum;
        this.size = size;
        this.tileSize = tileSize;
        this.half = size / 2.0;
        firstX = cellStart(floor(x - maximum - half));
        firstZ = cellStart(floor(z - maximum - half));
        lastX = cellStart(Math.addExact(floor(x + maximum - half), size - 1));
        lastZ = cellStart(Math.addExact(floor(z + maximum - half), size - 1));
    }

    public boolean allowsColumn(int blockX, int blockZ) {
        return intersects(blockX - size + 1.0 + half, blockZ - size + 1.0 + half,
                blockX + 1.0 + half, blockZ + 1.0 + half);
    }

    private boolean intersects(double lowX, double lowZ, double highX, double highZ) {
        if (maximum == 0) {
            return x >= lowX && x < highX && z >= lowZ && z < highZ;
        }
        highX = Math.nextDown(highX);
        highZ = Math.nextDown(highZ);
        double nearX = Math.max(lowX - x, Math.max(0, x - highX));
        double nearZ = Math.max(lowZ - z, Math.max(0, z - highZ));
        double farX = Math.max(Math.abs(lowX - x), Math.abs(highX - x));
        double farZ = Math.max(Math.abs(lowZ - z), Math.abs(highZ - z));
        return nearX * nearX + nearZ * nearZ <= maximum * maximum
                && farX * farX + farZ * farZ >= minimum * minimum;
    }

    private int cellStart(int block) {
        return Math.toIntExact((long) Math.floorDiv(block, tileSize) * tileSize);
    }

    private static int floor(double value) {
        return Math.toIntExact((long) Math.floor(value));
    }

    @Override
    public Iterator<Cell> iterator() {
        return new Iterator<>() {
            private long column = firstX;
            private long row = firstZ;
            private Cell next;

            @Override
            public boolean hasNext() {
                while (next == null && row <= lastZ) {
                    int cellX = Math.toIntExact(column);
                    int cellZ = Math.toIntExact(row);
                    column += tileSize;
                    if (column > lastX) {
                        column = firstX;
                        row += tileSize;
                    }
                    if (intersects(cellX - size + 1.0 + half, cellZ - size + 1.0 + half,
                            cellX + tileSize + half, cellZ + tileSize + half)) {
                        next = new Cell(cellX, cellZ, tileSize);
                    }
                }
                return next != null;
            }

            @Override
            public Cell next() {
                if (!hasNext()) throw new NoSuchElementException();
                Cell cell = next;
                next = null;
                return cell;
            }
        };
    }
}
