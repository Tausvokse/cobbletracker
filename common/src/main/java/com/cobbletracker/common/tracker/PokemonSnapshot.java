package com.cobbletracker.common.tracker;

import java.util.Locale;
import java.util.Set;

/**
 * Loader-neutral Pokemon traits for matching and persistence. IVs and EVs use HP/Atk/Def/SpA/SpD/Spe
 * order; empty EVs represent all zeros.
 */
public record PokemonSnapshot(
        String speciesId,
        String speciesName,
        boolean shiny,
        boolean legendary,
        boolean mythical,
        boolean ultraBeast,
        boolean boss,
        boolean ancient,
        boolean starter,
        boolean alolan,
        boolean hisuian,
        boolean galarian,
        boolean paldean,
        int level,
        Set<String> labels,
        Set<String> aspects,
        int perfectIvs,
        int ivTotal,
        double scale,
        String gender,
        String nature,
        String form,
        String ability,
        String ivs,
        String evs) {

    public PokemonSnapshot {
        labels = labels == null ? Set.of() : Set.copyOf(labels);
        aspects = aspects == null ? Set.of() : Set.copyOf(aspects);
        speciesId = speciesId == null ? "" : speciesId;
        speciesName = speciesName == null ? "" : speciesName;
        gender = gender == null ? "" : gender.toLowerCase(Locale.ROOT);
        nature = nature == null ? "" : nature.toLowerCase(Locale.ROOT);
        form = form == null ? "" : form.toLowerCase(Locale.ROOT);
        ability = ability == null ? "" : ability.toLowerCase(Locale.ROOT);
        ivs = ivs == null ? "" : ivs;
        evs = evs == null ? "" : evs;
    }

    /** Compatibility constructor for callers without extended flags. */
    public PokemonSnapshot(
            String speciesId,
            String speciesName,
            boolean shiny,
            boolean legendary,
            boolean mythical,
            boolean ultraBeast,
            boolean boss,
            int level,
            Set<String> labels,
            Set<String> aspects,
            int perfectIvs,
            int ivTotal,
            double scale,
            String gender,
            String nature,
            String form,
            String ability,
            String ivs,
            String evs) {
        this(speciesId, speciesName, shiny, legendary, mythical, ultraBeast, boss,
                false, false, false, false, false, false,
                level, labels, aspects, perfectIvs, ivTotal, scale, gender, nature, form, ability, ivs, evs);
    }

    /** The bare species path with no namespace, lower-case (e.g. {@code rayquaza}). */
    public String speciesPath() {
        String s = speciesId.contains(":") ? speciesId.substring(speciesId.indexOf(':') + 1) : speciesId;
        return s.toLowerCase(Locale.ROOT);
    }
}
