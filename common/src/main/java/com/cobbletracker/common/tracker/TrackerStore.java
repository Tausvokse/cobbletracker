package com.cobbletracker.common.tracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory spawn database: category id → ordered {@code (uuid → record)} map, newest last. Thread-safe
 * for the server sweep/record path. Inserting past a category's cap evicts the oldest entries first.
 * Purely in-memory and Minecraft-free; the mod layer mirrors it to {@code tracker.json}.
 */
public final class TrackerStore {

    private final Map<String, LinkedHashMap<UUID, SpawnRecord>> byCategory = new ConcurrentHashMap<>();

    /** Records a spawn and trims the category to {@code maxStored} (evicting the oldest). */
    public synchronized void record(SpawnRecord record, int maxStored) {
        LinkedHashMap<UUID, SpawnRecord> map =
                byCategory.computeIfAbsent(record.category(), k -> new LinkedHashMap<>());
        map.remove(record.id());
        map.put(record.id(), record);
        trim(map, maxStored);
    }

    /** Loads a record without trimming (used when reading {@code tracker.json} at startup). */
    public synchronized void put(SpawnRecord record) {
        byCategory.computeIfAbsent(record.category(), k -> new LinkedHashMap<>()).put(record.id(), record);
    }

    /** After a bulk load, sort each category by spawn-time so eviction order is correct. */
    public synchronized void resort() {
        for (LinkedHashMap<UUID, SpawnRecord> map : byCategory.values()) {
            List<SpawnRecord> sorted = new ArrayList<>(map.values());
            sorted.sort(Comparator.comparingLong(SpawnRecord::spawnTime));
            map.clear();
            for (SpawnRecord r : sorted) {
                map.put(r.id(), r);
            }
        }
    }

    /** Marks a target caught in whichever category holds it; returns the updated record if found. */
    public synchronized Optional<SpawnRecord> markCaught(UUID id, UUID catcher, String catcherName) {
        return update(id, r -> r.asCaught(catcher, catcherName));
    }

    /** Records a non-capture outcome ("defeated"/"despawned") on the matching record. */
    public synchronized Optional<SpawnRecord> markOutcome(UUID id, String outcome, String by) {
        return update(id, r -> r.caught() ? r : r.withOutcome(outcome, by));
    }

    private Optional<SpawnRecord> update(UUID id, java.util.function.UnaryOperator<SpawnRecord> change) {
        for (LinkedHashMap<UUID, SpawnRecord> map : byCategory.values()) {
            SpawnRecord existing = map.get(id);
            if (existing != null) {
                SpawnRecord updated = change.apply(existing);
                map.put(id, updated);
                return Optional.of(updated);
            }
        }
        return Optional.empty();
    }

    /** Looks up a record by id across all categories. */
    public synchronized Optional<SpawnRecord> findById(UUID id) {
        for (LinkedHashMap<UUID, SpawnRecord> map : byCategory.values()) {
            SpawnRecord r = map.get(id);
            if (r != null) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** All records in a category, newest first. */
    public synchronized List<SpawnRecord> category(String categoryId) {
        LinkedHashMap<UUID, SpawnRecord> map = byCategory.get(categoryId);
        if (map == null) {
            return List.of();
        }
        List<SpawnRecord> list = new ArrayList<>(map.values());
        java.util.Collections.reverse(list);
        return list;
    }

    /** Every record across all categories, newest first. */
    public synchronized List<SpawnRecord> all() {
        List<SpawnRecord> list = new ArrayList<>();
        for (LinkedHashMap<UUID, SpawnRecord> map : byCategory.values()) {
            list.addAll(map.values());
        }
        list.sort(Comparator.comparingLong(SpawnRecord::spawnTime).reversed());
        return list;
    }

    public synchronized List<String> categories() {
        return new ArrayList<>(byCategory.keySet());
    }

    private static void trim(LinkedHashMap<UUID, SpawnRecord> map, int maxStored) {
        var it = map.entrySet().iterator();
        while (map.size() > maxStored && it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
