package com.cobbletracker.mod.config;

import static org.junit.jupiter.api.Assertions.*;
import com.cobbletracker.common.spawn.SpawnGroupSettings;
import com.cobbletracker.mod.platform.PlatformServices;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {
    @TempDir Path temporaryConfig;

    @BeforeEach
    void installTemporaryPlatform() {
        PlatformServices.install(new PlatformServices.Bridge() {
            public Path configDirectory() { return temporaryConfig; }
            public boolean isModLoaded(String id) { return false; }
        });
    }

    @Test
    void firstLoadCopiesUsableDefaultsAndDisabledNativeExample() throws Exception {
        ConfigManager config = ConfigManager.load();
        assertTrue(config.readable());
        assertFalse(config.registry().categories().isEmpty());
        assertEquals(SpawnGroupSettings.legendaryDefaults(), config.spawnGroups().get("legendary"));
        assertEquals(1, config.speciesSpawns().size());
        assertFalse(config.speciesSpawns().getFirst().enabled());
        assertTrue(Files.exists(ConfigManager.dir().resolve("spawns/mew_spawn.json")));
    }

    @Test
    void verticalSpawnLimitLoadsAndOldConfigsUseSixteenBlocks() throws Exception {
        ConfigManager initial = ConfigManager.load();
        assertEquals(16, initial.registry().categories().stream()
                .filter(category -> category.id().equals("legendaries")).findFirst().orElseThrow()
                .spawn().maxVerticalDistance());
        Path file = ConfigManager.dir().resolve("config.yml");
        String original = Files.readString(file);
        Files.writeString(file, original.replace("max-vertical-distance: 16", "max-vertical-distance: 8"));
        ConfigManager edited = ConfigManager.load();
        assertTrue(edited.readable());
        assertEquals(8, edited.registry().categories().stream()
                .filter(category -> category.id().equals("legendaries")).findFirst().orElseThrow()
                .spawn().maxVerticalDistance());
        String legacy = original.replaceAll("(?m)^      max-vertical-distance: 16\\r?\\n", "");
        assertFalse(legacy.contains("max-vertical-distance: 16"));
        Files.writeString(file, legacy);
        assertEquals(16, ConfigManager.load().registry().categories().stream()
                .filter(category -> category.id().equals("legendaries")).findFirst().orElseThrow()
                .spawn().maxVerticalDistance());
    }

    @Test
    void omittedAndPartialBossSettingsKeepTheShippedDefaults() throws Exception {
        WorldBossSettings shipped = ConfigManager.load().worldBoss();
        Path file = ConfigManager.dir().resolve("config.yml");
        Files.writeString(file, "trackers: {}\n");
        ConfigManager omitted = ConfigManager.load();
        assertTrue(omitted.readable());
        assertEquals(shipped, omitted.worldBoss());
        Files.writeString(file, "trackers: {}\nworld-boss:\n  boss-bar-color: BLUE\n");
        ConfigManager partial = ConfigManager.load();
        assertTrue(partial.readable());
        assertEquals(new WorldBossSettings(shipped.enabled(), shipped.range(), "BLUE", shipped.bossBarStyle()),
                partial.worldBoss());
    }

    @Test
    void brokenLegendaryFileCannotBeAcceptedAsAnUnrestrictedReload() throws Exception {
        ConfigManager previous = ConfigManager.load();
        assertFalse(previous.legendaryRules().isEmpty());
        Files.writeString(ConfigManager.dir().resolve("legendaries.yml"), "legendaries: [");
        ConfigManager rejected = ConfigManager.load();
        assertFalse(rejected.readable());
        assertFalse(previous.legendaryRules().isEmpty());
    }

    @Test
    void truncatedFilesAndWrongSectionTypesRejectButExplicitEmptyTrackersRemainValid() throws Exception {
        ConfigManager.load();
        for (String name : java.util.List.of("config.yml", "announcements.yml", "legendaries.yml")) {
            Path file = ConfigManager.dir().resolve(name);
            String original = Files.readString(file);
            Files.writeString(file, "");
            assertFalse(ConfigManager.load().readable(), name);
            Files.writeString(file, original);
        }
        Path config = ConfigManager.dir().resolve("config.yml");
        for (String invalid : java.util.List.of("trackers: []\n", "trackers: null\n",
                "trackers: {}\nminimap: false\n", "trackers: {}\ngeneral-settings: []\n")) {
            Files.writeString(config, invalid);
            assertFalse(ConfigManager.load().readable(), invalid);
        }
        Files.writeString(config, "trackers: {}\n");
        assertTrue(ConfigManager.load().readable());
        Path announcements = ConfigManager.dir().resolve("announcements.yml");
        Files.writeString(announcements, "notifications: []\n");
        assertFalse(ConfigManager.load().readable());
        Files.writeString(announcements, "notifications: {}\n");
        assertTrue(ConfigManager.load().readable());
    }

    @Test
    void malformedLegacyConstraintsCannotWidenTheAcceptedRules() throws Exception {
        ConfigManager.load();
        Path file = ConfigManager.dir().resolve("legendaries.yml");
        for (String invalid : java.util.List.of("isRaining: yess", "isRainning: true", "isThundering: maybe",
                "minY: high", "minY: 1.5", "maxY: 2147483648", "time: nite", "weight: .NaN",
                "weight: 0", "2024: x", "null: x", "minY: 100\n    maxY: 50")) {
            Files.writeString(file, "legendaries:\n  - species: mew\n    " + invalid + "\n");
            assertFalse(ConfigManager.load().readable(), invalid);
        }
        Files.writeString(file, """
                legendaries:
                  - species: mew
                    time: NIGHT
                    isRaining: true
                    minY: -64
                    maxY: 320
                    weight: 2
                """);
        ConfigManager accepted = ConfigManager.load();
        assertTrue(accepted.readable());
        assertEquals("night", accepted.legendaryRules().getFirst().time());
        assertEquals(-64, accepted.legendaryRules().getFirst().minY());
        Files.writeString(file, "legendaries: []\n");
        assertTrue(ConfigManager.load().readable());
    }

    @Test
    void omittedDiscordSettingsKeepTheShippedDespawnPolicy() throws Exception {
        DiscordIntegrationSettings shipped = ConfigManager.load().discordIntegration();
        Files.writeString(ConfigManager.dir().resolve("config.yml"), "trackers: {}\n");
        assertEquals(shipped, ConfigManager.load().discordIntegration());
    }

    @Test
    void malformedSpeciesFileRejectsTheCompleteCandidateWithoutOverwritingIt() throws Exception {
        ConfigManager.load();
        Path file = ConfigManager.dir().resolve("spawns/mew_spawn.json");
        String broken = """
                {"species":"cobblemon:mew","enabled":true,"mode":"replace","group":"missing","spawns":[{}]}
                """;
        Files.writeString(file, broken);
        assertFalse(ConfigManager.load().readable());
        assertEquals(broken, Files.readString(file));
    }

    @Test
    void groupChangesAndExplicitEmptyHallOfFameListAreReadFromDisk() throws Exception {
        ConfigManager.load();
        Files.writeString(ConfigManager.dir().resolve("config.yml"), """
                trackers: {}
                hall-of-fame:
                  categories: []
                spawn-groups:
                  legendary:
                    enabled: true
                    interval-ticks: 200
                    chance: 0.8
                """);
        ConfigManager config = ConfigManager.load();
        assertTrue(config.readable());
        assertEquals(new SpawnGroupSettings(true, 200, 0.8), config.spawnGroups().get("legendary"));
        assertTrue(config.hallOfFame().categories().isEmpty());
    }

    @Test
    void duplicateYamlKeysAndInvalidGroupProbabilityRejectReload() throws Exception {
        ConfigManager.load();
        Path file = ConfigManager.dir().resolve("config.yml");
        Files.writeString(file, "trackers: {}\ntrackers: {}\n");
        assertFalse(ConfigManager.load().readable());
        Files.writeString(file, "trackers: {}\nspawn-groups:\n  legendary:\n    chance: .NaN\n");
        assertFalse(ConfigManager.load().readable());
    }
}
