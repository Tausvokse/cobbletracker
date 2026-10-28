package com.cobbletracker.mod.announce;

import static org.junit.jupiter.api.Assertions.*;
import com.cobbletracker.common.notify.NotificationPreference;
import com.cobbletracker.common.notify.NotificationPreference.Channel;
import com.cobbletracker.mod.config.ConfigManager;
import com.cobbletracker.mod.platform.PlatformServices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.lang.reflect.Field;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerPrefsTest {
    @TempDir Path temporaryConfig;
    private final UUID player = UUID.fromString("4e449ef0-f8ed-4b83-b82b-44e9317bd784");

    @BeforeEach
    void installTemporaryPlatform() throws Exception {
        PlatformServices.install(new PlatformServices.Bridge() {
            public Path configDirectory() { return temporaryConfig; }
            public boolean isModLoaded(String id) { return false; }
        });
        Files.createDirectories(ConfigManager.dir());
        PlayerPrefs.clear();
    }

    @AfterEach
    void clearPreferences() { PlayerPrefs.clear(); }

    @Test
    void legacyPreferencesKeepMasterRadiusAndSoundAndDefaultNewChannelsOn() throws Exception {
        Files.writeString(PlayerPrefs.file(), "{\"" + player + "\":{\"shinies\":{"
                + "\"enabled\":false,\"radius\":80,\"mute-sound\":true}}}");
        PlayerPrefs.load();
        NotificationPreference pref = PlayerPrefs.get(player, "shinies");
        assertEquals(new NotificationPreference(false, 80, true, true, true, true), pref);
        PlayerPrefs.save();
        PlayerPrefs.clear();
        PlayerPrefs.load();
        assertEquals(pref, PlayerPrefs.get(player, "shinies"));
    }

    @Test
    void personalTextChannelsRoundTripIndependentlyWithoutChangingMasterOrOtherCategory() {
        NotificationPreference textMuted = NotificationPreference.DEFAULT
                .withChannel(Channel.CHAT, false).withChannel(Channel.TITLE, false)
                .withChannel(Channel.ACTIONBAR, false);
        PlayerPrefs.set(player, "shinies", textMuted);
        PlayerPrefs.set(player, "legendaries", new NotificationPreference(false, 50, true));
        PlayerPrefs.save();
        PlayerPrefs.clear();
        PlayerPrefs.load();
        assertEquals(textMuted, PlayerPrefs.get(player, "shinies"));
        assertTrue(PlayerPrefs.get(player, "shinies").enabled());
        assertFalse(PlayerPrefs.get(player, "shinies").muteSound());
        assertFalse(PlayerPrefs.get(player, "legendaries").enabled());
        assertEquals(50, PlayerPrefs.get(player, "legendaries").radius());
    }

    @Test
    void returningToDefaultRemovesPersistedPlayerEntry() throws Exception {
        PlayerPrefs.set(player, "shinies", NotificationPreference.DEFAULT.withChannel(Channel.TITLE, false));
        PlayerPrefs.save();
        PlayerPrefs.set(player, "shinies", NotificationPreference.DEFAULT);
        PlayerPrefs.save();
        assertEquals("{}", Files.readString(PlayerPrefs.file()).trim());
    }

    @Test
    void corruptCategoryDoesNotDiscardOtherPlayersAndOriginalIsBackedUp() throws Exception {
        UUID other = UUID.randomUUID();
        String original = "{\"" + player + "\":{\"shinies\":{\"chat\":false},"
                + "\"bad\":{\"radius\":\"not-a-number\"},\"legendaries\":{\"title\":false}},"
                + "\"" + other + "\":{\"shinies\":{\"actionbar\":false}}}";
        Files.writeString(PlayerPrefs.file(), original);
        PlayerPrefs.load();

        assertFalse(PlayerPrefs.get(player, "shinies").chat());
        assertFalse(PlayerPrefs.get(player, "legendaries").title());
        assertFalse(PlayerPrefs.get(other, "shinies").actionbar());
        PlayerPrefs.save();
        try (var files = Files.list(ConfigManager.dir())) {
            Path backup = files.filter(path -> path.getFileName().toString().startsWith("prefs.json.damaged-"))
                    .findFirst().orElseThrow();
            assertEquals(original, Files.readString(backup));
        }
    }

    @Test
    void malformedRootIsNeverOverwrittenByShutdownSave() throws Exception {
        Files.writeString(PlayerPrefs.file(), "[]");
        PlayerPrefs.load();
        PlayerPrefs.set(player, "shinies", NotificationPreference.DEFAULT.withChannel(Channel.CHAT, false));
        PlayerPrefs.save();
        assertEquals("[]", Files.readString(PlayerPrefs.file()));
    }

    @Test
    void queuedSaveKeepsItsOriginalDestinationAndCannotReplaceFinalSnapshot() throws Exception {
        Field field = PlayerPrefs.class.getDeclaredField("WRITE_LOCK");
        field.setAccessible(true);
        Path originalFile = PlayerPrefs.file();
        NotificationPreference muted = NotificationPreference.DEFAULT.withChannel(Channel.CHAT, false);
        synchronized (field.get(null)) {
            PlayerPrefs.set(player, "shinies", muted);
            PlayerPrefs.saveIfDirty();
            Path nextConfig = temporaryConfig.resolve("next-world");
            PlatformServices.install(new PlatformServices.Bridge() {
                public Path configDirectory() { return nextConfig; }
                public boolean isModLoaded(String id) { return false; }
            });
        }
        ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS);
        assertTrue(Files.exists(originalFile));
        assertFalse(Files.exists(PlayerPrefs.file()));

        synchronized (field.get(null)) {
            PlayerPrefs.set(player, "shinies", muted);
            PlayerPrefs.saveIfDirty();
            PlayerPrefs.set(player, "shinies", NotificationPreference.DEFAULT.withChannel(Channel.TITLE, false));
            PlayerPrefs.save();
        }
        ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS);
        PlayerPrefs.load();
        assertTrue(PlayerPrefs.get(player, "shinies").chat());
        assertFalse(PlayerPrefs.get(player, "shinies").title());
    }
    @Test
    void pendingSaveCannotReplaceUnreadablePreferencesLoadedAfterItWasQueued() throws Exception {
        Field lock = PlayerPrefs.class.getDeclaredField("WRITE_LOCK");
        lock.setAccessible(true);
        synchronized (lock.get(null)) {
            PlayerPrefs.set(player, "shinies", NotificationPreference.DEFAULT.withChannel(Channel.CHAT, false));
            PlayerPrefs.saveIfDirty();
            Files.writeString(PlayerPrefs.file(), "[]");
            PlayerPrefs.load();
        }
        assertTrue(ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS));
        assertEquals("[]", Files.readString(PlayerPrefs.file()));
    }

    @Test
    void pendingSaveCannotRollBackSuccessfullyReloadedPreferences() throws Exception {
        Field lock = PlayerPrefs.class.getDeclaredField("WRITE_LOCK");
        lock.setAccessible(true);
        synchronized (lock.get(null)) {
            PlayerPrefs.set(player, "shinies", NotificationPreference.DEFAULT.withChannel(Channel.CHAT, false));
            PlayerPrefs.saveIfDirty();
            Files.writeString(PlayerPrefs.file(), "{}");
            PlayerPrefs.load();
        }
        assertTrue(ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS));
        assertEquals("{}", Files.readString(PlayerPrefs.file()));
    }

}
