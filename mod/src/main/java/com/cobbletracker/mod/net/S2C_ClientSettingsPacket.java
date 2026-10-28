package com.cobbletracker.mod.net;

import com.cobbletracker.mod.CobbleTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2C_ClientSettingsPacket(boolean huntEnabled, boolean resetBeams, boolean minimapEnabled,
        boolean xaero, boolean voxelmap, boolean journeymap, boolean useBeamColor) implements CustomPacketPayload {

    public S2C_ClientSettingsPacket(boolean huntEnabled, boolean resetBeams,
            com.cobbletracker.mod.config.MinimapSettings minimap) {
        this(huntEnabled, resetBeams, minimap.enabled(), minimap.xaero(), minimap.voxelmap(),
                minimap.journeymap(), minimap.useBeamColor());
    }

    public static final Type<S2C_ClientSettingsPacket> TYPE =
            new Type<>(CobbleTracker.id("s2c_client_settings_v3"));

    public static final StreamCodec<FriendlyByteBuf, S2C_ClientSettingsPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.huntEnabled());
                buf.writeBoolean(p.resetBeams());
                buf.writeBoolean(p.minimapEnabled());
                buf.writeBoolean(p.xaero());
                buf.writeBoolean(p.voxelmap());
                buf.writeBoolean(p.journeymap());
                buf.writeBoolean(p.useBeamColor());
            },
            buf -> new S2C_ClientSettingsPacket(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
