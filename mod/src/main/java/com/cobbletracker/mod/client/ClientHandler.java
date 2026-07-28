package com.cobbletracker.mod.client;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.client.gui.AdminScreen;
import com.cobbletracker.mod.client.gui.SpeciesSearchScreen;
import com.cobbletracker.mod.client.gui.TrackerScreen;
import com.cobbletracker.mod.client.hud.HudState;
import com.cobbletracker.mod.client.hud.HuntState;
import com.cobbletracker.mod.client.theme.ThemeManager;
import com.cobbletracker.mod.net.S2C_OpenAdminPacket;
import com.cobbletracker.mod.net.S2C_SetThemePacket;
import com.cobbletracker.mod.net.S2C_SpawnEndedPacket;
import com.cobbletracker.mod.net.S2C_SyncHistoryPacket;
import com.cobbletracker.mod.net.S2C_TrackedSpawnPacket;
import net.minecraft.client.Minecraft;

/**
 * Client-side receivers for the server → client packets. Each loader's client entry registers the
 * payloads and dispatches here on the client thread, so this stays loader-agnostic. Opening a screen /
 * updating HUD state is all that happens here.
 */
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
        // The beam is presence-based: the duration is a keep-alive grace, refreshed every frame the live
        // entity is visible (see BeamRenderer), so the beam persists while the Pokémon is alive and near,
        // survives brief absences, and only lapses once it has been out of range for the whole grace.
        long grace = Math.max(1, payload.beamDurationTicks()) * 50L;
        HudState.addTrackedSpawn(new HudState.TrackedSpawn(payload.entityId(), payload.category(),
                payload.species(), payload.color(), payload.x(), payload.y(), payload.z(),
                payload.dimension(), payload.beamEnabled(), payload.beamRadius(), payload.beamHeight(),
                grace));
        CobbleTracker.LOGGER.debug("Tracked spawn: '{}' ({}) entity {} at {},{},{}",
                payload.species(), payload.category(), payload.entityId(),
                payload.x(), payload.y(), payload.z());
    }

    /** Leaving a world: drop every beam and hunt target so nothing carries into the next one. */
    public static void disconnected() {
        HudState.clearSpawns();
        HuntState.setTargets(java.util.List.of());
        ClientData.clear();
        SpeciesSearchScreen.invalidateSpeciesCache();
    }

    /** The server says this tracked Pokémon is gone (caught / defeated / despawned) - drop its beam now. */
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
