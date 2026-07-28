package com.cobbletracker.mod.client.theme;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A GUI colour palette. All fields are packed ARGB ints so screens draw straight from them. The
 * {@code pokedex} theme is the default; players switch with {@code /ct theme <name>}. Pure data (no
 * client imports) so the server-side command can validate names against {@link #names()} without loading
 * any rendering classes.
 */
public record Theme(
        String name,
        int scrim,
        int panelTop,
        int panelBottom,
        int border,
        int borderGlow,
        int title,
        int header,
        int text,
        int textDim,
        int textMuted,
        int accent,
        int positive,
        int negative,
        int warn,
        int button,
        int buttonHover,
        int buttonText,
        int row,
        int rowAlt,
        int rowHover,
        int rowSelected,
        int barTrack,
        int barFill) {

    private static final Map<String, Theme> THEMES = new LinkedHashMap<>();

    /** Signature CobbleTracker look: deep violet holo panels, cyan/purple glow, gold highlights. */
    public static final Theme POKEDEX = register(new Theme("pokedex",
            0xE6070512, 0xF21A1730, 0xF2120E22, 0xFF3B2F63, 0xFF8B7BF0,
            0xFFFDCB6E, 0xFFA29BFE, 0xFFF1F5F9, 0xFFB8C4D6, 0xFF8394AC,
            0xFF6C5CE7, 0xFF4ADE80, 0xFFF87171, 0xFFFDCB6E,
            0xFF2A2350, 0xFF3B2F72, 0xFFF1F5F9, 0xFF171331, 0xFF1C1740, 0xFF272052, 0xFF3A2E72,
            0xFF241C46, 0xFF6C5CE7));

    public static final Theme DARK = register(new Theme("dark",
            0xE6060912, 0xF21A2233, 0xF2121A29, 0xFF334155, 0xFF60A5FA,
            0xFFFBBF24, 0xFF93C5FD, 0xFFF1F5F9, 0xFFB8C4D6, 0xFF8394AC,
            0xFF60A5FA, 0xFF4ADE80, 0xFFF87171, 0xFFFBBF24,
            0xFF273449, 0xFF334966, 0xFFF1F5F9, 0xFF172033, 0xFF1B2539, 0xFF243550, 0xFF2E4670,
            0xFF1E293B, 0xFF60A5FA));

    public static final Theme LIGHT = register(new Theme("light",
            0xC0121826, 0xFFFFFFFF, 0xFFEEF2F8, 0xFFCBD5E1, 0xFF2563EB,
            0xFFB45309, 0xFF1D4ED8, 0xFF0F172A, 0xFF475569, 0xFF6B7A8C,
            0xFF2563EB, 0xFF16A34A, 0xFFDC2626, 0xFFB45309,
            0xFFE7EDF6, 0xFFD4E0F0, 0xFF0F172A, 0xFFFFFFFF, 0xFFF1F5FA, 0xFFE2ECF8, 0xFFCFE0F8,
            0xFFDDE5EF, 0xFF2563EB));

    public static final Theme BLUE = register(new Theme("blue",
            0xE602060F, 0xF20E2444, 0xF20A1B34, 0xFF1E4E86, 0xFF38BDF8,
            0xFF7DD3FC, 0xFFBAE6FD, 0xFFEAF4FF, 0xFFAECBEC, 0xFF88A6CC,
            0xFF38BDF8, 0xFF34D399, 0xFFFB7185, 0xFFFBBF24,
            0xFF124273, 0xFF1A5A9E, 0xFFEAF4FF, 0xFF0C2344, 0xFF0F2A52, 0xFF174680, 0xFF1E5AA0,
            0xFF10345F, 0xFF38BDF8));

    public static final Theme MIDNIGHT = register(new Theme("midnight",
            0xF2050409, 0xF21E1640, 0xF2140E2C, 0xFF3B2F63, 0xFFA78BFA,
            0xFFC4B5FD, 0xFFDDD6FE, 0xFFF3F0FF, 0xFFC4BBDE, 0xFF9488B8,
            0xFFA78BFA, 0xFF4ADE80, 0xFFFB7185, 0xFFFBBF24,
            0xFF2E2258, 0xFF3F2F7A, 0xFFF3F0FF, 0xFF190F38, 0xFF1E1442, 0xFF2C1F58, 0xFF3B2A72,
            0xFF241A4A, 0xFFA78BFA));

    public static final Theme FOREST = register(new Theme("forest",
            0xE6030A06, 0xF2123526, 0xF20C2318, 0xFF2C5A3E, 0xFF34D399,
            0xFFBBF7D0, 0xFFA7F3D0, 0xFFEAF7EE, 0xFFB4CEBD, 0xFF7E9A88,
            0xFF34D399, 0xFF86EFAC, 0xFFFB7185, 0xFFFBBF24,
            0xFF1D4A34, 0xFF276848, 0xFFEAF7EE, 0xFF0F2A1C, 0xFF143324, 0xFF1E4A34, 0xFF2C6A48,
            0xFF143324, 0xFF34D399));

    private static Theme register(Theme theme) {
        THEMES.put(theme.name(), theme);
        return theme;
    }

    public static Theme byName(String name) {
        return THEMES.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
    }

    public static java.util.Set<String> names() {
        return THEMES.keySet();
    }
}
