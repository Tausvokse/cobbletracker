package com.cobbletracker.mod.spawning;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.reactive.ObservableSubscription;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.SpawnLoader;
import com.cobblemon.mod.common.api.spawning.SpawnSet;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.EntitySpawnResult;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool;
import com.cobbletracker.common.spawn.SpeciesSpawnDefinition;
import com.cobbletracker.common.spawn.SpeciesSpawnMode;
import com.cobbletracker.common.spawn.SpawnGroupSettings;
import com.cobbletracker.common.spawn.WeightedSpeciesOdds;
import com.cobbletracker.common.tracker.TrackerCategory;
import com.cobbletracker.common.tracker.CommandHookGuard;
import com.cobbletracker.mod.CobbleTracker;
import com.cobbletracker.mod.legendary.LegendaryDirector;
import com.cobbletracker.mod.tracker.TrackerServer;
import com.cobbletracker.mod.tracker.CommandHooks;
import com.cobbletracker.mod.protection.SpawnProtectionModule;
import com.cobbletracker.mod.command.Permissions;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobbletracker.mod.text.Msg;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kotlin.Unit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SpeciesSpawnEngine implements AutoCloseable {
    private final MinecraftServer server;
    private final Random random = new Random();
    private final Random samplingRandom = new Random();
    private final Map<String, Long> nextAttempts = new HashMap<>();
    private final Map<String, Snapshot> snapshots = new HashMap<>();
    private final Map<String, NativeSpawnScan> scans = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> reportWaiters = new HashMap<>();
    private final Map<String, Set<UUID>> forcedWaiters = new HashMap<>();
    private final Set<String> pendingAttempts = new HashSet<>();
    private final Map<String, Map<UUID, CommandHookGuard.Origin>> pendingForced = new HashMap<>();
    private final Map<String, Long> retryAfter = new HashMap<>();
    private int scanCursor;
    private final Deque<SpawnTask> spawnTasks = new ArrayDeque<>();
    private volatile Map<String, GroupSnapshot> published = Map.of();
    private Map<String, SpawnGroupSettings> groups = Map.of();
    private Map<String, List<Entry>> entries = Map.of();
    private Set<String> replaced = Set.of();
    private SpawnPool naturalPool;
    private List<SpawnDetail> upstream = List.of();
    private ObservableSubscription<SpawnPool> subscription;
    private long ticks;

    public SpeciesSpawnEngine(MinecraftServer server) {
        this.server = server;
        watchPool();
    }

    public boolean reload(Map<String, SpawnGroupSettings> settings, List<SpeciesSpawnDefinition> definitions) {
        Map<String, List<Entry>> prepared = new LinkedHashMap<>();
        Set<String> replacements = new HashSet<>();
        Set<String> entryIds = new HashSet<>();
        try {
            for (SpeciesSpawnDefinition definition : definitions) {
                if (!definition.enabled()) {
                    continue;
                }
                SpawnGroupSettings group = settings.get(definition.group());
                if (group == null) {
                    throw new IllegalArgumentException("Unknown spawn group: " + definition.group());
                }
                if (!group.enabled()) {
                    continue;
                }
                JsonObject set = new JsonObject();
                set.addProperty("enabled", true);
                JsonArray spawns = new JsonArray();
                List<JsonObject> authored = definition.spawns();
                authored.forEach(json -> {
                    NativeSpawnSchema.validatePresets(json);
                    spawns.add(json.deepCopy());
                });
                set.add("spawns", spawns);
                SpawnSet parsed = SpawnLoader.INSTANCE.getGson().fromJson(set, SpawnSet.class);
                List<Entry> target = prepared.computeIfAbsent(definition.group(), key -> new ArrayList<>());
                int entryIndex = 0;
                for (SpawnDetail detail : parsed.getSpawns()) {
                    NativeSpawnSchema.validate(authored.get(entryIndex++), detail);
                    if (detail.getId() == null || detail.getId().isBlank() || !entryIds.add(detail.getId())) {
                        throw new IllegalArgumentException("Native spawn IDs must be nonempty and unique: " + detail.getId());
                    }
                    if (!(detail instanceof PokemonSpawnDetail pokemon)
                            || !species(pokemon).equals(normalize(definition.species()))) {
                        throw new IllegalArgumentException("Spawn entry does not match species " + definition.species());
                    }
                    detail.onServerLoad(server);
                    if (detail.getPercentage() != -1) {
                        throw new IllegalArgumentException("Scheduled entries use group chance and relative weight; remove percentage from " + detail.getId());
                    }
                    if (!detail.isValid() || !Float.isFinite(detail.getWeight()) || detail.getWeight() <= 0) {
                        throw new IllegalArgumentException("Invalid native spawn entry: " + detail.getId());
                    }
                    target.add(new Entry(normalize(definition.species()), pokemon));
                }
                if (definition.mode() == SpeciesSpawnMode.REPLACE) {
                    replacements.add(normalize(definition.species()));
                }
            }
            watchPool();
            List<SpawnDetail> replacementPool = overlay(replacements);
            SpawnPool stagedPool = new SpawnPool("cobbletracker_validation");
            stagedPool.getDetails().addAll(replacementPool);
            stagedPool.precalculate();
            List<SpawnDetail> oldDetails = List.copyOf(naturalPool.getDetails());
            try {
                replaceDetails(replacementPool);
            } catch (RuntimeException | LinkageError failure) {
                replaceDetails(oldDetails);
                throw failure;
            }
        } catch (RuntimeException | LinkageError failure) {
            CobbleTracker.LOGGER.error("Custom spawn reload rejected; previous settings retained", failure);
            return false;
        }
        groups = Map.copyOf(settings);
        Map<String, List<Entry>> frozen = new LinkedHashMap<>();
        prepared.forEach((name, values) -> frozen.put(name, List.copyOf(values)));
        entries = Map.copyOf(frozen);
        replaced = Set.copyOf(replacements);
        nextAttempts.clear();
        snapshots.clear();
        clearPending();
        groups.forEach((name, group) -> nextAttempts.put(name, ticks + group.intervalTicks()));
        LegendaryDirector.invalidate();
        publish();
        return true;
    }

    public boolean managesGroup(String group) {
        return entries.containsKey(group) && !entries.get(group).isEmpty();
    }

    public boolean managesCategory(TrackerCategory category) {
        String label = LegendaryDirector.tierLabel(category);
        return managesGroup(category.id()) || ("legendary".equals(label) && managesGroup("legendary"));
    }

    public boolean isReplacedSpecies(String species) {
        return replaced.contains(normalize(species));
    }

    public void tick() {
        ticks++;
        if (ticks % 100 == 0) {
            entries.keySet().forEach(this::requestScan);
        }
        Set<String> requested = new HashSet<>(pendingAttempts);
        requested.addAll(reportWaiters.keySet());
        requested.addAll(forcedWaiters.keySet());
        for (String group : requested) {
            if (spawnTasks.stream().noneMatch(task -> task.group().equals(group))) requestScan(group);
        }
        for (Map.Entry<String, SpawnGroupSettings> group : groups.entrySet()) {
            String name = group.getKey();
            SpawnGroupSettings settings = group.getValue();
            if (!settings.enabled() || !managesGroup(name) || pendingAttempts.contains(name)
                    || ticks < nextAttempts.getOrDefault(name, Long.MAX_VALUE)) {
                continue;
            }
            nextAttempts.put(name, ticks + settings.intervalTicks());
            if (random.nextDouble() >= settings.chance()) {
                continue;
            }
            pendingAttempts.add(name);
            requestScan(name);
        }
        if (spawnTasks.isEmpty()) advanceScan();
        else executeSpawnTask();
        publish();
    }

    public GroupSnapshot snapshot(String group) {
        return published.getOrDefault(group, new GroupSnapshot(false, 0, 0, 0, Map.of()));
    }

    public String spawnNow(ServerPlayer player, String group) {
        if (!managesGroup(group)) return "No active custom spawn entries for this group.";
        if (!player.isAlive() || player.isSpectator()) return "Custom spawning requires a living, non-spectator player.";
        if (ticks < retryAfter.getOrDefault(group, 0L)) return "The native spawn search is unavailable; see the server log.";
        if (pendingForced.computeIfAbsent(group, ignored -> new HashMap<>())
                .putIfAbsent(player.getUUID(), CommandHooks.captureOrigin()) != null) {
            return "Your previous custom spawn request is still being processed.";
        }
        forcedWaiters.computeIfAbsent(group, ignored -> new HashSet<>()).add(player.getUUID());
        requestScan(group);
        if (!scans.containsKey(group)) return "The native spawn search could not start; see the server log.";
        scans.get(group).prioritize(player.getUUID());
        player.sendSystemMessage(Msg.info("Checking native spawn locations; the result will follow when the area search finishes."));
        return null;
    }

    private boolean spawn(NativeSpawnScan.PlayerChoices player, CommandHookGuard.Origin origin) {
        NativeSpawnScan.Choice chosen = choose(player.choices());
        ServerPlayer anchor = server.getPlayerList().getPlayer(player.anchor());
        if (chosen == null || anchor == null || !anchor.isAlive() || anchor.isSpectator()
                || anchor.serverLevel() != chosen.placement().input().getWorld()) return false;
        var fresh = NativeSpawnScan.refresh(anchor, chosen);
        if (fresh == null) return false;
        Object result = CommandHooks.withOrigin(origin, () -> CobblemonSpawnBridge.spawn(chosen.rule().detail(), fresh));
        if (!(result instanceof EntitySpawnResult spawned) || spawned.getEntities().isEmpty()) return false;
        TrackerServer tracker = TrackerServer.get();
        if (tracker != null) {
            for (var entity : spawned.getEntities()) {
                if (entity instanceof PokemonEntity pokemon) {
                    SpawnProtectionModule.protect(pokemon.getPokemon().getUuid(), anchor, tracker.config().spawnProtection());
                }
            }
        }
        return true;
    }

    private void publish() {
        Map<String, GroupSnapshot> fresh = new LinkedHashMap<>();
        groups.forEach((name, settings) -> {
            Snapshot sampled = snapshots.get(name);
            boolean enabled = settings.enabled() && managesGroup(name);
            boolean available = sampled != null && valid(sampled);
            SearchState state = !enabled ? SearchState.DISABLED
                    : scans.containsKey(name) ? (available ? SearchState.REFRESHING : SearchState.CALCULATING)
                    : retryAfter.getOrDefault(name, 0L) > ticks ? SearchState.FAILED
                    : available ? SearchState.READY : SearchState.CALCULATING;
            fresh.put(name, new GroupSnapshot(enabled, settings.chance(),
                    settings.intervalTicks(), Math.max(0, nextAttempts.getOrDefault(name, ticks) - ticks),
                    available ? sampled.shares() : Map.of(), state,
                    available ? Math.max(0, ticks - sampled.tick()) : -1));
        });
        published = Map.copyOf(fresh);
    }

    public void sendReport(ServerPlayer player, String group) {
        SpawnGroupSettings settings = groups.get(group);
        if (settings == null || !settings.enabled() || !managesGroup(group)) {
            player.sendSystemMessage(Msg.info("No active custom spawn entries for group '" + group + "'."));
            return;
        }
        if (ticks < retryAfter.getOrDefault(group, 0L)) {
            player.sendSystemMessage(Msg.warn("Native spawn search is unavailable; see the server log."));
            return;
        }
        long seconds = Math.max(0, nextAttempts.getOrDefault(group, ticks) - ticks + 19) / 20;
        player.sendSystemMessage(Msg.info(String.format(Locale.ROOT,
                "%s: %.2f%% per shared attempt; next attempt in %dm %ds. Natural append spawns are separate.",
                group, settings.chance() * 100, seconds / 60, seconds % 60)));
        Snapshot snapshot = snapshots.get(group);
        if (snapshot == null || !valid(snapshot) || ticks - snapshot.tick() >= 100) {
            reportWaiters.computeIfAbsent(group, ignored -> new HashSet<>()).add(player.getUUID());
            requestScan(group);
            if (!scans.containsKey(group)) return;
            player.sendSystemMessage(Msg.info("Calculating chances across loaded spawn areas; the complete result will follow."));
            return;
        }
        sendShares(player, group, snapshot);
    }

    private void sendShares(ServerPlayer player, String group, Snapshot snapshot) {
        String observation = "Observed players' loaded areas around captured positions; "
                + Math.max(0, ticks - snapshot.tick()) / 20 + "s old, collected over "
                + Math.max(1, (snapshot.tick() - snapshot.startedTick() + 19) / 20) + "s.";
        if (snapshot.players().isEmpty()) {
            player.sendSystemMessage(Msg.info("No eligible native spawn positions in the completed search.\n" + observation));
            return;
        }
        List<WeightedSpeciesOdds.Share> shares = snapshot.shares().entrySet().stream()
                .map(share -> new WeightedSpeciesOdds.Share(share.getKey(), share.getValue())).toList();
        String chances = WeightedSpeciesOdds.percentages(shares).stream().map(share -> {
            String path = share.species().substring(share.species().indexOf(':') + 1);
            var species = PokemonSpecies.getByName(path);
            String name = species == null ? path : species.getName();
            return String.format(Locale.ROOT, "%s - %.2f%%", name, share.percent());
        }).collect(Collectors.joining(", "));
        String title = Character.toUpperCase(group.charAt(0)) + group.substring(1).replace('_', ' ');
        player.sendSystemMessage(Msg.info(title + " chances:\n" + chances
                + "\nShares after a successful roll. " + observation));
    }


    private void requestScan(String group) {
        if (!managesGroup(group) || scans.containsKey(group) || ticks < retryAfter.getOrDefault(group, 0L)) return;
        try {
            var rules = entries.get(group).stream()
                    .map(entry -> new NativeSpawnScan.Rule(entry.species(), entry.detail())).toList();
            var scan = new NativeSpawnScan(server, group, rules, samplingRandom, ticks);
            forcedWaiters.getOrDefault(group, Set.of()).forEach(scan::prioritize);
            scans.put(group, scan);
        } catch (RuntimeException | LinkageError failure) {
            failedScan(group, failure);
        }
    }

    private void advanceScan() {
        if (scans.isEmpty()) return;
        List<String> active = List.copyOf(scans.keySet());
        String group = active.get(Math.floorMod(scanCursor++, active.size()));
        NativeSpawnScan scan = scans.get(group);
        try {
            scan.discardUnavailablePlayers();
            if (!scan.environmentValid()) {
                scans.remove(group);
                snapshots.remove(group);
                requestScan(group);
                return;
            }
            scan.advance(System.nanoTime() + 2_000_000L);
            scan.discardUnavailablePlayers();
            queueForced(group, scan);
            if (!scan.done()) return;
            scans.remove(group);
            if (!scan.anchorsValid()) {
                snapshots.remove(group);
                requestScan(group);
                return;
            }
            Map<String, Double> shares = new LinkedHashMap<>();
            var weighted = scan.results().stream().map(player -> player.choices().stream()
                    .map(choice -> new WeightedSpeciesOdds.Weight(choice.rule().species(), choice.weight())).toList()).toList();
            WeightedSpeciesOdds.aggregatePlayers(weighted).forEach(share -> shares.put(share.species(), share.share()));
            Snapshot snapshot = new Snapshot(ticks, scan.results(), Collections.unmodifiableMap(shares), scan.anchors(),
                    scan.startedTick(), scan.globalConditions());
            snapshots.put(group, snapshot);
            for (UUID id : reportWaiters.getOrDefault(group, Set.of())) {
                var player = server.getPlayerList().getPlayer(id);
                if (player != null) sendShares(player, group, snapshot);
            }
            reportWaiters.remove(group);
            if (pendingAttempts.contains(group)) {
                if (snapshot.players().isEmpty()) pendingAttempts.remove(group);
                else spawnTasks.addLast(new SpawnTask(group, choosePlayer(snapshot.players()), null, snapshot, CommandHookGuard.Origin.NORMAL));
            }
        } catch (RuntimeException | LinkageError failure) {
            failedScan(group, failure);
        }
    }

    private void queueForced(String group, NativeSpawnScan scan) {
        Set<UUID> waiting = forcedWaiters.get(group);
        if (waiting == null) return;
        for (UUID id : Set.copyOf(waiting)) {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                waiting.remove(id);
                releaseForced(group, id);
                continue;
            }
            if (!player.isAlive() || player.isSpectator()) {
                waiting.remove(id);
                releaseForced(group, id);
                player.sendSystemMessage(Msg.info("Spawn request cancelled because you are no longer eligible."));
                continue;
            }
            if (!scan.playerFinished(id)) continue;
            waiting.remove(id);
            var choices = scan.results().stream().filter(candidate -> candidate.anchor().equals(id)).findFirst().orElse(null);
            if (choices == null) {
                releaseForced(group, id);
                player.sendSystemMessage(Msg.info("No eligible native spawn position was found near you."));
                continue;
            }
            var local = new Snapshot(ticks, List.of(choices), Map.of(), scan.anchors(), scan.startedTick(), scan.globalConditions());
            spawnTasks.addLast(new SpawnTask(group, choices, id, local, pendingForced.get(group).get(id)));
        }
        if (waiting.isEmpty()) forcedWaiters.remove(group);
    }

    private boolean valid(Snapshot snapshot) {
        return snapshot.anchors().stream().allMatch(anchor -> anchor.valid(server))
                && snapshot.anchors().stream().allMatch(NativeSpawnScan.Anchor::environmentValid)
                && snapshot.globalConditions().stream().allMatch(NativeSpawnScan.GlobalCondition::valid);
    }

    private void executeSpawnTask() {
        SpawnTask task = spawnTasks.removeFirst();
        boolean placed = false;
        boolean denied = false;
        try {
            boolean current = task.requester() == null ? valid(task.snapshot())
                    : task.snapshot().anchors().stream().filter(anchor -> anchor.player().equals(task.requester()))
                            .anyMatch(anchor -> anchor.valid(server));
            if (task.requester() != null) {
                var requester = server.getPlayerList().getPlayer(task.requester());
                denied = requester != null && !Permissions.check(requester, Permissions.SPAWNLEGENDARY, Permissions.LEVEL_OPERATOR);
                current &= requester != null && !denied;
            }
            placed = current && spawn(task.choices(), task.origin());
        } catch (RuntimeException | LinkageError failure) {
            CobbleTracker.LOGGER.error("Custom spawn placement failed", failure);
        } finally {
            if (task.requester() == null) pendingAttempts.remove(task.group());
            else releaseForced(task.group(), task.requester());
        }
        if (task.requester() != null) {
            var player = server.getPlayerList().getPlayer(task.requester());
            if (player != null) player.sendSystemMessage(denied ? Msg.warn("Spawn request cancelled because your permission changed.")
                    : placed ? Msg.info("The custom spawn was placed.")
                    : Msg.info("No Pokemon was placed: conditions changed before placement."));
        }
    }

    private void failedScan(String group, Throwable failure) {
        scans.remove(group);
        snapshots.remove(group);
        pendingAttempts.remove(group);
        pendingForced.remove(group);
        retryAfter.put(group, ticks + 100);
        Set<UUID> waiting = new HashSet<>(reportWaiters.getOrDefault(group, Set.of()));
        waiting.addAll(forcedWaiters.getOrDefault(group, Set.of()));
        spawnTasks.removeIf(task -> {
            if (!task.group().equals(group)) return false;
            if (task.requester() != null) waiting.add(task.requester());
            return true;
        });
        reportWaiters.remove(group);
        forcedWaiters.remove(group);
        for (UUID id : waiting) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(Msg.warn("Native spawn search failed; see the server log."));
        }
        CobbleTracker.LOGGER.error("Native spawn search failed for group {}", group, failure);
    }

    private void releaseForced(String group, UUID id) {
        Map<UUID, CommandHookGuard.Origin> pending = pendingForced.get(group);
        if (pending != null) {
            pending.remove(id);
            if (pending.isEmpty()) pendingForced.remove(group);
        }
    }

    public void playerLeft(UUID id) {
        reportWaiters.values().forEach(waiting -> waiting.remove(id));
        forcedWaiters.values().forEach(waiting -> waiting.remove(id));
        pendingForced.values().forEach(waiting -> waiting.remove(id));
        spawnTasks.removeIf(task -> id.equals(task.requester()));
        reportWaiters.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        forcedWaiters.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        pendingForced.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        snapshots.clear();
        scans.values().forEach(scan -> scan.removePlayer(id));
    }

    private void clearPending() {
        Set<UUID> waiting = new HashSet<>();
        reportWaiters.values().forEach(waiting::addAll);
        forcedWaiters.values().forEach(waiting::addAll);
        spawnTasks.forEach(task -> { if (task.requester() != null) waiting.add(task.requester()); });
        for (UUID id : waiting) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(Msg.info("Spawn search cancelled because spawn settings changed."));
        }
        scans.clear();
        reportWaiters.clear();
        forcedWaiters.clear();
        pendingAttempts.clear();
        pendingForced.clear();
        retryAfter.clear();
        spawnTasks.clear();
    }

    private NativeSpawnScan.PlayerChoices choosePlayer(List<NativeSpawnScan.PlayerChoices> players) {
        double maximum = players.stream().flatMap(player -> player.choices().stream())
                .mapToDouble(NativeSpawnScan.Choice::weight).max().orElse(1);
        double total = players.stream().flatMap(player -> player.choices().stream())
                .mapToDouble(choice -> choice.weight() / maximum).sum();
        double roll = random.nextDouble() * total;
        for (var player : players) {
            for (var choice : player.choices()) roll -= choice.weight() / maximum;
            if (roll < 0) return player;
        }
        return players.getLast();
    }

    private NativeSpawnScan.Choice choose(List<NativeSpawnScan.Choice> choices) {
        double maximum = choices.stream().mapToDouble(NativeSpawnScan.Choice::weight).max().orElse(1);
        double roll = random.nextDouble() * choices.stream().mapToDouble(c -> c.weight() / maximum).sum();
        for (var choice : choices) {
            roll -= choice.weight() / maximum;
            if (roll < 0) return choice;
        }
        return choices.isEmpty() ? null : choices.getLast();
    }

    private void watchPool() {
        SpawnPool pool = CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL();
        if (pool == naturalPool) {
            return;
        }
        if (subscription != null) {
            subscription.unsubscribe();
        }
        naturalPool = pool;
        upstream = List.copyOf(pool.getDetails());
        subscription = pool.getObservable().subscribe(Priority.LOWEST, reloaded -> {
            upstream = List.copyOf(reloaded.getDetails());
            suppressReplaced();
            snapshots.clear();
            clearPending();
            publish();
            return Unit.INSTANCE;
        });
    }

    private void suppressReplaced() {
        replaceDetails(overlay(replaced));
        LegendaryDirector.invalidate();
    }

    private List<SpawnDetail> overlay(Set<String> replacements) {
        List<SpawnDetail> result = new ArrayList<>();
        for (SpawnDetail detail : upstream) {
            if (detail instanceof PokemonSpawnDetail pokemon && replacements.contains(species(pokemon))) {
                continue;
            }
            if (detail instanceof PokemonHerdSpawnDetail herd && herd.getHerdablePokemon().stream()
                    .anyMatch(member -> replacements.contains(normalize(member.getPokemon().getSpecies())))) {
                var gson = SpawnLoader.INSTANCE.getGson();
                PokemonHerdSpawnDetail copy = gson.fromJson(gson.toJsonTree(herd), PokemonHerdSpawnDetail.class);
                copy.getHerdablePokemon().removeIf(member -> replacements.contains(normalize(member.getPokemon().getSpecies())));
                if (copy.getHerdablePokemon().isEmpty() || !copy.isValid()) {
                    continue;
                }
                copy.onServerLoad(server);
                detail = copy;
            }
            result.add(detail);
        }
        return result;
    }

    private void replaceDetails(List<SpawnDetail> details) {
        naturalPool.getDetails().clear();
        naturalPool.getDetails().addAll(details);
        naturalPool.precalculate();
    }

    private static String species(PokemonSpawnDetail detail) {
        return normalize(detail.getPokemon().getSpecies());
    }

    private static String normalize(String species) {
        String value = species == null ? "" : species.toLowerCase(Locale.ROOT);
        return value.contains(":") ? value : "cobblemon:" + value;
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.unsubscribe();
        }
        replaced = Set.of();
        suppressReplaced();
        snapshots.clear();
        clearPending();
        published = Map.of();
    }

    public enum SearchState { DISABLED, CALCULATING, READY, REFRESHING, FAILED }

    public record GroupSnapshot(boolean enabled, double chance, int intervalTicks, long remainingTicks,
            Map<String, Double> speciesShares, SearchState state, long ageTicks) {
        public GroupSnapshot(boolean enabled, double chance, int intervalTicks, long remainingTicks,
                Map<String, Double> speciesShares) {
            this(enabled, chance, intervalTicks, remainingTicks, speciesShares,
                    enabled ? SearchState.CALCULATING : SearchState.DISABLED, -1);
        }
        public GroupSnapshot {
            speciesShares = Collections.unmodifiableMap(new LinkedHashMap<>(speciesShares));
        }
    }

    private record Entry(String species, PokemonSpawnDetail detail) {
    }

    private record Snapshot(long tick, List<NativeSpawnScan.PlayerChoices> players, Map<String, Double> shares,
            List<NativeSpawnScan.Anchor> anchors, long startedTick, List<NativeSpawnScan.GlobalCondition> globalConditions) {
    }

    private record SpawnTask(String group, NativeSpawnScan.PlayerChoices choices, UUID requester, Snapshot snapshot,
            CommandHookGuard.Origin origin) { }
}
