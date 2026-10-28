package com.cobbletracker.mod.minimap;

import com.cobbletracker.common.minimap.WaypointFormats;
import com.cobbletracker.mod.config.MinimapSettings;
import com.cobbletracker.mod.net.C2S_MinimapSupportPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MinimapWaypoints {
    private MinimapWaypoints() {
    }

    public static boolean send(ServerPlayer player, C2S_MinimapSupportPacket caps, MinimapSettings settings,
            String species, int color, int x, int y, int z, String dimension) {
        if (caps == null || !caps.any() || settings == null || !settings.enabled()) {
            return false;
        }
        boolean sent = false;
        if (caps.xaero() && settings.xaero()) {
            int markerColor = settings.useBeamColor() ? color : 0xFFFFFFFF;
            player.sendSystemMessage(Component.literal(WaypointFormats.xaero(species, markerColor, x, y, z, dimension)));
            sent = true;
        }
        if ((caps.voxelmap() && settings.voxelmap()) || (caps.journeymap() && settings.journeymap())) {
            player.sendSystemMessage(Component.literal(WaypointFormats.shared(species, x, y, z, dimension)));
            sent = true;
        }
        return sent;
    }
}
