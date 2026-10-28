package com.cobbletracker.fabric.placeholder;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.placeholder.PlaceholderValues;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlaceholders {

    private FabricPlaceholders() {
    }

    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            Integration.register();
        }
    }

    private static final class Integration {
        private static boolean registered;

        private static void register() {
            if (registered) {
                return;
            }
            for (String key : new String[] { "spawn_chance", "spawn_interval", "spawn_next",
                    "spawn_chances", "spawn_status", "spawn_age", "species_chance", "last_species" }) {
                eu.pb4.placeholders.api.Placeholders.register(CobbleTracker.id(key), (context, argument) -> {
                    String value = PlaceholderValues.resolve(key, argument);
                    return value == null ? eu.pb4.placeholders.api.PlaceholderResult.invalid()
                            : eu.pb4.placeholders.api.PlaceholderResult.value(value);
                });
            }
            registered = true;
            CobbleTracker.LOGGER.info("Registered Text Placeholder API placeholders");
        }
    }
}
