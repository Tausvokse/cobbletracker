package com.cobbletracker.mod.protection;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.config.ConfigManager;
import com.cobbletracker.mod.config.SpawnProtectionSettings;
import com.cobbletracker.mod.text.Msg;
import com.cobbletracker.mod.tracker.TrackerServer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;

public final class SpawnProtectionModule {

    private record Protection(
            UUID anchorUuid,
            String anchorName,
            long expiresAtMs) {

        boolean isExpired(long now) {
            return now >= expiresAtMs;
        }

        long secondsRemaining(long now) {
            return Math.max(0, (expiresAtMs - now + 999) / 1000);
        }
    }

    private static final Map<UUID, Protection> PROTECTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_NOTICE = new ConcurrentHashMap<>();
    private static boolean registered;

    private SpawnProtectionModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGH, event -> {
            onBattleStartedPre(event);
            return Unit.INSTANCE;
        });
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGH, event -> {
            onPokeballHit(event);
            return Unit.INSTANCE;
        });
    }

    public static void protect(UUID pokemonId, ServerPlayer anchorPlayer, SpawnProtectionSettings settings) {
        if (!settings.enabled() || anchorPlayer == null || settings.gracePeriodSeconds() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        PROTECTIONS.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        long expiresAt = now + (settings.gracePeriodSeconds() * 1000L);
        Protection previous = PROTECTIONS.putIfAbsent(pokemonId,
                new Protection(anchorPlayer.getUUID(), anchorPlayer.getGameProfile().getName(), expiresAt));
        if (previous != null) return;
        CobbleTracker.LOGGER.debug("Protected spawn {} for {} ({}s)",
                pokemonId, anchorPlayer.getGameProfile().getName(), settings.gracePeriodSeconds());
    }

    public static void clearProtection(UUID pokemonId) {
        PROTECTIONS.remove(pokemonId);
    }

    public static void clearAll() {
        PROTECTIONS.clear();
        LAST_NOTICE.clear();
    }

    public static void playerLeft(UUID id) {
        PROTECTIONS.entrySet().removeIf(entry -> entry.getValue().anchorUuid().equals(id));
        LAST_NOTICE.remove(id);
    }

    public static void tick(SpawnProtectionSettings settings) {
        if (!settings.enabled()) {
            clearAll();
            return;
        }
        long now = System.currentTimeMillis();
        PROTECTIONS.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static void notifyBlocked(ServerPlayer player, Protection protection, long now) {
        long time = System.nanoTime();
        Long previous = LAST_NOTICE.get(player.getUUID());
        if (previous != null && time - previous < 1_000_000_000L) return;
        LAST_NOTICE.put(player.getUUID(), time);
        player.sendSystemMessage(Msg.warn("This Pokémon is protected for "
                + protection.anchorName() + " (" + protection.secondsRemaining(now) + "s left)!"));
    }

    private static void onBattleStartedPre(BattleStartedEvent.Pre event) {
        try {
            TrackerServer engine = TrackerServer.get();
            if (engine == null) {
                return;
            }
            ConfigManager config = engine.config();
            if (config == null) {
                return;
            }
            SpawnProtectionSettings settings = config.spawnProtection();
            if (!settings.enabled() || PROTECTIONS.isEmpty()) {
                return;
            }
            PokemonBattle battle = event.getBattle();
            if (battle == null) {
                return;
            }
            long now = System.currentTimeMillis();

            for (BattleActor actor : battle.getActors()) {
                for (BattlePokemon bp : actor.getPokemonList()) {
                    Pokemon original = bp.getOriginalPokemon();
                    if (original == null || !original.isWild()) {
                        continue;
                    }
                    Protection protection = PROTECTIONS.get(original.getUuid());
                    if (protection == null) {
                        continue;
                    }
                    if (protection.isExpired(now)) {
                        PROTECTIONS.remove(original.getUuid());
                        continue;
                    }
                    for (ServerPlayer player : battle.getPlayers()) {
                        if (!player.getUUID().equals(protection.anchorUuid())) {
                            event.cancel();
                            if (settings.notifyOthers()) {
                                notifyBlocked(player, protection, now);
                            }
                            return;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Error evaluating spawn protection", t);
        }
    }

    private static void onPokeballHit(ThrownPokeballHitEvent event) {
        TrackerServer engine = TrackerServer.get();
        if (engine == null || !engine.config().spawnProtection().enabled()
                || !(event.getPokeBall().getOwner() instanceof ServerPlayer player)) {
            return;
        }
        UUID pokemonId = event.getPokemon().getPokemon().getUuid();
        Protection protection = PROTECTIONS.get(pokemonId);
        if (protection == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (protection.isExpired(now)) {
            PROTECTIONS.remove(pokemonId);
        } else if (!player.getUUID().equals(protection.anchorUuid())) {
            event.cancel();
            if (engine.config().spawnProtection().notifyOthers()) {
                notifyBlocked(player, protection, now);
            }
        }
    }
}
