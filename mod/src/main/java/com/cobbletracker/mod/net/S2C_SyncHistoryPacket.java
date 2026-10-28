package com.cobbletracker.mod.net;

import com.cobbletracker.mod.CobbleTracker;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2C_SyncHistoryPacket(List<Category> categories, List<Entry> entries, List<Stat> stats,
        String focusCategory)
        implements CustomPacketPayload {

    public static final Type<S2C_SyncHistoryPacket> TYPE = new Type<>(CobbleTracker.id("s2c_sync_history_v2"));

    public record Category(String id, String display, int color,
            boolean notifyEnabled, boolean notifyMuted, int notifyRadius, boolean serverEnabled, int textChannels) {
        public static final StreamCodec<FriendlyByteBuf, Category> STREAM_CODEC = StreamCodec.of(
                (buf, c) -> {
                    buf.writeUtf(c.id());
                    buf.writeUtf(c.display());
                    buf.writeInt(c.color());
                    buf.writeBoolean(c.notifyEnabled());
                    buf.writeBoolean(c.notifyMuted());
                    buf.writeVarInt(c.notifyRadius());
                    buf.writeBoolean(c.serverEnabled());
                    buf.writeByte(c.textChannels());
                },
                buf -> new Category(buf.readUtf(), buf.readUtf(), buf.readInt(),
                        buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readUnsignedByte()));

        public Category withPrefs(boolean enabled, boolean muted, int radius) {
            return new Category(id, display, color, enabled, muted, Math.max(0, radius), serverEnabled, textChannels);
        }

        public boolean channel(int index) {
            return (textChannels & (1 << index)) != 0;
        }

        public Category withChannel(int index, boolean enabled) {
            int flags = enabled ? textChannels | (1 << index) : textChannels & ~(1 << index);
            return new Category(id, display, color, notifyEnabled, notifyMuted, notifyRadius, serverEnabled, flags);
        }
    }

    public record Entry(String category, String species, String speciesId, long spawnTime, String world,
            int x, int y, int z, String biome, boolean shiny, boolean caught, String catcherName,
            int level, String gender, String nature, String ability, String ivs, String evs) {
        public static final StreamCodec<FriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, e) -> {
                    buf.writeUtf(e.category());
                    buf.writeUtf(e.species());
                    buf.writeUtf(e.speciesId());
                    buf.writeLong(e.spawnTime());
                    buf.writeUtf(e.world());
                    buf.writeVarInt(e.x());
                    buf.writeVarInt(e.y());
                    buf.writeVarInt(e.z());
                    buf.writeUtf(e.biome());
                    buf.writeBoolean(e.shiny());
                    buf.writeBoolean(e.caught());
                    buf.writeUtf(e.catcherName() == null ? "" : e.catcherName());
                    buf.writeVarInt(Math.max(0, e.level()));
                    buf.writeUtf(e.gender());
                    buf.writeUtf(e.nature());
                    buf.writeUtf(e.ability());
                    buf.writeUtf(e.ivs());
                    buf.writeUtf(e.evs());
                },
                buf -> new Entry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readLong(), buf.readUtf(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
                        buf.readBoolean(), buf.readBoolean(), buf.readUtf(),
                        buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf()));

        public Entry {
            gender = gender == null ? "" : gender;
            nature = nature == null ? "" : nature;
            ability = ability == null ? "" : ability;
            ivs = ivs == null ? "" : ivs;
            evs = evs == null ? "" : evs;
        }
    }

    public record Stat(String player, int caught) {
        public static final StreamCodec<FriendlyByteBuf, Stat> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeUtf(s.player());
                    buf.writeVarInt(s.caught());
                },
                buf -> new Stat(buf.readUtf(), buf.readVarInt()));
    }

    private static final int MAX_CATEGORIES = 128;
    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_STATS = 64;

    private static final StreamCodec<FriendlyByteBuf, String> STRING = StreamCodec.of(
            FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf);

    public static final StreamCodec<FriendlyByteBuf, S2C_SyncHistoryPacket> STREAM_CODEC = StreamCodec.composite(
            Category.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CATEGORIES)), S2C_SyncHistoryPacket::categories,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), S2C_SyncHistoryPacket::entries,
            Stat.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STATS)), S2C_SyncHistoryPacket::stats,
            STRING, S2C_SyncHistoryPacket::focusCategory,
            S2C_SyncHistoryPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
