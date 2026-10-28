package com.cobbletracker.mod.client;

import com.cobbletracker.mod.net.S2C_SyncHistoryPacket;
import java.util.List;

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

    public static void clear() {
        lastSync = EMPTY;
    }
}
