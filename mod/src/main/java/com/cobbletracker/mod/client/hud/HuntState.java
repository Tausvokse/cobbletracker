package com.cobbletracker.mod.client.hud;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;

public final class HuntState {

    private HuntState() {
    }

    public static final int HUNT_COLOR = 0xFFFFAA00;

    private static final Set<String> hunted = ConcurrentHashMap.newKeySet();
    private static volatile List<Entity> targets = List.of();

    // Default to enabled for servers without the feature-policy packet.
    private static volatile boolean allowed = true;

    private static volatile boolean loaded;

    private static volatile boolean dirty;

    private static int saveCooldown;

    // Either the scanner or search screen may load the list first.
    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        // Avoid retrying a failed read every client tick.
        loaded = true;
        hunted.addAll(HuntStore.load());
    }

    public static boolean isHunted(String speciesPath) {
        return speciesPath != null && hunted.contains(speciesPath.toLowerCase(java.util.Locale.ROOT));
    }

    public static void toggle(String speciesPath) {
        if (speciesPath == null) {
            return;
        }
        ensureLoaded();
        String key = speciesPath.toLowerCase(java.util.Locale.ROOT);
        if (!hunted.remove(key)) {
            hunted.add(key);
        }
        markDirty();
    }

    public static void clear() {
        ensureLoaded();
        hunted.clear();
        targets = List.of();
        markDirty();
    }

    private static void markDirty() {
        dirty = true;
        saveCooldown = 40; 
    }

    public static void flush() {
        if (!dirty) {
            return;
        }
        if (saveCooldown > 0) {
            saveCooldown--;
            return;
        }
        flushNow();
    }

    /** Flushes pending edits when the hunt screen closes, even without a world. */
    public static void flushNow() {
        if (!dirty) {
            return;
        }
        dirty = false;
        saveCooldown = 0;
        HuntStore.save(new java.util.LinkedHashSet<>(hunted));
    }

    public static Set<String> hunted() {
        return hunted;
    }

    public static void setAllowed(boolean value) {
        allowed = value;
        if (!value) {
            targets = List.of();
        }
    }

    public static boolean allowed() {
        return allowed;
    }

    public static boolean anyHunted() {
        return allowed && !hunted.isEmpty();
    }

    public static void setTargets(List<Entity> newTargets) {
        targets = newTargets == null ? List.of() : newTargets;
    }

    public static List<Entity> targets() {
        return targets;
    }
}
