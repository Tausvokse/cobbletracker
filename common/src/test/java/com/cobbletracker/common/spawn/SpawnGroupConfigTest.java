package com.cobbletracker.common.spawn;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpawnGroupConfigTest {

    @Test
    void legacyConfigurationGetsTheLegendaryDefaultWithoutOtherImplicitGroups() {
        assertEquals(Map.of("legendary", SpawnGroupSettings.legendaryDefaults()), SpawnGroupConfig.parse(null));
        assertEquals(1, SpawnGroupConfig.parse(Map.of()).size());
    }

    @Test
    void configuredGroupsOverrideDefaultsAndHaveTheirOwnSettings() {
        Map<String, SpawnGroupSettings> groups = SpawnGroupConfig.parse(Map.of(
                "legendary", Map.of("chance", 0.8, "interval-ticks", 100, "enabled", false),
                "shiny", Map.of("chance", 0.2, "interval-ticks", 200)));
        assertEquals(new SpawnGroupSettings(false, 100, 0.8), groups.get("legendary"));
        assertEquals(new SpawnGroupSettings(true, 200, 0.2), groups.get("shiny"));
        assertThrows(UnsupportedOperationException.class, groups::clear);
    }

    @Test
    void malformedGroupsRejectTheLoad() {
        for (Object configured : new Object[] {"bad", Map.of("legendary", true),
                Map.of("legendary", Map.of("enabled", "true")),
                Map.of("legendary", Map.of("interval-ticks", 1.5)),
                Map.of("legendary", Map.of("interval-ticks", Long.MAX_VALUE)),
                Map.of("legendary", Map.of("chance", Double.NaN)),
                Map.of("legendary", Map.of("chance", "35%")),
                Map.of("bad group", Map.of())}) {
            assertThrows(IllegalArgumentException.class, () -> SpawnGroupConfig.parse(configured));
        }
    }

    @Test
    void normalizedDuplicateGroupIdsAreRejected() {
        Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("Legendary", Map.of());
        groups.put("legendary", Map.of());
        assertThrows(IllegalArgumentException.class, () -> SpawnGroupConfig.parse(groups));
    }
}
