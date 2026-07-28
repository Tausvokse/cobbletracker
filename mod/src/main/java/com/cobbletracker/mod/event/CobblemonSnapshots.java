package com.cobbletracker.mod.event;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobbletracker.common.tracker.PokemonSnapshot;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Turns a live Cobblemon {@link Pokemon} into the loader-neutral {@link PokemonSnapshot} the matcher
 * works with. This is the one place "legendary/shiny/boss/..." is decided, so it can be tweaked without
 * touching anything else. Every Cobblemon read is defensive.
 */
public final class CobblemonSnapshots {

    private CobblemonSnapshots() {
    }

    public static PokemonSnapshot of(Pokemon pokemon) {
        Species species = pokemon.getSpecies();
        Set<String> labels = new HashSet<>();
        try {
            for (String label : species.getLabels()) {
                labels.add(label.toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {
            // labels unavailable on this build - leave empty
        }
        Set<String> aspects = new HashSet<>();
        try {
            for (String aspect : pokemon.getAspects()) {
                aspects.add(aspect.toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {
            // aspects unavailable - leave empty
        }
        boolean legendary = labels.contains("legendary");
        boolean mythical = labels.contains("mythical");
        boolean ultraBeast = labels.contains("ultra_beast") || labels.contains("ultrabeast");
        boolean boss = labels.contains("boss") || aspects.contains("boss");
        return new PokemonSnapshot(
                species.getResourceIdentifier().toString(),
                species.getName(),
                pokemon.getShiny(),
                legendary,
                mythical,
                ultraBeast,
                boss,
                pokemon.getLevel(),
                labels,
                aspects);
    }

    /** Namespaced biome id at a position, or {@code ""} if it can't be resolved. */
    public static String biomeId(ServerLevel level, BlockPos pos) {
        try {
            return level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("");
        } catch (Throwable t) {
            return "";
        }
    }
}
