package com.cobbletracker.mod.command;

import com.cobbletracker.mod.client.theme.Theme;
import com.cobbletracker.mod.net.PlatformNetworking;
import com.cobbletracker.mod.net.S2C_SetThemePacket;
import com.cobbletracker.mod.text.Msg;
import com.cobbletracker.mod.tracker.TrackerServer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /cobbletracker} command tree, aliased {@code /ct}, {@code /last} and {@code /ll}. The bare
 * command opens the history GUI; {@code reload}, {@code admin} and {@code fakehit} are OP-only.
 * {@code theme} pushes a GUI theme to the caller's client, and {@code waypoint} - what the clickable
 * coordinates in an announcement run - drops that spawn onto their minimap.
 */
public final class CobbleTrackerCommands {

    private CobbleTrackerCommands() {
    }

    // Theme is a plain palette record with no client imports, so naming it here is safe on a dedicated
    // server - it only ever validates and suggests names, the client does the rendering.
    private static final SuggestionProvider<CommandSourceStack> THEMES = (ctx, builder) -> {
        for (String name : Theme.names()) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("cobbletracker")
                .executes(CobbleTrackerCommands::openGui)
                .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(CobbleTrackerCommands::reload))
                .then(Commands.literal("admin")
                        .requires(s -> s.hasPermission(2))
                        .executes(CobbleTrackerCommands::admin))
                .then(Commands.literal("fakehit")
                        .requires(s -> s.hasPermission(2))
                        .executes(CobbleTrackerCommands::fakehit))
                .then(Commands.literal("waypoint")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> waypoint(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("theme")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(THEMES)
                                .executes(ctx -> theme(ctx, StringArgumentType.getString(ctx, "name"))))));

        // Aliases. redirect() forwards sub-commands to the root's children, but Brigadier does NOT
        // copy the root's own executor onto a redirected node - so we set executes() here too, or bare
        // "/ct" / "/last" would fail with "unknown or incomplete command".
        dispatcher.register(Commands.literal("ct")
                .executes(CobbleTrackerCommands::openGui).redirect(root));
        dispatcher.register(Commands.literal("last")
                .executes(CobbleTrackerCommands::openGui).redirect(root));
        dispatcher.register(Commands.literal("ll")
                .executes(CobbleTrackerCommands::openGui).redirect(root));
        // A plain chat rundown of recent legendary spawns - no GUI, no client mod needed.
        dispatcher.register(Commands.literal("lastlegend")
                .executes(CobbleTrackerCommands::openLegends));
    }

    private static int openGui(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        TrackerServer engine = TrackerServer.get();
        if (engine == null) {
            ctx.getSource().sendFailure(Msg.error("CobbleTracker isn't ready yet."));
            return 0;
        }
        if (!PlatformNetworking.canReach(player)) {
            player.sendSystemMessage(Msg.warn("Install the CobbleTracker client mod to open the spawn history GUI."));
            return 0;
        }
        engine.syncTo(player);
        return 1;
    }

    private static int openLegends(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        TrackerServer engine = TrackerServer.get();
        if (engine == null) {
            ctx.getSource().sendFailure(Msg.error("CobbleTracker isn't ready yet."));
            return 0;
        }
        engine.sendLegendReport(player);
        return 1;
    }

    private static int waypoint(CommandContext<CommandSourceStack> ctx, String id) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        UUID recordId;
        try {
            recordId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            // Typed by hand or a stale link; nothing to say beyond "that isn't a spawn id".
            ctx.getSource().sendFailure(Msg.error("That isn't a valid spawn id."));
            return 0;
        }
        TrackerServer.ifRunning(engine -> engine.dropWaypoint(player, recordId));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        TrackerServer engine = TrackerServer.get();
        if (engine == null) {
            ctx.getSource().sendFailure(Msg.error("CobbleTracker isn't ready yet."));
            return 0;
        }
        if (!engine.reload()) {
            ctx.getSource().sendFailure(Msg.error(
                    "config.yml or announcements.yml could not be parsed - kept the previous settings. "
                            + "See the server log for the exact line."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Msg.success("Reloaded CobbleTracker configuration."), true);
        return 1;
    }

    private static int admin(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        if (!PlatformNetworking.canReach(player)) {
            player.sendSystemMessage(Msg.warn("Install the CobbleTracker client mod to open the admin screen."));
            return 0;
        }
        TrackerServer.ifRunning(engine -> engine.openAdmin(player));
        return 1;
    }

    private static int fakehit(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        TrackerServer engine = TrackerServer.get();
        if (engine == null) {
            ctx.getSource().sendFailure(Msg.error("CobbleTracker isn't ready yet."));
            return 0;
        }
        boolean ok = engine.sendTestBeam(player);
        if (ok) {
            ctx.getSource().sendSuccess(() ->
                    Msg.info("Spawned a test shiny Pikachu - watch chat, the beam and your minimap."), false);
            return 1;
        }
        ctx.getSource().sendFailure(Msg.error("Couldn't place the test spawn here - try open ground."));
        return 0;
    }

    private static int theme(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        if (Theme.byName(name) == null) {
            ctx.getSource().sendFailure(Msg.error("Unknown theme <yellow>" + Msg.esc(name)
                    + "</yellow>. Try: <white>" + String.join(", ", Theme.names())));
            return 0;
        }
        PlatformNetworking.sendToPlayer(player, new S2C_SetThemePacket(name));
        return 1;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            return player;
        }
        ctx.getSource().sendFailure(Msg.error("Only players can use this command."));
        return null;
    }
}
