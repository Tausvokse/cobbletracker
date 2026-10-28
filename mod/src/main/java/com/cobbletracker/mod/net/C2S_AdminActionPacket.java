package com.cobbletracker.mod.net;

import com.cobbletracker.mod.CobbleTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2S_AdminActionPacket(String key, String value) implements CustomPacketPayload {

    public static final Type<C2S_AdminActionPacket> TYPE = new Type<>(CobbleTracker.id("c2s_admin_action"));

    public static final StreamCodec<FriendlyByteBuf, C2S_AdminActionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, C2S_AdminActionPacket::key,
            ByteBufCodecs.STRING_UTF8, C2S_AdminActionPacket::value,
            C2S_AdminActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
