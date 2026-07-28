package com.cobbletracker.common.tracker;

import java.util.UUID;

/**
 * A single recorded spawn, as stored in tracker.json.
 *
 * <p>{@code outcome} is what happened to it afterwards: "" while it's still out there, or
 * "caught" / "defeated" / "despawned". {@code outcomeBy} is the player responsible (the catcher or the
 * killer); it's empty for a plain despawn. The old {@code caught}/{@code catcherName} fields are kept
 * around because the capture leaderboard and the history GUI still read them.
 *
 * @param id          the Pokémon's UUID, and the key inside a category
 * @param speciesId   namespaced id (e.g. cobblemon:rayquaza), used for the GUI sprite
 */
public record SpawnRecord(
        UUID id,
        String category,
        String species,
        String speciesId,
        long spawnTime,
        String world,
        int x,
        int y,
        int z,
        String biome,
        boolean shiny,
        boolean caught,
        UUID catcherUuid,
        String catcherName,
        String outcome,
        String outcomeBy) {

    public SpawnRecord {
        outcome = outcome == null ? "" : outcome;
        outcomeBy = outcomeBy == null ? "" : outcomeBy;
    }

    /** Marks it caught by the given player. */
    public SpawnRecord asCaught(UUID catcher, String catcherDisplayName) {
        return new SpawnRecord(id, category, species, speciesId, spawnTime, world, x, y, z, biome, shiny,
                true, catcher, catcherDisplayName, "caught", catcherDisplayName == null ? "" : catcherDisplayName);
    }

    /** Marks a non-capture outcome ("defeated" by someone, or "despawned" with no actor). */
    public SpawnRecord withOutcome(String newOutcome, String by) {
        return new SpawnRecord(id, category, species, speciesId, spawnTime, world, x, y, z, biome, shiny,
                caught, catcherUuid, catcherName, newOutcome == null ? "" : newOutcome, by == null ? "" : by);
    }
}
