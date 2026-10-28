package com.cobbletracker.common.spawn;

import java.util.OptionalDouble;
import java.util.function.IntToDoubleFunction;

public final class VerticalSpawnSearch {
    private VerticalSpawnSearch() {
    }

    public static OptionalDouble findSurface(double anchorY, int distance, int minY, int maxY,
            IntToDoubleFunction surface) {
        int origin = (int) Math.floor(anchorY) - 1;
        for (int offset = 0; offset <= distance + 2; offset++) {
            for (int direction = 0; direction < (offset == 0 ? 1 : 2); direction++) {
                int blockY = direction == 0 ? origin - offset : origin + offset;
                if (blockY < minY || blockY > maxY) continue;
                double feetY = surface.applyAsDouble(blockY);
                if (Double.isFinite(feetY) && Math.abs(feetY - anchorY) <= distance) {
                    return OptionalDouble.of(feetY);
                }
            }
        }
        return OptionalDouble.empty();
    }
}
