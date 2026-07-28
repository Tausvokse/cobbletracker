package com.cobbletracker.mod.net;

import com.cobbletracker.mod.CobbleTracker;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: opens the operator settings screen ({@code /ct admin}) with the current server-side
 * settings as editable rows. Edits go back via {@link C2S_AdminActionPacket}; the server re-checks
 * permission before applying.
 */
public record S2C_OpenAdminPacket(List<Setting> settings) implements CustomPacketPayload {

    public static final Type<S2C_OpenAdminPacket> TYPE = new Type<>(CobbleTracker.id("s2c_open_admin"));

    /** Wire limit on the row count - the screen has a fixed set, so anything larger is nonsense. */
    private static final int MAX_SETTINGS = 64;

    /**
     * One editable setting.
     *
     * @param type one of {@code bool}, {@code int} - how the client renders/edits it
     */
    public record Setting(String key, String label, String value, String type) {
        public static final StreamCodec<FriendlyByteBuf, Setting> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeUtf(s.key());
                    buf.writeUtf(s.label());
                    buf.writeUtf(s.value());
                    buf.writeUtf(s.type());
                },
                buf -> new Setting(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()));
    }

    public static final StreamCodec<FriendlyByteBuf, S2C_OpenAdminPacket> STREAM_CODEC = StreamCodec.composite(
            Setting.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SETTINGS)), S2C_OpenAdminPacket::settings,
            S2C_OpenAdminPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
