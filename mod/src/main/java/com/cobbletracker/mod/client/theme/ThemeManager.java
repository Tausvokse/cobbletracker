package com.cobbletracker.mod.client.theme;

import com.cobbletracker.mod.CobbleTracker;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Holds the client's selected {@link Theme} and persists it to a tiny client-side JSON so it survives
 * restarts and is independent of any server. Every screen reads {@link #current()}, so switching
 * restyles all menus instantly. Defaults to the signature {@link Theme#POKEDEX}.
 */
public final class ThemeManager {

    private ThemeManager() {
    }

    private static volatile Theme current = Theme.POKEDEX;
    private static volatile boolean loaded;

    public static Theme current() {
        if (!loaded) {
            load();
        }
        return current;
    }

    /** Sets the theme by name, persisting it. Returns false if the name is unknown. */
    public static boolean set(String name) {
        Theme theme = Theme.byName(name);
        if (theme == null) {
            return false;
        }
        loaded = true;
        if (theme == current) {
            return true; // already on it - no need to touch the file again
        }
        current = theme;
        save();
        return true;
    }

    private static Path file() {
        return Platform.getConfigFolder().resolve("cobbletracker_client.json");
    }

    private static void load() {
        loaded = true;
        try {
            Path path = file();
            if (Files.exists(path)) {
                JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (root.has("theme")) {
                    Theme theme = Theme.byName(root.get("theme").getAsString());
                    if (theme != null) {
                        current = theme;
                    }
                }
            }
        } catch (Exception e) {
            CobbleTracker.LOGGER.warn("Failed to read client theme, using default", e);
        }
    }

    private static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("theme", current.name());
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CobbleTracker.LOGGER.warn("Failed to save client theme", e);
        }
    }
}
