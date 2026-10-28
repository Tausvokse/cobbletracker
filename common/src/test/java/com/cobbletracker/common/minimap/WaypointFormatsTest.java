package com.cobbletracker.common.minimap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaypointFormatsTest {
    @Test
    void xaeroCarriesTargetDimensionAndCoordinates() {
        String line = WaypointFormats.xaero("Mew", 0xFFFFFFFF, -12, 70, 34, "minecraft:the_nether");
        assertEquals("xaero-waypoint:Mew:Me:-12:70:34:15:false:0:Internal-the-nether-waypoints", line);
        assertEquals(10, line.split(":").length);
    }

    @Test
    void customDimensionUsesXaeroDirectoryEncoding() {
        assertTrue(WaypointFormats.xaero("Mew", -1, 1, 2, 3, "my-mod:sky_island/moon.")
                .endsWith(":Internal-dim%my^min^mod$sky-island%moon,-waypoints"));
    }

    @Test
    void xaeroLimitsNameToItsParserLimit() {
        String line = WaypointFormats.xaero("x".repeat(80), -1, 1, 2, 3, "minecraft:overworld");
        assertEquals(32, line.split(":")[1].length());
        assertEquals("Pokemon", WaypointFormats.xaero("", -1, 1, 2, 3, "minecraft:overworld").split(":")[1]);
    }

    @Test
    void sharedFormatPreservesResourceIdentifierAndSanitizesName() {
        assertEquals("[name:Mew  x  bad, x:-1, y:80, z:2, dim:custom:sky_island]",
                WaypointFormats.shared("Mew:[x],bad", -1, 80, 2, "custom:sky_island"));
    }

    @Test
    void xaeroPreservesPunctuationThatItsParserEscapes() {
        assertTrue(WaypointFormats.xaero("Ho-Oh_shiny", -1, 0, 70, 0, "minecraft:overworld")
                .startsWith("xaero-waypoint:Ho^min^Oh-shiny:Ho:"));
    }
}
