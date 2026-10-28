package com.cobbletracker.mod.legendary;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.List;
import java.util.Objects;

final class SpawnSpecies {
    private SpawnSpecies() {}

    static List<Species> of(SpawnDetail detail) {
        if (detail instanceof PokemonSpawnDetail pokemon) {
            Species species = resolve(pokemon.getPokemon().getSpecies());
            return species == null ? List.of() : List.of(species);
        }
        if (detail instanceof PokemonHerdSpawnDetail herd) {
            return herd.getHerdablePokemon().stream()
                    .map(member -> resolve(member.getPokemon().getSpecies()))
                    .filter(Objects::nonNull).distinct().toList();
        }
        return List.of();
    }

    private static Species resolve(String id) {
        if (id == null || id.isBlank()) return null;
        return PokemonSpecies.getByName(id.contains(":") ? id.substring(id.indexOf(':') + 1) : id);
    }
}
