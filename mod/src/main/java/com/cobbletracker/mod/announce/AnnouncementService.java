package com.cobbletracker.mod.announce;

import com.cobbletracker.common.tracker.SpawnRecord;
import com.cobbletracker.common.util.Obfuscation;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.config.GeneralSettings;
import com.cobbletracker.mod.config.NotificationConfig;
import com.cobbletracker.mod.text.Msg;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Broadcasts a qualifying spawn to the players it should reach. By default that's just a chat line and a
 * sound; the on-screen title only shows if the server turned it on. Text is MiniMessage (legacy &-codes
 * work too). {@code %waypoint%} in the chat line becomes a clickable coordinate that says "Create
 * Waypoint" on hover and only drops the marker when the player clicks it. Coordinates respect
 * hide-exact-position (chunk centre instead of the exact block).
 */
public final class AnnouncementService {

    private AnnouncementService() {
    }

    public static void announce(GeneralSettings general, NotificationConfig note,
            SpawnRecord record, List<ServerPlayer> recipients) {
        if (note == null || !note.enabled() || recipients.isEmpty()) {
            return;
        }
        MutableComponent title;
        MutableComponent subtitle;
        MutableComponent chat;
        SoundEvent sound;
        try {
            int x = Obfuscation.maybeObfuscate(record.x(), !general.hideExactPosition());
            int y = record.y();
            int z = Obfuscation.maybeObfuscate(record.z(), !general.hideExactPosition());

            boolean titles = general.showTitle();
            title = titles && notBlank(note.title()) ? Msg.parseAny(sub(note.title(), record, x, y, z)) : null;
            subtitle = titles && notBlank(note.subtitle()) ? Msg.parseAny(sub(note.subtitle(), record, x, y, z)) : null;
            chat = buildChat(note, record, x, y, z);
            sound = resolveSound(note.sound());
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Couldn't build the announcement for {} - check the templates in "
                    + "announcements.yml", record.species(), t);
            return;
        }
        // One player failing (kicked mid-send, broken connection) must not cost everyone else their line.
        for (ServerPlayer player : recipients) {
            try {
                if (title != null || subtitle != null) {
                    player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
                    player.connection.send(new ClientboundSetTitleTextPacket(
                            title != null ? title : Component.empty()));
                    if (subtitle != null) {
                        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                    }
                }
                if (chat != null) {
                    player.sendSystemMessage(chat);
                }
                if (sound != null) {
                    player.playNotifySound(sound, SoundSource.MASTER, note.soundVolume(), note.soundPitch());
                }
            } catch (Throwable t) {
                CobbleTracker.LOGGER.debug("Announcement to {} failed", player.getGameProfile().getName(), t);
            }
        }
    }

    /** Builds the prefixed chat line, turning a {@code %waypoint%} token into a clickable coordinate. */
    private static MutableComponent buildChat(NotificationConfig note, SpawnRecord record, int x, int y, int z) {
        String template = note.chat();
        if (template == null || template.isBlank()) {
            return null;
        }
        MutableComponent line = Msg.parse(Msg.prefix());
        int idx = template.indexOf("%waypoint%");
        if (idx < 0) {
            return line.append(Msg.parseAny(sub(template, record, x, y, z)));
        }
        String before = template.substring(0, idx);
        String after = template.substring(idx + "%waypoint%".length());
        line.append(Msg.parseAny(sub(before, record, x, y, z)));
        line.append(waypoint(note, record, x, y, z));
        return line.append(Msg.parseAny(sub(after, record, x, y, z)));
    }

    /** The clickable coordinate: aqua + underlined, "Create Waypoint" on hover, drops the marker on click. */
    private static MutableComponent waypoint(NotificationConfig note, SpawnRecord record, int x, int y, int z) {
        String coords = x + ", " + y + ", " + z;
        if (!note.waypoint()) {
            return Component.literal(coords).withStyle(ChatFormatting.GRAY);
        }
        return Component.literal(coords).withStyle(Style.EMPTY
                .withColor(TextColor.fromRgb(0x55FFFF))
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/cobbletracker waypoint " + record.id()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Create Waypoint"))));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * The players a spawn should reach: everyone (when play-to-all and no radius) or only players within
     * broadcast-radius in the spawn's dimension. Empty when the notification is disabled, which also
     * suppresses the beam for that category.
     *
     * <p>Always a snapshot, never the server's live player list - the caller sends packets while walking
     * it, and a send that drops a connection would otherwise mutate the list mid-iteration.
     */
    public static List<ServerPlayer> recipients(MinecraftServer server, NotificationConfig note, SpawnRecord record) {
        return recipients(server, note, record.world(), record.x(), record.y(), record.z());
    }

    /** As {@link #recipients(MinecraftServer, NotificationConfig, SpawnRecord)}, from a bare position. */
    public static List<ServerPlayer> recipients(MinecraftServer server, NotificationConfig note,
            String world, int x, int y, int z) {
        if (note == null || !note.enabled()) {
            return List.of();
        }
        List<ServerPlayer> all = onlinePlayers(server);
        int radius = note.broadcastRadius();
        if (radius <= 0 && note.playToAll()) {
            return all;
        }
        int effectiveRadius = radius > 0 ? radius : 128;
        List<ServerPlayer> out = new ArrayList<>();
        BlockPos spawn = new BlockPos(x, y, z);
        for (ServerPlayer player : all) {
            if (!player.level().dimension().location().toString().equals(world)) {
                continue;
            }
            if (player.blockPosition().distSqr(spawn) <= (long) effectiveRadius * effectiveRadius) {
                out.add(player);
            }
        }
        return out;
    }

    /** A stable snapshot of who is online, safe to iterate while sending. */
    public static List<ServerPlayer> onlinePlayers(MinecraftServer server) {
        return List.copyOf(server.getPlayerList().getPlayers());
    }

    /**
     * Reports what became of a tracked spawn - caught by whom, defeated by whom, or despawned.
     * {@code phrase} is the full predicate, e.g. "was caught by Steve!". It goes to the same players the
     * original announcement did, so a radius-limited category doesn't suddenly tell the whole server.
     */
    public static void lifecycle(List<ServerPlayer> recipients, int color, String species, String phrase) {
        if (recipients.isEmpty()) {
            return;
        }
        try {
            MutableComponent sp = Component.literal(species).withStyle(Style.EMPTY
                    .withColor(TextColor.fromRgb(color)).withBold(true));
            MutableComponent line = Msg.parse(Msg.prefix()).append(sp)
                    .append(Component.literal(" " + phrase).withStyle(ChatFormatting.GRAY));
            for (ServerPlayer player : recipients) {
                try {
                    player.sendSystemMessage(line);
                } catch (Throwable t) {
                    CobbleTracker.LOGGER.debug("Lifecycle line to {} failed",
                            player.getGameProfile().getName(), t);
                }
            }
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Failed to broadcast lifecycle for {}", species, t);
        }
    }

    private static SoundEvent resolveSound(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            ResourceLocation key = ResourceLocation.parse(id);
            SoundEvent registered = BuiltInRegistries.SOUND_EVENT.get(key);
            return registered != null ? registered : SoundEvent.createVariableRangeEvent(key);
        } catch (Throwable t) {
            CobbleTracker.LOGGER.warn("Unusable sound id '{}' in announcements.yml - playing nothing", id);
            return null;
        }
    }

    private static String sub(String template, SpawnRecord record, int x, int y, int z) {
        return template
                .replace("%species%", record.species())
                .replace("%biome%", pretty(record.biome()))
                .replace("%world%", pretty(record.world()))
                .replace("%x%", Integer.toString(x))
                .replace("%y%", Integer.toString(y))
                .replace("%z%", Integer.toString(z))
                // Plain-text fallback where a clickable waypoint can't go (titles, waypoint disabled).
                .replace("%waypoint%", x + ", " + y + ", " + z);
    }

    private static String pretty(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        String s = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        s = s.replace('_', ' ');
        return s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
