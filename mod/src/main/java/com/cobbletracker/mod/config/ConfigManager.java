package com.cobbletracker.mod.config;

import com.cobbletracker.common.tracker.SpawnSettings;
import com.cobbletracker.common.tracker.TargetKind;
import com.cobbletracker.common.tracker.TrackerCategory;
import com.cobbletracker.common.util.Colors;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.text.Msg;
import com.cobbletracker.mod.tracker.TrackerRegistry;
import dev.architectury.platform.Platform;
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

/**
 * Loads and holds the operator configuration from {@code config/cobbletracker/}.
 * The human-edited files are YAML with comments; the commented defaults are shipped as resources and
 * copied out on first run, then parsed with SnakeYAML. Missing keys fall back to defaults, and a
 * malformed category/notification is skipped-and-logged rather than aborting the whole load - a bad
 * edit never takes tracking down. Rebuild on {@code /ct reload} by calling {@link #load()} again.
 */
public final class ConfigManager {

    private static final String DIR = "cobbletracker";

    private final GeneralSettings general;
    private final BeamSettings beam;
    private final MinimapSettings minimap;
    private final TrackerRegistry registry;
    private final Map<String, NotificationConfig> notifications;
    private final boolean readable;

    private ConfigManager(GeneralSettings general, BeamSettings beam, MinimapSettings minimap,
            TrackerRegistry registry, Map<String, NotificationConfig> notifications, boolean readable) {
        this.general = general;
        this.beam = beam;
        this.minimap = minimap;
        this.registry = registry;
        this.notifications = notifications;
        this.readable = readable;
    }

    /**
     * False when a config file exists but couldn't be parsed, so this is all built-in defaults. An empty
     * config and a broken one look identical once loaded, and the difference matters: on a reload the
     * caller should keep what it already had rather than quietly dropping every tracker.
     */
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

    public TrackerRegistry registry() {
        return registry;
    }

    public NotificationConfig notification(String categoryId) {
        return notifications.getOrDefault(categoryId, NotificationConfig.disabled());
    }

    /** A copy with the general settings replaced (used by live admin edits; not persisted to disk). */
    public ConfigManager withGeneral(GeneralSettings replacement) {
        return new ConfigManager(replacement, beam, minimap, registry, notifications, readable);
    }

    /** A copy with the beam settings replaced (live admin edits; not persisted to disk). */
    public ConfigManager withBeam(BeamSettings replacement) {
        return new ConfigManager(general, replacement, minimap, registry, notifications, readable);
    }

    /** A copy with the minimap settings replaced (live admin edits; not persisted to disk). */
    public ConfigManager withMinimap(MinimapSettings replacement) {
        return new ConfigManager(general, beam, replacement, registry, notifications, readable);
    }

    /** Directory the config + data files live in ({@code config/cobbletracker/}). */
    public static Path dir() {
        return Platform.getConfigFolder().resolve(DIR);
    }

    /** Reads config.yml + announcements.yml (writing commented defaults first if absent). */
    public static ConfigManager load() {
        Read config = readYaml("config.yml");
        Read announce = readYaml("announcements.yml");

        GeneralSettings general = readGeneral(child(config.values(), "general-settings"));
        BeamSettings beam = readBeam(child(config.values(), "beam"));
        MinimapSettings minimap = readMinimap(child(config.values(), "minimap"));
        List<TrackerCategory> categories = readCategories(child(config.values(), "trackers"));
        Map<String, NotificationConfig> notifications = readNotifications(child(announce.values(), "notifications"));

        Msg.setPrefix(general.chatPrefix());
        return new ConfigManager(general, beam, minimap, new TrackerRegistry(categories), notifications,
                config.ok() && announce.ok());
    }

    // ---- file IO ----------------------------------------------------------

    /** One parsed file: its top-level map, and whether it actually parsed. */
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
                // An empty file is legitimate - it just means "all defaults".
                return new Read(Map.of(), root == null);
            }
        } catch (Exception e) {
            CobbleTracker.LOGGER.error("Failed to read {} - using built-in defaults", fileName, e);
            return new Read(Map.of(), false);
        }
    }

    /**
     * A YAML reader restricted to plain scalars, lists and maps. SnakeYAML's default constructor will
     * happily instantiate arbitrary classes named by a {@code !!} tag in the document; a config file is
     * not a place where that should ever be possible, and the size limits keep a corrupt or hostile file
     * from eating the heap on load.
     */
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

    // ---- parsing ----------------------------------------------------------

    private static GeneralSettings readGeneral(Map<String, Object> m) {
        GeneralSettings d = GeneralSettings.defaults();
        if (m.isEmpty()) {
            return d;
        }
        return new GeneralSettings(
                string(m, "chat-prefix", d.chatPrefix()),
                bool(m, "hide-exact-position", d.hideExactPosition()),
                bool(m, "show-title", d.showTitle()));
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

    private static List<TrackerCategory> readCategories(Map<String, Object> trackers) {
        List<TrackerCategory> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : trackers.entrySet()) {
            String id = e.getKey();
            try {
                Map<String, Object> t = castMap((Map<?, ?>) e.getValue());
                String name = string(t, "name", id);
                String spec = string(t, "spec", "");
                int color = Colors.parseHex(string(t, "color", "#AAAAAA"), 0xFFAAAAAA);
                int maxStored = integer(t, "max-stored", 10);
                TargetKind kind = "block".equalsIgnoreCase(string(t, "kind", "pokemon"))
                        ? TargetKind.BLOCK : TargetKind.POKEMON;
                out.add(new TrackerCategory(id, name, spec, color, maxStored,
                        splitList(string(t, "blacklist", "")), splitList(string(t, "dimensions", "")),
                        kind, readSpawn(child(t, "spawn")), bool(t, "enabled", true)));
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
                new ArrayList<>(splitList(string(s, "species-pool", ""))));
    }

    /** {@code "50-70"} → [50,70]; {@code "60"} → [60,60]. */
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
        for (Map.Entry<String, Object> e : notes.entrySet()) {
            try {
                Map<String, Object> n = castMap((Map<?, ?>) e.getValue());
                out.put(e.getKey(), new NotificationConfig(
                        bool(n, "enabled", true),
                        string(n, "title", ""),
                        string(n, "subtitle", ""),
                        string(n, "chat", ""),
                        string(n, "sound", ""),
                        (float) doubleValue(n, "sound-volume", 1.0),
                        (float) doubleValue(n, "sound-pitch", 1.0),
                        bool(n, "waypoint", true),
                        bool(n, "play-to-all", true),
                        integer(n, "broadcast-radius", 0)));
            } catch (Exception ex) {
                CobbleTracker.LOGGER.warn("Skipping malformed notification '{}': {}", e.getKey(), ex.getMessage());
            }
        }
        return out;
    }

    // ---- small typed accessors -------------------------------------------

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

    private static Set<String> splitList(String csv) {
        Set<String> out = new LinkedHashSet<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                String s = part.trim().toLowerCase(Locale.ROOT);
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
