package com.cobbletracker.mod.config;

/**
 * One entry in announcements.yml: how a category's spawn is broadcast. {@code title}/{@code subtitle}/
 * {@code chat} are MiniMessage (legacy &-codes work too). Placeholders {@code %species% %biome% %world%
 * %x% %y% %z%} are filled in at broadcast time, and {@code %waypoint%} becomes a clickable "Create
 * Waypoint" link.
 *
 * @param sound          sound id, or "" for none
 * @param soundVolume    playback volume
 * @param soundPitch     playback pitch
 * @param waypoint       include the clickable %waypoint% link (if the template uses it)
 * @param broadcastRadius 0 = whole server (with {@code playToAll}); >0 = only players within N blocks
 */
public record NotificationConfig(
        boolean enabled,
        String title,
        String subtitle,
        String chat,
        String sound,
        float soundVolume,
        float soundPitch,
        boolean waypoint,
        boolean playToAll,
        int broadcastRadius) {

    public NotificationConfig {
        soundVolume = soundVolume <= 0f ? 1.0f : Math.min(soundVolume, 10.0f);
        // Vanilla only carries pitch in the 0.5-2.0 range; anything outside is silently snapped anyway.
        soundPitch = soundPitch <= 0f ? 1.0f : Math.max(0.5f, Math.min(soundPitch, 2.0f));
        broadcastRadius = Math.max(0, broadcastRadius);
    }

    public static NotificationConfig disabled() {
        return new NotificationConfig(false, "", "", "", "", 1.0f, 1.0f, true, true, 0);
    }
}
