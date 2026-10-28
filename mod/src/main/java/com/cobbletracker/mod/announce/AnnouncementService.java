package com.cobbletracker.mod.announce;

import com.cobbletracker.common.tracker.SpawnRecord;
import com.cobbletracker.common.util.ChatLocationTemplate;
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

public final class AnnouncementService {

    private AnnouncementService() {
    }

    // Keep recipient failures isolated so one disconnected client cannot interrupt delivery.
    public static void announce(GeneralSettings general, NotificationConfig note,
            SpawnRecord record, List<ServerPlayer> recipients) {
        if (note == null || !note.enabled() || recipients.isEmpty()) {
            return;
        }
        MutableComponent title;
        MutableComponent subtitle;
        MutableComponent chat;
        MutableComponent actionbar;
        SoundEvent sound;
        try {
            int x = Obfuscation.maybeObfuscate(record.x(), !general.hideExactPosition());
            int y = record.y();
            int z = Obfuscation.maybeObfuscate(record.z(), !general.hideExactPosition());

            boolean titles = general.showTitle();
            title = titles && notBlank(note.title()) ? Msg.parseAny(sub(note.title(), record, x, y, z)) : null;
            subtitle = titles && notBlank(note.subtitle()) ? Msg.parseAny(sub(note.subtitle(), record, x, y, z)) : null;
            chat = buildChat(general, note, record, x, y, z);
            actionbar = notBlank(note.actionbar()) ? Msg.parseAny(sub(note.actionbar(), record, x, y, z)) : null;
            sound = resolveSound(note.sound());
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Couldn't build the announcement for {} - check the templates in "
                    + "announcements.yml", record.species(), t);
            return;
        }
        for (ServerPlayer player : recipients) {
            try {
                var pref = PlayerPrefs.get(player.getUUID(), record.category());
                if (pref.title() && (title != null || subtitle != null)) {
                    player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
                    player.connection.send(new ClientboundSetSubtitleTextPacket(
                            subtitle != null ? subtitle : Component.empty()));
                    player.connection.send(new ClientboundSetTitleTextPacket(
                            title != null ? title : Component.empty()));
                }
                if (pref.chat() && chat != null) {
                    player.sendSystemMessage(chat);
                }
                if (pref.actionbar() && actionbar != null) {
                    player.displayClientMessage(actionbar, true);
                }
                if (sound != null && note.soundVolume() > 0f
                        && !pref.muteSound()) {
                    player.playNotifySound(sound, SoundSource.MASTER, note.soundVolume(), note.soundPitch());
                }
            } catch (Throwable t) {
                CobbleTracker.LOGGER.debug("Announcement to {} failed", player.getGameProfile().getName(), t);
            }
        }
    }

    private static MutableComponent buildChat(GeneralSettings general, NotificationConfig note,
            SpawnRecord record, int x, int y, int z) {
        String template = ChatLocationTemplate.prepare(note.chat(), general.showSpawnLocationInChat());
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

    private static MutableComponent waypoint(NotificationConfig note, SpawnRecord record, int x, int y, int z) {
        String coords = x + ", " + y + ", " + z;
        if (!note.waypoint()) {
            return Component.literal(coords).withStyle(ChatFormatting.GRAY);
        }
        return Component.literal(coords).withStyle(Style.EMPTY
                .withColor(TextColor.fromRgb(0x55FFFF))
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, coords))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Copy coordinates"))));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public static List<ServerPlayer> recipients(MinecraftServer server, NotificationConfig note, SpawnRecord record) {
        return recipients(server, note, record.category(), record.world(), record.x(), record.y(), record.z());
    }

    public static List<ServerPlayer> recipients(MinecraftServer server, NotificationConfig note,
            String categoryId, String world, int x, int y, int z) {
        if (note == null || !note.enabled()) {
            return List.of();
        }
        List<ServerPlayer> out = new ArrayList<>();
        BlockPos spawn = new BlockPos(x, y, z);
        for (ServerPlayer player : onlinePlayers(server)) {
            if (reaches(note, categoryId, world, spawn, player)) {
                out.add(player);
            }
        }
        return out;
    }

    public static boolean reaches(NotificationConfig note, String categoryId, String world, BlockPos spawn,
            ServerPlayer player) {
        if (note == null || !note.enabled()) {
            return false;
        }
        var pref = PlayerPrefs.get(player.getUUID(), categoryId);
        if (!pref.enabled()) {
            return false;
        }
        int serverRadius = note.broadcastRadius();
        int radius = pref.narrow(serverRadius);
        if (serverRadius <= 0 && note.playToAll() && radius <= 0) {
            return true;
        }
        if (!player.level().dimension().location().toString().equals(world)) {
            return false;
        }
        int effectiveRadius = radius > 0 ? radius : 128;
        return player.blockPosition().distSqr(spawn) <= (long) effectiveRadius * effectiveRadius;
    }

    public static List<ServerPlayer> onlinePlayers(MinecraftServer server) {
        return List.copyOf(server.getPlayerList().getPlayers());
    }

    public static void lifecycle(List<ServerPlayer> recipients, String category, int color, String species, String phrase) {
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
                    if (PlayerPrefs.get(player.getUUID(), category).chat()) player.sendSystemMessage(line);
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
        String traits = formatTraits(record);
        String speciesText = buildSpeciesName(record, template);
        return template
                .replace("%traits%", traits)
                .replace("%species%", speciesText)
                .replace("%biome%", pretty(record.biome()))
                .replace("%world%", pretty(record.world()))
                .replace("%x%", Integer.toString(x))
                .replace("%y%", Integer.toString(y))
                .replace("%z%", Integer.toString(z))
                .replace("%waypoint%", x + ", " + y + ", " + z);
    }

    private static String formatTraits(SpawnRecord record) {
        List<String> parts = new ArrayList<>();
        java.util.Set<String> set = parseTraits(record);
        if (record.shiny() || set.contains("shiny")) {
            parts.add("<yellow>✨ Shiny</yellow>");
        }
        if (set.contains("boss")) {
            parts.add("<dark_red>Boss</dark_red>");
        }
        if (set.contains("legendary")) {
            parts.add("<gold>Legendary</gold>");
        }
        if (set.contains("mythic") || set.contains("mythical")) {
            parts.add("<light_purple>Mythic</light_purple>");
        }
        if (set.contains("ultra-beast") || set.contains("ultrabeast")) {
            parts.add("<light_purple>Ultra-Beast</light_purple>");
        }
        if (set.contains("ancient")) {
            parts.add("<gold>Ancient</gold>");
        }
        if (set.contains("starter")) {
            parts.add("<green>Starter</green>");
        }
        if (set.contains("alolan")) {
            parts.add("<aqua>Alolan</aqua>");
        }
        if (set.contains("hisuian")) {
            parts.add("<dark_purple>Hisuian</dark_purple>");
        }
        if (set.contains("galarian")) {
            parts.add("<color:#ec4899>Galarian</color>");
        }
        if (set.contains("paldean")) {
            parts.add("<gold>Paldean</gold>");
        }
        return String.join(" ", parts);
    }

    private static java.util.Set<String> parseTraits(SpawnRecord record) {
        java.util.Set<String> set = new java.util.HashSet<>();
        if (record.shiny()) {
            set.add("shiny");
        }
        if (record.traits() != null && !record.traits().isBlank()) {
            for (String t : record.traits().split(",")) {
                String clean = t.trim().toLowerCase(java.util.Locale.ROOT);
                if (!clean.isEmpty()) {
                    set.add(clean);
                }
            }
        }
        return set;
    }

    private static String buildSpeciesName(SpawnRecord record, String template) {
        if (template.contains("%traits%")) {
            return record.species();
        }
        String lowerTemplate = template.toLowerCase(java.util.Locale.ROOT);
        java.util.Set<String> traits = parseTraits(record);
        List<String> prefixes = new ArrayList<>();
        if (traits.contains("boss") && !lowerTemplate.contains("boss")) {
            prefixes.add("Boss");
        }
        if ((record.shiny() || traits.contains("shiny")) && !lowerTemplate.contains("shiny")) {
            prefixes.add("✨ Shiny");
        }
        if (traits.contains("legendary") && !lowerTemplate.contains("legend")) {
            prefixes.add("Legendary");
        }
        if ((traits.contains("mythic") || traits.contains("mythical")) && !lowerTemplate.contains("mythic")) {
            prefixes.add("Mythic");
        }
        if ((traits.contains("ultra-beast") || traits.contains("ultrabeast")) && !lowerTemplate.contains("ultra")) {
            prefixes.add("Ultra-Beast");
        }
        if (traits.contains("ancient") && !lowerTemplate.contains("ancient")) {
            prefixes.add("Ancient");
        }
        if (traits.contains("starter") && !lowerTemplate.contains("starter")) {
            prefixes.add("Starter");
        }
        if (traits.contains("alolan") && !lowerTemplate.contains("alola")) {
            prefixes.add("Alolan");
        }
        if (traits.contains("hisuian") && !lowerTemplate.contains("hisui")) {
            prefixes.add("Hisuian");
        }
        if (traits.contains("galarian") && !lowerTemplate.contains("galar")) {
            prefixes.add("Galarian");
        }
        if (traits.contains("paldean") && !lowerTemplate.contains("paldea")) {
            prefixes.add("Paldean");
        }
        if (prefixes.isEmpty()) {
            return record.species();
        }
        return String.join(" ", prefixes) + " " + record.species();
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
