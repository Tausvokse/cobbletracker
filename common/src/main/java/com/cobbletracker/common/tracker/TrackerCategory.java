package com.cobbletracker.common.tracker;

import java.util.Locale;
import java.util.Set;

/**
 * One tracker category from the {@code trackers:} block of config.yml. Immutable; the compiled
 * {@link SpecMatcher} lives next to it in the registry so this stays a plain data type. Add a category
 * here and it shows up everywhere - announcements, history, stats, the spawner.
 *
 * @param id          key used in tracker.json and packets (e.g. legendaries)
 * @param displayName label shown in the GUI
 * @param spec        matcher spec (see {@link SpecParser})
 * @param color       ARGB tier colour for the beam / GUI accents
 * @param maxStored   history cap; the oldest goes first. {@code 0} announces spawns without keeping any
 *                    history for them.
 * @param blacklist   species names/paths this category never records
 * @param dimensions  only track in these dimension ids; empty means everywhere
 * @param enabled     master switch for the whole category
 */
public record TrackerCategory(
        String id,
        String displayName,
        String spec,
        int color,
        int maxStored,
        Set<String> blacklist,
        Set<String> dimensions,
        TargetKind kind,
        SpawnSettings spawn,
        boolean enabled) {

    public TrackerCategory {
        blacklist = blacklist == null ? Set.of() : Set.copyOf(blacklist);
        dimensions = dimensions == null ? Set.of() : Set.copyOf(dimensions);
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
