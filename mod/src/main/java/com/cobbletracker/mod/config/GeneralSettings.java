package com.cobbletracker.mod.config;

public record GeneralSettings(
        String chatPrefix,
        boolean hideExactPosition,
        boolean showTitle,
        boolean showSpawnLocationInChat) {

    public static GeneralSettings defaults() {
        return new GeneralSettings(com.cobbletracker.mod.text.Msg.DEFAULT_PREFIX, false, false, false);
    }
}
