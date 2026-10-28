package com.cobbletracker.common.tracker;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Compiles space-separated tokens with AND semantics. Unknown or invalid tokens match nothing. */
public final class SpecParser {

    /** token key (lower-case) → factory turning the raw value into a matcher. */
    private static final Map<String, Function<String, SpecMatcher>> HANDLERS = new ConcurrentHashMap<>();

    private static final ThreadLocal<String> LAST_ERROR = new ThreadLocal<>();

    static {
        registerBool("isshiny", PokemonSnapshot::shiny);
        registerBool("islegendary", PokemonSnapshot::legendary);
        registerBool("ismythical", PokemonSnapshot::mythical);
        registerBool("isancient", PokemonSnapshot::ancient);
        registerBool("isultrabeast", PokemonSnapshot::ultraBeast);
        registerBool("isboss", PokemonSnapshot::boss);
        registerBool("isstarter", PokemonSnapshot::starter);
        registerBool("isalolan", PokemonSnapshot::alolan);
        registerBool("ishisuian", PokemonSnapshot::hisuian);
        registerBool("isgalarian", PokemonSnapshot::galarian);
        registerBool("ispaldean", PokemonSnapshot::paldean);
        register("species", value -> onSnapshot(snapshot ->
                snapshot.speciesPath().equalsIgnoreCase(strip(value))
                        || snapshot.speciesId().equalsIgnoreCase(value)));
        register("label", value -> onSnapshot(snapshot ->
                snapshot.labels().contains(value.toLowerCase(Locale.ROOT))));
        register("aspect", value -> onSnapshot(snapshot ->
                snapshot.aspects().contains(value.toLowerCase(Locale.ROOT))));
        registerNumber("level", snapshot -> snapshot.level());
        registerNumber("perfectivs", snapshot -> snapshot.perfectIvs());
        registerNumber("ivs", snapshot -> snapshot.ivTotal());
        registerNumber("scale", PokemonSnapshot::scale);
        registerText("gender", PokemonSnapshot::gender);
        registerText("nature", PokemonSnapshot::nature);
        registerText("form", PokemonSnapshot::form);
        registerContextTokens();
    }

    /** Context tokens reject EMPTY. */
    private static void registerContextTokens() {
        register("biome", value -> {
            String wanted = value.trim().toLowerCase(Locale.ROOT);
            if (wanted.isEmpty()) {
                throw new IllegalArgumentException("expected a biome id");
            }
            // Namespaced biomes match exactly; bare paths match any namespace.
            if (wanted.indexOf(':') >= 0) {
                return (snapshot, context) -> !context.isEmpty() && context.biome().equals(wanted);
            }
            return (snapshot, context) -> !context.isEmpty() && context.biomePath().equals(wanted);
        });
        register("dimension", value -> {
            String wanted = value.trim().toLowerCase(Locale.ROOT);
            if (wanted.isEmpty()) {
                throw new IllegalArgumentException("expected a dimension id");
            }
            return (snapshot, context) -> !context.isEmpty() && context.dimension().equals(wanted);
        });
        register("time", value -> {
            String wanted = value.trim().toLowerCase(Locale.ROOT);
            return switch (wanted) {
                case "day" -> (snapshot, context) -> !context.isEmpty() && context.day();
                case "night" -> (snapshot, context) -> !context.isEmpty() && !context.day();
                default -> throw new IllegalArgumentException("expected day or night, got '" + value + "'");
            };
        });
        register("weather", value -> {
            String wanted = value.trim().toLowerCase(Locale.ROOT);
            return switch (wanted) {
                // Rain also matches thunderstorms.
                case "rain" -> (snapshot, context) -> !context.isEmpty() && context.raining();
                case "thunder" -> (snapshot, context) -> !context.isEmpty() && context.thundering();
                case "clear" -> (snapshot, context) -> !context.isEmpty() && !context.raining();
                default -> throw new IllegalArgumentException(
                        "expected rain, thunder or clear, got '" + value + "'");
            };
        });
        
        register("y", SpecParser::contextComparison);
    }

    /** {@code y:>100}, {@code y:<0} - the same comparison vocabulary, read off the context instead. */
    private static SpecMatcher contextComparison(String value) {
        String v = value.trim();
        if (v.startsWith(">=")) {
            double n = parseNumber(v.substring(2));
            return (snapshot, context) -> !context.isEmpty() && context.y() >= n;
        }
        if (v.startsWith("<=")) {
            double n = parseNumber(v.substring(2));
            return (snapshot, context) -> !context.isEmpty() && context.y() <= n;
        }
        if (v.startsWith(">")) {
            double n = parseNumber(v.substring(1));
            return (snapshot, context) -> !context.isEmpty() && context.y() > n;
        }
        if (v.startsWith("<")) {
            double n = parseNumber(v.substring(1));
            return (snapshot, context) -> !context.isEmpty() && context.y() < n;
        }
        double n = parseNumber(v);
        return (snapshot, context) -> !context.isEmpty() && Math.abs(context.y() - n) < EPSILON;
    }

    /** Numeric comparison handler for equality and ordered comparisons. */
    private static void registerNumber(String key, java.util.function.ToDoubleFunction<PokemonSnapshot> prop) {
        register(key, value -> comparison(value, prop));
    }

    /** A token compared as lower-case text ({@code gender:female}, {@code nature:adamant}). */
    private static void registerText(String key, java.util.function.Function<PokemonSnapshot, String> prop) {
        register(key, value -> {
            String wanted = value.trim().toLowerCase(Locale.ROOT);
            if (wanted.isEmpty()) {
                throw new IllegalArgumentException("expected a value");
            }
            return onSnapshot(snapshot -> wanted.equals(prop.apply(snapshot)));
        });
    }

    private SpecParser() {
    }

    /** Registers/overrides a token handler. {@code key} is matched case-insensitively. */
    public static void register(String key, Function<String, SpecMatcher> factory) {
        HANDLERS.put(key.toLowerCase(Locale.ROOT), factory);
    }

    private static void registerBool(String key, java.util.function.Predicate<PokemonSnapshot> prop) {
        register(key, value -> {
            boolean expected = parseBool(value);
            return onSnapshot(snapshot -> prop.test(snapshot) == expected);
        });
    }

    /** Adapts a Pokemon-only predicate to the context-aware matcher. */
    private static SpecMatcher onSnapshot(java.util.function.Predicate<PokemonSnapshot> predicate) {
        return (snapshot, context) -> predicate.test(snapshot);
    }

    /** A bare boolean token means true; invalid boolean values reject compilation. */
    private static boolean parseBool(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) {
            return true;
        }
        return switch (v) {
            case "true", "yes", "y", "on", "1" -> true;
            case "false", "no", "n", "off", "0" -> false;
            default -> throw new IllegalArgumentException("expected true or false, got '" + value + "'");
        };
    }

    /** Returns an unambiguous boolean requirement, or null for absent, negated or alternated tokens. */
    public static Boolean expectation(String spec, String key) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String wanted = key.toLowerCase(Locale.ROOT);
        for (String rawToken : spec.trim().split("\\s+")) {
            if (rawToken.isBlank() || rawToken.charAt(0) == '!') {
                continue; // negated: no single value to satisfy
            }
            int colon = rawToken.indexOf(':');
            String k = (colon < 0 ? rawToken : rawToken.substring(0, colon)).toLowerCase(Locale.ROOT);
            if (!k.equals(wanted)) {
                continue;
            }
            String value = colon < 0 ? "" : rawToken.substring(colon + 1);
            if (value.indexOf('|') >= 0) {
                return null; // alternatives: ambiguous
            }
            try {
                return parseBool(value);
            } catch (RuntimeException e) {
                return null; // compile() reports the same bad token; nothing to add here
            }
        }
        return null;
    }

    /** The most recent compilation error on this thread; reset by the next compile call. */
    public static String lastError() {
        return LAST_ERROR.get();
    }

    /**
     * Compiles AND-ed tokens with ! negation and | alternatives. Blank specs match all; invalid specs
     * match none.
     */
    public static SpecMatcher compile(String spec) {
        LAST_ERROR.remove();
        if (spec == null || spec.isBlank()) {
            return SpecMatcher.ANY;
        }
        SpecMatcher result = SpecMatcher.ANY;
        for (String rawToken : spec.trim().split("\\s+")) {
            if (rawToken.isBlank()) {
                continue;
            }
            boolean negated = rawToken.charAt(0) == '!';
            String token = negated ? rawToken.substring(1) : rawToken;
            if (token.isBlank()) {
                LAST_ERROR.set("'!' with nothing to negate");
                return SpecMatcher.NONE;
            }
            int colon = token.indexOf(':');
            String key = (colon < 0 ? token : token.substring(0, colon)).toLowerCase(Locale.ROOT);
            String value = colon < 0 ? "" : token.substring(colon + 1);
            Function<String, SpecMatcher> handler = HANDLERS.get(key);
            if (handler == null) {
                LAST_ERROR.set("unknown spec token '" + key + "'");
                return SpecMatcher.NONE;
            }
            SpecMatcher matcher;
            try {
                matcher = alternatives(handler, value);
            } catch (RuntimeException e) {
                LAST_ERROR.set("bad value for '" + key + "': " + value
                        + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
                return SpecMatcher.NONE;
            }
            result = result.and(negated ? matcher.negate() : matcher);
        }
        return result;
    }

    /** ORs alternatives and rejects empty branches. */
    private static SpecMatcher alternatives(Function<String, SpecMatcher> handler, String value) {
        if (value.indexOf('|') < 0) {
            return handler.apply(value);
        }
        SpecMatcher result = null;
        for (String part : value.split("\\|", -1)) {
            if (part.isBlank()) {
                throw new IllegalArgumentException("empty alternative around '|'");
            }
            SpecMatcher one = handler.apply(part);
            result = result == null ? one : result.or(one);
        }
        return result == null ? SpecMatcher.NONE : result;
    }

    /** Parses ordered comparisons or numeric equality as doubles. */
    private static SpecMatcher comparison(String value, java.util.function.ToDoubleFunction<PokemonSnapshot> prop) {
        String v = value.trim();
        if (v.startsWith(">=")) {
            double n = parseNumber(v.substring(2));
            return onSnapshot(snapshot -> prop.applyAsDouble(snapshot) >= n);
        }
        if (v.startsWith("<=")) {
            double n = parseNumber(v.substring(2));
            return onSnapshot(snapshot -> prop.applyAsDouble(snapshot) <= n);
        }
        if (v.startsWith(">")) {
            double n = parseNumber(v.substring(1));
            return onSnapshot(snapshot -> prop.applyAsDouble(snapshot) > n);
        }
        if (v.startsWith("<")) {
            double n = parseNumber(v.substring(1));
            return onSnapshot(snapshot -> prop.applyAsDouble(snapshot) < n);
        }
        double n = parseNumber(v);
        // Allow for Cobblemon float widening when comparing decimal scale values.
        return onSnapshot(snapshot -> Math.abs(prop.applyAsDouble(snapshot) - n) < EPSILON);
    }

    /** Tolerance for {@code =} comparisons; well below the gap between any two meaningful values. */
    private static final double EPSILON = 1e-6;

    private static double parseNumber(String raw) {
        double n = Double.parseDouble(raw.trim());
        if (Double.isNaN(n) || Double.isInfinite(n)) {
            // Reject non-finite comparison values.
            throw new IllegalArgumentException("not a usable number: " + raw.trim());
        }
        return n;
    }

    private static String strip(String id) {
        return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
    }
}
