package com.cobbletracker.mod.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Loader-specific packet transport. */
public interface INetworkBridge {

    void sendToServer(CustomPacketPayload payload);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    boolean canReach(ServerPlayer player);
}
