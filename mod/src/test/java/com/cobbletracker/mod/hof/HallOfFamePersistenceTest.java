package com.cobbletracker.mod.hof;

import static org.junit.jupiter.api.Assertions.*;
import com.cobbletracker.mod.config.ConfigManager;
import com.cobbletracker.mod.platform.PlatformServices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HallOfFamePersistenceTest {
    @TempDir Path temporaryConfig;

    @Test
    void pendingSaveCannotReplaceUnreadableReloadedHallOfFame() throws Exception {
        PlatformServices.install(new PlatformServices.Bridge() {
            public Path configDirectory() { return temporaryConfig; }
            public boolean isModLoaded(String id) { return false; }
        });
        Files.createDirectories(ConfigManager.dir());
        HallOfFameModule.load();
        var lock = HallOfFameModule.class.getDeclaredField("WRITE_LOCK");
        lock.setAccessible(true);
        var saveAsync = HallOfFameModule.class.getDeclaredMethod("saveAsync");
        saveAsync.setAccessible(true);
        synchronized (lock.get(null)) {
            saveAsync.invoke(null);
            Files.writeString(HallOfFameModule.file(), "[]");
            HallOfFameModule.load();
        }
        assertTrue(ForkJoinPool.commonPool().awaitQuiescence(5, TimeUnit.SECONDS));
        assertEquals("[]", Files.readString(HallOfFameModule.file()));
    }
}
