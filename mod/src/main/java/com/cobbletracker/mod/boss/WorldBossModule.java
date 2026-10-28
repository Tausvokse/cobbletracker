package com.cobbletracker.mod.boss;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobbletracker.common.tracker.PokemonSnapshot;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.config.WorldBossSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;

public final class WorldBossModule {

    private static final Map<UUID, ActiveBoss> ACTIVE_BOSSES = new ConcurrentHashMap<>();

    private record ActiveBoss(
            UUID pokemonId,
            UUID entityId,
            String speciesName,
            int level,
            String dimension,
            ServerBossEvent bossEvent) {
    }

    private WorldBossModule() {
    }

    public static void onBossSpawn(PokemonSnapshot snapshot, UUID pokemonId, UUID entityId,
            String dimension, WorldBossSettings settings) {
        if (!settings.enabled() || !snapshot.boss()) {
            return;
        }
        ActiveBoss previous = ACTIVE_BOSSES.get(pokemonId);
        if (previous != null && previous.entityId.equals(entityId) && previous.dimension.equals(dimension)
                && previous.level == snapshot.level() && previous.speciesName.equals(snapshot.speciesName())) return;
        try {
            BossEvent.BossBarColor color = parseColor(settings.bossBarColor());
            BossEvent.BossBarOverlay style = parseOverlay(settings.bossBarStyle());
            Component title = Component.literal("⚡ World Boss: " + snapshot.speciesName() + " [Lv. " + snapshot.level() + "]")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            ServerBossEvent bossEvent = new ServerBossEvent(title, color, style);
            bossEvent.setProgress(1.0f);
            ActiveBoss replaced = ACTIVE_BOSSES.put(pokemonId,
                    new ActiveBoss(pokemonId, entityId, snapshot.speciesName(), snapshot.level(), dimension, bossEvent));
            if (replaced != null) replaced.bossEvent.removeAllPlayers();
            CobbleTracker.LOGGER.debug("Registered World Boss raid bar for {}", snapshot.speciesName());
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Failed to initialize boss bar for {}", snapshot.speciesName(), t);
        }
    }

    public static void tick(MinecraftServer server, WorldBossSettings settings) {
        if (ACTIVE_BOSSES.isEmpty()) {
            return;
        }
        if (!settings.enabled()) {
            clearAll();
            return;
        }
        List<UUID> finished = new ArrayList<>();
        for (ActiveBoss boss : ACTIVE_BOSSES.values()) {
            PokemonEntity entity = findEntity(server, boss.dimension, boss.entityId);
            if (entity == null) {
                boss.bossEvent.removeAllPlayers();
                continue;
            }
            if (!entity.isAlive()) {
                boss.bossEvent.removeAllPlayers();
                finished.add(boss.pokemonId);
                continue;
            }
            String dimension = entity.level().dimension().location().toString();
            if (!dimension.equals(boss.dimension)) {
                ACTIVE_BOSSES.put(boss.pokemonId, new ActiveBoss(boss.pokemonId, boss.entityId,
                        boss.speciesName, boss.level, dimension, boss.bossEvent));
            }
            float maxHealth = entity.getPokemon().getMaxHealth();
            float currentHealth = entity.getPokemon().getCurrentHealth();
            float progress = maxHealth > 0 ? Math.clamp(currentHealth / maxHealth, 0.0f, 1.0f) : 0.0f;
            boss.bossEvent.setProgress(progress);
            boss.bossEvent.setColor(parseColor(settings.bossBarColor()));
            boss.bossEvent.setOverlay(parseOverlay(settings.bossBarStyle()));
            int percent = Math.round(progress * 100);
            boss.bossEvent.setName(Component.literal("⚡ World Boss: " + boss.speciesName + " [Lv. " + boss.level + "] (" + percent + "%)")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

            int range = settings.range();
            long r2 = (long) range * range;
            BlockPos pos = entity.blockPosition();
            Set<ServerPlayer> currentSubscribers = Set.copyOf(boss.bossEvent.getPlayers());
            for (ServerPlayer subscriber : currentSubscribers) {
                if (!subscriber.level().dimension().location().toString().equals(dimension)
                        || subscriber.hasDisconnected()) boss.bossEvent.removePlayer(subscriber);
            }

            for (ServerLevel level : server.getAllLevels()) {
                if (!level.dimension().location().toString().equals(dimension)) {
                    continue;
                }
                for (ServerPlayer player : level.players()) {
                    boolean inRange = range <= 0 || player.blockPosition().distSqr(pos) <= r2;
                    if (inRange && !currentSubscribers.contains(player)) {
                        boss.bossEvent.addPlayer(player);
                    } else if (!inRange && currentSubscribers.contains(player)) {
                        boss.bossEvent.removePlayer(player);
                    }
                }
            }
        }
        for (UUID id : finished) {
            ACTIVE_BOSSES.remove(id);
        }
    }

    public static void onBossEnded(UUID pokemonId) {
        ActiveBoss boss = ACTIVE_BOSSES.remove(pokemonId);
        if (boss != null) {
            try {
                boss.bossEvent.removeAllPlayers();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void clearAll() {
        for (ActiveBoss boss : ACTIVE_BOSSES.values()) {
            try {
                boss.bossEvent.removeAllPlayers();
            } catch (Throwable ignored) {
            }
        }
        ACTIVE_BOSSES.clear();
    }

    private static PokemonEntity findEntity(MinecraftServer server, String dimension, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) {
                Entity e = level.getEntity(entityId);
                if (e instanceof PokemonEntity pe) {
                    return pe;
                }
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) continue;
            Entity entity = level.getEntity(entityId);
            if (entity instanceof PokemonEntity pokemon) return pokemon;
        }
        return null;
    }

    private static BossEvent.BossBarColor parseColor(String name) {
        if (name == null) return BossEvent.BossBarColor.RED;
        try {
            return BossEvent.BossBarColor.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Throwable t) {
            return BossEvent.BossBarColor.RED;
        }
    }

    private static BossEvent.BossBarOverlay parseOverlay(String name) {
        if (name == null) return BossEvent.BossBarOverlay.PROGRESS;
        try {
            return BossEvent.BossBarOverlay.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Throwable t) {
            return BossEvent.BossBarOverlay.PROGRESS;
        }
    }
}
