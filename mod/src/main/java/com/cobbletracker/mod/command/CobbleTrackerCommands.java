package com.cobbletracker.mod.command;

import com.cobbletracker.mod.announce.PlayerPrefs;
import com.cobbletracker.common.notify.NotificationPreference;
import com.cobbletracker.mod.client.theme.Theme;
import com.cobbletracker.mod.net.PlatformNetworking;
import com.cobbletracker.mod.net.S2C_SetThemePacket;
import com.cobbletracker.mod.text.Msg;
import com.cobbletracker.mod.tracker.TrackerServer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class CobbleTrackerCommands {

    private CobbleTrackerCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> THEMES = (ctx, builder) -> {
        for (String name : Theme.names()) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> CATEGORIES = (ctx, builder) -> {
        TrackerServer engine = TrackerServer.get();
        if (engine != null) {
            for (String id : engine.categoryIds()) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("cobbletracker")
                .requires(s -> Permissions.check(s, Permissions.GUI, Permissions.LEVEL_ANYONE))
                .executes(CobbleTrackerCommands::openGui)
                .then(Commands.literal("reload")
                        .requires(s -> Permissions.check(s, Permissions.RELOAD, Permissions.LEVEL_OPERATOR))
                        .executes(CobbleTrackerCommands::reload))
                .then(Commands.literal("admin")
                        .requires(s -> Permissions.check(s, Permissions.ADMIN, Permissions.LEVEL_OPERATOR))
                        .executes(CobbleTrackerCommands::admin))
                .then(Commands.literal("fakehit")
                        .requires(s -> Permissions.check(s, Permissions.FAKEHIT, Permissions.LEVEL_OPERATOR))
                        .executes(CobbleTrackerCommands::fakehit))
                .then(Commands.literal("waypoint")
                        .requires(s -> Permissions.check(s, Permissions.WAYPOINT, Permissions.LEVEL_ANYONE))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> waypoint(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("notify")
                        .requires(s -> Permissions.check(s, Permissions.NOTIFY, Permissions.LEVEL_ANYONE))
                        .executes(CobbleTrackerCommands::notifyList)
                        .then(Commands.argument("category", StringArgumentType.word()).suggests(CATEGORIES)
                                .then(Commands.literal("on")
                                        .executes(ctx -> notifySet(ctx, true, null, null)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> notifySet(ctx, false, null, null)))
                                .then(notificationChannel("chat", NotificationPreference.Channel.CHAT))
                                .then(notificationChannel("title", NotificationPreference.Channel.TITLE))
                                .then(notificationChannel("actionbar", NotificationPreference.Channel.ACTIONBAR))
                                .then(Commands.literal("sound")
                                        .then(Commands.literal("on")
                                                .executes(ctx -> notifySet(ctx, null, false, null)))
                                        .then(Commands.literal("off")
                                                .executes(ctx -> notifySet(ctx, null, true, null))))
                                .then(Commands.literal("radius")
                                        .then(Commands.argument("blocks", IntegerArgumentType.integer(0, 100000))
                                                .executes(ctx -> notifySet(ctx, null, null,
                                                        IntegerArgumentType.getInteger(ctx, "blocks")))))))
                .then(Commands.literal("theme")
                        .requires(s -> Permissions.check(s, Permissions.THEME, Permissions.LEVEL_ANYONE))
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(THEMES)
                                .executes(ctx -> theme(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("hof")
                        .requires(s -> Permissions.check(s, Permissions.GUI, Permissions.LEVEL_ANYONE))
                        .executes(CobbleTrackerCommands::hallOfFame)));

        // Brigadier redirects do not inherit the root executor.
        dispatcher.register(Commands.literal("ct")
                .requires(s -> Permissions.check(s, Permissions.GUI, Permissions.LEVEL_ANYONE))
                .executes(CobbleTrackerCommands::openGui).redirect(root));
        dispatcher.register(Commands.literal("last")
                .requires(s -> Permissions.check(s, Permissions.GUI, Permissions.LEVEL_ANYONE))
                .executes(CobbleTrackerCommands::openGui).redirect(root));
        dispatcher.register(Commands.literal("ll")
                .requires(s -> Permissions.check(s, Permissions.GUI, Permissions.LEVEL_ANYONE))
                .executes(CobbleTrackerCommands::openGui).redirect(root));
        dispatcher.register(Commands.literal("lastlegend")
                .requires(s -> Permissions.check(s, Permissions.LASTLEGEND, Permissions.LEVEL_ANYONE))
                .executes(CobbleTrackerCommands::openLegendsGui));
        dispatcher.register(Commands.literal("checklegendary")
                .requires(s -> Permissions.check(s, Permissions.CHECKLEGENDARY, Permissions.LEVEL_ANYONE))
                .executes(CobbleTrackerCommands::checkLegendary));
        dispatcher.register(Commands.literal("checkspawn")
                .requires(s -> Permissions.check(s, "cobbletracker.command.checkspawn", Permissions.LEVEL_ANYONE))
                .then(Commands.argument("group", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            TrackerServer engine = TrackerServer.get();
                            if (engine != null) engine.config().spawnGroups().keySet().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            ServerPlayer player = player(ctx);
                            TrackerServer engine = TrackerServer.get();
                            if (player == null || engine == null) return 0;
                            engine.sendSpawnReport(player, StringArgumentType.getString(ctx, "group"));
                            return 1;
                        })));
        dispatcher.register(Commands.literal("spawnlegendary")
                .requires(s -> Permissions.check(s, Permissions.SPAWNLEGENDARY, Permissions.LEVEL_OPERATOR))
                .executes(CobbleTrackerCommands::spawnLegendary));
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
        if (!engine.syncTo(player)) {
            player.sendSystemMessage(Msg.warn("Your spawn history is still on its way - try again in a moment."));
            return 0;
        }
        return 1;
    }

    private static int openLegendsGui(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        TrackerServer engine = TrackerServer.get();
        if (engine == null) {
            ctx.getSource().sendFailure(Msg.error("CobbleTracker isn't ready yet."));
            return 0;
        }
        String legendary = engine.legendaryCategoryId();
        if (!PlatformNetworking.canReach(player) || legendary.isEmpty()) {
            return openLegends(ctx);
        }
        if (!engine.syncTo(player, legendary)) {
            return openLegends(ctx);
        }
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

    private static int spawnLegendary(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        TrackerServer engine = TrackerServer.get();
        if (engine == null) {
            ctx.getSource().sendFailure(Msg.error("CobbleTracker isn't ready yet."));
            return 0;
        }
        String outcome = engine.forceLegendarySpawn(player);
        if (outcome == null) {
                ctx.getSource().sendSuccess(() -> Msg.success("Legendary spawn request accepted."), false);
            return 1;
        }
        ctx.getSource().sendFailure(Msg.error(outcome));
        return 0;
    }

    private static int checkLegendary(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        TrackerServer engine = TrackerServer.get();
        if (engine == null) {
            ctx.getSource().sendFailure(Msg.error("CobbleTracker isn't ready yet."));
            return 0;
        }
        engine.sendLegendaryCheck(player);
        return 1;
    }

    private static int notifyList(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        TrackerServer engine = TrackerServer.get();
        if (engine == null) {
            ctx.getSource().sendFailure(Msg.error("CobbleTracker isn't ready yet."));
            return 0;
        }
        engine.sendNotifyReport(player);
        return 1;
    }

    private static int notifySet(CommandContext<CommandSourceStack> ctx, Boolean enabled,
            Boolean muteSound, Integer radius) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        TrackerServer engine = TrackerServer.get();
        if (engine == null) {
            ctx.getSource().sendFailure(Msg.error("CobbleTracker isn't ready yet."));
            return 0;
        }
        String category = StringArgumentType.getString(ctx, "category");
        if (!engine.hasCategory(category)) {
            ctx.getSource().sendFailure(Msg.error("Unknown category <yellow>" + Msg.esc(category)
                    + "</yellow>. Try: <white>" + String.join(", ", engine.categoryIds())));
            return 0;
        }
        NotificationPreference current = PlayerPrefs.get(player.getUUID(), category);
        if (enabled != null) current = current.withCategory(enabled);
        if (radius != null) current = current.withRadius(radius);
        if (muteSound != null) current = current.withChannel(NotificationPreference.Channel.SOUND, !muteSound);
        engine.setNotifyPref(player, category, current);
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> notificationChannel(
            String name, NotificationPreference.Channel channel) {
        return Commands.literal(name)
                .then(Commands.literal("on").executes(ctx -> notifyChannel(ctx, channel, true)))
                .then(Commands.literal("off").executes(ctx -> notifyChannel(ctx, channel, false)));
    }

    private static int notifyChannel(CommandContext<CommandSourceStack> ctx,
            NotificationPreference.Channel channel, boolean enabled) {
        ServerPlayer player = player(ctx);
        TrackerServer engine = TrackerServer.get();
        if (player == null || engine == null) return 0;
        String category = StringArgumentType.getString(ctx, "category");
        if (!engine.hasCategory(category)) {
            ctx.getSource().sendFailure(Msg.error("Unknown category: " + Msg.esc(category)));
            return 0;
        }
        engine.setNotifyPref(player, category,
                PlayerPrefs.get(player.getUUID(), category).withChannel(channel, enabled));
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
                    "A CobbleTracker YAML or species spawn JSON file is invalid - kept the previous settings. "
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

    private static int hallOfFame(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack s = ctx.getSource();
        if (s.getEntity() instanceof ServerPlayer player) {
            com.cobbletracker.mod.hof.HallOfFameModule.display(player);
        } else {
            s.sendSuccess(() -> Msg.info("Hall of Fame can only be viewed in-game by players."), false);
        }
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
