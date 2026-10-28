package com.cobbletracker.mod.tracker;

import com.cobbletracker.common.tracker.PokemonSnapshot;
import com.cobbletracker.common.tracker.SpawnContext;
import com.cobbletracker.common.tracker.SpecMatcher;
import com.cobbletracker.common.tracker.SpecParser;
import com.cobbletracker.common.tracker.TrackerCategory;
import com.cobbletracker.mod.CobbleTracker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ordered category definitions with compiled matchers. */
public final class TrackerRegistry {

    private final List<TrackerCategory> categories;
    private final Map<String, SpecMatcher> matchers = new LinkedHashMap<>();

    public TrackerRegistry(List<TrackerCategory> categories) {
        this.categories = List.copyOf(categories);
        for (TrackerCategory category : this.categories) {
            matchers.put(category.id(), SpecParser.compile(category.spec()));
            String error = SpecParser.lastError();
            if (error != null) {
                // Keep invalid categories loaded, but report that their matcher rejects all spawns.
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

    /** Tests the compiled spec only; callers enforce dimensions and blacklists separately. */
    public boolean matches(TrackerCategory category, PokemonSnapshot snapshot, SpawnContext context) {
        return matchers.getOrDefault(category.id(), SpecMatcher.NONE).matches(snapshot, context);
    }

    /** First enabled spec match excluding blacklisted species. Dimensions are checked separately. */
    public Optional<TrackerCategory> firstMatch(PokemonSnapshot snapshot, SpawnContext context) {
        for (TrackerCategory category : categories) {
            if (!category.enabled()) {
                continue;
            }
            if (!category.allowsDimension(context.dimension())) {
                continue;
            }
            if (category.isBlacklisted(snapshot.speciesName()) || category.isBlacklisted(snapshot.speciesPath())) {
                continue;
            }
            SpecMatcher matcher = matchers.getOrDefault(category.id(), SpecMatcher.NONE);
            if (matcher.matches(snapshot, context)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
