package com.cobbletracker.mod.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Central factory for CobbleTracker chat output. Every player-facing line goes through here so it gets
 * a consistent, colourful prefix and MiniMessage formatting (see {@link MiniMsg}). Config-authored
 * strings should be parsed with {@link #parseAny} so both MiniMessage and legacy {@code &}-codes work.
 */
public final class Msg {

    private Msg() {
    }

    /** Default gradient prefix. Bold is applied outside the gradient (MiniMsg renders gradients as plain). */
    public static final String DEFAULT_PREFIX = "<bold><gradient:#6C5CE7:#38BDF8>[CobbleTracker]</gradient></bold> ";

    private static volatile String prefix = DEFAULT_PREFIX;

    public static void setPrefix(String miniPrefix) {
        if (miniPrefix != null && !miniPrefix.isBlank()) {
            prefix = miniPrefix;
        }
    }

    public static String prefix() {
        return prefix;
    }

    /** Parse a MiniMessage string with no prefix. */
    public static MutableComponent parse(String mini) {
        return MiniMsg.parse(mini);
    }

    /** Parse config text tolerant of both MiniMessage tags and legacy {@code &}-codes. */
    public static MutableComponent parseAny(String text) {
        return MiniMsg.parse(LegacyCodes.toMini(text));
    }

    /** A prefixed line: {@code [CobbleTracker] <parsed mini>}. */
    public static MutableComponent line(String mini) {
        return MiniMsg.parse(prefix).append(MiniMsg.parse(mini));
    }

    public static MutableComponent success(String mini) {
        return line("<green>" + mini);
    }

    public static MutableComponent error(String mini) {
        return line("<red>" + mini);
    }

    public static MutableComponent info(String mini) {
        return line("<#CBD5E1>" + mini);
    }

    public static MutableComponent warn(String mini) {
        return line("<gold>" + mini);
    }

    public static MutableComponent accent(String mini) {
        return line("<aqua>" + mini);
    }

    /** Escapes a dynamic value so stray {@code <} in it isn't parsed as a tag. */
    public static String esc(Object value) {
        return String.valueOf(value).replace("<", "\\<");
    }

    public static Component empty() {
        return Component.empty();
    }
}
