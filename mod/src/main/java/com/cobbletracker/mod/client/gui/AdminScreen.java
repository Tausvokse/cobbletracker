package com.cobbletracker.mod.client.gui;

import com.cobbletracker.mod.client.theme.Theme;
import com.cobbletracker.mod.client.theme.ThemedButton;
import com.cobbletracker.mod.client.theme.Ui;
import com.cobbletracker.mod.net.C2S_AdminActionPacket;
import com.cobbletracker.mod.net.PlatformNetworking;
import com.cobbletracker.mod.net.S2C_OpenAdminPacket;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Operator settings screen ({@code /ct admin}): one editable row per server setting - a toggle for
 * booleans, {@code −/＋} steppers for integers. Edits send {@link C2S_AdminActionPacket}; the server
 * re-checks OP, clamps and applies the value, then pushes a fresh {@link S2C_OpenAdminPacket} which
 * reopens this screen with the result. Changes are live but in-memory: config.yml still wins on reload.
 */
public class AdminScreen extends Screen {

    private static final int MAX_PANEL_W = 320;
    private static final int ROW_H = 26;
    private static final int HEADER_H = 26;
    private static final int FOOTER_H = 12;

    private final List<S2C_OpenAdminPacket.Setting> settings;

    public AdminScreen(List<S2C_OpenAdminPacket.Setting> settings) {
        super(Component.translatable("gui.cobbletracker.admin.title"));
        this.settings = settings;
    }

    private int panelW() {
        return Math.min(MAX_PANEL_W, Math.max(200, this.width - 8));
    }

    /** Tall enough for every row, but never taller than the window - otherwise rows fall off-screen. */
    private int panelH() {
        int wanted = HEADER_H + Math.max(1, settings.size()) * ROW_H + FOOTER_H;
        return Math.min(wanted, Math.max(80, this.height - 8));
    }

    /** How many rows actually fit; anything past this is dropped rather than drawn out of bounds. */
    private int visibleRows() {
        return Math.max(1, (panelH() - HEADER_H - FOOTER_H) / ROW_H);
    }

    private int left() {
        return (this.width - panelW()) / 2;
    }

    private int top() {
        return (this.height - panelH()) / 2;
    }

    @Override
    protected void init() {
        int lx = left();
        int pw = panelW();
        int rowY = top() + HEADER_H + 4;
        int shown = Math.min(settings.size(), visibleRows());
        for (int i = 0; i < shown; i++) {
            S2C_OpenAdminPacket.Setting s = settings.get(i);
            if ("bool".equals(s.type())) {
                boolean on = Boolean.parseBoolean(s.value());
                addRenderableWidget(new ThemedButton(lx + pw - 76, rowY + 3, 60, 18,
                        Component.literal(on ? "ON" : "OFF"), on,
                        () -> send(s.key(), Boolean.toString(!on))));
            } else {
                int step = stepFor(s.key());
                int value = parseInt(s.value());
                addRenderableWidget(new ThemedButton(lx + pw - 76, rowY + 3, 18, 18,
                        Component.literal("−"), false, () -> send(s.key(), Integer.toString(value - step))));
                addRenderableWidget(new ThemedButton(lx + pw - 34, rowY + 3, 18, 18,
                        Component.literal("＋"), false, () -> send(s.key(), Integer.toString(value + step))));
            }
            rowY += ROW_H;
        }
    }

    private void send(String key, String value) {
        PlatformNetworking.sendToServer(new C2S_AdminActionPacket(key, value));
    }

    private static int stepFor(String key) {
        return switch (key) {
            case "beam-radius" -> 8;
            case "beam-duration-seconds" -> 5;
            case "beam-height" -> 32;
            default -> 1;
        };
    }

    private static int parseInt(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The backdrop is part of render() so it sits under our own panel, not the vanilla blur.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = Ui.theme();
        Ui.scrim(g, this.width, this.height);
        int lx = left();
        int ty = top();
        int pw = panelW();
        Ui.panel(g, lx, ty, pw, panelH());
        Ui.headerBar(g, this.font, this.title, lx, ty, pw);

        int rowY = ty + HEADER_H + 4;
        int shown = Math.min(settings.size(), visibleRows());
        for (int i = 0; i < shown; i++) {
            S2C_OpenAdminPacket.Setting s = settings.get(i);
            Ui.row(g, lx + 4, rowY, pw - 8, ROW_H - 2, i, false, false);
            g.drawString(this.font, s.label(), lx + 12, rowY + 6, t.text(), false);
            if (!"bool".equals(s.type())) {
                String v = s.value();
                g.drawString(this.font, v, lx + pw - 76 - 22 - this.font.width(v), rowY + 6, t.accent(), false);
            }
            rowY += ROW_H;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
