package com.cobbletracker.mod.client.theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shared, theme-aware drawing helpers. Screens use these instead of hand-picking colours, so every menu
 * looks consistent and restyles together when {@link ThemeManager#current()} changes. Panels get a drop
 * shadow, rounded corners, a gradient body, a double border and a glowing header accent.
 */
public final class Ui {

    private Ui() {
    }

    public static Theme theme() {
        return ThemeManager.current();
    }

    public static void scrim(GuiGraphics g, int width, int height) {
        Theme t = theme();
        g.fillGradient(0, 0, width, height, t.scrim(), darker(t.scrim()));
    }

    private static void shadow(GuiGraphics g, int x, int y, int w, int h) {
        for (int s = 5; s >= 1; s--) {
            int alpha = (6 - s) * 0x10;
            g.fill(x - s, y + s, x + w + s, y + h + s + 1, (alpha << 24));
        }
    }

    /** A themed card: drop shadow, rounded gradient body, rounded double border, header accent line. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        Theme t = theme();
        shadow(g, x, y, w, h);
        g.fillGradient(x, y, x + w, y + h, t.panelTop(), t.panelBottom());
        int corner = 0xFF000000 | (t.scrim() & 0xFFFFFF);
        g.fill(x, y, x + 1, y + 1, corner);
        g.fill(x + w - 1, y, x + w, y + 1, corner);
        g.fill(x, y + h - 1, x + 1, y + h, corner);
        g.fill(x + w - 1, y + h - 1, x + w, y + h, corner);
        g.fill(x + 1, y, x + w - 1, y + 1, t.border());
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, t.border());
        g.fill(x, y + 1, x + 1, y + h - 1, t.border());
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, t.border());
        g.fill(x + 2, y + 1, x + w - 2, y + 2, withAlpha(t.borderGlow(), 0x44));
    }

    /** The title strip across the top of a panel. Returns the y where content can start. */
    public static int headerBar(GuiGraphics g, Font font, Component title, int x, int y, int w) {
        Theme t = theme();
        int h = 22;
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h, withAlpha(t.borderGlow(), 0x2A), 0x00000000);
        g.drawCenteredString(font, title, x + w / 2, y + 6, t.title());
        g.fill(x + 10, y + h - 1, x + w - 10, y + h, withAlpha(t.borderGlow(), 0xCC));
        return y + h + 3;
    }

    public static void sectionHeader(GuiGraphics g, Font font, String text, int x, int y) {
        g.drawString(font, text, x, y, theme().header(), false);
    }

    public static void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, withAlpha(theme().border(), 0x99));
    }

    /** A list row background reflecting its zebra/hover/selected state, with a selection stripe. */
    public static void row(GuiGraphics g, int x, int y, int w, int h, int index, boolean hovered, boolean selected) {
        Theme t = theme();
        int fill = selected ? t.rowSelected() : hovered ? t.rowHover() : (index % 2 == 0 ? t.row() : t.rowAlt());
        g.fill(x, y, x + w, y + h, fill);
        if (selected) {
            g.fill(x, y, x + 2, y + h, t.borderGlow());
        } else if (hovered) {
            g.fill(x, y, x + 2, y + h, withAlpha(t.borderGlow(), 0x88));
        }
        g.fill(x, y + h - 1, x + w, y + h, withAlpha(t.border(), 0x55));
    }

    /** A small coloured pill (type badge / tier tag). Returns the x just past it. */
    public static int pill(GuiGraphics g, Font font, String label, int x, int y, int bg, int fg) {
        int w = font.width(label) + 6;
        g.fill(x, y, x + w, y + 10, bg);
        g.fill(x, y, x + w, y + 1, withAlpha(0xFFFFFFFF, 0x33));
        g.drawString(font, label, x + 3, y + 1, fg, false);
        return x + w + 2;
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int fill) {
        Theme t = theme();
        g.fill(x, y, x + w, y + h, t.barTrack());
        int f = Math.max(0, Math.min(w, Math.round(w * frac)));
        g.fill(x, y, x + f, y + h, fill);
        g.fill(x, y, x + f, y + 1, withAlpha(0xFFFFFFFF, 0x33));
    }

    /** A vertical scrollbar track + thumb for a scrolling viewport. No-op if everything fits. */
    public static void scrollbar(GuiGraphics g, int x, int y, int h, int contentHeight, int viewHeight, float scroll) {
        if (contentHeight <= viewHeight) {
            return;
        }
        Theme t = theme();
        g.fill(x, y, x + 3, y + h, withAlpha(t.barTrack(), 0xAA));
        float visible = (float) viewHeight / contentHeight;
        int thumbH = Math.max(12, (int) (h * visible));
        float maxScroll = contentHeight - viewHeight;
        float p = maxScroll <= 0 ? 0 : Math.max(0, Math.min(1, scroll / maxScroll));
        int thumbY = y + (int) ((h - thumbH) * p);
        g.fill(x, thumbY, x + 3, thumbY + thumbH, t.borderGlow());
    }

    public static int opaque(int argb) {
        return 0xFF000000 | (argb & 0xFFFFFF);
    }

    public static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static int darker(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * 0.55f);
        int gg = (int) (((argb >> 8) & 0xFF) * 0.55f);
        int b = (int) ((argb & 0xFF) * 0.55f);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }
}
