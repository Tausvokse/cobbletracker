package com.cobbletracker.mod.client.hud;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobbletracker.mod.client.ClientHandler;
import com.cobbletracker.mod.client.minimap.MinimapMarkers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class HuntScanner {

    private HuntScanner() {
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            ClientHandler.disconnected();
            return;
        }
        if (mc.player == null) {
            HuntState.setTargets(List.of());
            HudState.setBeamEntities(Map.of());
            MinimapMarkers.clear();
            return;
        }
        // Load before checking whether the saved hunt list is empty.

        HuntState.ensureLoaded();
        // Persist edits even when the final hunt target was removed.
        HuntState.flush();
        boolean hunting = HuntState.anyHunted();
        // Resolve hunt and tracked-spawn targets in the same entity pass.

        Set<UUID> wantedBeams = HudState.beamTargets(mc.level.dimension().location().toString());
        if (!hunting && wantedBeams.isEmpty()) {
            HuntState.setTargets(List.of());
            HudState.setBeamEntities(Map.of());
            MinimapMarkers.clear();
            return;
        }
        int radius = Math.max(1, mc.options.getEffectiveRenderDistance()) * 16;
        long r2 = (long) radius * radius;
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        List<Entity> out = new ArrayList<>();
        Map<UUID, Entity> beams = wantedBeams.isEmpty() ? Map.of() : new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!wantedBeams.isEmpty() && wantedBeams.contains(e.getUUID())) {
                beams.put(e.getUUID(), e);
            }
            if (!hunting || !(e instanceof PokemonEntity pe) || !visibleMarker(e)) {
                continue;
            }
            String path = speciesPath(pe);
            if (path == null || !HuntState.isHunted(path)) {
                continue;
            }
            double dx = e.getX() - px;
            double dz = e.getZ() - pz;
            if (dx * dx + dz * dz > r2) {
                continue;
            }
            out.add(e);
        }
        out.sort((a, b) -> Double.compare(distSq(a, px, pz), distSq(b, px, pz)));
        HuntState.setTargets(out);
        HudState.setBeamEntities(beams);
        updateMarkers(mc, beams, out);
    }

    private static void updateMarkers(Minecraft client, Map<UUID, Entity> tracked, List<Entity> hunted) {
        if (!HudState.hudVisible()) {
            MinimapMarkers.clear();
            return;
        }
        String dimension = client.level.dimension().location().toString();
        Map<UUID, MinimapMarkers.Marker> markers = new LinkedHashMap<>();
        Map<UUID, Integer> trackedColors = new java.util.HashMap<>();
        int autoRadius = Math.max(1, client.options.getEffectiveRenderDistance()) * 16;
        for (HudState.TrackedSpawn spawn : HudState.trackedSpawns()) {
            if (!dimension.equals(spawn.dimension())) {
                continue;
            }
            trackedColors.put(spawn.entityId(), spawn.color());
            Entity entity = tracked.get(spawn.entityId());
            if (entity == null) {
                continue;
            }
            HudState.keepAlive(spawn.entityId(), spawn.graceMs());
            if (!visibleMarker(entity)) {
                continue;
            }
            int radius = spawn.beamRadius() > 0 ? spawn.beamRadius() : autoRadius;
            double dx = entity.getX() - client.player.getX();
            double dz = entity.getZ() - client.player.getZ();
            if (dx * dx + dz * dz <= (double) radius * radius) {
                markers.put(entity.getUUID(), marker(entity, spawn.species(), spawn.color(), dimension));
            }
        }
        for (Entity entity : hunted) {
            if (visibleMarker(entity)) {
                int color = trackedColors.getOrDefault(entity.getUUID(), HuntState.HUNT_COLOR);
                markers.putIfAbsent(entity.getUUID(), marker(entity, entity.getName().getString(), color, dimension));
            }
        }
        MinimapMarkers.update(List.copyOf(markers.values()));
    }

    static boolean visibleMarker(Entity entity) {
        if (!entity.isAlive() || entity.isRemoved() || entity.isInvisible()) {
            return false;
        }
        if (!(entity instanceof PokemonEntity pokemon)) {
            return true;
        }
        var data = pokemon.getPokemon();
        return data != null && pokemon.getPhasingTargetId() == -1 && !pokemon.isTame() && data.isWild()
                && data.getOwnerUUID() == null && !data.isNPCOwned() && pokemon.getOwner() == null;
    }

    private static MinimapMarkers.Marker marker(Entity entity, String name, int color, String dimension) {
        return new MinimapMarkers.Marker(entity.getUUID(), name, color, entity.blockPosition().getX(),
                entity.blockPosition().getY(), entity.blockPosition().getZ(), dimension);
    }

    private static double distSq(Entity e, double px, double pz) {
        double dx = e.getX() - px;
        double dz = e.getZ() - pz;
        return dx * dx + dz * dz;
    }

    public static String speciesPath(PokemonEntity pe) {
        try {
            return pe.getPokemon().getSpecies().getResourceIdentifier().getPath().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
    }
}
