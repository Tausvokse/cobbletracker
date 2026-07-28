package com.cobbletracker.mod.net;

import com.cobbletracker.mod.CobbleTracker;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the spawn-history payload the {@code TrackerScreen} renders. Carries the recorded
 * spawns, the category definitions (so the sidebar tabs + colours are entirely data-driven - add a
 * category in config and it appears here), and the capture leaderboard for the stats tab. The client
 * caches it and browses the cache, so opening a tab never goes back to the server.
 *
 * <p>Every list is length-capped on the wire. Decoding runs before anything has vouched for the sender,
 * and an unbounded count field is all it takes to have a client allocate until it dies.
 */
public record S2C_SyncHistoryPacket(List<Category> categories, List<Entry> entries, List<Stat> stats,
        String focusCategory)
        implements CustomPacketPayload {

    public static final Type<S2C_SyncHistoryPacket> TYPE = new Type<>(CobbleTracker.id("s2c_sync_history"));

    /** A category tab: id, display label, ARGB accent colour. */
    public record Category(String id, String display, int color) {
        public static final StreamCodec<FriendlyByteBuf, Category> STREAM_CODEC = StreamCodec.of(
                (buf, c) -> {
                    buf.writeUtf(c.id());
                    buf.writeUtf(c.display());
                    buf.writeInt(c.color());
                },
                buf -> new Category(buf.readUtf(), buf.readUtf(), buf.readInt()));
    }

    /** One recorded spawn. Coordinates are exact for OP-visible history; the HUD path obfuscates separately. */
    public record Entry(String category, String species, String speciesId, long spawnTime, String world,
            int x, int y, int z, String biome, boolean shiny, boolean caught, String catcherName) {
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
                },
                buf -> new Entry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readLong(), buf.readUtf(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
                        buf.readBoolean(), buf.readBoolean(), buf.readUtf()));
    }

    /** One row of the capture leaderboard (stats tab). */
    public record Stat(String player, int caught) {
        public static final StreamCodec<FriendlyByteBuf, Stat> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeUtf(s.player());
                    buf.writeVarInt(s.caught());
                },
                buf -> new Stat(buf.readUtf(), buf.readVarInt()));
    }

    /** Wire limits, comfortably above anything a sane config produces. */
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
