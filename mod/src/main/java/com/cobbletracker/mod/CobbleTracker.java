package com.cobbletracker.mod;

import com.cobbletracker.mod.command.CobbleTrackerCommands;
import com.cobbletracker.mod.event.CobblemonBridge;
import com.cobbletracker.mod.tracker.TrackerServer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared initialization; client classes are loaded through loader entry points. */
public final class CobbleTracker {

    public static final String MODID = "cobbletracker";
    public static final Logger LOGGER = LoggerFactory.getLogger("CobbleTracker");

    private static boolean initialized;

    private CobbleTracker() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        LOGGER.info("Initializing CobbleTracker");

        CobblemonBridge.register();
    }
}
