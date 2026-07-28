package com.cobbletracker.common.tracker;

/**
 * A compiled predicate over a {@link PokemonSnapshot}. Categories declare a spec string in
 * {@code config.yml} (e.g. {@code "isLegendary:true"}); {@link SpecParser} compiles it into one of
 * these. It stays a single-method interface so a new spec token is just another lambda over there.
 */
@FunctionalInterface
public interface SpecMatcher {

    boolean matches(PokemonSnapshot snapshot);

    /** Matches everything (used as the safe fallback for an empty/blank spec). */
    SpecMatcher ANY = snapshot -> true;

    /** Matches nothing (used as the safe fallback for an unparseable spec). */
    SpecMatcher NONE = snapshot -> false;

    default SpecMatcher and(SpecMatcher other) {
        return snapshot -> this.matches(snapshot) && other.matches(snapshot);
    }
}
