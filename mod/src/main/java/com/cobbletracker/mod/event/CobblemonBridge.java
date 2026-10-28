package com.cobbletracker.mod.event;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonFaintedEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletracker.common.tracker.PokemonSnapshot;
import com.cobbletracker.common.tracker.SpawnContext;
import com.cobbletracker.common.tracker.CommandHookGuard;
import com.cobbletracker.mod.tracker.CommandHooks;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.tracker.TrackerServer;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import kotlin.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Tracks wild entities through platform add events and Cobblemon lifecycle events. */
public final class CobblemonBridge {
    private static final Map<UUID, PendingSpawn> pending = new HashMap<>();

    private record PendingSpawn(PokemonEntity entity, ServerLevel level, int tick,
            CommandHookGuard.Origin origin) {}

    private CobblemonBridge() {
    }

    public static void register() {

        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
            onCaptured(event);
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_FAINTED.subscribe(Priority.NORMAL, event -> {
            onFainted(event);
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
            onBattleVictory(event);
            return Unit.INSTANCE;
        });
        CobbleTracker.LOGGER.info("Watching entity spawns (platform entity-add events) + Cobblemon capture/faint/battle events");
    }

    public static void onEntityAdd(Entity entity, Level level) {
        try {
            if (entity instanceof PokemonEntity pokemonEntity && level instanceof ServerLevel serverLevel) {
                if (isWild(pokemonEntity)) {
                    pending.put(entity.getUUID(), new PendingSpawn(pokemonEntity, serverLevel,
                            serverLevel.getServer().getTickCount() + 2, CommandHooks.captureOrigin()));
                }
            }
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Error handling entity add", t);
        }

    }

    public static boolean isWild(PokemonEntity entity) {
        Pokemon pokemon = entity.getPokemon();
        return pokemon != null && !entity.isTame() && pokemon.isWild() && pokemon.getOwnerUUID() == null
                && !pokemon.isNPCOwned() && entity.getOwner() == null;
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        var ready = new java.util.ArrayList<PendingSpawn>();
        pending.values().removeIf(spawn -> {
            if (spawn.level().getServer() != server) return true;
            if (spawn.tick() > server.getTickCount()) return false;
            ready.add(spawn);
            return true;
        });
        for (PendingSpawn spawn : ready) {
            if (!spawn.entity().isRemoved() && spawn.entity().isAlive() && isWild(spawn.entity())) {
                onSpawn(spawn.entity(), spawn.level(), spawn.origin());
            }
        }
    }

    public static void clear() {
        pending.clear();
    }

    private static void onSpawn(PokemonEntity entity, ServerLevel level, CommandHookGuard.Origin origin) {
        try {
            TrackerServer engine = TrackerServer.get();
            if (engine == null) {
                return;
            }
            Pokemon pokemon = entity.getPokemon();
            if (pokemon == null) {
                return;
            }
            if (!isWild(entity)) {
                return;
            }
            // Deduplicate chunk reloads before allocating a Pokémon snapshot.
            if (!engine.isNewSpawn(pokemon.getUuid(), entity.getUUID())
                    && !engine.needsLiveRestore(pokemon.getUuid())) {
                return;
            }
            PokemonSnapshot snapshot = CobblemonSnapshots.of(pokemon);
            BlockPos pos = entity.blockPosition();
            String world = level.dimension().location().toString();
            String biome = CobblemonSnapshots.biomeId(level, pos);
            SpawnContext context = new SpawnContext(biome, world, level.getDayTime(),
                    level.isRaining(), level.isThundering(), pos.getY());
            engine.recordSpawn(snapshot, pokemon.getUuid(), entity.getUUID(),
                    pos.getX(), pos.getY(), pos.getZ(), world, biome, context, origin);
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Error handling Pokémon spawn", t);
        }
    }

    private static void onCaptured(PokemonCapturedEvent event) {
        try {
            Pokemon pokemon = event.getPokemon();
            ServerPlayer player = event.getPlayer();
            UUID catcher = player == null ? null : player.getUUID();
            String catcherName = player == null ? "" : player.getGameProfile().getName();
            TrackerServer.ifRunning(engine -> engine.markCaught(pokemon.getUuid(), catcher, catcherName));
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Error handling POKEMON_CAPTURED", t);
        }
    }

    private static void onFainted(PokemonFaintedEvent event) {
        try {
            Pokemon pokemon = event.getPokemon();
            if (!pokemon.isWild() || pokemon.getOwnerUUID() != null || pokemon.isNPCOwned()) return;
            String killer = null;
            try {
                PokemonEntity entity = pokemon.getEntity();
                if (entity != null && entity.getKillCredit() instanceof ServerPlayer sp) {
                    killer = sp.getGameProfile().getName();
                }
            } catch (Throwable ignored) {
            }
            final String killerName = killer;
            TrackerServer.ifRunning(engine -> engine.markFainted(pokemon.getUuid(), killerName));
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Error handling POKEMON_FAINTED", t);
        }
    }

    private static void onBattleVictory(com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent event) {
        try {
            if (event.getWasWildCapture()) {
                return;  
            }
            String winnerName = "";
            for (com.cobblemon.mod.common.api.battles.model.actor.BattleActor winner : event.getWinners()) {
                if (winner.getName() != null) {
                    winnerName = winner.getName().getString();
                    break;
                }
            }
            final String killer = winnerName;
            for (com.cobblemon.mod.common.api.battles.model.actor.BattleActor loser : event.getLosers()) {
                for (com.cobblemon.mod.common.battles.pokemon.BattlePokemon bp : loser.getPokemonList()) {
                    Pokemon mon = bp.getOriginalPokemon();
                    if (mon != null && mon.isWild() && mon.getOwnerUUID() == null && !mon.isNPCOwned()) {
                        TrackerServer.ifRunning(engine -> engine.markFainted(mon.getUuid(), killer));
                    }
                }
            }
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Error handling BATTLE_VICTORY", t);
        }
    }
}
