package com.cobbletracker.mod.client.hud;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;

/**
 * Client-side personal-hunt state: which species the player is hunting (picked in the {@code \} search
 * menu, keyed by species resource path, e.g. {@code rayquaza}) and the live entities matching them that
 * {@link HuntScanner} found in the loaded chunks this tick. The beam lights those entities up and the
 * on-screen arrows point at the nearest few, so the player can just follow them to what they want.
 */
public final class HuntState {

    private HuntState() {
    }

    /** Accent colour for hunted-Pokémon beams / arrows. */
    public static final int HUNT_COLOR = 0xFFFFAA00;

    private static final Set<String> hunted = ConcurrentHashMap.newKeySet();
    private static volatile List<Entity> targets = List.of();

    public static boolean isHunted(String speciesPath) {
        return speciesPath != null && hunted.contains(speciesPath.toLowerCase(java.util.Locale.ROOT));
    }

    public static void toggle(String speciesPath) {
        if (speciesPath == null) {
            return;
        }
        String key = speciesPath.toLowerCase(java.util.Locale.ROOT);
        if (!hunted.remove(key)) {
            hunted.add(key);
        }
    }

    public static void clear() {
        hunted.clear();
        targets = List.of();
    }

    public static Set<String> hunted() {
        return hunted;
    }

    public static boolean anyHunted() {
        return !hunted.isEmpty();
    }

    /** Replaces the live match list (called by the scanner each client tick). */
    public static void setTargets(List<Entity> newTargets) {
        targets = newTargets == null ? List.of() : newTargets;
    }

    /** Live entities matching the hunted species, nearest first. */
    public static List<Entity> targets() {
        return targets;
    }
}
