package com.cobbletracker.mod.client.minimap;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.net.C2S_MinimapSupportPacket;
import dev.architectury.platform.Platform;

/**
 * Client-side detection of the supported minimap mods. On join the client checks the loaded mod list and
 * tells the server once which of Xaero's Minimap / VoxelMap / JourneyMap it has, so the server only ever
 * emits a waypoint format that player can actually read. To support another minimap, add a row to
 * {@link Provider} and a matching branch in {@link com.cobbletracker.mod.minimap.MinimapWaypoints}.
 */
public final class MinimapClient {

    private MinimapClient() {
    }

    /** One supported minimap: the capability it maps to, and the mod ids that count as "installed". */
    private enum Provider {
        XAERO("xaerominimap", "xaerominimapfair"),
        VOXELMAP("voxelmap"),
        JOURNEYMAP("journeymap");

        private final String[] modIds;

        Provider(String... modIds) {
            this.modIds = modIds;
        }

        boolean present() {
            for (String id : modIds) {
                try {
                    if (Platform.isModLoaded(id)) {
                        return true;
                    }
                } catch (Throwable ignored) {
                    // Platform not ready / unexpected loader state - treat as absent.
                }
            }
            return false;
        }
    }

    /** Builds the capability packet from the installed mod set (all-false if none present). */
    public static C2S_MinimapSupportPacket detect() {
        boolean xaero = Provider.XAERO.present();
        boolean voxel = Provider.VOXELMAP.present();
        boolean journey = Provider.JOURNEYMAP.present();
        if (xaero || voxel || journey) {
            CobbleTracker.LOGGER.info("Minimap integration active - xaero={} voxelmap={} journeymap={}",
                    xaero, voxel, journey);
        }
        return new C2S_MinimapSupportPacket(xaero, voxel, journey);
    }
}
