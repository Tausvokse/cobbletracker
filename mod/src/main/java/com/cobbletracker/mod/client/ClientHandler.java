package com.cobbletracker.mod.client;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.client.gui.AdminScreen;
import com.cobbletracker.mod.client.gui.SpeciesSearchScreen;
import com.cobbletracker.mod.client.gui.TrackerScreen;
import com.cobbletracker.mod.client.hud.HudState;
import com.cobbletracker.mod.client.hud.HuntState;
import com.cobbletracker.mod.client.theme.ThemeManager;
import com.cobbletracker.mod.net.S2C_ClientSettingsPacket;
import com.cobbletracker.mod.net.S2C_OpenAdminPacket;
import com.cobbletracker.mod.net.S2C_SetThemePacket;
import com.cobbletracker.mod.net.S2C_SpawnEndedPacket;
import com.cobbletracker.mod.net.S2C_SyncHistoryPacket;
import com.cobbletracker.mod.net.S2C_TrackedSpawnPacket;
import net.minecraft.client.Minecraft;

public final class ClientHandler {

    private ClientHandler() {
    }

    public static void openTracker(S2C_SyncHistoryPacket payload) {
        ClientData.setSync(payload);
        Minecraft.getInstance().setScreen(new TrackerScreen(payload));
        CobbleTracker.LOGGER.debug("Opened tracker GUI: {} categories, {} entries, {} stats",
                payload.categories().size(), payload.entries().size(), payload.stats().size());
    }

    public static void trackedSpawn(S2C_TrackedSpawnPacket payload) {
        // Visible entities renew the beam's grace period.

        long grace = Math.max(1, payload.beamDurationTicks()) * 50L;
        HudState.addTrackedSpawn(new HudState.TrackedSpawn(payload.entityId(), payload.category(),
                payload.species(), payload.color(), payload.x(), payload.y(), payload.z(),
                payload.dimension(), payload.beamEnabled(), payload.beamRadius(), payload.beamHeight(),
                grace));
        CobbleTracker.LOGGER.debug("Tracked spawn: '{}' ({}) entity {} at {},{},{}",
                payload.species(), payload.category(), payload.entityId(),
                payload.x(), payload.y(), payload.z());
    }

    public static void disconnected() {
        HudState.clearSpawns();
        com.cobbletracker.mod.client.minimap.MinimapMarkers.disconnected();
        HuntState.setTargets(java.util.List.of());
        // Reset server policy for the next connection.

        HuntState.setAllowed(true);
        ClientData.clear();
        SpeciesSearchScreen.invalidateSpeciesCache();
    }

    public static void clientSettings(S2C_ClientSettingsPacket payload) {
        com.cobbletracker.mod.client.minimap.MinimapMarkers.configure(payload.minimapEnabled(), payload.xaero(),
                payload.voxelmap(), payload.journeymap(), payload.useBeamColor());
        HuntState.setAllowed(payload.huntEnabled());
        if (payload.resetBeams()) HudState.clearSpawns();
    }

    public static void spawnEnded(S2C_SpawnEndedPacket payload) {
        HudState.removeTrackedSpawn(payload.entityId());
    }

    public static void setTheme(S2C_SetThemePacket payload) {
        ThemeManager.set(payload.theme());
    }

    public static void openAdmin(S2C_OpenAdminPacket payload) {
        Minecraft.getInstance().setScreen(new AdminScreen(payload.settings()));
    }
}
