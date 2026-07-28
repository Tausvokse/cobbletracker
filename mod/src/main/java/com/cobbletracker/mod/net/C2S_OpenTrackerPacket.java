package com.cobbletracker.mod.net;

import com.cobbletracker.mod.CobbleTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: "send me my spawn history". The server replies with {@link S2C_SyncHistoryPacket}.
 * Sent when the player runs {@code /ct} (or {@code /ct}'s GUI refreshes). No fields - a unit payload.
 */
public record C2S_OpenTrackerPacket() implements CustomPacketPayload {

    public static final Type<C2S_OpenTrackerPacket> TYPE = new Type<>(CobbleTracker.id("c2s_open_tracker"));

    public static final StreamCodec<FriendlyByteBuf, C2S_OpenTrackerPacket> STREAM_CODEC =
            StreamCodec.unit(new C2S_OpenTrackerPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
