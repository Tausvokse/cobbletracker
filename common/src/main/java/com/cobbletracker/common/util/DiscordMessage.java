package com.cobbletracker.common.util;

/** Formats bounded Discord webhook payloads. */
public final class DiscordMessage {

    private DiscordMessage() {
    }

    /** Discord content length limit. */
    private static final int MAX_CONTENT = 2000;

    /** The default message when a category sets a webhook but no template. */
    public static final String DEFAULT_TEMPLATE = "**%species%** spawned in %biome% at %x%, %y%, %z%";

    /** Escaped, length-capped content payload; null for empty messages. */
    public static String body(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.length() > MAX_CONTENT ? content.substring(0, MAX_CONTENT) : content;
        return "{\"content\":\"" + escape(trimmed) + "\"}";
    }

    /** Fills the placeholders. Same vocabulary as the chat line, minus the ones that need a click. */
    public static String render(String template, String species, String category, String biome, String world,
            int x, int y, int z) {
        String t = template == null || template.isBlank() ? DEFAULT_TEMPLATE : template;
        return t
                .replace("%species%", species == null ? "" : species)
                .replace("%category%", category == null ? "" : category)
                .replace("%biome%", biome == null ? "" : biome)
                .replace("%world%", world == null ? "" : world)
                .replace("%x%", Integer.toString(x))
                .replace("%y%", Integer.toString(y))
                .replace("%z%", Integer.toString(z));
    }

    /** Escapes JSON strings, including control characters. */
    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
