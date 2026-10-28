package com.cobbletracker.mod.tracker;

import com.cobbletracker.common.tracker.SpawnRecord;
import com.cobbletracker.common.tracker.CommandHookGuard;
import com.cobbletracker.common.tracker.TrackerCategory;
import com.cobbletracker.mod.CobbleTracker;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Runs event hooks as level-2 console commands in the spawn dimension. Substituted identifiers are
 * validated before dispatch.
 */
public final class CommandHooks {

    private CommandHooks() {
    }

    /** Most hooks one category may fire per event, so a runaway config can't stall the tick. */
    private static final int MAX_COMMANDS = 32;

    /** Capture hook provenance before deferred entity detection to prevent recursive spawn hooks. */
    private static final CommandHookGuard guard = new CommandHookGuard();

    public static CommandHookGuard.Origin captureOrigin() {
        return guard.captureOrigin();
    }

    public static <T> T withOrigin(CommandHookGuard.Origin origin, Supplier<T> action) {
        return guard.withOrigin(origin, action);
    }

    /** Fires {@code on-spawn-commands}. {@code %player%} is empty here - a spawn belongs to nobody yet. */
    public static void onSpawn(MinecraftServer server, TrackerCategory category, SpawnRecord record) {
        onSpawn(server, category, record, captureOrigin());
    }

    public static void onSpawn(MinecraftServer server, TrackerCategory category, SpawnRecord record,
            CommandHookGuard.Origin origin) {
        run(server, category.onSpawnCommands(), category, record, "", origin);
    }

    /** Fires {@code on-catch-commands} with {@code %player%} set to the catcher. */
    public static void onCatch(MinecraftServer server, TrackerCategory category, SpawnRecord record,
            String catcherName) {
        run(server, category.onCatchCommands(), category, record, catcherName == null ? "" : catcherName,
                captureOrigin());
    }

    private static void run(MinecraftServer server, List<String> commands, TrackerCategory category,
            SpawnRecord record, String playerName, CommandHookGuard.Origin origin) {
        if (server == null || commands.isEmpty()) {
            return;
        }
        // Dispatch world mutations on the server thread.
        if (!server.isSameThread()) {
            server.execute(() -> run(server, commands, category, record, playerName, origin));
            return;
        }
        guard.run(origin, () -> {
            CommandSourceStack source = sourceAt(server, record);
            int fired = 0;
            for (String template : commands) {
                if (fired++ >= MAX_COMMANDS) {
                    CobbleTracker.LOGGER.warn("Category '{}' has more than {} command hooks for one event - "
                            + "the rest were skipped", category.id(), MAX_COMMANDS);
                    break;
                }
                String command = substitute(template, category, record, playerName);
                if (command == null) {
                    continue; // substitute() has already said why
                }
                try {
                    server.getCommands().performPrefixedCommand(source, command);
                } catch (Throwable t) {
                    // A failed hook must not prevent the remaining hooks.
                    CobbleTracker.LOGGER.error("Command hook for '{}' failed: {}", category.id(), command, t);
                }
            }
        });
    }

    /** The console at level 2, placed at the spawn so {@code ~ ~ ~} means the spawn and not world origin. */
    private static CommandSourceStack sourceAt(MinecraftServer server, SpawnRecord record) {
        CommandSourceStack source = server.createCommandSourceStack().withPermission(2);
        try {
            ResourceLocation id = ResourceLocation.parse(record.world());
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (level != null) {
                source = source.withLevel(level)
                        .withPosition(new Vec3(record.x() + 0.5, record.y(), record.z() + 0.5))
                        .withRotation(Vec2.ZERO);
            }
        } catch (Throwable t) {
            // Unknown dimensions use the console default position.
            CobbleTracker.LOGGER.debug("Couldn't place command hooks at {}", record.world(), t);
        }
        return source;
    }

    /** Returns null when any substituted command value is unsafe. */
    private static String substitute(String template, TrackerCategory category, SpawnRecord record,
            String playerName) {
        String species = record.species() == null ? "" : record.species();
        if (template.contains("%player%") && !isSafe(playerName)) {
            CobbleTracker.LOGGER.warn("Skipping a command hook for '{}': player name '{}' isn't safe to "
                    + "substitute into a command", category.id(), playerName);
            return null;
        }
        if (template.contains("%species%") && !isSafe(species)) {
            CobbleTracker.LOGGER.warn("Skipping a command hook for '{}': species '{}' isn't safe to "
                    + "substitute into a command", category.id(), species);
            return null;
        }
        return template
                .replace("%player%", playerName)
                .replace("%species%", species)
                .replace("%category%", category.id())
                .replace("%world%", record.world() == null ? "" : record.world())
                .replace("%biome%", record.biome() == null ? "" : record.biome())
                .replace("%x%", Integer.toString(record.x()))
                .replace("%y%", Integer.toString(record.y()))
                .replace("%z%", Integer.toString(record.z()));
    }

    /** Allows only identifier characters; blank substitutions are allowed. */
    private static boolean isSafe(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == ':';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
