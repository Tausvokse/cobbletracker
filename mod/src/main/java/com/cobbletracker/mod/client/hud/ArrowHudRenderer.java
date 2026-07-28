package com.cobbletracker.mod.client.hud;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobbletracker.common.util.Geometry;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Draws a small directional-arrow list (top-left) pointing to the Pokémon the player is hunting via the
 * {@code \} search menu. Bearing and distance are recomputed every frame from the player's live position
 * and camera yaw, so the arrow keeps pointing at the target as they move. Only the targets
 * {@link HuntScanner} found - already inside the loaded chunks - appear.
 */
public final class ArrowHudRenderer {

    private static final int LIST_MAX = 3;

    private ArrowHudRenderer() {
    }

    public static void render(GuiGraphics g) {
        if (!HudState.hudVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) {
            return;
        }
        List<Entity> targets = HuntState.targets();
        if (targets.isEmpty()) {
            return;
        }
        Font font = mc.font;
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        float yaw = mc.player.getYRot();

        int x = 6;
        int y = 6;
        Component header = Component.translatable("hud.cobbletracker.hunting");
        g.drawString(font, header, x + 1, y + 1, 0xFF000000, false);
        g.drawString(font, header, x, y, 0xFFFFFFFF, false);
        y += 11;
        int shown = 0;
        for (Entity e : targets) {
            if (shown >= LIST_MAX) {
                break;
            }
            double dx = e.getX() - px;
            double dz = e.getZ() - pz;
            double dist = Geometry.horizontalDistance(dx, dz);
            double rel = Geometry.relativeAngle(dx, dz, yaw);
            String line = arrowFor(rel) + " " + speciesName(e) + "  " + (int) Math.round(dist) + "m";
            g.drawString(font, line, x + 1, y + 1, 0xFF000000, false);
            g.drawString(font, line, x, y, HuntState.HUNT_COLOR, false);
            y += 11;
            shown++;
        }
    }

    private static String speciesName(Entity e) {
        if (e instanceof PokemonEntity pe) {
            try {
                return pe.getPokemon().getSpecies().getName();
            } catch (Throwable ignored) {
                // fall through
            }
        }
        return "Pokémon";
    }

    private static String arrowFor(double rel) {
        double a = Geometry.normalize180(rel);
        if (a >= -22.5 && a < 22.5) {
            return "↑"; // up / ahead
        }
        if (a >= 22.5 && a < 67.5) {
            return "↗";
        }
        if (a >= 67.5 && a < 112.5) {
            return "→";
        }
        if (a >= 112.5 && a < 157.5) {
            return "↘";
        }
        if (a >= -67.5 && a < -22.5) {
            return "↖";
        }
        if (a >= -112.5 && a < -67.5) {
            return "←";
        }
        if (a >= -157.5 && a < -112.5) {
            return "↙";
        }
        return "↓"; // behind
    }
}
