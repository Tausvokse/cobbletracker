package com.cobbletracker.fabric;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.net.C2S_AdminActionPacket;
import com.cobbletracker.mod.net.C2S_MinimapSupportPacket;
import com.cobbletracker.mod.net.C2S_OpenTrackerPacket;
import com.cobbletracker.mod.net.INetworkBridge;
import com.cobbletracker.mod.net.PlatformNetworking;
import com.cobbletracker.mod.net.S2C_OpenAdminPacket;
import com.cobbletracker.mod.net.S2C_SetThemePacket;
import com.cobbletracker.mod.net.S2C_SpawnEndedPacket;
import com.cobbletracker.mod.net.S2C_SyncHistoryPacket;
import com.cobbletracker.mod.net.S2C_TrackedSpawnPacket;
import com.cobbletracker.mod.net.TrackerServerHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Fabric entry point: installs the network bridge, registers payloads + C2S receivers, then inits. */
public class CobbleTrackerFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        PlatformNetworking.setBridge(new INetworkBridge() {
            @Override
            public void sendToServer(CustomPacketPayload payload) {
                // The dedicated server never sends to itself; C2S is installed by the client bridge.
            }

            @Override
            public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
                ServerPlayNetworking.send(player, payload);
            }

            @Override
            public boolean canReach(ServerPlayer player) {
                return ServerPlayNetworking.canSend(player, S2C_SyncHistoryPacket.TYPE);
            }
        });

        // Payload types. Both directions are registered here, on both sides: the server needs the S2C
        // types to send them, and this entry point runs on a client too.
        PayloadTypeRegistry.playC2S().register(C2S_OpenTrackerPacket.TYPE, C2S_OpenTrackerPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(C2S_AdminActionPacket.TYPE, C2S_AdminActionPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(C2S_MinimapSupportPacket.TYPE, C2S_MinimapSupportPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2C_SyncHistoryPacket.TYPE, S2C_SyncHistoryPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2C_TrackedSpawnPacket.TYPE, S2C_TrackedSpawnPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2C_SpawnEndedPacket.TYPE, S2C_SpawnEndedPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2C_SetThemePacket.TYPE, S2C_SetThemePacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2C_OpenAdminPacket.TYPE, S2C_OpenAdminPacket.STREAM_CODEC);

        // C2S receivers (server side).
        ServerPlayNetworking.registerGlobalReceiver(C2S_OpenTrackerPacket.TYPE, (payload, context) ->
                context.player().server.execute(() -> TrackerServerHandler.handleOpenTracker(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(C2S_AdminActionPacket.TYPE, (payload, context) ->
                context.player().server.execute(() ->
                        TrackerServerHandler.handleAdminAction(context.player(), payload.key(), payload.value())));
        ServerPlayNetworking.registerGlobalReceiver(C2S_MinimapSupportPacket.TYPE, (payload, context) ->
                context.player().server.execute(() ->
                        TrackerServerHandler.handleMinimapSupport(context.player(), payload)));

        CobbleTracker.init();
    }
}
