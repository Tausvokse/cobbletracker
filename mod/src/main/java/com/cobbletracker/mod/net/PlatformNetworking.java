package com.cobbletracker.mod.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static facade over the per-loader {@link INetworkBridge}. Shared code calls
 * {@link #sendToPlayer}/{@link #sendToServer}; the bridge is installed by each loader's entry class
 * before any packet is sent.
 */
public final class PlatformNetworking {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobbletracker-net");
    private static INetworkBridge bridge;

    private PlatformNetworking() {
    }

    public static void setBridge(INetworkBridge newBridge) {
        bridge = newBridge;
        LOGGER.info("Network bridge set to {}", newBridge.getClass().getSimpleName());
    }

    public static void sendToServer(CustomPacketPayload payload) {
        if (bridge != null) {
            bridge.sendToServer(payload);
        } else {
            LOGGER.warn("No network bridge; dropped C2S {}", payload.type().id());
        }
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (bridge != null) {
            bridge.sendToPlayer(player, payload);
        } else {
            LOGGER.warn("No network bridge; dropped S2C {}", payload.type().id());
        }
    }

    /** Whether the player's client has the mod and can receive our HUD/GUI packets. */
    public static boolean canReach(ServerPlayer player) {
        return bridge != null && bridge.canReach(player);
    }
}
