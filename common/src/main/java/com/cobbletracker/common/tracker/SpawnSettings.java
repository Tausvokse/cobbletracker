package com.cobbletracker.common.tracker;

import java.util.List;

/**
 * The optional {@code spawn:} block of a tracker category. Plain config data; the mod-side
 * {@code SpawnEngine} interprets it.
 *
 * <p>When {@code distributeAmongPlayers} is true the engine rolls {@code chance} once per
 * {@code intervalTicks} for the whole server (not per player), so the rate doesn't scale with player
 * count; on a hit, one eligible player becomes the anchor. A player just used is skipped for
 * {@code playerCooldownTicks} so spawns spread out.
 *
 * <p>Every field is clamped on construction, so a nonsensical config.yml can't reach the spawner as, say,
 * a max distance below the minimum.
 *
 * @param intervalTicks          how often a spawn attempt is made (20 ticks = 1s)
 * @param chance                 probability [0,1] that an attempt spawns something
 * @param distributeAmongPlayers one server-wide roll and one anchor player, versus a roll per player
 * @param playerCooldownTicks    how long an anchored player is skipped afterwards
 * @param minDistance            closest the spawn may be placed to the anchor player, in blocks
 * @param maxDistance            furthest the spawn may be placed from the anchor player, in blocks
 * @param minLevel               lowest level that can be rolled (inclusive)
 * @param maxLevel               highest level that can be rolled (inclusive)
 * @param shiny                  force the shiny aspect
 * @param speciesPool            candidate species ids; empty = derive from the category's labels
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
        List<String> speciesPool) {

    public SpawnSettings {
        intervalTicks = Math.max(1, intervalTicks);
        chance = Math.max(0.0, Math.min(1.0, chance));
        playerCooldownTicks = Math.max(0, playerCooldownTicks);
        minDistance = Math.max(1, minDistance);
        maxDistance = Math.max(minDistance, maxDistance);
        minLevel = Math.max(1, Math.min(100, minLevel));
        maxLevel = Math.max(minLevel, Math.min(100, maxLevel));
        speciesPool = speciesPool == null ? List.of() : List.copyOf(speciesPool);
    }

    public static SpawnSettings disabled() {
        return new SpawnSettings(false, 12000, 0.25, true, 72000, 24, 64, 50, 70, false, List.of());
    }
}
