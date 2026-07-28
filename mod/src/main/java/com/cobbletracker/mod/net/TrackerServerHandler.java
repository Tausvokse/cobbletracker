package com.cobbletracker.mod.net;

import com.cobbletracker.mod.tracker.TrackerServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side handlers for the client → server packets. Loader-agnostic: the Fabric and NeoForge entry
 * classes marshal the payload onto the server thread and call in here. Anything an operator can do is
 * permission-checked again on arrival - the client having drawn the button proves nothing.
 */
public final class TrackerServerHandler {

    private TrackerServerHandler() {
    }

    /** Player asked to open the tracker GUI - reply with their spawn history. */
    public static void handleOpenTracker(ServerPlayer player) {
        TrackerServer.ifRunning(engine -> engine.syncTo(player));
    }

    /** Client told us which minimap mods it has - remember it for this player's waypoint emission. */
    public static void handleMinimapSupport(ServerPlayer player, C2S_MinimapSupportPacket caps) {
        TrackerServer.ifRunning(engine -> engine.setMinimapSupport(player, caps));
    }

    /** Admin screen edit - re-check OP, apply live, and refresh the screen. */
    public static void handleAdminAction(ServerPlayer player, String key, String value) {
        if (!player.hasPermissions(2)) {
            return;
        }
        TrackerServer.ifRunning(engine -> {
            engine.applyAdmin(key, value);
            engine.openAdmin(player);
        });
    }
}
