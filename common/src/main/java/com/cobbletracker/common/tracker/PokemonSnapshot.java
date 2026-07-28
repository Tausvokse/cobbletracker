package com.cobbletracker.common.tracker;

import java.util.Set;

/**
 * A loader-neutral description of a spawned Pokémon, captured once from the Cobblemon entity and then
 * used by the pure-logic layer (spec matching, persistence). Keeping this a plain record means the
 * matcher engine and its unit tests never touch Minecraft or Cobblemon types.
 *
 * @param speciesId  namespaced id, e.g. {@code cobblemon:rayquaza}
 * @param speciesName display name, e.g. {@code Rayquaza}
 * @param labels     species labels (lower-case), e.g. {@code legendary}, {@code mythical}, {@code ultra_beast}
 * @param aspects    Cobblemon aspects (lower-case), e.g. {@code shiny}, {@code female}, form aspects
 */
public record PokemonSnapshot(
        String speciesId,
        String speciesName,
        boolean shiny,
        boolean legendary,
        boolean mythical,
        boolean ultraBeast,
        boolean boss,
        int level,
        Set<String> labels,
        Set<String> aspects) {

    public PokemonSnapshot {
        labels = labels == null ? Set.of() : Set.copyOf(labels);
        aspects = aspects == null ? Set.of() : Set.copyOf(aspects);
        speciesId = speciesId == null ? "" : speciesId;
        speciesName = speciesName == null ? "" : speciesName;
    }

    /** The bare species path with no namespace, lower-case (e.g. {@code rayquaza}). */
    public String speciesPath() {
        String s = speciesId.contains(":") ? speciesId.substring(speciesId.indexOf(':') + 1) : speciesId;
        return s.toLowerCase(java.util.Locale.ROOT);
    }
}
