package com.cobbletracker.mod.config;

/**
 * The {@code general-settings:} block of config.yml: the chat prefix, the anti-x-ray toggle that rounds
 * beam/waypoint positions to the chunk centre, and whether spawns also flash a title on screen (off by
 * default - most servers just want the chat line).
 */
public record GeneralSettings(
        String chatPrefix,
        boolean hideExactPosition,
        boolean showTitle) {

    public static GeneralSettings defaults() {
        return new GeneralSettings(com.cobbletracker.mod.text.Msg.DEFAULT_PREFIX, false, false);
    }
}
