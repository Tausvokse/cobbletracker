package com.cobbletracker.mod.client.theme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A flat, theme-aware button. Reads {@link ThemeManager#current()} each frame so it restyles with the
 * rest of the UI, supports an accent "primary" variant for the main action, picks a readable label
 * colour from the fill's luminance, and shows an optional hover tooltip.
 */
public final class ThemedButton extends AbstractButton {

    private final Runnable onPress;
    private final boolean primary;

    public ThemedButton(int x, int y, int w, int h, Component label, boolean primary, Runnable onPress) {
        this(x, y, w, h, label, primary, onPress, null);
    }

    public ThemedButton(int x, int y, int w, int h, Component label, boolean primary, Runnable onPress,
            Component tooltip) {
        super(x, y, w, h, label);
        this.onPress = onPress;
        this.primary = primary;
        if (tooltip != null) {
            setTooltip(Tooltip.create(tooltip));
        }
    }

    @Override
    public void onPress() {
        if (onPress != null) {
            onPress.run();
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = ThemeManager.current();
        boolean hovered = isHoveredOrFocused();
        int base = primary ? t.borderGlow() : t.button();
        int fill = !active ? Ui.withAlpha(t.button(), 0x66) : hovered ? (primary ? lighten(t.borderGlow()) : t.buttonHover()) : base;
        g.fill(getX() + 1, getY(), getX() + width - 1, getY() + height, fill);
        g.fill(getX(), getY() + 1, getX() + 1, getY() + height - 1, fill);
        g.fill(getX() + width - 1, getY() + 1, getX() + width, getY() + height - 1, fill);
        int border = hovered ? t.borderGlow() : Ui.withAlpha(t.border(), 0xCC);
        g.fill(getX() + 1, getY(), getX() + width - 1, getY() + 1, border);
        g.fill(getX() + 1, getY() + height - 1, getX() + width - 1, getY() + height, border);
        g.fill(getX(), getY() + 1, getX() + 1, getY() + height - 1, border);
        g.fill(getX() + width - 1, getY() + 1, getX() + width, getY() + height - 1, border);
        g.fill(getX() + 2, getY() + 1, getX() + width - 2, getY() + 2, Ui.withAlpha(0xFFFFFFFF, hovered ? 0x40 : 0x22));

        int textColor = !active ? t.textMuted() : (primary ? contrastOn(fill) : t.buttonText());
        g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, getMessage(),
                getX() + width / 2, getY() + (height - 8) / 2, textColor);
    }

    private static int contrastOn(int argb) {
        int r = (argb >> 16) & 0xFF;
        int gc = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        double lum = (0.299 * r + 0.587 * gc + 0.114 * b);
        return lum > 150 ? 0xFF10141C : 0xFFFFFFFF;
    }

    private static int lighten(int argb) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * 1.15f) + 12);
        int gc = Math.min(255, (int) (((argb >> 8) & 0xFF) * 1.15f) + 12);
        int b = Math.min(255, (int) ((argb & 0xFF) * 1.15f) + 12);
        return 0xFF000000 | (r << 16) | (gc << 8) | b;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
