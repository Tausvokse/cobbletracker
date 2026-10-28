package com.cobbletracker.common.tracker;

/** Compiled matcher over Pokemon traits and world context. */
@FunctionalInterface
public interface SpecMatcher {

    boolean matches(PokemonSnapshot snapshot, SpawnContext context);

    /** Evaluates with no context. Equivalent to passing {@link SpawnContext#EMPTY}. */
    default boolean matches(PokemonSnapshot snapshot) {
        return matches(snapshot, SpawnContext.EMPTY);
    }

    /** Matches everything (used as the safe fallback for an empty/blank spec). */
    SpecMatcher ANY = (snapshot, context) -> true;

    /** Matches nothing (used as the safe fallback for an unparseable spec). */
    SpecMatcher NONE = (snapshot, context) -> false;

    default SpecMatcher and(SpecMatcher other) {
        return (snapshot, context) -> this.matches(snapshot, context) && other.matches(snapshot, context);
    }

    /** Either side matches. Backs the {@code a|b} form of a spec value (e.g. {@code species:pikachu|raichu}). */
    default SpecMatcher or(SpecMatcher other) {
        return (snapshot, context) -> this.matches(snapshot, context) || other.matches(snapshot, context);
    }

    /** Backs a {@code !}-prefixed spec token (e.g. {@code !species:magikarp}). */
    default SpecMatcher negate() {
        return (snapshot, context) -> !this.matches(snapshot, context);
    }
}
