package com.cobbletracker.mod.net;

import com.cobbletracker.mod.tracker.TrackerServer;
import com.cobbletracker.mod.command.Permissions;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;

public final class TrackerServerHandler {

    private static final Map<ServerPlayer, Long> LAST_CAPABILITIES = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_ADMIN_ACTION = new WeakHashMap<>();

    private TrackerServerHandler() {
    }

    public static void handleOpenTracker(ServerPlayer player) {
        if (!Permissions.check(player, Permissions.GUI, Permissions.LEVEL_ANYONE)) {
            return;
        }
        TrackerServer.ifRunning(engine -> engine.syncTo(player));
    }

    public static void handleMinimapSupport(ServerPlayer player, C2S_MinimapSupportPacket caps) {
        if (!allow(LAST_CAPABILITIES, player, 1_000)) {
            return;
        }
        TrackerServer.ifRunning(engine -> engine.clientReady(player, caps));
    }

    public static void handleAdminAction(ServerPlayer player, String key, String value) {
        if (!Permissions.check(player, Permissions.GUI, Permissions.LEVEL_ANYONE)
                || !Permissions.check(player, Permissions.ADMIN, Permissions.LEVEL_OPERATOR)
                || !allow(LAST_ADMIN_ACTION, player, 100)) {
            return;
        }
        TrackerServer.ifRunning(engine -> {
            engine.applyAdmin(key, value);
            engine.openAdmin(player);
        });
    }

    private static boolean allow(Map<ServerPlayer, Long> lastActions, ServerPlayer player, long cooldownMs) {
        long now = System.nanoTime();
        Long last = lastActions.get(player);
        if (last != null && now - last < cooldownMs * 1_000_000L) {
            return false;
        }
        lastActions.put(player, now);
        return true;
    }
}
