package com.cobbletracker.mod.legendary;

import com.cobbletracker.common.legendary.LegendaryRule;
import com.cobbletracker.common.tracker.SpawnContext;
import com.cobbletracker.common.tracker.SpawnSettings;
import com.cobbletracker.common.tracker.TrackerCategory;
import com.cobbletracker.common.util.Durations;
import com.cobbletracker.mod.config.ConfigManager;
import com.cobbletracker.mod.text.Msg;
import com.cobbletracker.mod.tracker.SpawnEngine;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

/** Chat diagnostics for the active legendary spawning source; no client mod required. */
public final class LegendaryReport {

    private LegendaryReport() {
    }

    /** Most rows printed, so a large datapack can't flood someone's chat. */
    private static final int MAX_ROWS = 20;

    private static final int TITLE_COLOR = 0xFFFF3333;

    public static void send(ServerPlayer player, ConfigManager config, SpawnEngine engine, long nowTicks) {
        LegendaryDex.Report report = LegendaryDex.report(player);

        player.sendSystemMessage(Msg.parse(Msg.prefix())
                .append(Component.literal("Legendary spawns")
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(TITLE_COLOR)).withBold(true)))
                .append(Component.literal(" - " + context(report)).withStyle(ChatFormatting.GRAY)));

        if (!report.poolAvailable()) {
            player.sendSystemMessage(Msg.warn("Cobblemon's spawn data isn't available yet - try again in a moment."));
            return;
        }

        TrackerCategory category = legendaryCategory(config);
        boolean ownDirector = category != null && (category.spawn().mode() == SpawnSettings.Mode.ALWAYS
                || (category.spawn().mode() == SpawnSettings.Mode.AUTO
                && !LegendaryDirector.coversTier(LegendaryDirector.tierLabel(category))));
        if (report.empty() || ownDirector) {
            sendOwnRules(player, config, report, engine, category);
        } else {
            sendPoolEntries(player, report);
        }
        sendTimer(player, config, engine, nowTicks, player.getUUID());
    }

    /** Shows eligible natural-pool entries and the count of blocked species. */
    private static void sendPoolEntries(ServerPlayer player, LegendaryDex.Report report) {
        List<LegendaryDex.Entry> eligible = report.eligible();
        int blocked = report.entries().size() - eligible.size();
        if (eligible.isEmpty()) {
            player.sendSystemMessage(Component.literal(" No natural legendary entry matches your exact position in this check.")
                    .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(Component.literal(" Nearby spawn positions can have different biome, height or light. /spawnlegendary previews a separate forced director attempt.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        int shown = 0;
        for (LegendaryDex.Entry entry : eligible) {
            if (shown++ >= MAX_ROWS) {
                player.sendSystemMessage(Component.literal("   … and " + (eligible.size() - MAX_ROWS)
                        + " more").withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            MutableComponent line = Component.literal(" ✔ ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(entry.speciesName())
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFD700))));
            String chance = entry.chance() > 0 ? " · ~" + Durations.percent(entry.chance()) + " per attempt" : "";
            line.append(Component.literal(" " + entry.bucket() + chance).withStyle(ChatFormatting.GRAY));
            if (entry.partiallyChecked()) {
                
                line.append(Component.literal(" (some conditions unchecked)").withStyle(ChatFormatting.DARK_GRAY));
            }
            player.sendSystemMessage(line);
        }
        if (blocked > 0) {
            player.sendSystemMessage(Component.literal(" " + blocked
                            + " other legendaries need a different biome, time or weather.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** No legendary spawn data anywhere: CobbleTracker's director is the only source, so show its rules. */
    private static void sendOwnRules(ServerPlayer player, ConfigManager config, LegendaryDex.Report report,
            SpawnEngine engine, TrackerCategory category) {
        player.sendSystemMessage(Component.literal(" CobbleTracker director candidates at your position:")
                .withStyle(ChatFormatting.YELLOW));
        if (category == null) {
            player.sendSystemMessage(Msg.warn("No legendary tracker category is configured."));
            return;
        }
        List<String> pool = engine.candidatePool(category);
        if (pool.isEmpty()) {
            player.sendSystemMessage(Msg.warn("The configured legendary species pool is empty."));
            return;
        }
        if (!SpawnEngine.usesContextRules(config, category.spawn(), pool)) {
            double share = 1.0 / pool.size();
            for (String species : pool.stream().limit(MAX_ROWS).toList()) {
                player.sendSystemMessage(Component.literal(" ✔ " + pretty(species) + " "
                        + Durations.percent(share) + " of the pick").withStyle(ChatFormatting.GRAY));
            }
            if (pool.size() > MAX_ROWS) player.sendSystemMessage(Msg.info("… and " + (pool.size() - MAX_ROWS) + " more"));
            player.sendSystemMessage(Msg.info("This category uses a flat species pool; contextual rules do not constrain its pick."));
            return;
        }
        List<LegendaryRule> rules = config.legendaryRules();
        if (rules.isEmpty()) {
            player.sendSystemMessage(Component.literal(" No rules in legendaries.yml either, so nothing "
                    + "will spawn.").withStyle(ChatFormatting.RED));
            return;
        }
        // Use the same contextual rule calculation as placement.
        BlockPos pos = player.blockPosition();
        SpawnContext context = new SpawnContext(report.biome(), report.dimension(),
                player.serverLevel().getDayTime(), report.raining(), report.thundering(), pos.getY());
        List<LegendaryDirector.Candidate> fitting = LegendaryDirector.fitting(
                player.serverLevel().getBiome(pos), context, rules, pool);
        if (fitting.isEmpty()) {
            player.sendSystemMessage(Component.literal(" No director rule matches your exact position. Nearby spawn positions may match different biome or height conditions.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        int shown = 0;
        for (LegendaryDirector.Candidate candidate : fitting) {
            if (shown++ >= MAX_ROWS) {
                player.sendSystemMessage(Component.literal("   … and " + (fitting.size() - MAX_ROWS) + " more")
                        .withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            player.sendSystemMessage(Component.literal(" ✔ ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(pretty(candidate.rule().speciesId()))
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFD700))))
                    .append(Component.literal(" " + Durations.percent(candidate.share()) + " of the pick · "
                            + candidate.rule().describe()).withStyle(ChatFormatting.GRAY)));
        }
        int blocked = rules.size() - fitting.size();
        if (blocked > 0) {
            player.sendSystemMessage(Component.literal(" " + blocked
                            + " other legendaries need a different biome, time or weather.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static TrackerCategory legendaryCategory(ConfigManager config) {
        return config.registry().categories().stream()
                .filter(category -> category.id().toLowerCase(Locale.ROOT).contains("legend")
                        || "legendary".equals(LegendaryDirector.tierLabel(category)))
                .findFirst().orElse(null);
    }

    /** Shows legacy countdown and anchor cooldown only while that scheduler is active. */
    private static void sendTimer(ServerPlayer player, ConfigManager config, SpawnEngine engine, long nowTicks,
            java.util.UUID playerId) {
        for (TrackerCategory category : config.registry().categories()) {
            String spec = category.spec() == null ? "" : category.spec().toLowerCase(Locale.ROOT);
            if (!spec.contains("islegendary") && !category.id().toLowerCase(Locale.ROOT).contains("legend")) {
                continue;
            }
            SpawnSettings settings = category.spawn();
            if (!settings.enabled()) {
                player.sendSystemMessage(Component.literal(" CobbleTracker's own legendary spawner is off "
                        + "(trackers." + category.id() + ".spawn.enabled)").withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            int ticks = engine.ticksUntilAttempt(category);
            if (ticks < 0) {
                
                String why = settings.mode() == SpawnSettings.Mode.NEVER
                        ? "spawn.mode is 'never'"
                        : "Cobblemon's own data is in charge";
                player.sendSystemMessage(Component.literal(" CobbleTracker's spawner is standing down - " + why)
                        .withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            long cooldown = engine.playerCooldownRemaining(category, playerId, nowTicks);
            MutableComponent line = Component.literal(" Next attempt: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(Durations.countdown(ticks / 20L)).withStyle(ChatFormatting.WHITE))
                    // Preserve fractional percentages.
                    .append(Component.literal(" · chance " + Durations.percent(settings.chance()))
                            .withStyle(ChatFormatting.GRAY));
            if (cooldown > 0) {
                line.append(Component.literal(" · you're on cooldown for "
                                + Durations.countdown(cooldown / 20L)).withStyle(ChatFormatting.DARK_GRAY));
            }
            player.sendSystemMessage(line);
            return;
        }
    }

    private static String context(LegendaryDex.Report report) {
        return shortId(report.dimension()) + ", " + shortId(report.biome()) + ", "
                + (report.day() ? "day" : "night") + weather(report);
    }

    private static String weather(LegendaryDex.Report report) {
        if (report.thundering()) {
            return ", thunderstorm";
        }
        return report.raining() ? ", rain" : ", clear";
    }

    private static String shortId(String id) {
        if (id == null || id.isEmpty()) {
            return "?";
        }
        String s = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return s.replace('_', ' ');
    }

    private static String pretty(String speciesId) {
        String s = shortId(speciesId);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
