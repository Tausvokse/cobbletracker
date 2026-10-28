package com.cobbletracker.mod.config;

public record NotificationConfig(
        boolean enabled,
        String title,
        String subtitle,
        String chat,
        String actionbar,
        String sound,
        float soundVolume,
        float soundPitch,
        boolean waypoint,
        boolean playToAll,
        int broadcastRadius,
        String discordWebhook,
        String discordTemplate) {

    public NotificationConfig {
        // Zero is an explicit mute.
        soundVolume = Float.isNaN(soundVolume) || soundVolume < 0f ? 1.0f : Math.min(soundVolume, 10.0f);
        soundPitch = Float.isNaN(soundPitch) || soundPitch <= 0f
                ? 1.0f : Math.max(0.5f, Math.min(soundPitch, 2.0f));
        broadcastRadius = Math.max(0, broadcastRadius);
        discordWebhook = discordWebhook == null ? "" : discordWebhook.trim();
        discordTemplate = discordTemplate == null ? "" : discordTemplate;
    }

    public static NotificationConfig disabled() {
        return new NotificationConfig(false, "", "", "", "", "", 1.0f, 1.0f, true, true, 0, "", "");
    }
}
