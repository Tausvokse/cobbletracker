package com.cobbletracker.mod.spawning;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool;
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput;
import com.cobbletracker.common.spawn.SpawnAreaCoverage;
import com.cobbletracker.common.spawn.SpawnRuleCandidates;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

final class NativeSpawnScan {
    record Rule(String species, PokemonSpawnDetail detail) { }
    record Placement(BlockPos at, SpawningZoneInput input) { }
    record Choice(Rule rule, Placement placement, double weight) { }
    record PlayerChoices(UUID anchor, List<Choice> choices) { }
    record Region(SpawnAreaCoverage.Cell cell, int y, int endY) {
        List<Region> split() {
            return cell.split().stream().map(child -> new Region(child, y, endY)).toList();
        }
    }
    record GlobalCondition(ServerLevel world, SpawningCondition<?> condition, boolean allowed) {
        boolean valid() { return NativeAreaPruning.worldAllows(condition, world) == allowed; }
    }
    record Anchor(UUID player, ServerPlayer source, ServerLevel world, Vec3 position,
            boolean raining, boolean thundering, int moonPhase) {
        boolean valid(MinecraftServer server) {
            var current = server.getPlayerList().getPlayer(player);
            return current == source && current.isAlive() && !current.isSpectator()
                    && current.serverLevel() == world;
        }

        boolean environmentValid() {
            return world.isRaining() == raining && world.isThundering() == thundering && world.getMoonPhase() == moonPhase;
        }
    }

    private final MinecraftServer server;
    private final List<Rule> rules;
    private final List<Anchor> anchors;
    private final List<GlobalCondition> globalConditions;
    private final SpawnPool pool;
    private final Random random;
    private final List<PlayerChoices> results = new ArrayList<>();
    private final Set<UUID> completedPlayers = new HashSet<>();
    private final int diameter, height, correction, haloH, haloV, tileSize, stepHeight;
    private final double minimum, maximum;
    private int playerIndex;
    private PlayerWork work;
    private boolean done;
    private final long startedTick;

    NativeSpawnScan(MinecraftServer server, String group, List<Rule> rules, Random random, long startedTick) {
        this.server = server;
        this.rules = List.copyOf(rules);
        this.random = random;
        this.startedTick = startedTick;
        anchors = new ArrayList<>(server.getPlayerList().getPlayers().stream()
                .filter(player -> player.isAlive() && !player.isSpectator())
                .map(player -> new Anchor(player.getUUID(), player, player.serverLevel(), player.position(),
                        player.serverLevel().isRaining(), player.serverLevel().isThundering(), player.serverLevel().getMoonPhase())).toList());
        List<GlobalCondition> clocks = new ArrayList<>();
        anchors.stream().map(Anchor::world).distinct().forEach(world -> rules.forEach(rule ->
                NativeStructureRules.conditions(rule.detail()).forEach(condition -> clocks.add(
                        new GlobalCondition(world, condition, NativeAreaPruning.worldAllows(condition, world))))));
        globalConditions = clocks;
        pool = new SpawnPool("cobbletracker_" + group);
        rules.forEach(rule -> pool.getDetails().add(rule.detail()));
        pool.precalculate();
        var config = Cobblemon.INSTANCE.getConfig();
        diameter = config.getSpawningZoneDiameter();
        height = config.getSpawningZoneHeight();
        correction = config.getMaxVerticalCorrectionBlocks();
        minimum = config.getMinimumSpawningZoneDistanceFromPlayer();
        maximum = config.getMaximumSpawningZoneDistanceFromPlayer();
        haloH = config.getMaxNearbyBlocksHorizontalRange();
        haloV = Math.max(1, Math.max(config.getMaxVerticalSpace(), config.getMaxNearbyBlocksVerticalRange()));
        if (diameter < 1 || diameter > 64 || height < 1 || height > 64 || correction < 0 || correction > 256
                || !Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum < 0 || maximum < minimum
                || maximum > 256 || haloH < 0 || haloH > 16 || haloV > 64) {
            throw new IllegalArgumentException("Custom spawn search requires diameter/height 1..64, correction/radius <=256, horizontal context <=16 and vertical context <=64");
        }
        int wideStepHeight = Math.min(height, 65536 / ((16 + 2 * haloH) * (16 + 2 * haloH)) - 2 * haloV);
        tileSize = wideStepHeight > 0 ? 16 : 8;
        stepHeight = tileSize == 16 ? wideStepHeight
                : Math.min(height, 65536 / ((8 + 2 * haloH) * (8 + 2 * haloH)) - 2 * haloV);
        if (stepHeight < 1) throw new IllegalArgumentException("Native context halo exceeds the custom spawn cell budget (65536 blocks)");
    }

    boolean done() { return done; }
    List<PlayerChoices> results() { return List.copyOf(results); }
    List<Anchor> anchors() { return List.copyOf(anchors); }
    long startedTick() { return startedTick; }
    List<GlobalCondition> globalConditions() { return List.copyOf(globalConditions); }
    boolean environmentValid() {
        return anchors.stream().allMatch(Anchor::environmentValid)
                && globalConditions.stream().allMatch(GlobalCondition::valid);
    }

    void prioritize(UUID id) {
        for (int i = playerIndex; i < anchors.size(); i++) {
            if (anchors.get(i).player().equals(id)) {
                anchors.add(playerIndex, anchors.remove(i));
                return;
            }
        }
    }

    boolean playerFinished(UUID id) {
        return completedPlayers.contains(id) && anchors.stream()
                .filter(anchor -> anchor.player().equals(id)).anyMatch(anchor -> anchor.valid(server));
    }

    boolean anchorsValid() {
        return anchors.stream().allMatch(anchor -> anchor.valid(server)) && environmentValid();
    }

    void removePlayer(UUID id) {
        results.removeIf(result -> result.anchor().equals(id));
        completedPlayers.remove(id);
        if (work != null && work.anchor.player().equals(id)) work = null;
        for (int i = anchors.size() - 1; i >= 0; i--) {
            if (!anchors.get(i).player().equals(id)) continue;
            anchors.remove(i);
            if (i < playerIndex) playerIndex--;
        }
        globalConditions.removeIf(condition -> anchors.stream().noneMatch(anchor -> anchor.world() == condition.world()));
    }

    void discardUnavailablePlayers() {
        anchors.stream().filter(anchor -> !anchor.valid(server)).map(Anchor::player).toList()
                .forEach(this::removePlayer);
    }

    void advance(long deadline) {
        int visited = 0;
        while (!done && visited++ < 64 && System.nanoTime() < deadline) {
            if (work == null) {
                if (playerIndex == anchors.size()) { done = true; return; }
                Anchor anchor = anchors.get(playerIndex++);
                if (!anchor.valid(server)) continue;
                work = new PlayerWork(anchor, server.getPlayerList().getPlayer(anchor.player()));
            }
            if (!work.anchor.valid(server)) { work = null; continue; }
            if (work.regions.isEmpty()) {
                if (work.cell == null) {
                    if (!work.cells.hasNext()) {
                        List<Choice> choices = new ArrayList<>();
                        work.candidates.forEach((rule, candidates) -> {
                            if (!candidates.isEmpty()) choices.add(new Choice(rule, candidates.selected(), candidates.meanWeight()));
                        });
                        if (!choices.isEmpty()) results.add(new PlayerChoices(work.anchor.player(), List.copyOf(choices)));
                        completedPlayers.add(work.anchor.player());
                        work = null;
                        continue;
                    }
                    work.cell = work.cells.next();
                    work.y = work.lowY;
                }
                int y = work.y, endY = Math.min(work.highY, y + stepHeight);
                if (endY <= y) { work.cell = null; continue; }
                work.regions.addLast(new Region(work.cell, y, endY));
                work.y = endY;
                if (endY >= work.highY) work.cell = null;
            }
            Region region = work.regions.removeFirst();
            var cell = region.cell();
            int y = region.y(), endY = region.endY();
            List<Rule> possible = work.rulesForPlayer.stream().filter(rule -> rule.detail().isModDependencySatisfied()
                    && NativeAreaPruning.mayMatch(rule.detail(), work.anchor.world(), cell.x(), y, cell.z(),
                            cell.x() + cell.size() - 1, endY - 1, cell.z() + cell.size() - 1)).toList();
            if (possible.isEmpty()) continue;
            ServerLevel world = work.anchor.world();
            if (possible.stream().anyMatch(rule -> NativeStructureRules.constrained(rule.detail()))
                    && !NativeAreaPruning.structureDataLoaded(world, cell.x(), cell.z(),
                            cell.x() + cell.size() - 1, cell.z() + cell.size() - 1)) {
                if (work.subdivide(region)) continue;
                possible = possible.stream().filter(rule -> !NativeStructureRules.constrained(rule.detail())).toList();
                if (possible.isEmpty()) continue;
            }
            if (NativeAreaPruning.groundedOnly(possible.stream().map(Rule::detail).toList())
                    && !NativeAreaPruning.hasPossibleFloor(world, cell.x(), y, cell.z(),
                            cell.x() + cell.size() - 1, endY - 1, cell.z() + cell.size() - 1)) continue;
            int baseY = Math.max(world.getMinBuildHeight(), y - haloV);
            int topY = Math.min(world.getMaxBuildHeight(), endY + haloV);
            var input = new SpawningZoneInput(work.cause, world, cell.x() - haloH, baseY, cell.z() - haloH,
                    cell.size() + 2 * haloH, topY - baseY, cell.size() + 2 * haloH);
            if (!loaded(input)) {
                work.subdivide(region);
                continue;
            }
            for (var position : resolve(work.spawner, input)) {
                var at = position.getPosition();
                if (at.getX() < cell.x() || at.getX() >= cell.x() + cell.size()
                        || at.getZ() < cell.z() || at.getZ() >= cell.z() + cell.size()
                        || at.getY() < y || at.getY() >= endY || !work.coverage.allowsColumn(at.getX(), at.getZ())) continue;
                for (Rule rule : possible) {
                    if (!rule.detail().getSpawnablePositionType().getClazz().equals(position.getClass())) continue;
                    double weight = NativeStructureRules.weight(rule.detail(), position);
                    if (!Double.isFinite(weight)) {
                        throw new IllegalArgumentException("Non-finite native spawn weight for " + rule.detail().getId());
                    }
                    work.candidates.get(rule).offer(new Placement(at.immutable(), input), weight, random.nextDouble());
                }
            }
        }
    }

    static SpawnablePosition refresh(ServerPlayer player, Choice choice) {
        var input = choice.placement().input();
        if (player.serverLevel() != input.getWorld() || !loaded(input)) return null;
        var config = Cobblemon.INSTANCE.getConfig();
        var coverage = new SpawnAreaCoverage(player.getX(), player.getZ(),
                config.getMinimumSpawningZoneDistanceFromPlayer(), config.getMaximumSpawningZoneDistanceFromPlayer(),
                config.getSpawningZoneDiameter());
        var at = choice.placement().at();
        if (NativeStructureRules.constrained(choice.rule().detail())
                && !NativeAreaPruning.structureDataLoaded(input.getWorld(), at.getX(), at.getZ(), at.getX(), at.getZ())) return null;
        double extent = config.getMaxVerticalCorrectionBlocks() + config.getSpawningZoneHeight() / 2.0;
        if (!coverage.allowsColumn(at.getX(), at.getZ()) || at.getY() < Math.floor(player.getY() - extent)
                || at.getY() > Math.floor(player.getY() + extent)) return null;
        var pool = new SpawnPool("cobbletracker_placement");
        pool.getDetails().add(choice.rule().detail());
        pool.precalculate();
        var spawner = new PlayerSpawner(player, pool);
        var freshInput = new SpawningZoneInput(new SpawnCause(spawner, player), input.getWorld(),
                input.getBaseX(), input.getBaseY(), input.getBaseZ(), input.getLength(), input.getHeight(), input.getWidth());
        for (var position : resolve(spawner, freshInput)) {
            if (position.getPosition().equals(choice.placement().at())
                    && choice.rule().detail().getSpawnablePositionType().getClazz().equals(position.getClass())
                    && choice.rule().detail().isModDependencySatisfied()) {
                double weight = NativeStructureRules.weight(choice.rule().detail(), position);
                if (Double.isFinite(weight) && weight > 0) return position;
            }
        }
        return null;
    }

    private static List<? extends SpawnablePosition> resolve(PlayerSpawner spawner, SpawningZoneInput input) {
        return spawner.getResolver().resolve(spawner,
                SpawnablePositionCalculator.Companion.getPrioritizedAreaCalculators(),
                spawner.getGenerator().generate(spawner, input));
    }

    private static boolean loaded(SpawningZoneInput input) {
        var world = input.getWorld();
        int maxX = input.getBaseX() + input.getLength(), maxZ = input.getBaseZ() + input.getWidth();
        var border = world.getWorldBorder();
        if (input.getBaseX() < border.getMinX() || maxX > border.getMaxX()
                || input.getBaseZ() < border.getMinZ() || maxZ > border.getMaxZ()) return false;
        for (int x = input.getBaseX() >> 4; x <= (maxX - 1) >> 4; x++) {
            for (int z = input.getBaseZ() >> 4; z <= (maxZ - 1) >> 4; z++) {
                if (!world.areEntitiesLoaded(ChunkPos.asLong(x, z)) || world.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    private final class PlayerWork {
        final Anchor anchor;
        final PlayerSpawner spawner;
        final SpawnCause cause;
        final SpawnAreaCoverage coverage;
        final Iterator<SpawnAreaCoverage.Cell> cells;
        final Deque<Region> regions = new ArrayDeque<>();
        final Map<Rule, SpawnRuleCandidates<Placement>> candidates = new LinkedHashMap<>();
        final List<Rule> rulesForPlayer;
        final int lowY, highY;
        SpawnAreaCoverage.Cell cell;
        int y;

        PlayerWork(Anchor anchor, ServerPlayer player) {
            this.anchor = anchor;
            spawner = new PlayerSpawner(player, pool);
            cause = new SpawnCause(spawner, player);
            coverage = new SpawnAreaCoverage(anchor.position().x, anchor.position().z, minimum, maximum, diameter, tileSize);
            rulesForPlayer = rules.stream().filter(rule -> rule.detail().isModDependencySatisfied()
                    && NativeAreaPruning.worldAllows(rule.detail(), anchor.world())).toList();
            cells = rulesForPlayer.isEmpty() ? Collections.emptyIterator() : coverage.iterator();
            double extent = correction + height / 2.0;
            lowY = Math.max(anchor.world().getMinBuildHeight(), (int) Math.floor(anchor.position().y - extent));
            highY = Math.min(anchor.world().getMaxBuildHeight(), (int) Math.floor(anchor.position().y + extent) + 1);
            rules.forEach(rule -> candidates.put(rule, new SpawnRuleCandidates<>()));
        }

        boolean subdivide(Region region) {
            var cell = region.cell();
            if (cell.size() <= 8) return false;
            regions.addAll(region.split());
            return true;
        }
    }
}
