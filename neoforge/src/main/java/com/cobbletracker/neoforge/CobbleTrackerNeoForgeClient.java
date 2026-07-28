package com.cobbletracker.neoforge;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.client.ClientHandler;
import com.cobbletracker.mod.client.gui.SpeciesSearchScreen;
import com.cobbletracker.mod.client.hud.ArrowHudRenderer;
import com.cobbletracker.mod.client.hud.BeamRenderer;
import com.cobbletracker.mod.client.hud.HuntScanner;
import com.cobbletracker.mod.client.minimap.MinimapClient;
import com.cobbletracker.mod.net.C2S_MinimapSupportPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * NeoForge client wiring: the {@code \} hunt-menu keybind on the mod bus, plus the in-world beam render,
 * the HUD arrows, the minimap handshake on login and the key polling on the game bus. Only touched when
 * {@code Dist} says we're on a client, so a dedicated server never loads a client-only type from here.
 */
public final class CobbleTrackerNeoForgeClient {

    private static final KeyMapping HUD_MENU_KEY = new KeyMapping(
            "key.cobbletracker.hud_menu", GLFW.GLFW_KEY_BACKSLASH, "key.categories.cobbletracker");

    private CobbleTrackerNeoForgeClient() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(HUD_MENU_KEY));

        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                BeamRenderer.render(event.getPoseStack(), event.getCamera().getPosition(),
                        Minecraft.getInstance().renderBuffers().bufferSource());
            }
        });

        // On-screen arrows guiding the player to hunted Pokémon.
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) ->
                ArrowHudRenderer.render(event.getGuiGraphics()));

        // Announce our installed minimap mods once, on login, so the server tailors waypoint emission.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            C2S_MinimapSupportPacket caps = MinimapClient.detect();
            if (caps.any()) {
                try {
                    PacketDistributor.sendToServer(caps);
                } catch (Throwable t) {
                    CobbleTracker.LOGGER.debug("Server does not accept minimap support packet", t);
                }
            }
        });

        // Leaving a world clears the beams, hunt targets and cached history, so nothing from one server
        // shows up on the next.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
                ClientHandler.disconnected());

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            HuntScanner.tick(mc);
            while (HUD_MENU_KEY.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new SpeciesSearchScreen());
                }
            }
        });
    }
}
