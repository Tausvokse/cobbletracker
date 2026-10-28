package com.cobbletracker.mod.platform;

import java.nio.file.Path;
import java.util.Objects;

public final class PlatformServices {
    public interface Bridge {
        Path configDirectory();
        boolean isModLoaded(String id);
    }

    private static Bridge bridge;

    private PlatformServices() { }

    public static void install(Bridge value) {
        bridge = Objects.requireNonNull(value);
    }

    public static Path configDirectory() {
        return Objects.requireNonNull(bridge, "Platform services not initialized").configDirectory();
    }

    public static boolean isModLoaded(String id) {
        return bridge != null && bridge.isModLoaded(id);
    }
}
