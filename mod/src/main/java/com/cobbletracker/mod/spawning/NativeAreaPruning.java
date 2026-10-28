package com.cobbletracker.mod.spawning;

import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.position.GroundedSpawnablePosition;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

final class NativeAreaPruning {
    private NativeAreaPruning() { }

    static boolean worldAllows(SpawningCondition<?> condition, ServerLevel world) {
        return (condition.getDimensions() == null || condition.getDimensions().isEmpty()
                || condition.getDimensions().contains(world.dimension().location()))
                && (condition.isRaining() == null || condition.isRaining() == world.isRaining())
                && (condition.isThundering() == null || condition.isThundering() == world.isThundering())
                && (condition.getTimeRange() == null || condition.getTimeRange().contains((int) (world.getDayTime() % 24000)));
    }

    static boolean worldAllows(SpawnDetail detail, ServerLevel world) {
        if (!worldBranches(detail.getConditions(), world)) return false;
        var composite = detail.getCompositeCondition();
        return composite == null || worldBranches(composite.getConditions(), world);
    }

    private static boolean worldBranches(List<? extends SpawningCondition<?>> conditions, ServerLevel world) {
        return conditions.isEmpty() || conditions.stream().anyMatch(condition -> worldAllows(condition, world));
    }

    static boolean mayMatch(SpawnDetail detail, ServerLevel world,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (!branches(detail.getConditions(), world, minX, minY, minZ, maxX, maxY, maxZ)) return false;
        var composite = detail.getCompositeCondition();
        return composite == null || branches(composite.getConditions(), world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    static boolean hasPossibleFloor(ServerLevel world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                var chunk = world.getChunkSource().getChunkNow(x, z);
                if (chunk == null) return true;
                int low = Math.max(0, chunk.getSectionIndex(minY));
                int high = Math.min(chunk.getSections().length - 1, chunk.getSectionIndex(maxY));
                for (int section = low; section <= high; section++) {
                    if (!chunk.getSections()[section].hasOnlyAir()) return true;
                }
            }
        }
        return false;
    }

    static boolean groundedOnly(List<? extends SpawnDetail> details) {
        return details.stream().allMatch(detail -> detail.getSpawnablePositionType().getClazz() == GroundedSpawnablePosition.class);
    }

    static boolean structureDataLoaded(ServerLevel world, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                var chunk = world.getChunkSource().getChunkNow(x, z);
                if (chunk == null) return false;
                for (var references : chunk.getAllReferences().values()) {
                    for (long origin : references) {
                        if (world.getChunkSource().getChunkNow(ChunkPos.getX(origin), ChunkPos.getZ(origin)) == null) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean branches(List<? extends SpawningCondition<?>> conditions, ServerLevel world,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (conditions.isEmpty()) return true;
        for (var condition : conditions) {
            if (!worldAllows(condition, world)) continue;
            if (condition.getMinX() != null && (float) maxX < condition.getMinX()) continue;
            if (condition.getMaxX() != null && (float) minX > condition.getMaxX()) continue;
            if (condition.getMinY() != null && (float) maxY < condition.getMinY()) continue;
            if (condition.getMaxY() != null && (float) minY > condition.getMaxY()) continue;
            if (condition.getMinZ() != null && (float) maxZ < condition.getMinZ()) continue;
            if (condition.getMaxZ() != null && (float) minZ > condition.getMaxZ()) continue;
            if (structures(condition, world, minX, minY, minZ, maxX, maxY, maxZ)) return true;
        }
        return false;
    }

    private static boolean structures(SpawningCondition<?> condition, ServerLevel world,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (condition.getStructures() == null || condition.getStructures().isEmpty()) return true;
        var registry = world.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                var chunk = world.getChunkSource().getChunkNow(x, z);
                if (chunk == null) return true;
                for (var reference : chunk.getAllReferences().entrySet()) {
                    var holder = registry.wrapAsHolder(reference.getKey());
                    boolean matching = condition.getStructures().stream()
                            .anyMatch(rule -> rule.map(holder::is, holder::is));
                    if (!matching) continue;
                    for (long origin : reference.getValue()) {
                        var startChunk = world.getChunkSource().getChunkNow(ChunkPos.getX(origin), ChunkPos.getZ(origin));
                        if (startChunk == null) return true;
                        var start = startChunk.getStartForStructure(reference.getKey());
                        if (start == null) return true;
                        if (!start.isValid()) continue;
                        var bounds = start.getBoundingBox();
                        if (bounds.maxX() >= minX && bounds.minX() <= maxX
                                && bounds.maxY() >= minY && bounds.minY() <= maxY
                                && bounds.maxZ() >= minZ && bounds.minZ() <= maxZ) return true;
                    }
                }
            }
        }
        return false;
    }
}
