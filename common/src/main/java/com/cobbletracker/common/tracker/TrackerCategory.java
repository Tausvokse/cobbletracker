package com.cobbletracker.common.tracker;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Immutable tracker configuration. A history cap of 0 disables retention; an empty dimension set
 * allows all dimensions.
 */
public record TrackerCategory(
        String id,
        String displayName,
        String spec,
        int color,
        int maxStored,
        Set<String> blacklist,
        Set<String> dimensions,
        List<String> onSpawnCommands,
        List<String> onCatchCommands,
        SpawnSettings spawn,
        boolean enabled) {

    public TrackerCategory {
        blacklist = blacklist == null ? Set.of() : Set.copyOf(blacklist);
        dimensions = dimensions == null ? Set.of() : Set.copyOf(dimensions);
        // Preserve hook order, case and deliberate duplicates.
        onSpawnCommands = onSpawnCommands == null ? List.of() : List.copyOf(onSpawnCommands);
        onCatchCommands = onCatchCommands == null ? List.of() : List.copyOf(onCatchCommands);
        if (maxStored < 0) {
            maxStored = 0;
        }
        if (spawn == null) {
            spawn = SpawnSettings.disabled();
        }
    }

    /** True if the species name/path is blacklisted here. */
    public boolean isBlacklisted(String speciesNameOrPath) {
        if (speciesNameOrPath == null) {
            return false;
        }
        return blacklist.contains(speciesNameOrPath.toLowerCase(Locale.ROOT));
    }

    /** True if this category should track spawns in the given dimension (empty filter = all). */
    public boolean allowsDimension(String dimensionId) {
        if (dimensions.isEmpty()) {
            return true;
        }
        return dimensionId != null && dimensions.contains(dimensionId.toLowerCase(Locale.ROOT));
    }
}
