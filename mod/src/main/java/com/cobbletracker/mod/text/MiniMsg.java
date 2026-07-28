package com.cobbletracker.mod.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * A dependency-free MiniMessage parser that renders straight to a vanilla {@link Component}. It covers
 * the tags people actually put in chat and announcement templates rather than everything Adventure
 * supports: named colours, {@code <#RRGGBB>} / {@code <color:#RRGGBB>}, the usual decorations and their
 * aliases, {@code <reset>}, the generic close {@code </…>}, and per-character
 * {@code <gradient:#a:#b[:#c…]>…</gradient>}. Anything it doesn't recognise is emitted literally, so a
 * typo in a config template shows up as the raw tag instead of throwing.
 */
public final class MiniMsg {

    private MiniMsg() {
    }

    private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("dark_blue", 0x0000AA), Map.entry("dark_green", 0x00AA00),
            Map.entry("dark_aqua", 0x00AAAA), Map.entry("dark_red", 0xAA0000), Map.entry("dark_purple", 0xAA00AA),
            Map.entry("gold", 0xFFAA00), Map.entry("gray", 0xAAAAAA), Map.entry("grey", 0xAAAAAA),
            Map.entry("dark_gray", 0x555555), Map.entry("dark_grey", 0x555555), Map.entry("blue", 0x5555FF),
            Map.entry("green", 0x55FF55), Map.entry("aqua", 0x55FFFF), Map.entry("red", 0xFF5555),
            Map.entry("light_purple", 0xFF55FF), Map.entry("yellow", 0xFFFF55), Map.entry("white", 0xFFFFFF));

    private enum Deco { BOLD, ITALIC, UNDERLINED, STRIKETHROUGH, OBFUSCATED }

    /** Parses a MiniMessage string into a component. Never null; never throws on bad input. */
    public static MutableComponent parse(String input) {
        MutableComponent root = Component.empty();
        if (input == null || input.isEmpty()) {
            return root;
        }
        List<Tag> stack = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int i = 0;
        int n = input.length();
        while (i < n) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char next = input.charAt(i + 1);
                if (next == '<' || next == '>' || next == '\\') {
                    text.append(next);
                    i += 2;
                    continue;
                }
            }
            if (c == '<') {
                int close = input.indexOf('>', i);
                if (close < 0) {
                    text.append(input.substring(i));
                    break;
                }
                String raw = input.substring(i + 1, close);
                String lower = raw.toLowerCase(Locale.ROOT);
                if (lower.startsWith("gradient:")) {
                    flush(root, text, stack);
                    int end = findClose(input, close + 1, "gradient");
                    String inner = end < 0 ? input.substring(close + 1) : input.substring(close + 1, end);
                    // Colour names and hex are matched lower-case anyway, so read the stops off `lower`
                    // rather than slicing `raw` at an index derived from it.
                    appendGradient(root, inner, lower.substring("gradient:".length()), style(stack));
                    i = end < 0 ? n : end + "</gradient>".length();
                    continue;
                }
                Tag tag = parseTag(lower);
                if (tag == null) {
                    text.append('<').append(raw).append('>'); // unknown → literal
                } else {
                    flush(root, text, stack);
                    applyTag(stack, tag);
                }
                i = close + 1;
            } else {
                text.append(c);
                i++;
            }
        }
        flush(root, text, stack);
        return root;
    }

    private record Tag(String kind, boolean closing, int color, Deco deco) {
    }

    private static Tag parseTag(String lower) {
        boolean closing = lower.startsWith("/");
        String body = closing ? lower.substring(1) : lower;
        if (body.equals("reset")) {
            return new Tag("reset", false, -1, null);
        }
        if (closing && body.isEmpty()) {
            return new Tag("pop", true, -1, null); // generic </>
        }
        Deco deco = decoOf(body);
        if (deco != null) {
            return new Tag("deco", closing, -1, deco);
        }
        if (body.equals("color") || body.equals("c")) {
            return new Tag("color", closing, -1, null); // closing colour tag
        }
        Integer col = colorOf(body);
        if (col != null) {
            return new Tag("color", closing, col, null);
        }
        return null;
    }

    private static void applyTag(List<Tag> stack, Tag tag) {
        switch (tag.kind()) {
            case "reset" -> stack.clear();
            case "pop" -> {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
            }
            case "color" -> {
                if (tag.closing()) {
                    popKind(stack, "color");
                } else {
                    stack.add(tag);
                }
            }
            case "deco" -> {
                if (tag.closing()) {
                    popDeco(stack, tag.deco());
                } else {
                    stack.add(tag);
                }
            }
            default -> {
            }
        }
    }

    private static void popKind(List<Tag> stack, String kind) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).kind().equals(kind)) {
                stack.remove(i);
                return;
            }
        }
    }

    private static void popDeco(List<Tag> stack, Deco deco) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if ("deco".equals(stack.get(i).kind()) && stack.get(i).deco() == deco) {
                stack.remove(i);
                return;
            }
        }
    }

    private static void flush(MutableComponent root, StringBuilder text, List<Tag> stack) {
        if (text.length() == 0) {
            return;
        }
        root.append(Component.literal(text.toString()).withStyle(style(stack)));
        text.setLength(0);
    }

    private static Style style(List<Tag> stack) {
        Style s = Style.EMPTY;
        for (Tag t : stack) {
            if ("color".equals(t.kind()) && t.color() >= 0) {
                s = s.withColor(TextColor.fromRgb(t.color()));
            } else if ("deco".equals(t.kind())) {
                s = switch (t.deco()) {
                    case BOLD -> s.withBold(true);
                    case ITALIC -> s.withItalic(true);
                    case UNDERLINED -> s.withUnderlined(true);
                    case STRIKETHROUGH -> s.withStrikethrough(true);
                    case OBFUSCATED -> s.withObfuscated(true);
                };
            }
        }
        return s;
    }

    private static void appendGradient(MutableComponent root, String inner, String spec, Style base) {
        List<Integer> stops = new ArrayList<>();
        for (String part : spec.split(":")) {
            Integer col = colorOf(part.toLowerCase(Locale.ROOT));
            if (col != null) {
                stops.add(col);
            }
        }
        if (inner.isEmpty()) {
            return;
        }
        List<Character> chars = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        List<Tag> stack = new ArrayList<>();
        int i = 0;
        int n = inner.length();
        while (i < n) {
            char c = inner.charAt(i);
            if (c == '\\' && i + 1 < n && (inner.charAt(i + 1) == '<' || inner.charAt(i + 1) == '>')) {
                chars.add(inner.charAt(i + 1));
                styles.add(decorate(base, stack));
                i += 2;
                continue;
            }
            if (c == '<') {
                int close = inner.indexOf('>', i);
                if (close >= 0) {
                    Tag tag = parseTag(inner.substring(i + 1, close).toLowerCase(Locale.ROOT));
                    if (tag != null) {
                        applyTag(stack, tag);
                        i = close + 1;
                        continue;
                    }
                }
            }
            chars.add(c);
            styles.add(decorate(base, stack));
            i++;
        }
        if (stops.size() < 2) {
            for (int k = 0; k < chars.size(); k++) {
                root.append(Component.literal(String.valueOf(chars.get(k))).withStyle(styles.get(k)));
            }
            return;
        }
        int len = chars.size();
        for (int k = 0; k < len; k++) {
            float t = len == 1 ? 0f : (float) k / (len - 1);
            int color = gradientAt(stops, t);
            root.append(Component.literal(String.valueOf(chars.get(k)))
                    .withStyle(styles.get(k).withColor(TextColor.fromRgb(color))));
        }
    }

    private static Style decorate(Style base, List<Tag> stack) {
        Style s = base;
        for (Tag t : stack) {
            if ("deco".equals(t.kind())) {
                s = switch (t.deco()) {
                    case BOLD -> s.withBold(true);
                    case ITALIC -> s.withItalic(true);
                    case UNDERLINED -> s.withUnderlined(true);
                    case STRIKETHROUGH -> s.withStrikethrough(true);
                    case OBFUSCATED -> s.withObfuscated(true);
                };
            }
        }
        return s;
    }

    private static int gradientAt(List<Integer> stops, float t) {
        float scaled = t * (stops.size() - 1);
        int seg = Math.min((int) scaled, stops.size() - 2);
        float local = scaled - seg;
        return lerp(stops.get(seg), stops.get(seg + 1), local);
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static Deco decoOf(String body) {
        return switch (body) {
            case "bold", "b" -> Deco.BOLD;
            case "italic", "i", "em" -> Deco.ITALIC;
            case "underlined", "u" -> Deco.UNDERLINED;
            case "strikethrough", "st" -> Deco.STRIKETHROUGH;
            case "obfuscated", "obf" -> Deco.OBFUSCATED;
            default -> null;
        };
    }

    private static Integer colorOf(String body) {
        String v = body;
        if (v.startsWith("color:")) {
            v = v.substring("color:".length());
        } else if (v.startsWith("c:")) {
            v = v.substring("c:".length());
        }
        if (v.startsWith("#") && v.length() == 7) {
            try {
                return Integer.parseInt(v.substring(1), 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return NAMED.get(v);
    }

    /**
     * Index of the next {@code </name>}, matched case-insensitively. Deliberately not a search over a
     * lower-cased copy: lower-casing can change a string's length (a few characters map to two), and the
     * index would then no longer line up with the original.
     */
    private static int findClose(String input, int from, String name) {
        String needle = "</" + name + ">";
        int limit = input.length() - needle.length();
        for (int i = Math.max(0, from); i <= limit; i++) {
            if (input.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }
}
