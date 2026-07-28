package com.cobbletracker.mod.client.util;

import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Deterministic tinted-token fallback for a Pokémon species when the real Cobblemon model isn't
 * available. Each species gets a stable hash-derived tint plus its short name, drawn purely with
 * {@link GuiGraphics}.
 */
public final class SpriteClientUtils {

    private SpriteClientUtils() {
    }

    public static void drawSpeciesIcon(GuiGraphics guiGraphics, Font font, String species, int x, int y, int size) {
        if (species == null || species.isEmpty()) {
            species = "?";
        }
        int tint = tintFor(species);
        guiGraphics.fill(x, y, x + size, y + size, 0xFF000000 | (tint & 0xFFFFFF));
        guiGraphics.renderOutline(x, y, size, size, 0xFF0A0A0A);
        String label = shortLabel(species);
        int textX = x + (size - font.width(label)) / 2;
        guiGraphics.drawString(font, label, textX, y + size / 2 - 4, 0xFF101010, false);
    }

    /** {@code minecraft:cherry_grove} → {@code Cherry grove}. Used for biome and world labels. */
    public static String prettyName(String species) {
        if (species == null || species.isEmpty()) {
            return "";
        }
        String name = species.contains(":") ? species.substring(species.indexOf(':') + 1) : species;
        name = name.replace('_', ' ');
        return name.isEmpty() ? name
                : Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String shortLabel(String species) {
        String name = species.contains(":") ? species.substring(species.indexOf(':') + 1) : species;
        name = name.replace("_", "");
        String abbreviation = name.length() <= 3 ? name : name.substring(0, 3);
        return abbreviation.toUpperCase(Locale.ROOT);
    }

    private static int tintFor(String species) {
        float hue = (((species.hashCode() % 360) + 360) % 360) / 360f;
        return hsbToRgb(hue, 0.45f, 0.85f);
    }

    private static int hsbToRgb(float hue, float saturation, float brightness) {
        int r = 0;
        int g = 0;
        int b = 0;
        float h = (hue - (float) Math.floor(hue)) * 6.0f;
        float f = h - (float) Math.floor(h);
        float p = brightness * (1.0f - saturation);
        float q = brightness * (1.0f - saturation * f);
        float t = brightness * (1.0f - saturation * (1.0f - f));
        switch ((int) h) {
            case 0 -> { r = round(brightness); g = round(t); b = round(p); }
            case 1 -> { r = round(q); g = round(brightness); b = round(p); }
            case 2 -> { r = round(p); g = round(brightness); b = round(t); }
            case 3 -> { r = round(p); g = round(q); b = round(brightness); }
            case 4 -> { r = round(t); g = round(p); b = round(brightness); }
            default -> { r = round(brightness); g = round(p); b = round(q); }
        }
        return (r << 16) | (g << 8) | b;
    }

    private static int round(float channel) {
        return (int) (channel * 255.0f + 0.5f);
    }
}
