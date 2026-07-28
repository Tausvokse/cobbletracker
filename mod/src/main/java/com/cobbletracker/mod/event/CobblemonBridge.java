package com.cobbletracker.mod.event;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonFaintedEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletracker.common.tracker.PokemonSnapshot;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.tracker.TrackerServer;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import java.util.UUID;
import kotlin.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Where we hook the spawn/capture/faint events. A Pokémon entering a server level gets recorded (if it
 * matches a category) and announced; a capture or faint updates the record and reports it. Calls are
 * wrapped so an upstream API change only breaks this one class instead of the server.
 *
 * <p>We use Architectury's {@link EntityEvent#ADD} rather than Cobblemon's {@code POKEMON_ENTITY_SPAWN}:
 * that one only fires from the natural spawner, so {@code /pokespawn} and anything that builds a Pokémon
 * directly slipped through with no announcement, beam, or lifecycle. {@code EntityEvent.ADD} fires for
 * every entity added to a level; chunk-reload duplicates are filtered in
 * {@link TrackerServer#recordSpawn} (by Pokémon UUID).
 */
public final class CobblemonBridge {

    private CobblemonBridge() {
    }

    /** Subscribes to the spawn/capture/faint events we care about. Called once at init. */
    public static void register() {
        EntityEvent.ADD.register(CobblemonBridge::onEntityAdd);
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
            onCaptured(event);
            return Unit.INSTANCE;
        });
        CobblemonEvents.POKEMON_FAINTED.subscribe(Priority.NORMAL, event -> {
            onFainted(event);
            return Unit.INSTANCE;
        });
        CobbleTracker.LOGGER.info("Watching entity spawns (EntityEvent.ADD) + Cobblemon capture/faint events");
    }

    /** Fires for every entity added to a level; we act only on server-side Pokémon. Never cancels. */
    private static EventResult onEntityAdd(Entity entity, Level level) {
        try {
            if (entity instanceof PokemonEntity pokemonEntity && level instanceof ServerLevel serverLevel) {
                onSpawn(pokemonEntity, serverLevel);
            }
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Error handling entity add", t);
        }
        return EventResult.pass();
    }

    private static void onSpawn(PokemonEntity entity, ServerLevel level) {
        try {
            Pokemon pokemon = entity.getPokemon();
            PokemonSnapshot snapshot = CobblemonSnapshots.of(pokemon);
            BlockPos pos = entity.blockPosition();
            String world = level.dimension().location().toString();
            String biome = CobblemonSnapshots.biomeId(level, pos);
            // Pokémon UUID identifies the record (capture matching); entity UUID lets the client beam it.
            TrackerServer.ifRunning(engine -> engine.recordSpawn(
                    snapshot, pokemon.getUuid(), entity.getUUID(),
                    pos.getX(), pos.getY(), pos.getZ(), world, biome));
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
            String killer = null;
            try {
                PokemonEntity entity = pokemon.getEntity();
                if (entity != null && entity.getKillCredit() instanceof ServerPlayer sp) {
                    killer = sp.getGameProfile().getName();
                }
            } catch (Throwable ignored) {
                // kill credit unavailable - just report a plain defeat
            }
            final String killerName = killer;
            TrackerServer.ifRunning(engine -> engine.markFainted(pokemon.getUuid(), killerName));
        } catch (Throwable t) {
            CobbleTracker.LOGGER.error("Error handling POKEMON_FAINTED", t);
        }
    }
}
