package com.cobbletracker.mod.net;

import com.cobbletracker.mod.CobbleTracker;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: a tracked Pokémon's spawn has definitively ended (it was caught, defeated or has
 * genuinely despawned). The client drops the beam for that entity immediately, rather than waiting for
 * the local keep-alive grace to lapse. This is what lets the beam appear/disappear by presence - it
 * lingers while the Pokémon is alive and near, and is torn down the moment the server says it is gone.
 *
 * @param entityId the {@link net.minecraft.world.entity.Entity#getUUID() entity} UUID whose beam to drop
 */
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
