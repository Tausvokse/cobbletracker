package com.cobbletracker.fabric.client;

import com.cobbletracker.mod.client.ClientHandler;
import com.cobbletracker.mod.client.gui.SpeciesSearchScreen;
import com.cobbletracker.mod.client.hud.ArrowHudRenderer;
import com.cobbletracker.mod.client.hud.BeamRenderer;
import com.cobbletracker.mod.client.hud.HuntScanner;
import com.cobbletracker.mod.client.minimap.MinimapClient;
import com.cobbletracker.mod.net.C2S_MinimapSupportPacket;
import com.cobbletracker.mod.net.INetworkBridge;
import com.cobbletracker.mod.net.PlatformNetworking;
import com.cobbletracker.mod.net.S2C_OpenAdminPacket;
import com.cobbletracker.mod.net.S2C_SetThemePacket;
import com.cobbletracker.mod.net.S2C_SpawnEndedPacket;
import com.cobbletracker.mod.net.S2C_SyncHistoryPacket;
import com.cobbletracker.mod.net.S2C_TrackedSpawnPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric client entry: a bridge that can send in both directions (so a single-player integrated server
 * still delivers S2C), the receivers that open screens and drive the beam, the minimap handshake sent on
 * join, the {@code \} hotkey for the hunt menu, and the world/HUD render hooks.
 */
public class CobbleTrackerFabricClient implements ClientModInitializer {

    private static final KeyMapping HUD_MENU_KEY = new KeyMapping(
            "key.cobbletracker.hud_menu", GLFW.GLFW_KEY_BACKSLASH, "key.categories.cobbletracker");

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(HUD_MENU_KEY);

        PlatformNetworking.setBridge(new INetworkBridge() {
            @Override
            public void sendToServer(CustomPacketPayload payload) {
                ClientPlayNetworking.send(payload);
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

        ClientPlayNetworking.registerGlobalReceiver(S2C_SyncHistoryPacket.TYPE, (payload, context) ->
                context.client().execute(() -> ClientHandler.openTracker(payload)));
        ClientPlayNetworking.registerGlobalReceiver(S2C_TrackedSpawnPacket.TYPE, (payload, context) ->
                context.client().execute(() -> ClientHandler.trackedSpawn(payload)));
        ClientPlayNetworking.registerGlobalReceiver(S2C_SpawnEndedPacket.TYPE, (payload, context) ->
                context.client().execute(() -> ClientHandler.spawnEnded(payload)));
        ClientPlayNetworking.registerGlobalReceiver(S2C_SetThemePacket.TYPE, (payload, context) ->
                context.client().execute(() -> ClientHandler.setTheme(payload)));
        ClientPlayNetworking.registerGlobalReceiver(S2C_OpenAdminPacket.TYPE, (payload, context) ->
                context.client().execute(() -> ClientHandler.openAdmin(payload)));

        // Announce our installed minimap mods once, on join, so the server tailors waypoint emission.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            C2S_MinimapSupportPacket caps = MinimapClient.detect();
            if (caps.any() && ClientPlayNetworking.canSend(C2S_MinimapSupportPacket.TYPE)) {
                ClientPlayNetworking.send(caps);
            }
        }));

        // Leaving a world clears the beams, hunt targets and cached history, so nothing from one server
        // shows up on the next.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(ClientHandler::disconnected));

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
                BeamRenderer.render(context.matrixStack(), context.camera().getPosition(), context.consumers()));

        // On-screen arrows guiding the player to hunted Pokémon.
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> ArrowHudRenderer.render(guiGraphics));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HuntScanner.tick(client);
            while (HUD_MENU_KEY.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new SpeciesSearchScreen());
                }
            }
        });
    }
}
