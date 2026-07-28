package com.cobbletracker.neoforge;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.client.ClientHandler;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * NeoForge entry point: installs the network bridge, registers the payloads, then hands over to the
 * shared init. The S2C handlers below name {@link ClientHandler}, which is client-only - that's fine
 * because a handler body is not resolved until it runs, and on a dedicated server these never do. Keep
 * it that way: calling into client code from anywhere else in this class would break the server.
 */
@Mod(CobbleTracker.MODID)
public class CobbleTrackerNeoForge {

    public CobbleTrackerNeoForge(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::registerPayloads);

        PlatformNetworking.setBridge(new INetworkBridge() {
            @Override
            public void sendToServer(CustomPacketPayload payload) {
                PacketDistributor.sendToServer(payload);
            }

            @Override
            public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
                PacketDistributor.sendToPlayer(player, payload);
            }

            @Override
            public boolean canReach(ServerPlayer player) {
                return player.connection.hasChannel(S2C_SyncHistoryPacket.TYPE);
            }
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            CobbleTrackerNeoForgeClient.init(modEventBus);
        }

        CobbleTracker.init();
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(C2S_OpenTrackerPacket.TYPE, C2S_OpenTrackerPacket.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        TrackerServerHandler.handleOpenTracker(player);
                    }
                }));
        registrar.playToServer(C2S_AdminActionPacket.TYPE, C2S_AdminActionPacket.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        TrackerServerHandler.handleAdminAction(player, payload.key(), payload.value());
                    }
                }));
        registrar.playToServer(C2S_MinimapSupportPacket.TYPE, C2S_MinimapSupportPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        TrackerServerHandler.handleMinimapSupport(player, payload);
                    }
                }));
        registrar.playToClient(S2C_SyncHistoryPacket.TYPE, S2C_SyncHistoryPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandler.openTracker(payload)));
        registrar.playToClient(S2C_TrackedSpawnPacket.TYPE, S2C_TrackedSpawnPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandler.trackedSpawn(payload)));
        registrar.playToClient(S2C_SpawnEndedPacket.TYPE, S2C_SpawnEndedPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandler.spawnEnded(payload)));
        registrar.playToClient(S2C_SetThemePacket.TYPE, S2C_SetThemePacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandler.setTheme(payload)));
        registrar.playToClient(S2C_OpenAdminPacket.TYPE, S2C_OpenAdminPacket.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandler.openAdmin(payload)));
    }
}
