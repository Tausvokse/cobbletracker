package com.cobbletracker.common.tracker;

import java.util.List;

/**
 * Legacy category spawn settings. Shared mode rolls once per server interval and applies an anchor
 * cooldown. Numeric ranges are clamped; vertical distance is relative to the player.
 */
public record SpawnSettings(
        boolean enabled,
        int intervalTicks,
        double chance,
        boolean distributeAmongPlayers,
        int playerCooldownTicks,
        int minDistance,
        int maxDistance,
        int minLevel,
        int maxLevel,
        boolean shiny,
        List<String> speciesPool,
        Mode mode,
        boolean contextAware,
        int maxVerticalDistance) {

    public SpawnSettings(boolean enabled, int intervalTicks, double chance, boolean distributeAmongPlayers,
            int playerCooldownTicks, int minDistance, int maxDistance, int minLevel, int maxLevel,
            boolean shiny, List<String> speciesPool, Mode mode, boolean contextAware) {
        this(enabled, intervalTicks, chance, distributeAmongPlayers, playerCooldownTicks, minDistance,
                maxDistance, minLevel, maxLevel, shiny, speciesPool, mode, contextAware, 16);
    }

    /** Controls whether legacy scheduling defers to natural tier coverage. */
    public enum Mode {
        /** Run only while Cobblemon's live pool has no spawns of its own for this category. */
        AUTO,
        /** Always run, regardless of what the pool contains. */
        ALWAYS,
        /** Never run; the {@code spawn:} block is kept but dormant. */
        NEVER;

        /** Parses the config word; anything unrecognised falls back to {@link #AUTO}. */
        public static Mode parse(String raw) {
            if (raw == null) {
                return AUTO;
            }
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "always" -> ALWAYS;
                case "never" -> NEVER;
                default -> AUTO;
            };
        }
    }

    public SpawnSettings {
        intervalTicks = Math.max(1, intervalTicks);
        chance = Double.isNaN(chance) ? 0.0 : Math.max(0.0, Math.min(1.0, chance));
        playerCooldownTicks = Math.max(0, playerCooldownTicks);
        minDistance = Math.max(1, minDistance);
        maxDistance = Math.max(minDistance, maxDistance);
        maxVerticalDistance = Math.max(0, Math.min(256, maxVerticalDistance));
        minLevel = Math.max(1, Math.min(100, minLevel));
        maxLevel = Math.max(minLevel, Math.min(100, maxLevel));
        speciesPool = speciesPool == null ? List.of() : speciesPool.stream()
                .map(id -> id.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(id -> !id.isEmpty())
                .map(id -> id.contains(":") ? id : "cobblemon:" + id)
                .distinct().toList();
        mode = mode == null ? Mode.AUTO : mode;
    }

    public static SpawnSettings disabled() {
        return new SpawnSettings(false, 12000, 0.25, true, 72000, 24, 64, 50, 70, false, List.of(),
                Mode.AUTO, true);
    }
}
