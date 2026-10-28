package com.cobbletracker.mod.net;

import com.cobbletracker.mod.CobbleTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2S_MinimapSupportPacket(
        boolean xaero,
        boolean voxelmap,
        boolean journeymap) implements CustomPacketPayload {

    public static final Type<C2S_MinimapSupportPacket> TYPE = new Type<>(CobbleTracker.id("c2s_minimap_support"));

    public static final StreamCodec<FriendlyByteBuf, C2S_MinimapSupportPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.xaero());
                buf.writeBoolean(p.voxelmap());
                buf.writeBoolean(p.journeymap());
            },
            buf -> new C2S_MinimapSupportPacket(buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

    public boolean any() {
        return xaero || voxelmap || journeymap;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
