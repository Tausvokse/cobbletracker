package com.cobbletracker.common.tracker;

import java.util.UUID;

/**
 * Persistent encounter. An empty outcome means active; other outcomes are caught, defeated or
 * despawned. Legacy capture fields remain readable.
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
        String outcomeBy,
        SpawnDetails details,
        String traits) {

    public SpawnRecord {
        outcome = outcome == null ? "" : outcome;
        outcomeBy = outcomeBy == null ? "" : outcomeBy;
        traits = traits == null ? "" : traits;
        // Older records have no optional traits.
        details = details == null ? SpawnDetails.EMPTY : details;
    }

    /** Overload for backwards compatibility with callers not passing traits. */
    public SpawnRecord(
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
            String outcomeBy,
            SpawnDetails details) {
        this(id, category, species, speciesId, spawnTime, world, x, y, z, biome, shiny,
                caught, catcherUuid, catcherName, outcome, outcomeBy, details, "");
    }

    /** Marks it caught by the given player. */
    public SpawnRecord asCaught(UUID catcher, String catcherDisplayName) {
        return new SpawnRecord(id, category, species, speciesId, spawnTime, world, x, y, z, biome, shiny,
                true, catcher, catcherDisplayName, "caught",
                catcherDisplayName == null ? "" : catcherDisplayName, details, traits);
    }

    /** Marks a non-capture outcome ("defeated" by someone, or "despawned" with no actor). */
    public SpawnRecord withOutcome(String newOutcome, String by) {
        return new SpawnRecord(id, category, species, speciesId, spawnTime, world, x, y, z, biome, shiny,
                caught, catcherUuid, catcherName, newOutcome == null ? "" : newOutcome,
                by == null ? "" : by, details, traits);
    }
}
