package com.cobbletracker.mod.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side HUD state: the tracked spawns the server told us about (each highlighted by an in-world
 * beam while it is inside our loaded chunks) and the player's local visibility toggles, which never leak
 * to the server - they only hide things on this client. All static since there is exactly one client.
 */
public final class HudState {

    private HudState() {
    }

    private static volatile boolean hudVisible = true;
    private static volatile boolean beamVisible = true;
    private static final CopyOnWriteArrayList<TrackedSpawn> spawns = new CopyOnWriteArrayList<>();
    /**
     * When each spawn's beam lapses, kept beside the list rather than inside the record: the renderer
     * pushes these deadlines out every frame, and rewriting an immutable element in place would mean a
     * whole-list copy per frame plus a race against the packet thread adding or removing entries.
     */
    private static final Map<UUID, Long> beamExpiry = new ConcurrentHashMap<>();

    /** Hard cap so a burst of spawns can't grow the beam list without bound. */
    private static final int MAX_SPAWNS = 24;

    /**
     * A Pokémon the server flagged as tracked. The beam follows the live entity resolved by
     * {@code entityId}; the coordinates are the spawn anchor (already chunk-obfuscated server-side when
     * configured) and are only a fallback for when the entity can't be found.
     *
     * @param beamRadius blocks; {@code 0} = auto (the player's render distance)
     * @param graceMs    how long the beam survives after the Pokémon was last seen
     */
    public record TrackedSpawn(UUID entityId, String category, String species, int color,
            int x, int y, int z, String dimension, boolean beamEnabled, int beamRadius, int beamHeight,
            long graceMs) {
    }

    public static void addTrackedSpawn(TrackedSpawn spawn) {
        spawns.removeIf(s -> s.entityId().equals(spawn.entityId()));
        spawns.add(spawn);
        beamExpiry.put(spawn.entityId(), System.currentTimeMillis() + Math.max(1000L, spawn.graceMs()));
        while (spawns.size() > MAX_SPAWNS) {
            TrackedSpawn dropped = spawns.remove(0);
            beamExpiry.remove(dropped.entityId());
        }
    }

    /** Drops a tracked spawn's beam immediately (server confirmed it was caught / defeated / despawned). */
    public static void removeTrackedSpawn(UUID entityId) {
        spawns.removeIf(s -> s.entityId().equals(entityId));
        beamExpiry.remove(entityId);
    }

    /**
     * Keeps a beam alive while its Pokémon is still present: the renderer calls this every frame the live
     * entity is resolved, pushing the expiry out by the spawn's grace window. So the beam only lapses once
     * the Pokémon has been out of the player's loaded chunks for that whole window (flew off for good), or
     * the server sends {@code S2C_SpawnEndedPacket} - and it re-appears the instant the player is back in
     * range while the grace is unspent.
     */
    public static void keepAlive(UUID entityId, long graceMs) {
        long deadline = System.currentTimeMillis() + Math.max(1000L, graceMs);
        beamExpiry.merge(entityId, deadline, Math::max);
    }

    /** Non-expired tracked spawns (prunes expired ones as a side effect). */
    public static List<TrackedSpawn> trackedSpawns() {
        if (spawns.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<TrackedSpawn> out = new ArrayList<>();
        List<TrackedSpawn> dead = new ArrayList<>();
        for (TrackedSpawn s : spawns) {
            Long expires = beamExpiry.get(s.entityId());
            if (expires == null || expires <= now) {
                dead.add(s);
            } else {
                out.add(s);
            }
        }
        for (TrackedSpawn s : dead) {
            spawns.remove(s);
            beamExpiry.remove(s.entityId());
        }
        return out;
    }

    /** Forgets every tracked spawn - called on disconnect so beams never carry into the next world. */
    public static void clearSpawns() {
        spawns.clear();
        beamExpiry.clear();
    }

    public static boolean hudVisible() {
        return hudVisible;
    }

    public static void toggleHud() {
        hudVisible = !hudVisible;
    }

    public static boolean beamVisible() {
        return beamVisible;
    }

    public static void toggleBeam() {
        beamVisible = !beamVisible;
    }
}
