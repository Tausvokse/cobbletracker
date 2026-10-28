package com.cobbletracker.common.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SpecParserTest {

    private static PokemonSnapshot mon(String id, String name, boolean shiny, boolean legendary, int level,
            Set<String> labels) {
        return new PokemonSnapshot(id, name, shiny, legendary, false, false, false, level, labels, Set.of(),
                0, 0, 1.0, "male", "adamant", "normal", "static", "", "");
    }

    
    private static PokemonSnapshot detailed(int perfectIvs, int ivTotal, double scale, String gender,
            String nature, String form) {
        return new PokemonSnapshot("cobblemon:pikachu", "Pikachu", false, false, false, false, false, 25,
                Set.of(), Set.of(), perfectIvs, ivTotal, scale, gender, nature, form, "static", "", "");
    }

    @Test
    void legendaryTokenMatchesLegendaries() {
        SpecMatcher m = SpecParser.compile("isLegendary:true");
        assertTrue(m.matches(mon("cobblemon:rayquaza", "Rayquaza", false, true, 70, Set.of("legendary"))));
        assertFalse(m.matches(mon("cobblemon:pidgey", "Pidgey", false, false, 5, Set.of())));
        assertNull(SpecParser.lastError());
    }

    @Test
    void extendedCategoryTokensMatchFlags() {
        PokemonSnapshot ancientMon = new PokemonSnapshot("cobblemon:roaring_moon", "Roaring Moon",
                false, false, false, false, false, true, false, false, false, false, false,
                65, Set.of("ancient", "paradox"), Set.of(), 3, 150, 1.0, "genderless", "adamant", "normal", "protosynthesis", "", "");
        PokemonSnapshot starterMon = new PokemonSnapshot("cobblemon:charizard", "Charizard",
                false, false, false, false, false, false, true, false, false, false, false,
                36, Set.of("starter"), Set.of(), 0, 120, 1.0, "male", "modest", "normal", "blaze", "", "");
        PokemonSnapshot alolanMon = new PokemonSnapshot("cobblemon:raichu", "Raichu",
                false, false, false, false, false, false, false, true, false, false, false,
                30, Set.of("alolan"), Set.of("alolan"), 0, 110, 1.0, "male", "timid", "alolan", "surge_surfer", "", "");
        PokemonSnapshot hisuianMon = new PokemonSnapshot("cobblemon:zoroark", "Zoroark",
                false, false, false, false, false, false, false, false, true, false, false,
                50, Set.of("hisuian"), Set.of("hisuian"), 0, 110, 1.0, "male", "timid", "hisuian", "illusion", "", "");
        PokemonSnapshot galarianMon = new PokemonSnapshot("cobblemon:zigzagoon", "Zigzagoon",
                false, false, false, false, false, false, false, false, false, true, false,
                15, Set.of("galarian"), Set.of("galarian"), 0, 90, 1.0, "male", "jolly", "galarian", "pickup", "", "");
        PokemonSnapshot paldeanMon = new PokemonSnapshot("cobblemon:wooper", "Wooper",
                false, false, false, false, false, false, false, false, false, false, true,
                10, Set.of("paldean"), Set.of("paldean"), 0, 85, 1.0, "female", "relaxed", "paldean", "poison_point", "", "");

        assertTrue(SpecParser.compile("isAncient").matches(ancientMon));
        assertFalse(SpecParser.compile("isAncient").matches(starterMon));

        assertTrue(SpecParser.compile("isStarter").matches(starterMon));
        assertFalse(SpecParser.compile("isStarter").matches(ancientMon));

        assertTrue(SpecParser.compile("isAlolan").matches(alolanMon));
        assertFalse(SpecParser.compile("isAlolan").matches(starterMon));

        assertTrue(SpecParser.compile("isHisuian").matches(hisuianMon));
        assertFalse(SpecParser.compile("isHisuian").matches(paldeanMon));

        assertTrue(SpecParser.compile("isGalarian").matches(galarianMon));
        assertFalse(SpecParser.compile("isGalarian").matches(alolanMon));

        assertTrue(SpecParser.compile("isPaldean").matches(paldeanMon));
        assertFalse(SpecParser.compile("isPaldean").matches(galarianMon));
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
    void unreadableBooleanIsAnErrorRatherThanASilentInversion() {
        
        SpecMatcher m = SpecParser.compile("isLegendary:maybe");
        assertFalse(m.matches(mon("cobblemon:pidgey", "Pidgey", false, false, 5, Set.of())));
        assertFalse(m.matches(mon("cobblemon:rayquaza", "Rayquaza", false, true, 70, Set.of("legendary"))));
        assertNotNull(SpecParser.lastError());
    }

    @Test
    void everydayBooleanSpellingsAreAccepted() {
        assertTrue(SpecParser.compile("isLegendary:yes")
                .matches(mon("cobblemon:rayquaza", "Rayquaza", false, true, 70, Set.of("legendary"))));
        assertNull(SpecParser.lastError());
        assertTrue(SpecParser.compile("isLegendary:no")
                .matches(mon("cobblemon:pidgey", "Pidgey", false, false, 5, Set.of())));
        assertNull(SpecParser.lastError());
    }

    @Test
    void expectationReadsBooleanTokensForTheSpawner() {
        assertEquals(Boolean.TRUE, SpecParser.expectation("isShiny:true level:>10", "isShiny"));
        assertEquals(Boolean.TRUE, SpecParser.expectation("isShiny", "isshiny"));
        assertEquals(Boolean.FALSE, SpecParser.expectation("isShiny:false", "isShiny"));
        assertNull(SpecParser.expectation("isLegendary:true", "isShiny"));
        assertNull(SpecParser.expectation("isShiny:perhaps", "isShiny"));
        assertNull(SpecParser.expectation("", "isShiny"));
        assertNull(SpecParser.expectation(null, "isShiny"));
    }

    @Test
    void leadingBangNegatesAToken() {
        SpecMatcher m = SpecParser.compile("!isShiny");
        assertTrue(m.matches(mon("cobblemon:pikachu", "Pikachu", false, false, 12, Set.of())));
        assertFalse(m.matches(mon("cobblemon:pikachu", "Pikachu", true, false, 12, Set.of())));
        assertNull(SpecParser.lastError());
    }

    @Test
    void negationCombinesWithTheOtherTokens() {
        
        SpecMatcher m = SpecParser.compile("isShiny:true !species:magikarp");
        assertTrue(m.matches(mon("cobblemon:gyarados", "Gyarados", true, false, 30, Set.of())));
        assertFalse(m.matches(mon("cobblemon:magikarp", "Magikarp", true, false, 30, Set.of())));
        assertFalse(m.matches(mon("cobblemon:gyarados", "Gyarados", false, false, 30, Set.of())));
        assertNull(SpecParser.lastError());
    }

    @Test
    void bareBangIsAnError() {
        assertFalse(SpecParser.compile("!").matches(mon("cobblemon:x", "X", false, false, 1, Set.of())));
        assertNotNull(SpecParser.lastError());
    }

    @Test
    void pipeInAValueIsOr() {
        SpecMatcher m = SpecParser.compile("species:pikachu|raichu");
        assertTrue(m.matches(mon("cobblemon:pikachu", "Pikachu", false, false, 12, Set.of())));
        assertTrue(m.matches(mon("cobblemon:raichu", "Raichu", false, false, 30, Set.of())));
        assertFalse(m.matches(mon("cobblemon:pidgey", "Pidgey", false, false, 5, Set.of())));
        assertNull(SpecParser.lastError());
    }

    @Test
    void orAppliesToEveryTokenNotJustSpecies() {
        
        SpecMatcher m = SpecParser.compile("level:>50|<10");
        assertTrue(m.matches(mon("cobblemon:gyarados", "Gyarados", false, false, 55, Set.of())));
        assertTrue(m.matches(mon("cobblemon:magikarp", "Magikarp", false, false, 5, Set.of())));
        assertFalse(m.matches(mon("cobblemon:magikarp", "Magikarp", false, false, 30, Set.of())));
        assertNull(SpecParser.lastError());
    }

    @Test
    void negationAndOrCombineInOneToken() {
        SpecMatcher m = SpecParser.compile("!species:magikarp|pidgey");
        assertFalse(m.matches(mon("cobblemon:magikarp", "Magikarp", false, false, 5, Set.of())));
        assertFalse(m.matches(mon("cobblemon:pidgey", "Pidgey", false, false, 5, Set.of())));
        assertTrue(m.matches(mon("cobblemon:rayquaza", "Rayquaza", false, true, 70, Set.of())));
        assertNull(SpecParser.lastError());
    }

    @Test
    void emptyAlternativeIsAnErrorRatherThanBeingSkipped() {
        
        assertFalse(SpecParser.compile("species:pikachu||raichu")
                .matches(mon("cobblemon:pikachu", "Pikachu", false, false, 12, Set.of())));
        assertNotNull(SpecParser.lastError());
        assertFalse(SpecParser.compile("species:pikachu|")
                .matches(mon("cobblemon:pikachu", "Pikachu", false, false, 12, Set.of())));
        assertNotNull(SpecParser.lastError());
    }

    @Test
    void expectationRefusesNegatedAndAlternatedTokens() {
        
        assertNull(SpecParser.expectation("!isShiny", "isShiny"));
        assertNull(SpecParser.expectation("isLegendary:true !isShiny:true", "isShiny"));
        assertNull(SpecParser.expectation("isShiny:true|false", "isShiny"));
        
        assertEquals(Boolean.TRUE, SpecParser.expectation("isShiny:true !species:magikarp", "isShiny"));
    }

    @Test
    void perfectIvsCountsMaxedStats() {
        SpecMatcher m = SpecParser.compile("perfectIvs:>=4");
        assertTrue(m.matches(detailed(4, 150, 1.0, "male", "adamant", "normal")));
        assertTrue(m.matches(detailed(6, 186, 1.0, "male", "adamant", "normal")));
        assertFalse(m.matches(detailed(3, 140, 1.0, "male", "adamant", "normal")));
        assertNull(SpecParser.lastError());
    }

    @Test
    void ivTotalIsComparable() {
        SpecMatcher m = SpecParser.compile("ivs:>=150");
        assertTrue(m.matches(detailed(0, 150, 1.0, "male", "adamant", "normal")));
        assertFalse(m.matches(detailed(0, 149, 1.0, "male", "adamant", "normal")));
    }

    @Test
    void scaleComparesFractionally() {
        
        SpecMatcher big = SpecParser.compile("scale:>1.15");
        assertTrue(big.matches(detailed(0, 0, 1.16, "male", "adamant", "normal")));
        assertFalse(big.matches(detailed(0, 0, 1.15, "male", "adamant", "normal")));
        assertFalse(big.matches(detailed(0, 0, 1.0, "male", "adamant", "normal")));
        SpecMatcher small = SpecParser.compile("scale:<0.9");
        assertTrue(small.matches(detailed(0, 0, 0.85, "male", "adamant", "normal")));
        assertFalse(small.matches(detailed(0, 0, 1.0, "male", "adamant", "normal")));
        assertNull(SpecParser.lastError());
    }

    @Test
    void exactScaleMatchesDespiteFloatWidening() {
        
        PokemonSnapshot mon = detailed(0, 0, 1.15f, "male", "adamant", "normal");
        assertTrue(SpecParser.compile("scale:1.15").matches(mon));
        assertFalse(SpecParser.compile("scale:1.16").matches(mon));
        
        assertTrue(SpecParser.compile("perfectIvs:4")
                .matches(detailed(4, 0, 1.0, "male", "adamant", "normal")));
        assertFalse(SpecParser.compile("perfectIvs:4")
                .matches(detailed(5, 0, 1.0, "male", "adamant", "normal")));
    }

    @Test
    void genderNatureAndFormMatchCaseInsensitively() {
        assertTrue(SpecParser.compile("gender:FEMALE")
                .matches(detailed(0, 0, 1.0, "female", "adamant", "normal")));
        assertFalse(SpecParser.compile("gender:female")
                .matches(detailed(0, 0, 1.0, "male", "adamant", "normal")));
        assertTrue(SpecParser.compile("nature:Adamant")
                .matches(detailed(0, 0, 1.0, "male", "adamant", "normal")));
        assertTrue(SpecParser.compile("form:alolan")
                .matches(detailed(0, 0, 1.0, "male", "adamant", "alolan")));
        assertFalse(SpecParser.compile("form:alolan")
                .matches(detailed(0, 0, 1.0, "male", "adamant", "normal")));
    }

    @Test
    void newTokensGetTheOperatorsToo() {
        
        assertTrue(SpecParser.compile("!gender:female")
                .matches(detailed(0, 0, 1.0, "male", "adamant", "normal")));
        assertTrue(SpecParser.compile("nature:adamant|jolly")
                .matches(detailed(0, 0, 1.0, "male", "jolly", "normal")));
        assertFalse(SpecParser.compile("nature:adamant|jolly")
                .matches(detailed(0, 0, 1.0, "male", "timid", "normal")));
        assertNull(SpecParser.lastError());
    }

    @Test
    void anUnreadableNumberIsReported() {
        assertFalse(SpecParser.compile("perfectIvs:>lots")
                .matches(detailed(6, 186, 1.0, "male", "adamant", "normal")));
        assertNotNull(SpecParser.lastError());
    }

    @Test
    void customTokenCanBeRegistered() {
        SpecParser.register("namestartswith", value -> (snap, ctx) ->
                snap.speciesName().toLowerCase(java.util.Locale.ROOT).startsWith(value.toLowerCase(java.util.Locale.ROOT)));
        SpecMatcher m = SpecParser.compile("nameStartsWith:ray");
        assertTrue(m.matches(mon("cobblemon:rayquaza", "Rayquaza", false, true, 70, Set.of())));
        assertFalse(m.matches(mon("cobblemon:lugia", "Lugia", false, true, 70, Set.of())));
    }

    

    private static final PokemonSnapshot ANY_MON =
            new PokemonSnapshot("cobblemon:pikachu", "Pikachu", false, false, false, false, false, 25,
                    Set.of(), Set.of(), 0, 0, 1.0, "male", "adamant", "normal", "static", "", "");

    private static SpawnContext at(String biome, long dayTime, boolean rain, boolean thunder, int y) {
        return new SpawnContext(biome, "minecraft:overworld", dayTime, rain, thunder, y);
    }

    @Test
    void biomeTokenAcceptsIdOrBarePath() {
        SpecMatcher m = SpecParser.compile("biome:minecraft:jungle");
        assertTrue(m.matches(ANY_MON, at("minecraft:jungle", 0, false, false, 64)));
        assertFalse(m.matches(ANY_MON, at("minecraft:desert", 0, false, false, 64)));
        assertTrue(SpecParser.compile("biome:jungle")
                .matches(ANY_MON, at("minecraft:jungle", 0, false, false, 64)));
    }

    @Test
    void aNamespacedBiomeDoesNotMatchAnotherNamespace() {
        
        assertFalse(SpecParser.compile("biome:minecraft:jungle")
                .matches(ANY_MON, at("terralith:jungle", 0, false, false, 64)));
        
        assertTrue(SpecParser.compile("biome:jungle")
                .matches(ANY_MON, at("terralith:jungle", 0, false, false, 64)));
    }

    @Test
    void timeAndWeatherTokens() {
        assertTrue(SpecParser.compile("time:day").matches(ANY_MON, at("b", 1000, false, false, 64)));
        assertFalse(SpecParser.compile("time:day").matches(ANY_MON, at("b", 18000, false, false, 64)));
        assertTrue(SpecParser.compile("time:night").matches(ANY_MON, at("b", 18000, false, false, 64)));
        assertTrue(SpecParser.compile("weather:rain").matches(ANY_MON, at("b", 0, true, false, 64)));
        assertTrue(SpecParser.compile("weather:clear").matches(ANY_MON, at("b", 0, false, false, 64)));
        assertTrue(SpecParser.compile("weather:thunder").matches(ANY_MON, at("b", 0, true, true, 64)));
        assertFalse(SpecParser.compile("weather:thunder").matches(ANY_MON, at("b", 0, true, false, 64)));
    }

    @Test
    void yTokenComparesTheSpawnHeight() {
        assertTrue(SpecParser.compile("y:<0").matches(ANY_MON, at("b", 0, false, false, -20)));
        assertFalse(SpecParser.compile("y:<0").matches(ANY_MON, at("b", 0, false, false, 10)));
        assertTrue(SpecParser.compile("y:>=120").matches(ANY_MON, at("b", 0, false, false, 120)));
    }

    @Test
    void contextTokensDeclineWithoutAContext() {
        
        assertFalse(SpecParser.compile("time:night").matches(ANY_MON));
        assertFalse(SpecParser.compile("biome:jungle").matches(ANY_MON));
        assertFalse(SpecParser.compile("weather:clear").matches(ANY_MON));
    }

    @Test
    void pokemonTokensAreUnaffectedByContext() {
        
        SpecMatcher m = SpecParser.compile("isShiny:false level:25");
        assertTrue(m.matches(ANY_MON));
        assertTrue(m.matches(ANY_MON, at("minecraft:jungle", 18000, true, true, -40)));
    }

    @Test
    void contextTokensCombineWithPokemonOnes() {
        SpecMatcher m = SpecParser.compile("isShiny:false time:night");
        assertTrue(m.matches(ANY_MON, at("b", 18000, false, false, 64)));
        assertFalse(m.matches(ANY_MON, at("b", 1000, false, false, 64)));
        assertNull(SpecParser.lastError());
    }

    @Test
    void badContextValuesAreReported() {
        assertFalse(SpecParser.compile("time:dusk").matches(ANY_MON, at("b", 0, false, false, 64)));
        assertNotNull(SpecParser.lastError());
        assertFalse(SpecParser.compile("weather:snow").matches(ANY_MON, at("b", 0, false, false, 64)));
        assertNotNull(SpecParser.lastError());
    }
}
