package com.cobbletracker.common.util;

/** Chat duration and probability formatting. */
public final class Durations {

    private Durations() {
    }

    /** Formats at most two units; elapsed countdowns read as now. */
    public static String countdown(long seconds) {
        if (seconds <= 0) {
            return "now";
        }
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            long m = seconds / 60;
            long s = seconds % 60;
            return s == 0 ? m + "m" : m + "m " + s + "s";
        }
        if (seconds < 86400) {
            long h = seconds / 3600;
            long m = (seconds % 3600) / 60;
            return m == 0 ? h + "h" : h + "h " + m + "m";
        }
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        return h == 0 ? d + "d" : d + "d " + h + "h";
    }

    /** Formats percentages without rounding small nonzero chances to zero. */
    public static String percent(double fraction) {
        if (fraction <= 0) {
            return "0%";
        }
        double pct = fraction * 100.0;
        if (pct >= 10) {
            return Math.round(pct) + "%";
        }
        if (pct >= 1) {
            return String.format(java.util.Locale.ROOT, "%.1f%%", pct);
        }
        if (pct >= 0.01) {
            return String.format(java.util.Locale.ROOT, "%.2f%%", pct);
        }
        return String.format(java.util.Locale.ROOT, "%.4f%%", pct);
    }
}
