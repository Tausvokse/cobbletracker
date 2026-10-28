package com.cobbletracker.mod.config;

import com.cobbletracker.common.legendary.LegendaryRule;
import com.cobbletracker.common.spawn.SpawnGroupSettings;
import com.cobbletracker.common.spawn.SpawnGroupConfig;
import com.cobbletracker.common.spawn.SpeciesSpawnDefinition;
import com.cobbletracker.common.spawn.SpeciesSpawnFiles;
import com.cobbletracker.common.tracker.SpawnSettings;
import com.cobbletracker.common.tracker.TrackerCategory;
import com.cobbletracker.common.util.Colors;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.tracker.TrackerRegistry;
import com.cobbletracker.mod.platform.PlatformServices;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Loads configuration snapshots; callers retain the active snapshot when a reload is unreadable. */
public final class ConfigManager {

    private static final String DIR = "cobbletracker";

    private final GeneralSettings general;
    private final BeamSettings beam;
    private final MinimapSettings minimap;
    private final HuntSettings hunt;
    private final WorldBossSettings worldBoss;
    private final DiscordIntegrationSettings discordIntegration;
    private final SpawnProtectionSettings spawnProtection;
    private final HallOfFameSettings hallOfFame;
    private final TrackerRegistry registry;
    private final Map<String, NotificationConfig> notifications;
    private final List<LegendaryRule> legendaryRules;
    private final Map<String, SpawnGroupSettings> spawnGroups;
    private final List<SpeciesSpawnDefinition> speciesSpawns;
    private final boolean readable;

    private ConfigManager(GeneralSettings general, BeamSettings beam, MinimapSettings minimap,
            HuntSettings hunt, WorldBossSettings worldBoss, DiscordIntegrationSettings discordIntegration,
            SpawnProtectionSettings spawnProtection, HallOfFameSettings hallOfFame,
            TrackerRegistry registry, Map<String, NotificationConfig> notifications,
            List<LegendaryRule> legendaryRules, Map<String, SpawnGroupSettings> spawnGroups,
            List<SpeciesSpawnDefinition> speciesSpawns, boolean readable) {
        this.general = general;
        this.beam = beam;
        this.minimap = minimap;
        this.hunt = hunt;
        this.worldBoss = worldBoss;
        this.discordIntegration = discordIntegration;
        this.spawnProtection = spawnProtection;
        this.hallOfFame = hallOfFame;
        this.registry = registry;
        this.notifications = notifications;
        this.legendaryRules = List.copyOf(legendaryRules);
        this.spawnGroups = Map.copyOf(spawnGroups);
        this.speciesSpawns = List.copyOf(speciesSpawns);
        this.readable = readable;
    }

    /** Whether this snapshot can replace the active configuration. */
    public boolean readable() {
        return readable;
    }

    public GeneralSettings general() {
        return general;
    }

    public BeamSettings beam() {
        return beam;
    }

    public MinimapSettings minimap() {
        return minimap;
    }

    public HuntSettings hunt() {
        return hunt;
    }

    public WorldBossSettings worldBoss() {
        return worldBoss;
    }

    public DiscordIntegrationSettings discordIntegration() {
        return discordIntegration;
    }

    public SpawnProtectionSettings spawnProtection() {
        return spawnProtection;
    }

    public HallOfFameSettings hallOfFame() {
        return hallOfFame;
    }

    public TrackerRegistry registry() {
        return registry;
    }

    public NotificationConfig notification(String categoryId) {
        return notifications.getOrDefault(categoryId, NotificationConfig.disabled());
    }

    public List<LegendaryRule> legendaryRules() {
        return legendaryRules;
    }

    public Map<String, SpawnGroupSettings> spawnGroups() {
        return spawnGroups;
    }

    public List<SpeciesSpawnDefinition> speciesSpawns() {
        return speciesSpawns;
    }

    public ConfigManager withGeneral(GeneralSettings replacement) {
        return new ConfigManager(replacement, beam, minimap, hunt, worldBoss, discordIntegration,
                spawnProtection, hallOfFame, registry, notifications, legendaryRules, spawnGroups, speciesSpawns, readable);
    }

    public ConfigManager withBeam(BeamSettings replacement) {
        return new ConfigManager(general, replacement, minimap, hunt, worldBoss, discordIntegration,
                spawnProtection, hallOfFame, registry, notifications, legendaryRules, spawnGroups, speciesSpawns, readable);
    }

    public ConfigManager withMinimap(MinimapSettings replacement) {
        return new ConfigManager(general, beam, replacement, hunt, worldBoss, discordIntegration,
                spawnProtection, hallOfFame, registry, notifications, legendaryRules, spawnGroups, speciesSpawns, readable);
    }

    public static Path dir() {
        return PlatformServices.configDirectory().resolve(DIR);
    }

    public static ConfigManager load() {
        Read config = readYaml("config.yml");
        Read announce = readYaml("announcements.yml");
        Read legends = readYaml("legendaries.yml");

        GeneralSettings general = readGeneral(child(config.values(), "general-settings"));
        BeamSettings beam = readBeam(child(config.values(), "beam"));
        MinimapSettings minimap = readMinimap(child(config.values(), "minimap"));
        HuntSettings hunt = readHunt(child(config.values(), "hunt"));
        WorldBossSettings worldBoss = readWorldBoss(child(config.values(), "world-boss"));
        DiscordIntegrationSettings discord = readDiscordIntegration(child(config.values(), "discord-integration"));
        SpawnProtectionSettings spawnProtection = readSpawnProtection(child(config.values(), "spawn-protection"));
        HallOfFameSettings hallOfFame = readHallOfFame(child(config.values(), "hall-of-fame"));
        List<TrackerCategory> categories = readCategories(child(config.values(), "trackers"));
        boolean categoryLimitValid = categories.size() <= 128;
        if (!categoryLimitValid) CobbleTracker.LOGGER.error("At most 128 tracker categories are supported");
        Map<String, NotificationConfig> notifications = readNotifications(child(announce.values(), "notifications"));
        List<LegendaryRule> legendaryRules = List.of();
        boolean legendaryRulesReadable = true;
        try {
            legendaryRules = readLegendaryRules(legends.values());
        } catch (IllegalArgumentException e) {
            CobbleTracker.LOGGER.error("Invalid legendaries.yml: {}", e.getMessage());
            legendaryRulesReadable = false;
        }
        Map<String, SpawnGroupSettings> spawnGroups = Map.of("legendary", SpawnGroupSettings.legendaryDefaults());
        List<SpeciesSpawnDefinition> speciesSpawns = List.of();
        boolean spawnFilesReadable = true;
        try {
            spawnGroups = SpawnGroupConfig.parse(config.values().get("spawn-groups"));
            speciesSpawns = readSpeciesSpawns(spawnGroups);
        } catch (Exception e) {
            CobbleTracker.LOGGER.error("Invalid spawn configuration: {}", e.getMessage());
            spawnFilesReadable = false;
        }

        return new ConfigManager(general, beam, minimap, hunt, worldBoss, discord, spawnProtection, hallOfFame,
                new TrackerRegistry(categories), notifications,
                legendaryRules, spawnGroups, speciesSpawns,
                config.ok() && announce.ok() && legends.ok() && trackersPresent(config)
                        && validSections(config.values(), "general-settings", "beam", "minimap", "hunt", "world-boss",
                                "discord-integration", "spawn-protection", "hall-of-fame")
                        && announce.values().get("notifications") instanceof Map<?, ?>
                        && legendaryRulesReadable && spawnFilesReadable && categoryLimitValid);
    }

    private static List<SpeciesSpawnDefinition> readSpeciesSpawns(Map<String, SpawnGroupSettings> groups)
            throws java.io.IOException {
        Path folder = dir().resolve("spawns");
        if (!Files.exists(folder)) {
            copyDefault("spawns/mew_spawn.json", folder.resolve("mew_spawn.json"));
        }
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("spawns must be a directory");
        }
        return SpeciesSpawnFiles.read(folder, groups.keySet());
    }

    private static List<LegendaryRule> readLegendaryRules(Map<String, Object> root) {
        List<LegendaryRule> out = new ArrayList<>();
        Object list = root.get("legendaries");
        if (!(list instanceof List<?> items)) throw new IllegalArgumentException("legendaries must be a list");
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Each legendary rule must be a mapping");
            Map<String, Object> r = castMap(raw);
            for (Object key : r.keySet()) {
                if (!(key instanceof String) || !java.util.Set.of("species", "weight", "biomes", "dimensions",
                        "time", "isRaining", "isThundering", "minY", "maxY").contains(key)) {
                    throw new IllegalArgumentException("Unknown legendary field: " + key);
                }
            }
            String species = string(r, "species", "").trim();
            if (species.isEmpty()) throw new IllegalArgumentException("Legendary species must not be empty");
            String time = string(r, "time", "any").trim().toLowerCase(Locale.ROOT);
            if (!java.util.Set.of("day", "night", "any").contains(time)) {
                throw new IllegalArgumentException("Invalid time for " + species + ": " + time);
            }
            double weight;
            try {
                weight = r.containsKey("weight") ? Double.parseDouble(String.valueOf(r.get("weight"))) : 1;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid weight for " + species);
            }
            if (!Double.isFinite(weight) || weight <= 0) {
                throw new IllegalArgumentException("Weight must be finite and positive for " + species);
            }
            Integer minY = optionalInt(r, "minY"), maxY = optionalInt(r, "maxY");
            if (minY != null && maxY != null && minY > maxY) {
                throw new IllegalArgumentException("minY exceeds maxY for " + species);
            }
            out.add(new LegendaryRule(species, weight, stringList(r, "biomes"), stringList(r, "dimensions"),
                    time, optionalBool(r, "isRaining"), optionalBool(r, "isThundering"), minY, maxY));
        }
        return out;
    }

    private static Boolean optionalBool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "true", "yes", "on", "1" -> Boolean.TRUE;
            case "false", "no", "off", "0" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException(key + " must be true or false: " + v);
        };
    }

    private static Integer optionalInt(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer: " + v);
        }
    }

    private static boolean trackersPresent(Read config) {
        if (config.values().get("trackers") instanceof Map<?, ?>) {
            return true;
        }
        CobbleTracker.LOGGER.error("config.yml requires a top-level 'trackers:' mapping; use trackers: {} for an empty registry");
        return false;
    }

    private static boolean validSections(Map<String, Object> root, String... sections) {
        for (String section : sections) {
            if (root.containsKey(section) && !(root.get(section) instanceof Map<?, ?>)) {
                CobbleTracker.LOGGER.error("config.yml section '{}' must be a mapping", section);
                return false;
            }
        }
        return true;
    }


    private record Read(Map<String, Object> values, boolean ok) {
    }

    private static Read readYaml(String fileName) {
        Path file = dir().resolve(fileName);
        try {
            if (!Files.exists(file)) {
                copyDefault(fileName, file);
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Object root = safeYaml().load(reader);
                if (root instanceof Map<?, ?> map) {
                    return new Read(castMap(map), true);
                }
                CobbleTracker.LOGGER.error("{} must contain a YAML mapping", fileName);
                return new Read(Map.of(), false);
            }
        } catch (Exception e) {
            CobbleTracker.LOGGER.error("Failed to read {} - using built-in defaults", fileName, e);
            return new Read(Map.of(), false);
        }
    }

    private static Yaml safeYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(64);
        options.setCodePointLimit(8 * 1024 * 1024);
        return new Yaml(new SafeConstructor(options));
    }

    private static void copyDefault(String fileName, Path dest) {
        try (InputStream in = ConfigManager.class.getClassLoader()
                .getResourceAsStream("assets/cobbletracker/default_config/" + fileName)) {
            if (in == null) {
                CobbleTracker.LOGGER.error("Bundled default {} is missing from the jar", fileName);
                return;
            }
            Files.createDirectories(dest.getParent());
            Files.copy(in, dest);
            CobbleTracker.LOGGER.info("Wrote default {}", fileName);
        } catch (Exception e) {
            CobbleTracker.LOGGER.error("Failed to write default {}", fileName, e);
        }
    }


    private static GeneralSettings readGeneral(Map<String, Object> m) {
        GeneralSettings d = GeneralSettings.defaults();
        if (m.isEmpty()) {
            return d;
        }
        return new GeneralSettings(
                string(m, "chat-prefix", d.chatPrefix()),
                bool(m, "hide-exact-position", d.hideExactPosition()),
                bool(m, "show-title", d.showTitle()),
                bool(m, "show-spawn-location-in-chat", d.showSpawnLocationInChat()));
    }

    private static BeamSettings readBeam(Map<String, Object> m) {
        BeamSettings d = BeamSettings.defaults();
        if (m.isEmpty()) {
            return d;
        }
        return new BeamSettings(
                bool(m, "enabled", d.enabled()),
                integer(m, "radius", d.radius()),
                integer(m, "duration-seconds", d.durationSeconds()),
                integer(m, "height", d.height()));
    }

    private static MinimapSettings readMinimap(Map<String, Object> m) {
        MinimapSettings d = MinimapSettings.defaults();
        if (m.isEmpty()) {
            return d;
        }
        return new MinimapSettings(
                bool(m, "enabled", d.enabled()),
                bool(m, "xaero", d.xaero()),
                bool(m, "voxelmap", d.voxelmap()),
                bool(m, "journeymap", d.journeymap()),
                bool(m, "use-beam-color", d.useBeamColor()));
    }

    private static HuntSettings readHunt(Map<String, Object> m) {
        HuntSettings d = HuntSettings.defaults();
        return m.isEmpty() ? d : new HuntSettings(bool(m, "enabled", d.enabled()));
    }

    private static WorldBossSettings readWorldBoss(Map<String, Object> m) {
        WorldBossSettings d = WorldBossSettings.defaults();
        if (m.isEmpty()) {
            return d;
        }
        return new WorldBossSettings(
                bool(m, "enabled", d.enabled()),
                integer(m, "range", d.range()),
                string(m, "boss-bar-color", d.bossBarColor()),
                string(m, "boss-bar-style", d.bossBarStyle()));
    }

    private static DiscordIntegrationSettings readDiscordIntegration(Map<String, Object> m) {
        DiscordIntegrationSettings d = DiscordIntegrationSettings.defaults();
        if (m.isEmpty()) {
            return d;
        }
        return new DiscordIntegrationSettings(
                bool(m, "enabled", d.enabled()),
                bool(m, "rich-embeds", d.richEmbeds()),
                bool(m, "post-captures", d.postCaptures()),
                bool(m, "post-defeats", d.postDefeats()),
                bool(m, "post-despawns", d.postDespawns()));
    }

    private static SpawnProtectionSettings readSpawnProtection(Map<String, Object> m) {
        SpawnProtectionSettings d = SpawnProtectionSettings.defaults();
        if (m.isEmpty()) {
            return d;
        }
        return new SpawnProtectionSettings(
                bool(m, "enabled", d.enabled()),
                integer(m, "grace-period-seconds", d.gracePeriodSeconds()),
                bool(m, "notify-others", d.notifyOthers()));
    }

    private static HallOfFameSettings readHallOfFame(Map<String, Object> m) {
        HallOfFameSettings d = HallOfFameSettings.defaults();
        if (m.isEmpty()) {
            return d;
        }
        List<String> cats = new ArrayList<>(stringList(m, "categories"));
        return new HallOfFameSettings(
                bool(m, "enabled", d.enabled()),
                bool(m, "broadcast-server-first", d.broadcastServerFirst()),
                m.containsKey("categories") ? cats : d.categories());
    }

    private static List<TrackerCategory> readCategories(Map<String, Object> trackers) {
        List<TrackerCategory> out = new ArrayList<>();
        for (Map.Entry<?, ?> e : trackers.entrySet()) {
            String id = String.valueOf(e.getKey());
            try {
                Map<String, Object> t = castMap((Map<?, ?>) e.getValue());
                String name = string(t, "name", id);
                String spec = string(t, "spec", "");
                int color = Colors.parseHex(string(t, "color", "#AAAAAA"), 0xFFAAAAAA);
                int maxStored = integer(t, "max-stored", 10);
                out.add(new TrackerCategory(id, name, spec, color, maxStored,
                        stringList(t, "blacklist"), stringList(t, "dimensions"),
                        commandList(t, "on-spawn-commands"), commandList(t, "on-catch-commands"),
                        readSpawn(child(t, "spawn")), bool(t, "enabled", true)));
            } catch (Exception ex) {
                CobbleTracker.LOGGER.warn("Skipping malformed tracker '{}': {}", id, ex.getMessage());
            }
        }
        return out;
    }

    private static SpawnSettings readSpawn(Map<String, Object> s) {
        if (s.isEmpty()) {
            return SpawnSettings.disabled();
        }
        int[] range = parseLevelRange(string(s, "level", "50-70"));
        return new SpawnSettings(
                bool(s, "enabled", false),
                integer(s, "interval-ticks", 12000),
                doubleValue(s, "chance", 0.25),
                bool(s, "distribute-among-players", true),
                integer(s, "player-cooldown-ticks", 72000),
                integer(s, "min-distance", 24),
                integer(s, "max-distance", 64),
                range[0],
                range[1],
                bool(s, "shiny", false),
                new ArrayList<>(stringList(s, "species-pool")),
                SpawnSettings.Mode.parse(string(s, "mode", "auto")),
                bool(s, "context-aware", true),
                integer(s, "max-vertical-distance", 16));
    }

    private static int[] parseLevelRange(String raw) {
        try {
            String v = raw.trim();
            int dash = v.indexOf('-');
            if (dash > 0) {
                return new int[] {Integer.parseInt(v.substring(0, dash).trim()),
                        Integer.parseInt(v.substring(dash + 1).trim())};
            }
            int single = Integer.parseInt(v);
            return new int[] {single, single};
        } catch (RuntimeException e) {
            return new int[] {50, 70};
        }
    }

    private static double doubleValue(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        if (v instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return v == null ? def : Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Map<String, NotificationConfig> readNotifications(Map<String, Object> notes) {
        Map<String, NotificationConfig> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : notes.entrySet()) {
            String id = String.valueOf(e.getKey());
            try {
                Map<String, Object> n = castMap((Map<?, ?>) e.getValue());
                out.put(id, new NotificationConfig(
                        bool(n, "enabled", true),
                        string(n, "title", ""),
                        string(n, "subtitle", ""),
                        string(n, "chat", ""),
                        string(n, "actionbar", ""),
                        string(n, "sound", ""),
                        (float) doubleValue(n, "sound-volume", 1.0),
                        (float) doubleValue(n, "sound-pitch", 1.0),
                        bool(n, "waypoint", true),
                        bool(n, "play-to-all", true),
                        integer(n, "broadcast-radius", 0),
                        string(n, "discord-webhook", ""),
                        string(n, "discord-template", "")));
            } catch (Exception ex) {
                CobbleTracker.LOGGER.warn("Skipping malformed notification '{}': {}", id, ex.getMessage());
            }
        }
        return out;
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    private static Map<String, Object> child(Map<String, Object> parent, String key) {
        Object v = parent.get(key);
        return v instanceof Map<?, ?> map ? castMap(map) : Map.of();
    }

    private static String string(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static int integer(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v instanceof Number num) {
            return num.intValue();
        }
        try {
            return v == null ? def : Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return v == null ? def : Boolean.parseBoolean(String.valueOf(v).trim());
    }

    private static Set<String> stringList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        Set<String> out = new LinkedHashSet<>();
        if (v instanceof Iterable<?> items) {
            for (Object item : items) {
                addEntry(out, String.valueOf(item));
            }
        } else if (v != null) {
            for (String part : String.valueOf(v).split(",")) {
                addEntry(out, part);
            }
        }
        return out;
    }

    private static List<String> commandList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        List<String> out = new ArrayList<>();
        if (v instanceof Iterable<?> items) {
            for (Object item : items) {
                addCommand(out, String.valueOf(item));
            }
        } else if (v != null) {
            addCommand(out, String.valueOf(v));
        }
        return out;
    }

    private static void addCommand(List<String> out, String raw) {
        String s = raw.trim();
        if (s.startsWith("/")) {
            s = s.substring(1).trim();
        }
        if (!s.isEmpty()) {
            out.add(s);
        }
    }

    private static void addEntry(Set<String> out, String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (!s.isEmpty()) {
            out.add(s);
        }
    }
}
