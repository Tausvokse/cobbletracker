package com.cobbletracker.mod.net;

import com.cobbletracker.mod.CobbleTracker;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a tracked Pokémon spawned in this player's chunks. Sent once, only to the players the
 * announcement reaches. The client looks up the live entity by {@code entityId} each frame and raises a
 * beacon beam over it, so the beam follows the Pokémon as it moves; {@code x/y/z} are the fallback anchor.
 * The beam settings ride along so no separate config sync is needed. Coordinates are already
 * chunk-rounded server-side when hide-exact-position is on.
 *
 * @param entityId          the entity UUID to beam
 * @param color             ARGB tier accent
 * @param beamRadius        blocks; 0 = auto (player render distance)
 * @param beamDurationTicks keep-alive grace in ticks
 * @param beamHeight        beam column height in blocks
 */
public record S2C_TrackedSpawnPacket(
        UUID entityId,
        String category,
        String species,
        int color,
        int x,
        int y,
        int z,
        String dimension,
        boolean beamEnabled,
        int beamRadius,
        int beamDurationTicks,
        int beamHeight) implements CustomPacketPayload {

    public static final Type<S2C_TrackedSpawnPacket> TYPE = new Type<>(CobbleTracker.id("s2c_tracked_spawn"));

    public static final StreamCodec<FriendlyByteBuf, S2C_TrackedSpawnPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.entityId());
                buf.writeUtf(p.category());
                buf.writeUtf(p.species());
                buf.writeInt(p.color());
                buf.writeVarInt(p.x());
                buf.writeVarInt(p.y());
                buf.writeVarInt(p.z());
                buf.writeUtf(p.dimension());
                buf.writeBoolean(p.beamEnabled());
                buf.writeVarInt(p.beamRadius());
                buf.writeVarInt(p.beamDurationTicks());
                buf.writeVarInt(p.beamHeight());
            },
            buf -> new S2C_TrackedSpawnPacket(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
                    buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
