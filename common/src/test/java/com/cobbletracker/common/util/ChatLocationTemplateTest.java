package com.cobbletracker.common.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChatLocationTemplateTest {
    @Test
    void defaultFragmentIsOptionalAsAWhole() {
        String template = "<aqua>%species%</aqua> appeared in <green>%biome%</green>%location%.";
        assertEquals("%species% appeared in %biome%.", plain(ChatLocationTemplate.prepare(template, false)));
        assertEquals("%species% appeared in %biome% at %waypoint%.", plain(ChatLocationTemplate.prepare(template, true)));
    }

    @Test
    void hidesLegacyWaypointAndConnectorWithoutLosingSpeciesColor() {
        String template = "<white>A wild</white> <aqua>%species%</aqua> <white>appeared in</white> "
                + "<green>%biome%</green> <gray>at</gray> %waypoint%<gray>.</gray>";
        String result = ChatLocationTemplate.prepare(template, false);
        assertEquals("A wild %species% appeared in %biome%.", plain(result));
        assertTrue(result.contains("<aqua>%species%</aqua>"));
        assertTrue(result.contains("<green>%biome%</green>"));
    }

    @Test
    void hidesColoredLegacyCoordinateTripletAndNearPhrase() {
        String result = ChatLocationTemplate.prepare("&e%species% spawned near &7(%x%, %y%, %z%)&e!", false);
        assertEquals("%species% spawned!", plain(result));
        assertTrue(result.startsWith("&e%species%"));
        assertFalse(result.contains("%x%"));
        assertFalse(result.contains("%y%"));
        assertFalse(result.contains("%z%"));
    }

    @Test
    void noCoordinatePlaceholderSurvivesEvenInsideClickAttributes() {
        String result = ChatLocationTemplate.prepare("<click:run_command:'/tp %x% %y% %z%'>%waypoint%</click> %location%", false);
        for (String token : new String[] {"%x%", "%y%", "%z%", "%waypoint%", "%location%"}) {
            assertFalse(result.contains(token), token);
        }
    }

    @Test
    void optInPreservesCustomLegacyFormatting() {
        String template = "&e%species% near &7%x%, %y%, %z% &b%waypoint%";
        assertEquals(template, ChatLocationTemplate.prepare(template, true));
    }

    private static String plain(String text) {
        return text.replaceAll("<[^>]*>|[&§][0-9a-fk-or]", "").trim();
    }
}
