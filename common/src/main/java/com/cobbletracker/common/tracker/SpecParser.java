package com.cobbletracker.common.tracker;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Compiles a category spec string into a {@link SpecMatcher}. A spec is a space-separated list of
 * {@code key} or {@code key:value} tokens, all AND-ed together, e.g.
 * {@code "isLegendary:true level:>50"} or {@code "species:rayquaza"}.
 *
 * <p>Every token is just an entry in {@link #HANDLERS}. Register a new one with
 * {@link #register(String, Function)} and nothing else has to change, so adding a match dimension is a
 * one-line job. Unknown tokens compile to {@link SpecMatcher#NONE} (the category simply records nothing)
 * and are reported via {@link #lastError()} rather than throwing, so a bad config never crashes tracking.
 */
public final class SpecParser {

    /** token key (lower-case) → factory turning the raw value into a matcher. */
    private static final Map<String, Function<String, SpecMatcher>> HANDLERS = new ConcurrentHashMap<>();

    private static final ThreadLocal<String> LAST_ERROR = new ThreadLocal<>();

    static {
        registerBool("isshiny", PokemonSnapshot::shiny);
        registerBool("islegendary", PokemonSnapshot::legendary);
        registerBool("ismythical", PokemonSnapshot::mythical);
        registerBool("isultrabeast", PokemonSnapshot::ultraBeast);
        registerBool("isboss", PokemonSnapshot::boss);
        register("species", value -> snapshot ->
                snapshot.speciesPath().equalsIgnoreCase(strip(value)) || snapshot.speciesId().equalsIgnoreCase(value));
        register("label", value -> snapshot -> snapshot.labels().contains(value.toLowerCase(Locale.ROOT)));
        register("aspect", value -> snapshot -> snapshot.aspects().contains(value.toLowerCase(Locale.ROOT)));
        register("level", SpecParser::levelMatcher);
    }

    private SpecParser() {
    }

    /** Registers/overrides a token handler. {@code key} is matched case-insensitively. */
    public static void register(String key, Function<String, SpecMatcher> factory) {
        HANDLERS.put(key.toLowerCase(Locale.ROOT), factory);
    }

    private static void registerBool(String key, java.util.function.Predicate<PokemonSnapshot> prop) {
        register(key, value -> {
            boolean expected = value.isEmpty() || Boolean.parseBoolean(value);
            return snapshot -> prop.test(snapshot) == expected;
        });
    }

    /**
     * Why the last {@link #compile} call on this thread fell back to {@link SpecMatcher#NONE}, or
     * {@code null} if it compiled cleanly. Read it right after compiling - the next compile resets it.
     */
    public static String lastError() {
        return LAST_ERROR.get();
    }

    /** Compiles a spec; blank → {@link SpecMatcher#ANY}; any bad token → {@link SpecMatcher#NONE}. */
    public static SpecMatcher compile(String spec) {
        LAST_ERROR.remove();
        if (spec == null || spec.isBlank()) {
            return SpecMatcher.ANY;
        }
        SpecMatcher result = SpecMatcher.ANY;
        for (String token : spec.trim().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            int colon = token.indexOf(':');
            String key = (colon < 0 ? token : token.substring(0, colon)).toLowerCase(Locale.ROOT);
            String value = colon < 0 ? "" : token.substring(colon + 1);
            Function<String, SpecMatcher> handler = HANDLERS.get(key);
            if (handler == null) {
                LAST_ERROR.set("unknown spec token '" + key + "'");
                return SpecMatcher.NONE;
            }
            try {
                result = result.and(handler.apply(value));
            } catch (RuntimeException e) {
                LAST_ERROR.set("bad value for '" + key + "': " + value);
                return SpecMatcher.NONE;
            }
        }
        return result;
    }

    /** {@code level:>50}, {@code level:<10}, {@code level:>=30}, {@code level:25}. */
    private static SpecMatcher levelMatcher(String value) {
        String v = value.trim();
        if (v.startsWith(">=")) {
            int n = Integer.parseInt(v.substring(2).trim());
            return snapshot -> snapshot.level() >= n;
        }
        if (v.startsWith("<=")) {
            int n = Integer.parseInt(v.substring(2).trim());
            return snapshot -> snapshot.level() <= n;
        }
        if (v.startsWith(">")) {
            int n = Integer.parseInt(v.substring(1).trim());
            return snapshot -> snapshot.level() > n;
        }
        if (v.startsWith("<")) {
            int n = Integer.parseInt(v.substring(1).trim());
            return snapshot -> snapshot.level() < n;
        }
        int n = Integer.parseInt(v);
        return snapshot -> snapshot.level() == n;
    }

    private static String strip(String id) {
        return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
    }
}
