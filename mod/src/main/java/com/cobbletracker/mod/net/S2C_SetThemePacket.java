package com.cobbletracker.mod.net;

import com.cobbletracker.mod.CobbleTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: apply and persist a GUI theme by name (from {@code /ct theme <name>}). */
public record S2C_SetThemePacket(String theme) implements CustomPacketPayload {

    public static final Type<S2C_SetThemePacket> TYPE = new Type<>(CobbleTracker.id("s2c_set_theme"));

    public static final StreamCodec<FriendlyByteBuf, S2C_SetThemePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, S2C_SetThemePacket::theme,
            S2C_SetThemePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
