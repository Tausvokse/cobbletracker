package com.cobbletracker.mod.placeholder;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.config.ConfigManager;
import com.cobbletracker.mod.platform.PlatformServices;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class TabPlaceholders {
    private static Object manager;
    private static ConfigManager registeredConfig;
    private static final Map<String, Object> registered = new LinkedHashMap<>();
    private static boolean warned;

    private record Value(String key, String argument) implements Supplier<String> {
        @Override
        public String get() {
            return PlaceholderValues.resolve(key, argument);
        }
    }

    private TabPlaceholders() {
    }

    public static void clear() {
        manager = null;
        registeredConfig = null;
        registered.clear();
        warned = false;
    }

    public static boolean register(ConfigManager config) {
        if (!PlatformServices.isModLoaded("tab")) {
            return false;
        }
        try {
            Class<?> api = Class.forName("me.neznamy.tab.api.TabAPI");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object current = api.getMethod("getPlaceholderManager").invoke(instance);
            if (current == null) {
                return false;
            }
            if (current != manager) {
                manager = current;
                registered.clear();
                registeredConfig = null;
            }
            Class<?> type = Class.forName("me.neznamy.tab.api.placeholder.PlaceholderManager");
            Method add = type.getMethod("registerServerPlaceholder", String.class, int.class, Supplier.class);
            Method remove = type.getMethod("unregisterPlaceholder", String.class);
            Method lookup = type.getMethod("getPlaceholder", String.class);
            Map<String, Value> requested = requestedPlaceholders(current);
            boolean unchanged = registeredConfig == config && registered.keySet().containsAll(requested.keySet());
            for (String identifier : Set.copyOf(registered.keySet())) {
                if (lookup.invoke(manager, identifier) != registered.get(identifier)) {
                    registered.remove(identifier);
                    unchanged = false;
                }
            }
            if (unchanged) {
                return true;
            }
            Map<String, Value> desired = placeholders(config);
            desired.putAll(requested);
            for (String identifier : Set.copyOf(registered.keySet())) {
                if (!desired.containsKey(identifier)) {
                    remove.invoke(manager, identifier);
                    registered.remove(identifier);
                }
            }
            for (Map.Entry<String, Value> entry : desired.entrySet()) {
                if (!registered.containsKey(entry.getKey())) {
                    Object placeholder = add.invoke(manager, entry.getKey(), 1_000, entry.getValue());
                    registered.put(entry.getKey(), placeholder);
                }
            }
            registeredConfig = config;
            return true;
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof IllegalStateException)) {
                warn(e.getCause());
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            warn(e);
        }
        return false;
    }

    private static Map<String, Value> requestedPlaceholders(Object current) throws ReflectiveOperationException {
        Method usage;
        try {
            usage = current.getClass().getMethod("getPlaceholderUsage");
        } catch (NoSuchMethodException ignored) {
            return Map.of();
        }
        Map<String, Value> values = new LinkedHashMap<>();
        for (Object raw : ((Map<?, ?>) usage.invoke(current)).keySet()) {
            if (!(raw instanceof String identifier) || !identifier.startsWith("%cobbletracker_")
                    || !identifier.endsWith("%")) continue;
            String name = identifier.substring("%cobbletracker_".length(), identifier.length() - 1);
            for (String key : new String[] {"spawn_chance", "spawn_interval", "spawn_next", "spawn_chances",
                    "spawn_status", "spawn_age", "species_chance", "last_species"}) {
                if (!name.equals(key) && !name.startsWith(key + "_")) continue;
                String argument = name.length() == key.length() ? "" : name.substring(key.length() + 1);
                if (key.equals("species_chance")) {
                    int separator = argument.indexOf("__");
                    if (separator >= 0) argument = argument.substring(0, separator) + " " + argument.substring(separator + 2);
                }
                values.put(identifier, new Value(key, argument));
                break;
            }
        }
        return values;
    }

    private static Map<String, Value> placeholders(ConfigManager config) {
        Map<String, Value> values = new LinkedHashMap<>();
        for (String key : new String[] { "spawn_chance", "spawn_interval", "spawn_next", "spawn_chances", "spawn_status", "spawn_age" }) {
            values.put("%cobbletracker_" + key + "%", new Value(key, "legendary"));
            for (String group : config.spawnGroups().keySet()) {
                values.put("%cobbletracker_" + key + "_" + group + "%", new Value(key, group));
            }
        }
        for (var definition : config.speciesSpawns()) {
            String species = definition.species();
            String path = species.substring(species.indexOf(':') + 1);
            Value value = new Value("species_chance", definition.group() + " " + species);
            values.put("%cobbletracker_species_chance_" + definition.group() + "__" + path + "%", value);
        }
        for (var category : config.registry().categories()) {
            values.put("%cobbletracker_last_species_" + category.id() + "%",
                    new Value("last_species", category.id()));
        }
        return values;
    }

    private static void warn(Throwable problem) {
        if (!warned) {
            warned = true;
            CobbleTracker.LOGGER.warn("TAB placeholder integration is unavailable: {}",
                    problem.getClass().getSimpleName());
        }
    }
}
