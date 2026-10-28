package com.cobbletracker.common.notify;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NotificationPreferenceTest {
    @Test
    void textCanBeMutedWithoutDisablingCategoryOrSound() {
        var pref = NotificationPreference.DEFAULT
                .withChannel(NotificationPreference.Channel.CHAT, false)
                .withChannel(NotificationPreference.Channel.TITLE, false)
                .withChannel(NotificationPreference.Channel.ACTIONBAR, false);
        assertTrue(pref.enabled());
        assertFalse(pref.muteSound());
        assertFalse(pref.chat());
        assertFalse(pref.title());
        assertFalse(pref.actionbar());
    }

    @Test
    void categoryAndRadiusChangesKeepChannelChoices() {
        var pref = NotificationPreference.DEFAULT.withChannel(NotificationPreference.Channel.TITLE, false)
                .withCategory(false).withRadius(64).withChannel(NotificationPreference.Channel.SOUND, false);
        assertFalse(pref.enabled());
        assertFalse(pref.title());
        assertTrue(pref.muteSound());
        assertEquals(64, pref.radius());
        assertEquals(32, pref.narrow(32));
        assertEquals(64, pref.narrow(0));
    }

    @Test
    void legacyPreferencesEnableNewChannels() {
        var pref = new NotificationPreference(false, 120, true);
        assertTrue(pref.chat());
        assertTrue(pref.title());
        assertTrue(pref.actionbar());
        assertFalse(pref.isDefault());
        assertTrue(new NotificationPreference(true, 0, false).isDefault());
    }
}
