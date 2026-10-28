package com.cobbletracker.mod.client.hud;

import static org.junit.jupiter.api.Assertions.*;
import com.cobbletracker.mod.config.ConfigManager;
import com.cobbletracker.mod.platform.PlatformServices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HuntStoreTest {
    @TempDir Path temporaryConfig;

    @BeforeEach
    void installTemporaryPlatform() throws Exception {
        PlatformServices.install(new PlatformServices.Bridge() {
            public Path configDirectory() { return temporaryConfig; }
            public boolean isModLoaded(String id) { return false; }
        });
        Files.createDirectories(ConfigManager.dir());
        HuntStore.load();
    }

    @Test
    void malformedListIsNeverOverwrittenByLaterSelection() throws Exception {
        for (String damaged : new String[] { "{unfinished", "[]", "{}", "{\"hunted\":false}" }) {
            Files.writeString(HuntStore.file(), damaged);
            assertTrue(HuntStore.load().isEmpty());
            HuntStore.save(Set.of("mew"));
            ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS);
            assertEquals(damaged, Files.readString(HuntStore.file()));
        }
    }

    @Test
    void damagedEntriesKeepValidSelectionsAndOriginalBeforeRepair() throws Exception {
        String damaged = "{\"hunted\":[\"Pikachu\",null,{},42,true,\"\",\" mew \"]}";
        Files.writeString(HuntStore.file(), damaged);
        assertEquals(Set.of("pikachu", "mew"), HuntStore.load());
        try (var files = Files.list(ConfigManager.dir())) {
            Path backup = files.filter(path -> path.getFileName().toString().startsWith("hunt.json.damaged-"))
                    .findFirst().orElseThrow();
            assertEquals(damaged, Files.readString(backup));
        }
        HuntStore.save(Set.of("latias"));
        ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS);
        assertEquals(Set.of("latias"), HuntStore.load());
    }
    @Test
    void pendingSaveCannotRollBackSuccessfullyReloadedSelections() throws Exception {
        var lock = HuntStore.class.getDeclaredField("WRITE_LOCK");
        lock.setAccessible(true);
        String current = "{\"hunted\":[\"latias\"]}";
        synchronized (lock.get(null)) {
            HuntStore.save(Set.of("mew"));
            Files.writeString(HuntStore.file(), current);
            assertEquals(Set.of("latias"), HuntStore.load());
        }
        assertTrue(ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS));
        assertEquals(current, Files.readString(HuntStore.file()));
    }

}
