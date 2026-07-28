package com.cobbletracker.mod.client.hud;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobbletracker.mod.client.hud.HudState.TrackedSpawn;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a beacon-style beam over each tracked Pokémon while it's in the player's loaded chunks. The beam
 * follows the live entity (looked up by UUID every frame), so it tracks the Pokémon as it moves. Since a
 * Pokémon only exists client-side within render distance, the beam shows up exactly when the target is in
 * range and drops when it isn't. Called from each loader's world-render hook.
 */
public final class BeamRenderer {

    /** Height used when the server didn't send one (older packets). */
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

        // Rare spawns the server flagged: find each by UUID and beam the live entity in its tier colour.
        if (!spawns.isEmpty()) {
            Set<UUID> wanted = new HashSet<>();
            for (TrackedSpawn s : spawns) {
                if (s.beamEnabled()) {
                    wanted.add(s.entityId());
                }
            }
            if (!wanted.isEmpty()) {
                Map<UUID, Entity> found = new HashMap<>();
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (wanted.contains(e.getUUID())) {
                        found.put(e.getUUID(), e);
                    }
                }
                for (TrackedSpawn s : spawns) {
                    Entity e = found.get(s.entityId());
                    if (e == null) {
                        continue; // not in loaded chunks right now
                    }
                    // It's here, so push the keep-alive out - the beam stays up while it's alive and near,
                    // and comes back whenever the player returns within range.
                    HudState.keepAlive(s.entityId(), s.graceMs());
                    if (beingCaught(e)) {
                        continue; // phasing into a poké ball - hide the beam until it breaks free
                    }
                    int radius = s.beamRadius() > 0 ? s.beamRadius() : autoRadius;
                    int height = s.beamHeight() > 0 ? s.beamHeight() : DEFAULT_HEIGHT;
                    drewAny |= beam(poseStack, buffers, cameraPos, e, s.color(), radius, height,
                            px, pz, partial, gameTime);
                }
            }
        }

        // Personal hunt (the \ menu): the scanner already filtered by range, so beam them all.
        for (Entity e : hunted) {
            if (beingCaught(e)) {
                continue;
            }
            drewAny |= beam(poseStack, buffers, cameraPos, e, HuntState.HUNT_COLOR, Integer.MAX_VALUE,
                    DEFAULT_HEIGHT, px, pz, partial, gameTime);
        }

        if (drewAny && buffers instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch();
        }
    }

    /** True while a Pokémon is phasing into a thrown ball (Cobblemon sets a phasing target during capture). */
    private static boolean beingCaught(Entity e) {
        return e instanceof PokemonEntity pokemon && pokemon.getPhasingTargetId() != -1;
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
