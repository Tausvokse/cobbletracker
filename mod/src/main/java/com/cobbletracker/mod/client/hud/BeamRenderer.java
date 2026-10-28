package com.cobbletracker.mod.client.hud;

import com.cobbletracker.mod.client.hud.HudState.TrackedSpawn;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Renders interpolated beams from the scanner's per-tick entity snapshot. */
public final class BeamRenderer {

    private static final int DEFAULT_HEIGHT = 512;

    private BeamRenderer() {
    }

    public static void render(PoseStack poseStack, Vec3 cameraPos, MultiBufferSource buffers) {
        if (poseStack == null || buffers == null) {
            return;
        }
        if (!HudState.hudVisible() || !HudState.beamVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        List<TrackedSpawn> spawns = HudState.trackedSpawns();
        List<Entity> hunted = HuntState.targets();
        if (spawns.isEmpty() && hunted.isEmpty()) {
            return;
        }

        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        long gameTime = mc.level.getGameTime();
        int autoRadius = Math.max(1, mc.options.getEffectiveRenderDistance()) * 16;
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        boolean drewAny = false;
        Map<UUID, Integer> trackedColors = new HashMap<>();
        Set<UUID> rendered = new HashSet<>();
        String dimension = mc.level.dimension().location().toString();
        for (TrackedSpawn spawn : spawns) {
            if (dimension.equals(spawn.dimension())) {
                trackedColors.put(spawn.entityId(), spawn.color());
            }
        }

        // Reuse the scanner snapshot instead of scanning the world each frame.

        if (!spawns.isEmpty()) {
            Map<UUID, Entity> found = HudState.beamEntities();
            for (TrackedSpawn s : spawns) {
                if (!s.beamEnabled() || !mc.level.dimension().location().toString().equals(s.dimension())) {
                    continue;
                }
                Entity e = found.get(s.entityId());
                if (e == null) {
                    continue; 
                }
                // Renew grace while the entity remains loaded.

                HudState.keepAlive(s.entityId(), s.graceMs());
                if (!HuntScanner.visibleMarker(e)) {
                    continue;
                }
                int radius = s.beamRadius() > 0 ? s.beamRadius() : autoRadius;
                int height = s.beamHeight() > 0 ? s.beamHeight() : DEFAULT_HEIGHT;
                if (beam(poseStack, buffers, cameraPos, e, s.color(), radius, height, px, pz, partial, gameTime)) {
                    rendered.add(s.entityId());
                    drewAny = true;
                }
            }
        }

        // Hunt targets are already filtered by range.
        for (Entity e : hunted) {
            if (rendered.contains(e.getUUID()) || !HuntScanner.visibleMarker(e)) {
                continue;
            }
            int color = trackedColors.getOrDefault(e.getUUID(), HuntState.HUNT_COLOR);
            drewAny |= beam(poseStack, buffers, cameraPos, e, color, Integer.MAX_VALUE,
                    DEFAULT_HEIGHT, px, pz, partial, gameTime);
        }

        if (drewAny && buffers instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch();
        }
    }

    private static boolean beam(PoseStack poseStack, MultiBufferSource buffers, Vec3 cameraPos, Entity e,
            int color, int radius, int height, double px, double pz, float partial, long gameTime) {
        double ex = Mth.lerp((double) partial, e.xo, e.getX());
        double ey = Mth.lerp((double) partial, e.yo, e.getY());
        double ez = Mth.lerp((double) partial, e.zo, e.getZ());
        if (radius != Integer.MAX_VALUE) {
            double hdx = ex - px;
            double hdz = ez - pz;
            if (hdx * hdx + hdz * hdz > (double) radius * radius) {
                return false;
            }
        }
        poseStack.pushPose();
        poseStack.translate(ex - cameraPos.x, ey - cameraPos.y, ez - cameraPos.z);
        BeaconRenderer.renderBeaconBeam(poseStack, buffers, BeaconRenderer.BEAM_LOCATION, partial, 1.0f,
                gameTime, 0, height, color & 0xFFFFFF, 0.2f, 0.25f);
        poseStack.popPose();
        return true;
    }
}
