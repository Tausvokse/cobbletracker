package com.cobbletracker.common.spawn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WeightedSpeciesOdds {

    public record Weight(String species, double weight) {
    }

    public record Share(String species, double share) {
    }

    public record Percent(String species, int basisPoints) {
        public double percent() {
            return basisPoints / 100.0;
        }
    }

    private WeightedSpeciesOdds() {
    }

    public static List<Share> normalize(List<Weight> entries) {
        double maximum = 0;
        for (Weight entry : entries) {
            if (usable(entry)) {
                maximum = Math.max(maximum, entry.weight());
            }
        }
        if (maximum == 0) {
            return List.of();
        }
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Weight entry : entries) {
            if (usable(entry)) {
                weights.merge(entry.species(), entry.weight() / maximum, Double::sum);
            }
        }
        return shares(weights);
    }

    public static List<Share> aggregatePlayers(List<? extends List<Weight>> players) {
        List<Weight> combined = new ArrayList<>();
        for (List<Weight> player : players) {
            combined.addAll(player);
        }
        return normalize(combined);
    }

    public static String choose(List<Share> shares, double roll) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) {
            throw new IllegalArgumentException("roll must be between 0 inclusive and 1 exclusive");
        }
        if (shares.isEmpty()) {
            return null;
        }
        double cumulative = 0;
        for (Share share : shares) {
            cumulative += share.share();
            if (roll < cumulative) {
                return share.species();
            }
        }
        return shares.get(shares.size() - 1).species();
    }

    public static List<Percent> percentages(List<Share> shares) {
        if (shares.isEmpty()) {
            return List.of();
        }
        double total = shares.stream().mapToDouble(Share::share).sum();
        int[] units = new int[shares.size()];
        List<Integer> remainders = new ArrayList<>();
        int assigned = 0;
        for (int i = 0; i < shares.size(); i++) {
            units[i] = (int) Math.floor(shares.get(i).share() / total * 10_000);
            assigned += units[i];
            remainders.add(i);
        }
        remainders.sort(Comparator.<Integer>comparingDouble(i ->
                shares.get(i).share() / total * 10_000 - units[i]).reversed());
        for (int i = 0; i < 10_000 - assigned; i++) {
            units[remainders.get(i % remainders.size())]++;
        }
        List<Percent> result = new ArrayList<>();
        for (int i = 0; i < shares.size(); i++) {
            result.add(new Percent(shares.get(i).species(), units[i]));
        }
        return List.copyOf(result);
    }

    private static boolean usable(Weight entry) {
        return entry != null && entry.species() != null && !entry.species().isBlank()
                && Double.isFinite(entry.weight()) && entry.weight() > 0;
    }

    private static List<Share> shares(Map<String, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return List.of();
        }
        List<Share> result = new ArrayList<>();
        weights.forEach((species, weight) -> result.add(new Share(species, weight / total)));
        result.sort(Comparator.comparingDouble(Share::share).reversed().thenComparing(Share::species));
        return List.copyOf(result);
    }
}
