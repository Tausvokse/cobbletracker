package com.cobbletracker.mod.config;

/**
 * The {@code minimap:} block of {@code config.yml}. CobbleTracker drops a temporary waypoint for a
 * tracked spawn onto whichever supported minimap mod the player actually has installed - the client
 * auto-detects its minimap ({@code xaerominimap} / {@code journeymap} / {@code voxelmap}) and tells the
 * server, which then emits only the matching waypoint format. Each mod can be switched off server-wide
 * here; adding support for another minimap is a localized change (one emitter + one detector id).
 *
 * @param enabled      master switch for all minimap waypoints
 * @param xaero        emit Xaero's Minimap waypoints
 * @param voxelmap     emit VoxelMap waypoints
 * @param journeymap   emit JourneyMap waypoints
 * @param useBeamColor colour the minimap marker to match the spawn beam (the category's tier colour).
 *                     When {@code false} the minimap mod's own default colour is used.
 */
public record MinimapSettings(
        boolean enabled,
        boolean xaero,
        boolean voxelmap,
        boolean journeymap,
        boolean useBeamColor) {

    public static MinimapSettings defaults() {
        return new MinimapSettings(true, true, true, true, true);
    }
}
