package com.cobbletracker.common.notify;

public record NotificationPreference(boolean enabled, int radius, boolean muteSound,
        boolean chat, boolean title, boolean actionbar) {

    public static final NotificationPreference DEFAULT = new NotificationPreference(true, 0, false);

    public enum Channel { CHAT, TITLE, ACTIONBAR, SOUND }

    public NotificationPreference(boolean enabled, int radius, boolean muteSound) {
        this(enabled, radius, muteSound, true, true, true);
    }

    public NotificationPreference {
        radius = Math.max(0, radius);
    }

    public boolean isDefault() {
        return equals(DEFAULT);
    }

    public int narrow(int serverRadius) {
        return radius <= 0 ? serverRadius : serverRadius <= 0 ? radius : Math.min(serverRadius, radius);
    }

    public NotificationPreference withCategory(boolean value) {
        return new NotificationPreference(value, radius, muteSound, chat, title, actionbar);
    }

    public NotificationPreference withRadius(int value) {
        return new NotificationPreference(enabled, value, muteSound, chat, title, actionbar);
    }

    public NotificationPreference withChannel(Channel channel, boolean value) {
        return switch (channel) {
            case CHAT -> new NotificationPreference(enabled, radius, muteSound, value, title, actionbar);
            case TITLE -> new NotificationPreference(enabled, radius, muteSound, chat, value, actionbar);
            case ACTIONBAR -> new NotificationPreference(enabled, radius, muteSound, chat, title, value);
            case SOUND -> new NotificationPreference(enabled, radius, !value, chat, title, actionbar);
        };
    }
}
