package com.cobbletracker.fabric;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.command.CobbleTrackerCommands;
import com.cobbletracker.mod.event.CobblemonBridge;
import com.cobbletracker.mod.platform.PlatformServices;
import com.cobbletracker.mod.tracker.TrackerServer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import java.nio.file.Path;
import com.cobbletracker.mod.net.C2S_AdminActionPacket;
import com.cobbletracker.mod.net.C2S_MinimapSupportPacket;
import com.cobbletracker.mod.net.C2S_OpenTrackerPacket;
import com.cobbletracker.mod.net.INetworkBridge;
import com.cobbletracker.mod.net.PlatformNetworking;
import com.cobbletracker.mod.net.S2C_ClientSettingsPacket;
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

public class CobbleTrackerFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        PlatformServices.install(new PlatformServices.Bridge() {
            public Path configDirectory() { return FabricLoader.getInstance().getConfigDir(); }
            public boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(TrackerServer::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> TrackerServer.stop());
        ServerTickEvents.END_SERVER_TICK.register(server -> TrackerServer.ifRunning(engine -> engine.tick(server)));
        ServerEntityEvents.ENTITY_LOAD.register(CobblemonBridge::onEntityAdd);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                TrackerServer.ifRunning(engine -> engine.playerLeft(handler.player.getUUID())));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, selection) ->
                CobbleTrackerCommands.register(dispatcher));
        PlatformNetworking.setBridge(new INetworkBridge() {
            @Override
            public void sendToServer(CustomPacketPayload payload) {
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

        PayloadTypeRegistry.playC2S().register(C2S_OpenTrackerPacket.TYPE, C2S_OpenTrackerPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(C2S_AdminActionPacket.TYPE, C2S_AdminActionPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(C2S_MinimapSupportPacket.TYPE, C2S_MinimapSupportPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2C_SyncHistoryPacket.TYPE, S2C_SyncHistoryPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2C_TrackedSpawnPacket.TYPE, S2C_TrackedSpawnPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2C_SpawnEndedPacket.TYPE, S2C_SpawnEndedPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2C_SetThemePacket.TYPE, S2C_SetThemePacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2C_OpenAdminPacket.TYPE, S2C_OpenAdminPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2C_ClientSettingsPacket.TYPE,
                S2C_ClientSettingsPacket.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(C2S_OpenTrackerPacket.TYPE, (payload, context) ->
                context.player().server.execute(() -> TrackerServerHandler.handleOpenTracker(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(C2S_AdminActionPacket.TYPE, (payload, context) ->
                context.player().server.execute(() ->
                        TrackerServerHandler.handleAdminAction(context.player(), payload.key(), payload.value())));
        ServerPlayNetworking.registerGlobalReceiver(C2S_MinimapSupportPacket.TYPE, (payload, context) ->
                context.player().server.execute(() ->
                        TrackerServerHandler.handleMinimapSupport(context.player(), payload)));

        CobbleTracker.init();
        com.cobbletracker.fabric.placeholder.FabricPlaceholders.init();
    }
}
