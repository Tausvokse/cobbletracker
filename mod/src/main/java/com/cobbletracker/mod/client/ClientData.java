package com.cobbletracker.mod.client;

import com.cobbletracker.mod.net.S2C_SyncHistoryPacket;
import java.util.List;

/**
 * Client cache of the last {@link S2C_SyncHistoryPacket}. The tracker GUI opens straight from this and
 * does its tab/scroll/sort work on the cache, so browsing never goes back to the server.
 */
public final class ClientData {

    private ClientData() {
    }

    private static final S2C_SyncHistoryPacket EMPTY =
            new S2C_SyncHistoryPacket(List.of(), List.of(), List.of(), "");

    private static volatile S2C_SyncHistoryPacket lastSync = EMPTY;

    public static void setSync(S2C_SyncHistoryPacket sync) {
        lastSync = sync == null ? EMPTY : sync;
    }

    public static S2C_SyncHistoryPacket sync() {
        return lastSync;
    }

    /** Drops the cached history, so one server's spawns are never shown while on another. */
    public static void clear() {
        lastSync = EMPTY;
    }
}
