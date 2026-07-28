package com.cobbletracker.mod.tracker;

import com.cobbletracker.common.tracker.PokemonSnapshot;
import com.cobbletracker.common.tracker.SpecMatcher;
import com.cobbletracker.common.tracker.SpecParser;
import com.cobbletracker.common.tracker.TargetKind;
import com.cobbletracker.common.tracker.TrackerCategory;
import com.cobbletracker.mod.CobbleTracker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An ordered list of {@link TrackerCategory} definitions from config.yml, each with its compiled
 * {@link SpecMatcher}. Announcements, persistence and the GUI all read categories from here, so adding or
 * removing one in the config takes effect everywhere. Rebuilt on reload.
 */
public final class TrackerRegistry {

    private final List<TrackerCategory> categories;
    private final Map<String, SpecMatcher> matchers = new LinkedHashMap<>();

    public TrackerRegistry(List<TrackerCategory> categories) {
        this.categories = List.copyOf(categories);
        for (TrackerCategory category : this.categories) {
            matchers.put(category.id(), SpecParser.compile(category.spec()));
            String error = SpecParser.lastError();
            if (error != null) {
                // The category still loads, it just matches nothing - say so, or a typo looks like the
                // tracker silently doing nothing.
                CobbleTracker.LOGGER.warn("Tracker '{}' has an unusable spec (\"{}\"): {} - it will not match "
                        + "anything until fixed", category.id(), category.spec(), error);
            }
        }
    }

    public List<TrackerCategory> categories() {
        return categories;
    }

    public Optional<TrackerCategory> byId(String id) {
        for (TrackerCategory c : categories) {
            if (c.id().equals(id)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /**
     * The first enabled Pokémon category that matches this snapshot in the given dimension and doesn't
     * blacklist the species.
     */
    public Optional<TrackerCategory> firstMatch(PokemonSnapshot snapshot, String dimension) {
        for (TrackerCategory category : categories) {
            if (category.kind() != TargetKind.POKEMON || !category.enabled()) {
                continue;
            }
            if (!category.allowsDimension(dimension)) {
                continue;
            }
            if (category.isBlacklisted(snapshot.speciesName()) || category.isBlacklisted(snapshot.speciesPath())) {
                continue;
            }
            SpecMatcher matcher = matchers.getOrDefault(category.id(), SpecMatcher.NONE);
            if (matcher.matches(snapshot)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
