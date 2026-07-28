package com.cobbletracker.mod.config;

/**
 * The {@code beam:} block of config.yml. The beam is a beacon-style light column over a tracked Pokémon
 * while it's in the player's loaded chunks - the in-world "it's here" marker instead of dumping raw
 * coordinates in chat.
 *
 * @param enabled         master switch
 * @param radius          how far (blocks) from the player the beam may show; 0 = auto (the player's own
 *                        render distance, which is the furthest a Pokémon exists client-side anyway)
 * @param durationSeconds keep-alive grace. The beam is presence-based and refreshed every frame the
 *                        Pokémon is visible, so this only counts once it's out of range - long enough
 *                        that coming back still shows it. A real catch/faint/despawn removes it at once.
 * @param height          height of the beam column in blocks
 */
public record BeamSettings(boolean enabled, int radius, int durationSeconds, int height) {

    public static BeamSettings defaults() {
        return new BeamSettings(true, 0, 600, 512);
    }

    /** Upper bounds keep a typo in config.yml - or a stuck "+" in the admin screen - from doing damage. */
    public BeamSettings {
        radius = clamp(radius, 0, 2048);
        durationSeconds = clamp(durationSeconds, 1, 86_400);
        height = clamp(height, 16, 4096);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    /** Grace window in client ticks (20/s). */
    public int durationTicks() {
        return durationSeconds * 20;
    }
}
