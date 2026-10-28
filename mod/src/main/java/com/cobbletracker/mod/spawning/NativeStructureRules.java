package com.cobbletracker.mod.spawning;

import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class NativeStructureRules {
    private NativeStructureRules() {
    }

    static boolean matches(SpawnDetail detail, SpawnablePosition position) {
        return constrained(detail) ? evaluate(position, () -> detail.isSatisfiedBy(position))
                : detail.isSatisfiedBy(position);
    }

    static double weight(SpawnDetail detail, SpawnablePosition position) {
        if (!constrained(detail)) {
            return detail.isSatisfiedBy(position) ? position.getWeight(detail) : 0.0;
        }
        return evaluate(position, () -> detail.isSatisfiedBy(position) ? (double) position.getWeight(detail) : 0.0);
    }

    static boolean constrained(SpawnDetail detail) {
        if (hasStructures(detail.getConditions()) || hasStructures(detail.getAnticonditions())) return true;
        var composite = detail.getCompositeCondition();
        if (composite != null && (hasStructures(composite.getConditions())
                || hasStructures(composite.getAnticonditions()))) return true;
        for (var multiplier : detail.getWeightMultipliers()) {
            if (hasStructures(multiplier.getConditions()) || hasStructures(multiplier.getAnticonditions())) return true;
        }
        return false;
    }

    static List<SpawningCondition<?>> conditions(SpawnDetail detail) {
        List<SpawningCondition<?>> conditions = new ArrayList<>(detail.getConditions());
        conditions.addAll(detail.getAnticonditions());
        var composite = detail.getCompositeCondition();
        if (composite != null) {
            conditions.addAll(composite.getConditions());
            conditions.addAll(composite.getAnticonditions());
        }
        for (var multiplier : detail.getWeightMultipliers()) {
            conditions.addAll(multiplier.getConditions());
            conditions.addAll(multiplier.getAnticonditions());
        }
        return List.copyOf(conditions);
    }

    private static boolean hasStructures(List<? extends SpawningCondition<?>> conditions) {
        for (var condition : conditions) {
            if (condition.getStructures() != null && !condition.getStructures().isEmpty()) return true;
        }
        return false;
    }

    private static <T> T evaluate(SpawnablePosition position, Supplier<T> evaluation) {
        var at = position.getPosition();
        var manager = position.getWorld().structureManager();
        var cache = position.getStructureCache(at);
        if (!cache.getLoadedStructures()) {
            cache.loadStructures(manager, at);
        }
        var outside = cache.getStructures().stream()
                .filter(holder -> !manager.getStructureAt(at, holder.value()).isValid()).toList();
        if (outside.isEmpty()) {
            return evaluation.get();
        }
        var structures = new HashSet<>(cache.getStructures());
        var identifiers = new HashSet<>(cache.getFoundIdentifiers());
        var foundTags = new HashSet<>(cache.getFoundTags());
        var missingTags = new HashSet<>(cache.getMissingTags());
        try {
            // Cobblemon caches structures by chunk; custom rules need the candidate's exact bounds.
            cache.getStructures().removeAll(outside);
            cache.getFoundIdentifiers().clear();
            cache.getStructures().forEach(holder -> holder.unwrapKey()
                    .ifPresent(key -> cache.getFoundIdentifiers().add(key.location())));
            cache.getFoundTags().clear();
            cache.getMissingTags().clear();
            return evaluation.get();
        } finally {
            cache.getStructures().clear();
            cache.getStructures().addAll(structures);
            cache.getFoundIdentifiers().clear();
            cache.getFoundIdentifiers().addAll(identifiers);
            cache.getFoundTags().clear();
            cache.getFoundTags().addAll(foundTags);
            cache.getMissingTags().clear();
            cache.getMissingTags().addAll(missingTags);
        }
    }
}
