package com.cobbletracker.mod;

import com.cobbletracker.mod.command.CobbleTrackerCommands;
import com.cobbletracker.mod.event.CobblemonBridge;
import com.cobbletracker.mod.tracker.TrackerServer;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared entry point. Called once from each loader's entry class ({@code CobbleTrackerFabric} /
 * {@code CobbleTrackerNeoForge}). Everything here goes through Architectury's event and networking
 * abstractions, so it's loader-independent.
 */
public final class CobbleTracker {

    public static final String MODID = "cobbletracker";
    public static final Logger LOGGER = LoggerFactory.getLogger("CobbleTracker");

    private static boolean initialized;

    private CobbleTracker() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** Server + common init: config, tracking service, commands, server lifecycle, Cobblemon hooks. */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        LOGGER.info("Initializing CobbleTracker");

        // Networking payloads are registered per loader in the entry classes (vanilla CustomPacketPayload).
        LifecycleEvent.SERVER_STARTED.register(TrackerServer::start);
        LifecycleEvent.SERVER_STOPPING.register(server -> TrackerServer.stop());
        // Custom spawner + debounced save run on the server post-tick.
        TickEvent.SERVER_POST.register(server -> TrackerServer.ifRunning(engine -> engine.tick(server)));
        // Drop whatever we were keeping per-player, or it accumulates for the server's whole uptime.
        PlayerEvent.PLAYER_QUIT.register(player ->
                TrackerServer.ifRunning(engine -> engine.playerLeft(player.getUUID())));

        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) ->
                CobbleTrackerCommands.register(dispatcher));

        // Watch spawns/captures/faints. Calls are guarded so an API change here won't take the server down.
        CobblemonBridge.register();
    }
}
