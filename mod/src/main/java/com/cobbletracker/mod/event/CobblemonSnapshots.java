package com.cobbletracker.mod.event;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.PokemonStats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobbletracker.common.tracker.PokemonSnapshot;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class CobblemonSnapshots {

    private CobblemonSnapshots() {
    }

    private static final Stat[] PERMANENT_STATS = {
            Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};

    private static final Set<String> ANCIENT_SPECIES = Set.of(
            "great_tusk", "scream_tail", "brute_bonnet", "flutter_mane",
            "slither_wing", "sandy_shocks", "roaring_moon", "koraidon",
            "walking_wake", "gouging_fire", "raging_bolt"
    );

    private static final Set<String> STARTER_SPECIES = Set.of(
            "bulbasaur", "ivysaur", "venusaur",
            "charmander", "charmeleon", "charizard",
            "squirtle", "wartortle", "blastoise",
            "chikorita", "bayleef", "meganium",
            "cyndaquil", "quilava", "typhlosion",
            "totodile", "croconaw", "feraligatr",
            "treecko", "grovyle", "sceptile",
            "torchic", "combusken", "blaziken",
            "mudkip", "marshtomp", "swampert",
            "turtwig", "grotle", "torterra",
            "chimchar", "monferno", "infernape",
            "piplup", "prinplup", "empoleon",
            "snivy", "servine", "serperior",
            "tepig", "pignite", "emboar",
            "oshawott", "dewwott", "samurott",
            "chespin", "quilladin", "chesnaught",
            "fennekin", "braixen", "delphox",
            "froakie", "frogadier", "greninja",
            "rowlet", "dartrix", "decidueye",
            "litten", "torracat", "incineroar",
            "popplio", "brionne", "primarina",
            "grookey", "thwackey", "rillaboom",
            "scorbunny", "raboot", "cinderace",
            "sobble", "drizzile", "inteleon",
            "sprigatito", "floragato", "meowscarada",
            "fuecoco", "crocalor", "skeledirge",
            "quaxly", "quaxwell", "quaquaval"
    );

    public static PokemonSnapshot of(Pokemon pokemon) {
        Species species = pokemon.getSpecies();
        String speciesPath = species.getResourceIdentifier().getPath().toLowerCase(Locale.ROOT);
        Set<String> labels = new HashSet<>();
        try {
            for (String label : species.getLabels()) {
                labels.add(label.toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {
        }
        try {
            if (pokemon.getForm() != null && pokemon.getForm().getLabels() != null) {
                for (String formLabel : pokemon.getForm().getLabels()) {
                    labels.add(formLabel.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Throwable ignored) {
        }
        Set<String> aspects = new HashSet<>();
        try {
            for (String aspect : pokemon.getAspects()) {
                aspects.add(aspect.toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {
        }
        String form = readForm(pokemon);
        String ability = readAbility(pokemon);

        boolean legendary = labels.contains("legendary");
        boolean mythical = labels.contains("mythical");
        boolean ultraBeast = labels.contains("ultra_beast") || labels.contains("ultrabeast");
        boolean boss = labels.contains("boss") || aspects.contains("boss");

        boolean ancient = labels.contains("ancient") || labels.contains("past_paradox")
                || "protosynthesis".equals(ability) || ANCIENT_SPECIES.contains(speciesPath);

        boolean starter = labels.contains("starter") || STARTER_SPECIES.contains(speciesPath) || isStarterLine(species);

        boolean alolan = form.contains("alola") || labels.contains("alola") || labels.contains("alolan")
                || aspects.contains("alola") || aspects.contains("alolan");
        boolean hisuian = form.contains("hisui") || labels.contains("hisui") || labels.contains("hisuian")
                || aspects.contains("hisui") || aspects.contains("hisuian");
        boolean galarian = form.contains("galar") || labels.contains("galar") || labels.contains("galarian")
                || aspects.contains("galar") || aspects.contains("galarian");
        boolean paldean = form.contains("paldea") || labels.contains("paldea") || labels.contains("paldean")
                || aspects.contains("paldea") || aspects.contains("paldean");

        int[] ivs = readIvs(pokemon);
        String ivList = readStats(pokemon, true);
        String evList = readStats(pokemon, false);
        return new PokemonSnapshot(
                species.getResourceIdentifier().toString(),
                species.getName(),
                pokemon.getShiny(),
                legendary,
                mythical,
                ultraBeast,
                boss,
                ancient,
                starter,
                alolan,
                hisuian,
                galarian,
                paldean,
                pokemon.getLevel(),
                labels,
                aspects,
                ivs[0],
                ivs[1],
                readScale(pokemon),
                readGender(pokemon),
                readNature(pokemon),
                form,
                ability,
                ivList,
                evList);
    }

    private static boolean isStarterLine(Species species) {
        try {
            Species curr = species;
            int depth = 0;
            while (curr != null && depth++ < 5) {
                if (curr.getLabels().contains("starter")) {
                    return true;
                }
                var pre = curr.getPreEvolution();
                if (pre == null) {
                    break;
                }
                curr = pre.getSpecies();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String readStats(Pokemon pokemon, boolean wantIvs) {
        try {
            StringBuilder sb = new StringBuilder(20);
            boolean anyNonZero = false;
            PokemonStats stats = wantIvs ? pokemon.getIvs() : pokemon.getEvs();
            for (Stat stat : PERMANENT_STATS) {
                int value = stats.getOrDefault(stat);
                anyNonZero |= value != 0;
                if (sb.length() > 0) {
                    sb.append('/');
                }
                sb.append(value);
            }
            return anyNonZero || wantIvs ? sb.toString() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static String readAbility(Pokemon pokemon) {
        try {
            return pokemon.getAbility().getName().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return "";
        }
    }

    private static int[] readIvs(Pokemon pokemon) {
        int perfect = 0;
        int total = 0;
        try {
            PokemonStats ivs = pokemon.getIvs();
            for (Stat stat : PERMANENT_STATS) {
                int value = ivs.getOrDefault(stat);
                total += value;
                if (value >= IVs.MAX_VALUE) {
                    perfect++;
                }
            }
        } catch (Throwable ignored) {
            return new int[] {0, 0};
        }
        return new int[] {perfect, total};
    }

    private static double readScale(Pokemon pokemon) {
        try {
            return pokemon.getScaleModifier();
        } catch (Throwable t) {
            return 1.0;
        }
    }

    private static String readGender(Pokemon pokemon) {
        try {
            return pokemon.getGender().name().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String readNature(Pokemon pokemon) {
        try {
            return pokemon.getNature().getName().getPath().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String readForm(Pokemon pokemon) {
        try {
            return pokemon.getForm().getName().toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return "";
        }
    }

    public static String biomeId(ServerLevel level, BlockPos pos) {
        try {
            return level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("");
        } catch (Throwable t) {
            return "";
        }
    }
}
