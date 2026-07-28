package com.cobbletracker.mod.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loader-agnostic send API. Each loader installs an implementation via
 * {@link PlatformNetworking#setBridge} at init, so shared code sends packets without knowing whether
 * it runs on NeoForge or Fabric.
 */
public interface INetworkBridge {

    void sendToServer(CustomPacketPayload payload);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /**
     * Whether the player's client has CobbleTracker installed and can receive our beam/GUI packets.
     * Vanilla clients are skipped entirely - they still get the chat announcement, just no beam or GUI.
     */
    boolean canReach(ServerPlayer player);
}
