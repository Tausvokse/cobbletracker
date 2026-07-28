package com.cobbletracker.common.tracker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SpecParserTest {

    private static PokemonSnapshot mon(String id, String name, boolean shiny, boolean legendary, int level,
            Set<String> labels) {
        return new PokemonSnapshot(id, name, shiny, legendary, false, false, false, level, labels, Set.of());
    }

    @Test
    void legendaryTokenMatchesLegendaries() {
        SpecMatcher m = SpecParser.compile("isLegendary:true");
        assertTrue(m.matches(mon("cobblemon:rayquaza", "Rayquaza", false, true, 70, Set.of("legendary"))));
        assertFalse(m.matches(mon("cobblemon:pidgey", "Pidgey", false, false, 5, Set.of())));
        assertNull(SpecParser.lastError());
    }

    @Test
    void bareBooleanTokenDefaultsTrue() {
        SpecMatcher m = SpecParser.compile("isShiny");
        assertTrue(m.matches(mon("cobblemon:pikachu", "Pikachu", true, false, 12, Set.of())));
        assertFalse(m.matches(mon("cobblemon:pikachu", "Pikachu", false, false, 12, Set.of())));
    }

    @Test
    void multipleTokensAreAnded() {
        SpecMatcher m = SpecParser.compile("isShiny:true level:>50");
        assertTrue(m.matches(mon("cobblemon:gyarados", "Gyarados", true, false, 55, Set.of())));
        assertFalse(m.matches(mon("cobblemon:gyarados", "Gyarados", true, false, 40, Set.of())));
        assertFalse(m.matches(mon("cobblemon:gyarados", "Gyarados", false, false, 55, Set.of())));
    }

    @Test
    void speciesTokenMatchesPathOrId() {
        SpecMatcher m = SpecParser.compile("species:rayquaza");
        assertTrue(m.matches(mon("cobblemon:rayquaza", "Rayquaza", false, true, 70, Set.of())));
        assertFalse(m.matches(mon("cobblemon:lugia", "Lugia", false, true, 70, Set.of())));
    }

    @Test
    void blankSpecMatchesAnything() {
        assertTrue(SpecParser.compile("").matches(mon("cobblemon:x", "X", false, false, 1, Set.of())));
        assertTrue(SpecParser.compile(null).matches(mon("cobblemon:x", "X", false, false, 1, Set.of())));
    }

    @Test
    void unknownTokenMatchesNothingAndReportsError() {
        SpecMatcher m = SpecParser.compile("isFluffy:true");
        assertFalse(m.matches(mon("cobblemon:x", "X", false, false, 1, Set.of())));
        assertNotNull(SpecParser.lastError());
    }

    @Test
    void customTokenCanBeRegistered() {
        SpecParser.register("namestartswith", value -> snap ->
                snap.speciesName().toLowerCase(java.util.Locale.ROOT).startsWith(value.toLowerCase(java.util.Locale.ROOT)));
        SpecMatcher m = SpecParser.compile("nameStartsWith:ray");
        assertTrue(m.matches(mon("cobblemon:rayquaza", "Rayquaza", false, true, 70, Set.of())));
        assertFalse(m.matches(mon("cobblemon:lugia", "Lugia", false, true, 70, Set.of())));
    }
}
