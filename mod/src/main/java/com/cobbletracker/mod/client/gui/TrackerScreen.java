package com.cobbletracker.mod.client.gui;

import com.cobbletracker.common.util.Geometry;
import com.cobbletracker.mod.client.theme.Anim;
import com.cobbletracker.mod.client.theme.Theme;
import com.cobbletracker.mod.client.theme.ThemedButton;
import com.cobbletracker.mod.client.theme.Ui;
import com.cobbletracker.mod.client.util.PokemonSpriteRenderer;
import com.cobbletracker.mod.client.util.SpriteClientUtils;
import com.cobbletracker.mod.net.S2C_SyncHistoryPacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The spawn-history GUI: a code-drawn, Pokédex-themed screen with a sidebar (All / per-category /
 * Stats), a scrollable list of sprite "spawn cards" and a cycling sort. It renders from the
 * {@link S2C_SyncHistoryPacket} it was opened with, so switching tabs or re-sorting never goes back to
 * the server. Clicking a card copies its coordinates.
 */
public class TrackerScreen extends Screen {

    private static final int MAX_PANEL_W = 448;
    private static final int MAX_PANEL_H = 252;
    private static final int SIDEBAR_W = 92;
    private static final int CARD_H = 44;
    private static final int CARD_GAP = 6;
    private static final int HEADER_H = 26;
    private static final int TAB_H = 18;
    private static final int TAB_STEP_MAX = 22;
    private static final int TAB_STEP_MIN = 20;

    private enum Sort {
        NEWEST("newest"), DISTANCE("distance"), SPECIES("species"), CATEGORY("tier"), CAUGHT("caught");

        final String key;

        Sort(String key) {
            this.key = key;
        }

        Component label() {
            return Component.translatable("gui.cobbletracker.sort." + key);
        }
    }

    private final List<S2C_SyncHistoryPacket.Category> categories;
    private final List<S2C_SyncHistoryPacket.Entry> entries;
    private final List<S2C_SyncHistoryPacket.Stat> stats;
    private final Map<String, S2C_SyncHistoryPacket.Category> categoryById = new HashMap<>();

    private final long openTime = System.currentTimeMillis();
    private int selectedTab; // 0 = ALL, 1..n = category, n+1 = STATS
    private Sort sort = Sort.NEWEST;
    private float scroll;
    private ThemedButton sortButton;

    /**
     * The current tab's cards, rebuilt only when the tab or sort changes. Distance sorting reads the
     * player's position, so it is refreshed once per frame in that mode rather than on every draw call.
     */
    private List<S2C_SyncHistoryPacket.Entry> visible = List.of();
    private boolean visibleStale = true;

    public TrackerScreen(S2C_SyncHistoryPacket data) {
        super(Component.translatable("gui.cobbletracker.title"));
        this.categories = data.categories();
        this.entries = data.entries();
        this.stats = data.stats();
        for (S2C_SyncHistoryPacket.Category c : categories) {
            categoryById.put(c.id(), c);
        }
        // Optional focus (e.g. opening straight on the legendary tab).
        String focus = data.focusCategory();
        if (focus != null && !focus.isEmpty()) {
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).id().equals(focus)) {
                    this.selectedTab = i + 1;
                    break;
                }
            }
        }
    }

    // ---- layout -----------------------------------------------------------
    // Every dimension is derived from the window: at GUI scale 4 on a small display the full panel is
    // wider than the screen, and a fixed size would push the sidebar and close button out of reach.

    private int panelW() {
        return Math.min(MAX_PANEL_W, Math.max(200, this.width - 8));
    }

    private int panelH() {
        return Math.min(MAX_PANEL_H, Math.max(120, this.height - 8));
    }

    private int sidebarW() {
        return Math.min(SIDEBAR_W, panelW() / 3);
    }

    private int left() {
        return (this.width - panelW()) / 2;
    }

    private int top() {
        return (this.height - panelH()) / 2;
    }

    private int contentLeft() {
        return left() + sidebarW() + 6;
    }

    private int contentTop() {
        return top() + HEADER_H + 20;
    }

    private int contentWidth() {
        return panelW() - sidebarW() - 16;
    }

    private int contentHeight() {
        return panelH() - HEADER_H - 28;
    }

    private int statsTab() {
        return categories.size() + 1;
    }

    /** Vertical pitch of the sidebar tabs, squeezed a little when there are a lot of categories. */
    private int tabStep() {
        int room = panelH() - HEADER_H - 10;
        int wanted = categories.size() + 2; // ALL + categories + STATS
        int step = wanted <= 0 ? TAB_STEP_MAX : room / wanted;
        return Math.max(TAB_STEP_MIN, Math.min(TAB_STEP_MAX, step));
    }

    @Override
    protected void init() {
        int lx = left();
        int ty = top();
        int step = tabStep();
        int sidebarBottom = ty + panelH() - 6;

        // ALL first and STATS last are always placed; the category tabs fill whatever is left between
        // them, so the two fixed tabs stay reachable no matter how many categories a server defines.
        int tabY = ty + HEADER_H + 6;
        addTab(lx + 6, tabY, Component.translatable("gui.cobbletracker.tab.all"), 0);
        tabY += step;
        int index = 1;
        for (S2C_SyncHistoryPacket.Category c : categories) {
            if (tabY + step + TAB_H > sidebarBottom) {
                break; // out of room - the rest stay reachable through the All tab
            }
            addTab(lx + 6, tabY, Component.literal(c.display().toUpperCase(Locale.ROOT)), index);
            tabY += step;
            index++;
        }
        addTab(lx + 6, Math.min(tabY, sidebarBottom - TAB_H), Component.translatable("gui.cobbletracker.tab.stats"),
                statsTab());

        sortButton = new ThemedButton(contentLeft() + contentWidth() - 88, ty + 3, 60, 16,
                sort.label(), false, this::cycleSort,
                Component.translatable("gui.cobbletracker.sort"));
        addRenderableWidget(sortButton);

        addRenderableWidget(new ThemedButton(lx + panelW() - 24, ty + 3, 18, 16,
                Component.literal("✕"), false, this::onClose,
                Component.translatable("gui.cobbletracker.close")));
    }

    private void addTab(int x, int y, Component label, int tabIndex) {
        addRenderableWidget(new ThemedButton(x, y, sidebarW() - 12, TAB_H,
                label, selectedTab == tabIndex, () -> selectTab(tabIndex)));
    }

    private void selectTab(int tab) {
        this.selectedTab = tab;
        this.scroll = 0;
        this.visibleStale = true;
        rebuildWidgets();
    }

    private void cycleSort() {
        sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
        sortButton.setMessage(sort.label());
        scroll = 0;
        visibleStale = true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The backdrop is part of render() so it sits under our own panel, not the vanilla blur.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = Ui.theme();
        float appear = Anim.easeOut(Anim.progress(openTime, 180));
        Ui.scrim(g, this.width, this.height);

        int lx = left();
        int ty = top();
        int pw = panelW();
        Ui.panel(g, lx, ty, pw, panelH());
        Ui.headerBar(g, this.font, this.title, lx, ty, pw);

        // Sidebar backing.
        g.fill(lx + 2, ty + HEADER_H + 2, lx + sidebarW(), ty + panelH() - 2, Ui.withAlpha(t.scrim(), 0x66));
        Ui.divider(g, contentLeft() - 4, ty + HEADER_H + 18, contentWidth() + 8);

        if (selectedTab == statsTab()) {
            renderStats(g);
        } else {
            renderCards(g, mouseX, mouseY, appear);
        }

        super.render(g, mouseX, mouseY, partialTick); // widgets (tabs, sort, close)

        g.drawString(this.font, "/ct theme " + String.join("/", Theme.names()),
                contentLeft(), ty + panelH() - 12, t.textMuted(), false);
    }

    private void renderCards(GuiGraphics g, int mouseX, int mouseY, float appear) {
        Theme t = Ui.theme();
        List<S2C_SyncHistoryPacket.Entry> shown = visibleEntries();
        int cx = contentLeft();
        int cy = contentTop();
        int cw = contentWidth();
        int ch = contentHeight();

        int total = shown.size() * (CARD_H + CARD_GAP);
        clampScroll(total, ch);

        g.enableScissor(cx, cy, cx + cw, cy + ch);
        int y = cy - (int) scroll + (int) ((1 - appear) * 8);
        int i = 0;
        for (S2C_SyncHistoryPacket.Entry e : shown) {
            if (y + CARD_H >= cy && y <= cy + ch) {
                drawCard(g, e, cx, y, cw - 4, hitsCard(mouseX, mouseY, cx, y, cw), i);
            }
            y += CARD_H + CARD_GAP;
            i++;
        }
        g.disableScissor();

        Ui.scrollbar(g, cx + cw - 2, cy, ch, total, ch, scroll);

        if (shown.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.cobbletracker.empty"),
                    cx + cw / 2, cy + ch / 2 - 4, t.textMuted());
        }
    }

    private boolean hitsCard(double mouseX, double mouseY, int cx, int cardY, int cw) {
        int cy = contentTop();
        int ch = contentHeight();
        return mouseX >= cx && mouseX <= cx + cw - 4
                && mouseY >= cardY && mouseY < cardY + CARD_H
                && mouseY >= cy && mouseY <= cy + ch;
    }

    private void drawCard(GuiGraphics g, S2C_SyncHistoryPacket.Entry e, int x, int y, int w, boolean hovered,
            int index) {
        Theme t = Ui.theme();
        Ui.row(g, x, y, w, CARD_H, index, hovered, false);
        int accent = color(e.category());
        g.fill(x, y, x + 2, y + CARD_H, accent);

        PokemonSpriteRenderer.drawPokemonOrToken(g, this.font, e.speciesId(), e.shiny(), x + 6, y + 6, 32);

        int tx = x + 44;
        String name = e.species() + (e.shiny() ? " ✨" : "");
        g.drawString(this.font, name, tx, y + 6, t.text(), false);

        S2C_SyncHistoryPacket.Category cat = categoryById.get(e.category());
        String tier = cat != null ? cat.display() : e.category();
        Ui.pill(g, this.font, tier, tx, y + 18, Ui.withAlpha(accent, 0xCC), 0xFF10101A);

        String loc = coords(e) + "  ·  " + SpriteClientUtils.prettyName(e.biome());
        g.drawString(this.font, loc, tx, y + 31, t.textMuted(), false);

        String dist = distanceLabel(e);
        if (!dist.isEmpty()) {
            g.drawString(this.font, dist, x + w - this.font.width(dist) - 6, y + 6, t.textDim(), false);
        }
        String ago = timeAgo(e.spawnTime());
        g.drawString(this.font, ago, x + w - this.font.width(ago) - 6, y + 18, t.textMuted(), false);
        if (e.caught()) {
            String who = e.catcherName() == null || e.catcherName().isBlank() ? "" : e.catcherName();
            Component badge = who.isEmpty()
                    ? Component.translatable("gui.cobbletracker.caught")
                    : Component.translatable("gui.cobbletracker.caught.by", who);
            g.drawString(this.font, badge, x + w - this.font.width(badge) - 6, y + 31, t.positive(), false);
        }
    }

    private void renderStats(GuiGraphics g) {
        Theme t = Ui.theme();
        int cx = contentLeft();
        int cy = contentTop();
        int cw = contentWidth();
        int ch = contentHeight();
        Ui.sectionHeader(g, this.font, Component.translatable("gui.cobbletracker.stats.title").getString(), cx, cy);
        int y = cy + 14;
        int rank = 1;
        int max = stats.isEmpty() ? 1 : Math.max(1, stats.get(0).caught());
        int barX = cx + Math.min(120, cw / 3);
        int barW = Math.max(20, cx + cw - barX - 40);
        g.enableScissor(cx, cy, cx + cw, cy + ch);
        for (S2C_SyncHistoryPacket.Stat s : stats) {
            if (y > cy + ch) {
                break;
            }
            g.drawString(this.font, "#" + rank, cx, y, t.textMuted(), false);
            g.drawString(this.font, s.player(), cx + 24, y, t.text(), false);
            Ui.bar(g, barX, y + 1, barW, 6, s.caught() / (float) max, t.accent());
            String n = Integer.toString(s.caught());
            g.drawString(this.font, n, cx + cw - this.font.width(n) - 2, y, t.textDim(), false);
            y += 14;
            rank++;
        }
        g.disableScissor();
        if (stats.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.cobbletracker.stats.empty"),
                    cx + cw / 2, cy + ch / 2, t.textMuted());
        }
    }

    // ---- data shaping -----------------------------------------------------

    /** The current tab's cards; recomputed only when something that affects them has changed. */
    private List<S2C_SyncHistoryPacket.Entry> visibleEntries() {
        // Distance depends on where the player is standing, so that one sort can't be cached.
        if (visibleStale || sort == Sort.DISTANCE) {
            visible = filteredSorted();
            visibleStale = false;
        }
        return visible;
    }

    private List<S2C_SyncHistoryPacket.Entry> filteredSorted() {
        String activeCategory = selectedTab >= 1 && selectedTab <= categories.size()
                ? categories.get(selectedTab - 1).id() : null;
        List<S2C_SyncHistoryPacket.Entry> out = new ArrayList<>();
        for (S2C_SyncHistoryPacket.Entry e : entries) {
            if (activeCategory == null || e.category().equals(activeCategory)) {
                out.add(e);
            }
        }
        Comparator<S2C_SyncHistoryPacket.Entry> cmp = switch (sort) {
            case NEWEST -> Comparator.comparingLong(S2C_SyncHistoryPacket.Entry::spawnTime).reversed();
            case DISTANCE -> Comparator.comparingDouble(this::distanceOf);
            case SPECIES -> Comparator.comparing(S2C_SyncHistoryPacket.Entry::species, String.CASE_INSENSITIVE_ORDER);
            case CATEGORY -> Comparator.comparing(S2C_SyncHistoryPacket.Entry::category);
            case CAUGHT -> Comparator.comparing(S2C_SyncHistoryPacket.Entry::caught).reversed();
        };
        out.sort(cmp);
        return out;
    }

    private double distanceOf(S2C_SyncHistoryPacket.Entry e) {
        if (minecraft == null || minecraft.player == null) {
            return Double.MAX_VALUE;
        }
        if (!minecraft.player.level().dimension().location().toString().equals(e.world())) {
            return Double.MAX_VALUE;
        }
        double dx = e.x() - minecraft.player.getX();
        double dz = e.z() - minecraft.player.getZ();
        return Geometry.horizontalDistance(dx, dz);
    }

    private String distanceLabel(S2C_SyncHistoryPacket.Entry e) {
        double d = distanceOf(e);
        if (d == Double.MAX_VALUE) {
            return "";
        }
        return (int) Math.round(d) + "m";
    }

    private static String coords(S2C_SyncHistoryPacket.Entry e) {
        return e.x() + ", " + e.y() + ", " + e.z();
    }

    private int color(String categoryId) {
        S2C_SyncHistoryPacket.Category c = categoryById.get(categoryId);
        return c != null ? Ui.opaque(c.color()) : Ui.theme().accent();
    }

    private static String timeAgo(long epochSeconds) {
        long now = System.currentTimeMillis() / 1000L;
        long d = Math.max(0, now - epochSeconds);
        if (d < 60) {
            return d + "s ago";
        }
        if (d < 3600) {
            return (d / 60) + "m ago";
        }
        if (d < 86400) {
            return (d / 3600) + "h ago";
        }
        return (d / 86400) + "d ago";
    }

    private void clampScroll(int total, int viewHeight) {
        float max = Math.max(0, total - viewHeight);
        if (scroll < 0) {
            scroll = 0;
        }
        if (scroll > max) {
            scroll = max;
        }
    }

    // ---- input ------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && selectedTab != statsTab()) {
            List<S2C_SyncHistoryPacket.Entry> shown = visibleEntries();
            int cx = contentLeft();
            int y = contentTop() - (int) scroll;
            for (S2C_SyncHistoryPacket.Entry e : shown) {
                if (hitsCard(mouseX, mouseY, cx, y, contentWidth())) {
                    copyCoords(e);
                    return true;
                }
                y += CARD_H + CARD_GAP;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Puts a spawn's coordinates on the clipboard - the quickest way to share or /tp to one. */
    private void copyCoords(S2C_SyncHistoryPacket.Entry e) {
        if (minecraft == null) {
            return;
        }
        minecraft.keyboardHandler.setClipboard(coords(e));
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("gui.cobbletracker.copied", coords(e)), true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selectedTab != statsTab()) {
            scroll -= (float) scrollY * (CARD_H + CARD_GAP);
            clampScroll(visibleEntries().size() * (CARD_H + CARD_GAP), contentHeight());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
