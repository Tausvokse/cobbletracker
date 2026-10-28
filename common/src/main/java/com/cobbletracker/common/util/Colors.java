package com.cobbletracker.common.util;

/** ARGB parsing; six-digit values default to full opacity. */
public final class Colors {

    private Colors() {
    }

    /** Parses {@code #RRGGBB} or {@code #AARRGGBB} (with/without {@code #}); returns {@code fallback} on error. */
    public static int parseHex(String hex, int fallback) {
        if (hex == null) {
            return fallback;
        }
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        try {
            if (s.length() == 6) {
                return 0xFF000000 | (int) Long.parseLong(s, 16);
            }
            if (s.length() == 8) {
                return (int) Long.parseLong(s, 16);
            }
        } catch (NumberFormatException ignored) {
            
        }
        return fallback;
    }
}
