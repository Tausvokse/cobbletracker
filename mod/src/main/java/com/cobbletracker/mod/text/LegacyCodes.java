package com.cobbletracker.mod.text;

/**
 * Converts legacy {@code &}-code strings (e.g. {@code &6&lLEGENDARY}) into MiniMessage tags so configs
 * written in the old style keep working while the mod standardises on MiniMessage. Only {@code &x}
 * sequences are rewritten; any existing {@code <…>} MiniMessage tags are left untouched, so the two
 * styles can even be mixed in one string. Legacy colour codes reset prior formatting (vanilla
 * semantics), emitted here as {@code <reset><color>}.
 */
public final class LegacyCodes {

    private LegacyCodes() {
    }

    /** Rewrites {@code &}-codes to MiniMessage; leaves the rest (including MiniMessage tags) intact. */
    public static String toMini(String input) {
        if (input == null || input.isEmpty() || input.indexOf('&') < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        int n = input.length();
        for (int i = 0; i < n; i++) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < n) {
                String tag = tagFor(Character.toLowerCase(input.charAt(i + 1)));
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String tagFor(char code) {
        return switch (code) {
            case '0' -> "<reset><black>";
            case '1' -> "<reset><dark_blue>";
            case '2' -> "<reset><dark_green>";
            case '3' -> "<reset><dark_aqua>";
            case '4' -> "<reset><dark_red>";
            case '5' -> "<reset><dark_purple>";
            case '6' -> "<reset><gold>";
            case '7' -> "<reset><gray>";
            case '8' -> "<reset><dark_gray>";
            case '9' -> "<reset><blue>";
            case 'a' -> "<reset><green>";
            case 'b' -> "<reset><aqua>";
            case 'c' -> "<reset><red>";
            case 'd' -> "<reset><light_purple>";
            case 'e' -> "<reset><yellow>";
            case 'f' -> "<reset><white>";
            case 'l' -> "<bold>";
            case 'o' -> "<italic>";
            case 'n' -> "<underlined>";
            case 'm' -> "<strikethrough>";
            case 'k' -> "<obfuscated>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }
}
