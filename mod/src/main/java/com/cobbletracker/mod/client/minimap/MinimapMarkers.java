package com.cobbletracker.mod.client.minimap;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.platform.PlatformServices;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

public final class MinimapMarkers {
    public record Marker(UUID entityId, String name, int color, int x, int y, int z, String dimension) {
        Marker neutral() {
            return new Marker(entityId, name, 0xFFFFFFFF, x, y, z, dimension);
        }
    }

    private static final List<NativeMarkers> providers = new ArrayList<>();
    private static boolean initialized;
    private static boolean enabled = true;
    private static boolean xaero = true;
    private static boolean voxel = true;
    private static boolean journey = true;
    private static boolean beamColor = true;
    private static String dimension;

    private MinimapMarkers() {
    }

    public static void configure(boolean active, boolean allowXaero, boolean allowVoxel, boolean allowJourney,
            boolean useBeamColor) {
        enabled = active;
        xaero = allowXaero;
        voxel = allowVoxel;
        journey = allowJourney;
        beamColor = useBeamColor;
        for (NativeMarkers provider : providers) provider.reconfigure();
        dimension = null;
    }

    public static void update(List<Marker> markers) {
        Minecraft client = Minecraft.getInstance();
        if (!enabled || client.level == null || client.player == null) {
            clear();
            return;
        }
        if (!initialized) {
            initialized = true;
            add("Xaero", "xaerominimap", "xaerominimapfair");
            add("VoxelMap", "voxelmap");
            add("JourneyMap", "journeymap");
        }
        String current = client.level.dimension().location().toString();
        if (!current.equals(dimension)) {
            clear();
            dimension = current;
        }
        Map<UUID, Marker> desired = new LinkedHashMap<>();
        for (Marker marker : markers) {
            if (current.equals(marker.dimension()) && desired.size() < 64) {
                desired.put(marker.entityId(), beamColor ? marker : marker.neutral());
            }
        }
        for (NativeMarkers provider : providers) {
            boolean allowed = switch (provider.name()) {
                case "Xaero" -> xaero;
                case "VoxelMap" -> voxel;
                default -> journey;
            };
            provider.update(allowed ? desired : Map.of(), client.level.dimension());
        }
    }

    private static void add(String name, String... modIds) {
        for (String id : modIds) {
            if (PlatformServices.isModLoaded(id)) {
                try {
                    providers.add(NativeMarkers.create(name));
                } catch (ReflectiveOperationException | LinkageError e) {
                    CobbleTracker.LOGGER.warn("{} automatic minimap markers are unavailable: {}", name,
                            e.getClass().getSimpleName());
                }
                return;
            }
        }
    }

    public static void remove(UUID entityId) {
        for (NativeMarkers provider : providers) {
            provider.remove(entityId);
        }
    }

    public static void clear() {
        for (NativeMarkers provider : providers) {
            provider.clear();
        }
        dimension = null;
    }

    public static void disconnected() {
        clear();
        providers.clear();
        initialized = false;
        enabled = true;
        xaero = true;
        voxel = true;
        journey = true;
        beamColor = true;
    }

    public static int xaeroColor(Object waypoint, int original) {
        return NativeMarkers.xaeroColor(waypoint, original);
    }

    public static ArrayList<?> voxelWaypoints(ArrayList<?> existing) {
        return NativeMarkers.voxelWaypoints(existing);
    }
}
