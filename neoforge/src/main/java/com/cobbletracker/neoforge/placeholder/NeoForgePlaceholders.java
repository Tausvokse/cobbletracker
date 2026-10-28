package com.cobbletracker.neoforge.placeholder;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.placeholder.PlaceholderValues;
import java.util.List;
import java.util.Locale;
import net.neoforged.fml.ModList;

public final class NeoForgePlaceholders {

    private NeoForgePlaceholders() {
    }

    public static void init() {
        if (ModList.get().isLoaded("forgeplaceholderapi")) {
            Integration.register();
        }
    }

    private static final class Integration {
        private static boolean registered;

        private static void register() {
            if (registered) {
                return;
            }
            String version = ModList.get().getModContainerById(CobbleTracker.MODID).orElseThrow()
                    .getModInfo().getVersion().toString();
            var manager = new com.envyful.papi.api.manager.AbstractPlaceholderManager<Object>(
                    CobbleTracker.MODID, new String[] { "Tausvokse" }, version, "CobbleTracker", Object.class) {
                @Override
                public String onPlaceholderRequest(Object context, String text) {
                    return super.onPlaceholderRequest(context == null ? this : context, text);
                }
            };
            for (String key : new String[] { "spawn_chance", "spawn_interval", "spawn_next",
                    "spawn_chances", "spawn_status", "spawn_age", "species_chance", "last_species" }) {
                manager.registerPlaceholder(new com.envyful.papi.api.manager.extensions.AbstractExtension<Object>(
                        key, 0, List.of("CobbleTracker " + key),
                        List.of("%cobbletracker_" + key + "_legendary%")) {
                    @Override
                    public boolean matches(Object context, String placeholder) {
                        String name = placeholder.toLowerCase(Locale.ROOT);
                        return name.equals(key) || name.startsWith(key + "_");
                    }

                    @Override
                    public String parse(Object context, String placeholder) {
                        String argument = placeholder.length() == key.length() ? ""
                                : placeholder.substring(key.length() + 1);
                        if (key.equals("species_chance")) {
                            int separator = argument.indexOf("__");
                            if (separator >= 0) {
                                argument = argument.substring(0, separator) + " " + argument.substring(separator + 2);
                            }
                        }
                        return PlaceholderValues.resolve(key, argument);
                    }
                });
            }
            com.envyful.papi.api.PlaceholderFactory.register(manager);
            registered = true;
            CobbleTracker.LOGGER.info("Registered Forge PlaceholderAPI placeholders");
        }
    }
}
