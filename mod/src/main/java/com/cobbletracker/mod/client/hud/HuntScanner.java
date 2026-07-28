package com.cobbletracker.mod.client.hud;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Each client tick, scans the loaded chunks for live Pokémon whose species the player is hunting and
 * hands the nearest-first list to {@link HuntState}. Runs entirely client-side off the entities already
 * present in the world, so a hunted Pokémon lights up the instant it enters the player's render distance -
 * no server round-trip. Cheap: one pass over the render list, only while something is being hunted.
 */
public final class HuntScanner {

    private HuntScanner() {
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || !HuntState.anyHunted()) {
            HuntState.setTargets(List.of());
            return;
        }
        int radius = Math.max(1, mc.options.getEffectiveRenderDistance()) * 16;
        long r2 = (long) radius * radius;
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        List<Entity> out = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof PokemonEntity pe) || !e.isAlive()) {
                continue;
            }
            if (pe.getPhasingTargetId() != -1) {
                continue; // being caught right now - don't beam or point at it
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
    }

    private static double distSq(Entity e, double px, double pz) {
        double dx = e.getX() - px;
        double dz = e.getZ() - pz;
        return dx * dx + dz * dz;
    }

    /** Lower-case species resource path of a Pokémon entity, or null if unavailable. */
    public static String speciesPath(PokemonEntity pe) {
        try {
            return pe.getPokemon().getSpecies().getResourceIdentifier().getPath().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
    }
}
