package com.cobbletracker.mod.net;

import com.cobbletracker.mod.CobbleTracker;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2C_SpawnEndedPacket(UUID entityId) implements CustomPacketPayload {

    public static final Type<S2C_SpawnEndedPacket> TYPE = new Type<>(CobbleTracker.id("s2c_spawn_ended"));

    public static final StreamCodec<FriendlyByteBuf, S2C_SpawnEndedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUUID(p.entityId()),
            buf -> new S2C_SpawnEndedPacket(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
