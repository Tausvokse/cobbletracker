package com.cobbletracker.mod.client.hud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.entity.Entity;
import com.cobbletracker.mod.client.minimap.MinimapMarkers;

public final class HudState {

    private HudState() {
    }

    private static volatile boolean hudVisible = true;
    private static volatile boolean beamVisible = true;
    private static final CopyOnWriteArrayList<TrackedSpawn> spawns = new CopyOnWriteArrayList<>();
    // Keep mutable deadlines outside the copy-on-write spawn list.
    private static final Map<UUID, Long> beamExpiry = new ConcurrentHashMap<>();

    private static final int MAX_SPAWNS = 24;

    /** Beam radius 0 uses render distance; graceMs controls expiry after the entity disappears. */
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
            MinimapMarkers.remove(dropped.entityId());
        }
    }

    public static void removeTrackedSpawn(UUID entityId) {
        spawns.removeIf(s -> s.entityId().equals(entityId));
        beamExpiry.remove(entityId);
        MinimapMarkers.remove(entityId);
    }

    /** Renews expiry while the entity is visible. */
    public static void keepAlive(UUID entityId, long graceMs) {
        long deadline = System.currentTimeMillis() + Math.max(1000L, graceMs);
        beamExpiry.merge(entityId, deadline, Math::max);
    }

    // Retain bounded metadata so returning entities can renew their grace.
    public static List<TrackedSpawn> trackedSpawns() {
        if (spawns.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        List<TrackedSpawn> out = new ArrayList<>();
        for (TrackedSpawn s : spawns) {
            Long expires = beamExpiry.get(s.entityId());
            if (expires != null && expires <= now) {
                beamExpiry.remove(s.entityId(), expires);
                MinimapMarkers.remove(s.entityId());
            } else if (expires != null) {
                out.add(s);
            }
        }
        return out;
    }

    // Resolved once per client tick; renderers reuse this snapshot.
    private static volatile Map<UUID, Entity> beamEntities = Map.of();

    public static Set<UUID> beamTargets() {
        Set<UUID> out = new HashSet<>();
        for (TrackedSpawn s : spawns) out.add(s.entityId());
        return out;
    }

    public static Set<UUID> beamTargets(String dimension) {
        Set<UUID> out = new HashSet<>();
        for (TrackedSpawn spawn : spawns) {
            if (dimension.equals(spawn.dimension())) out.add(spawn.entityId());
        }
        return out;
    }

    public static void setBeamEntities(Map<UUID, Entity> resolved) {
        beamEntities = resolved == null ? Map.of() : resolved;
        for (TrackedSpawn spawn : spawns) {
            Entity entity = beamEntities.get(spawn.entityId());
            if (entity != null && entity.isAlive() && !entity.isRemoved()
                    && spawn.dimension().equals(entity.level().dimension().location().toString())) {
                keepAlive(spawn.entityId(), spawn.graceMs());
            }
        }
    }

    public static Map<UUID, Entity> beamEntities() {
        return beamEntities;
    }

    public static void clearSpawns() {
        spawns.clear();
        beamExpiry.clear();
        beamEntities = Map.of();
        MinimapMarkers.clear();
    }

    public static boolean hudVisible() {
        return hudVisible;
    }

    public static void toggleHud() {
        hudVisible = !hudVisible;
        if (!hudVisible) {
            MinimapMarkers.clear();
        }
    }

    public static boolean beamVisible() {
        return beamVisible;
    }

    public static void toggleBeam() {
        beamVisible = !beamVisible;
    }
}
