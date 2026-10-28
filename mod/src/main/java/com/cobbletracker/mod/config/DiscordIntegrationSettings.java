package com.cobbletracker.mod.config;

public record DiscordIntegrationSettings(
        boolean enabled,
        boolean richEmbeds,
        boolean postCaptures,
        boolean postDefeats,
        boolean postDespawns) {

    public static DiscordIntegrationSettings defaults() {
        return new DiscordIntegrationSettings(true, true, true, true, true);
    }
}
