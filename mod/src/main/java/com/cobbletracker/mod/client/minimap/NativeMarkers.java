package com.cobbletracker.mod.client.minimap;

import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.client.minimap.MinimapMarkers.Marker;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

abstract class NativeMarkers {
    private record Point(Marker marker, Object nativePoint) {
    }

    private final String name;
    private final Map<UUID, Point> points = new LinkedHashMap<>();
    private boolean failed;
    private static final Map<Object, Integer> XAERO_COLORS = new IdentityHashMap<>();
    private static final Map<UUID, Object> VOXEL_POINTS = new LinkedHashMap<>();

    NativeMarkers(String name) {
        this.name = name;
    }

    static NativeMarkers create(String name) throws ReflectiveOperationException {
        return switch (name) {
            case "Xaero" -> new Xaero();
            case "VoxelMap" -> new Voxel();
            default -> new Journey();
        };
    }

    String name() {
        return name;
    }

    final void update(Map<UUID, Marker> desired, ResourceKey<Level> dimension) {
        if (failed) {
            return;
        }
        try {
            for (UUID id : new ArrayList<>(points.keySet())) {
                if (!desired.containsKey(id)) {
                    remove(id);
                }
            }
            for (Marker marker : desired.values()) {
                Point previous = points.get(marker.entityId());
                if (previous == null || !previous.marker().equals(marker)) {
                    if (previous != null) {
                        delete(previous.nativePoint());
                        points.remove(marker.entityId());
                    }
                    Object point = createPoint(marker, dimension);
                    if (point != null) {
                        points.put(marker.entityId(), new Point(marker, point));
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            clear();
            failed = true;
            CobbleTracker.LOGGER.warn("{} automatic minimap markers stopped: {}", name,
                    e.getClass().getSimpleName());
        }
    }

    final void remove(UUID entityId) {
        Point point = points.remove(entityId);
        if (point != null) {
            try {
                delete(point.nativePoint());
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                CobbleTracker.LOGGER.debug("Could not remove {} minimap marker", name, e);
            }
        }
    }

    final void clear() {
        for (UUID id : new ArrayList<>(points.keySet())) {
            remove(id);
        }
    }

    final void reconfigure() {
        clear();
        failed = false;
    }

    abstract Object createPoint(Marker marker, ResourceKey<Level> dimension) throws ReflectiveOperationException;

    abstract void delete(Object point) throws ReflectiveOperationException;

    static int xaeroColor(Object waypoint, int original) {
        return XAERO_COLORS.getOrDefault(waypoint, original);
    }

    static ArrayList<?> voxelWaypoints(ArrayList<?> existing) {
        if (VOXEL_POINTS.isEmpty()) {
            return existing;
        }
        ArrayList<Object> result = new ArrayList<>(existing);
        result.addAll(VOXEL_POINTS.values());
        return result;
    }

    private static final class Xaero extends NativeMarkers {
        private final Constructor<?> constructor;
        private final Method temporary;
        private final Method getCustom;
        private Map<Object, Object> custom;
        private int sequence;

        @SuppressWarnings("unchecked")
        Xaero() throws ReflectiveOperationException {
            super("Xaero");
            Class<?> type = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            constructor = type.getConstructor(int.class, int.class, int.class, String.class, String.class, int.class);
            temporary = type.getMethod("setTemporary", boolean.class);
            Class<?> manager = Class.forName("xaero.common.minimap.waypoints.WaypointsManager");
            getCustom = manager.getMethod("getCustomWaypoints", String.class);
        }

        @Override
        @SuppressWarnings("unchecked")
        Object createPoint(Marker marker, ResourceKey<Level> dimension) throws ReflectiveOperationException {
            custom = (Map<Object, Object>) getCustom.invoke(null, CobbleTracker.MODID);
            Object point = constructor.newInstance(marker.x(), marker.y(), marker.z(), marker.name(), "◆", 15);
            temporary.invoke(point, true);
            XAERO_COLORS.put(point, marker.color() & 0xFFFFFF);
            custom.put(++sequence, point);
            return point;
        }

        @Override
        void delete(Object point) {
            if (custom != null) {
                custom.values().removeIf(value -> value == point);
            }
            XAERO_COLORS.remove(point);
        }
    }

    private static final class Voxel extends NativeMarkers {
        private final Constructor<?> constructor;
        private final Method setX;
        private final Method setZ;

        Voxel() throws ReflectiveOperationException {
            super("VoxelMap");
            Class<?> waypoint = Class.forName("com.mamiyaotaru.voxelmap.util.Waypoint");
            constructor = waypoint.getConstructor(String.class,
                    int.class, int.class, int.class, boolean.class, float.class, float.class, float.class,
                    String.class, String.class, TreeSet.class);
            setX = waypoint.getMethod("setX", int.class);
            setZ = waypoint.getMethod("setZ", int.class);
        }

        @Override
        Object createPoint(Marker marker, ResourceKey<Level> dimension) throws ReflectiveOperationException {
            int color = marker.color();
            Object point = constructor.newInstance(marker.name(), 0, 0, marker.y(), true,
                    ((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f,
                    "", "", new TreeSet<>());
            setX.invoke(point, marker.x());
            setZ.invoke(point, marker.z());
            VOXEL_POINTS.put(marker.entityId(), point);
            return point;
        }

        @Override
        void delete(Object point) {
            VOXEL_POINTS.values().removeIf(value -> value == point);
        }
    }

    private static final class Journey extends NativeMarkers {
        private final Object api;
        private final Constructor<?> image;
        private final Constructor<?> marker;
        private final Method color;
        private final Method iconWidth;
        private final Method iconHeight;
        private final Method centerIcon;
        private final Method label;
        private final Method textProperties;
        private final Method labelOffset;
        private final Method setDimension;
        private final Method active;
        private final Object minimapUis;
        private final Method show;
        private final Method remove;

        Journey() throws ReflectiveOperationException {
            super("JourneyMap");
            Class<?> clientApi = Class.forName("journeymap.api.client.impl.ClientAPI");
            api = clientApi.getField("INSTANCE").get(null);
            Class<?> imageType = Class.forName("journeymap.api.v2.client.model.MapImage");
            Class<?> markerType = Class.forName("journeymap.api.v2.client.display.MarkerOverlay");
            Class<?> display = Class.forName("journeymap.api.v2.client.display.Displayable");
            Class<?> ui = Class.forName("journeymap.api.v2.client.display.Context$UI");
            image = imageType.getConstructor(ResourceLocation.class, int.class, int.class);
            marker = markerType.getConstructor(String.class, BlockPos.class, imageType);
            color = imageType.getMethod("setColor", int.class);
            iconWidth = imageType.getMethod("setDisplayWidth", double.class);
            iconHeight = imageType.getMethod("setDisplayHeight", double.class);
            centerIcon = imageType.getMethod("centerAnchors");
            label = markerType.getMethod("setLabel", String.class);
            textProperties = markerType.getMethod("getTextProperties");
            labelOffset = textProperties.getReturnType().getMethod("setOffsetY", int.class);
            setDimension = markerType.getMethod("setDimension", ResourceKey.class);
            minimapUis = Array.newInstance(ui, 1);
            Array.set(minimapUis, 0, ui.getField("Minimap").get(null));
            active = markerType.getMethod("setActiveUIs", minimapUis.getClass());
            show = clientApi.getMethod("show", display);
            remove = clientApi.getMethod("remove", display);
        }

        @Override
        Object createPoint(Marker point, ResourceKey<Level> dimension) throws ReflectiveOperationException {
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("journeymap",
                    "textures/waypoint/icon/00_markers/jwi01circle_full.png");
            Object icon = image.newInstance(texture, 16, 16);
            color.invoke(icon, point.color() & 0xFFFFFF);
            iconWidth.invoke(icon, 8d);
            iconHeight.invoke(icon, 8d);
            centerIcon.invoke(icon);
            Object overlay = marker.newInstance(CobbleTracker.MODID, new BlockPos(point.x(), point.y(), point.z()), icon);
            label.invoke(overlay, point.name());
            labelOffset.invoke(textProperties.invoke(overlay), 12);
            setDimension.invoke(overlay, dimension);
            active.invoke(overlay, minimapUis);
            show.invoke(api, overlay);
            return overlay;
        }

        @Override
        void delete(Object point) throws ReflectiveOperationException {
            remove.invoke(api, point);
        }
    }
}
