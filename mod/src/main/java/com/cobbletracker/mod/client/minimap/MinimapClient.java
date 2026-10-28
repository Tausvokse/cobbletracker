package com.cobbletracker.mod.client.minimap;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.net.C2S_MinimapSupportPacket;
import com.cobbletracker.mod.platform.PlatformServices;

public final class MinimapClient {

    private MinimapClient() {
    }

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
                    if (PlatformServices.isModLoaded(id)) {
                        return true;
                    }
                } catch (Throwable ignored) {
                    // Treat unavailable platform metadata as no provider.
                }
            }
            return false;
        }
    }

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
