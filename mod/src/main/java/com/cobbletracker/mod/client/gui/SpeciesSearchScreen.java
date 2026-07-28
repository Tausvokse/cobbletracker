package com.cobbletracker.mod.client.gui;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobbletracker.mod.client.hud.HudState;
import com.cobbletracker.mod.client.hud.HuntState;
import com.cobbletracker.mod.client.theme.Theme;
import com.cobbletracker.mod.client.theme.ThemedButton;
import com.cobbletracker.mod.client.theme.Ui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The {@code \} menu: search across every Pokémon species and toggle which ones to hunt. A hunted
 * species is highlighted in-world by a beam and an on-screen arrow the moment it enters the player's
 * loaded chunks ({@link HuntState} / {@link com.cobbletracker.mod.client.hud.HuntScanner} /
 * {@link com.cobbletracker.mod.client.hud.ArrowHudRenderer}). The species list comes from Cobblemon's
 * client-synced registry, so it always matches whatever the server is actually running.
 */
public class SpeciesSearchScreen extends Screen {

    private static final int MAX_PANEL_W = 260;
    private static final int HEADER_H = 26;
    private static final int ROW_H = 14;
    private static final int FOOTER_H = 24;

    /**
     * Species list, built once the registry is populated. Cleared on disconnect: two servers can run
     * different content packs, and a stale list would offer species the next one doesn't have.
     */
    private static volatile List<Sp> cached;

    private record Sp(String path, String name) {
    }

    private final List<Sp> all;
    private List<Sp> filtered;
    private EditBox search;
    private ThemedButton beamButton;
    private float scroll;

    public SpeciesSearchScreen() {
        super(Component.translatable("gui.cobbletracker.hunt.title"));
        this.all = allSpecies();
        this.filtered = all;
    }

    /** Forgets the species list so the next open rebuilds it from whichever server we're on now. */
    public static void invalidateSpeciesCache() {
        cached = null;
    }

    private static List<Sp> allSpecies() {
        List<Sp> known = cached;
        if (known != null && !known.isEmpty()) {
            return known;
        }
        List<Sp> list = new ArrayList<>();
        try {
            for (Species s : PokemonSpecies.getSpecies()) {
                list.add(new Sp(s.getResourceIdentifier().getPath().toLowerCase(Locale.ROOT), s.getName()));
            }
        } catch (Throwable ignored) {
            // Registry not populated yet - an empty list still lets the screen open and say so.
        }
        list.sort(Comparator.comparing(sp -> sp.name().toLowerCase(Locale.ROOT)));
        cached = list;
        return list;
    }

    private int panelW() {
        return Math.min(MAX_PANEL_W, Math.max(160, this.width - 8));
    }

    private int panelH() {
        return Math.min(this.height - 8, 220);
    }

    private int left() {
        return (this.width - panelW()) / 2;
    }

    private int top() {
        return (this.height - panelH()) / 2;
    }

    private int listTop() {
        return top() + HEADER_H + 22;
    }

    private int listBottom() {
        return top() + panelH() - FOOTER_H;
    }

    @Override
    protected void init() {
        int lx = left();
        int ty = top();
        int pw = panelW();
        search = new EditBox(this.font, lx + 10, ty + HEADER_H + 2, pw - 20, 16,
                Component.translatable("gui.cobbletracker.search"));
        search.setHint(Component.translatable("gui.cobbletracker.search"));
        search.setResponder(this::onSearch);
        addRenderableWidget(search);
        setInitialFocus(search);

        beamButton = new ThemedButton(lx + 10, ty + panelH() - 20, 74, 16,
                beamLabel(), HudState.beamVisible(), this::toggleBeam,
                Component.translatable("gui.cobbletracker.hunt.beam.tooltip"));
        addRenderableWidget(beamButton);

        addRenderableWidget(new ThemedButton(lx + pw - 70, ty + panelH() - 20, 60, 16,
                Component.translatable("gui.cobbletracker.hunt.clear"), false, HuntState::clear));
    }

    private static Component beamLabel() {
        return Component.translatable(HudState.beamVisible()
                ? "gui.cobbletracker.hunt.beam.on" : "gui.cobbletracker.hunt.beam.off");
    }

    private void toggleBeam() {
        HudState.toggleBeam();
        beamButton.setMessage(beamLabel());
    }

    private void onSearch(String q) {
        scroll = 0;
        String query = q.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            filtered = all;
            return;
        }
        List<Sp> out = new ArrayList<>();
        for (Sp s : all) {
            if (s.name().toLowerCase(Locale.ROOT).contains(query) || s.path().contains(query)) {
                out.add(s);
            }
        }
        filtered = out;
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

        int lt = listTop();
        int lb = listBottom();
        int total = filtered.size() * ROW_H;
        clampScroll(total, lb - lt);

        g.enableScissor(lx + 6, lt, lx + pw - 6, lb);
        int y = lt - (int) scroll;
        for (Sp s : filtered) {
            if (y + ROW_H >= lt && y <= lb) {
                boolean on = HuntState.isHunted(s.path());
                boolean hovered = mouseX >= lx + 8 && mouseX <= lx + pw - 8
                        && mouseY >= y && mouseY < y + ROW_H && mouseY >= lt && mouseY < lb;
                if (on) {
                    g.fill(lx + 8, y, lx + pw - 8, y + ROW_H, Ui.withAlpha(t.borderGlow(), 0x55));
                } else if (hovered) {
                    g.fill(lx + 8, y, lx + pw - 8, y + ROW_H, Ui.withAlpha(t.scrim(), 0x55));
                }
                g.drawString(this.font, s.name(), lx + 12, y + 3, on ? HuntState.HUNT_COLOR : t.text(), false);
                String tag = on ? "●" : "○";
                g.drawString(this.font, tag, lx + pw - 20, y + 3, on ? HuntState.HUNT_COLOR : t.textMuted(), false);
            }
            y += ROW_H;
        }
        g.disableScissor();
        Ui.scrollbar(g, lx + pw - 8, lt, lb - lt, total, lb - lt, scroll);

        g.drawString(this.font, Component.translatable("gui.cobbletracker.hunt.count", HuntState.hunted().size()),
                lx + 12, lb + 2, t.textMuted(), false);
        if (filtered.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable(all.isEmpty()
                            ? "gui.cobbletracker.hunt.notloaded" : "gui.cobbletracker.hunt.nomatches"),
                    lx + pw / 2, (lt + lb) / 2, t.textMuted());
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void clampScroll(int total, int viewHeight) {
        float max = Math.max(0, total - viewHeight);
        scroll = Math.max(0, Math.min(max, scroll));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int lx = left();
        int lt = listTop();
        int lb = listBottom();
        if (button == 0 && mouseX >= lx + 8 && mouseX <= lx + panelW() - 8 && mouseY >= lt && mouseY < lb) {
            int idx = (int) ((mouseY - lt + scroll) / ROW_H);
            if (idx >= 0 && idx < filtered.size()) {
                HuntState.toggle(filtered.get(idx).path());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (float) scrollY * ROW_H * 2;
        clampScroll(filtered.size() * ROW_H, listBottom() - listTop());
        return true;
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
