package com.cobbletracker.mod.client.gui;

import com.cobbletracker.common.util.Geometry;
import com.cobbletracker.mod.client.hud.HuntState;
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

public class TrackerScreen extends Screen {

    private static final int MAX_PANEL_W = 448;
    private static final int MAX_PANEL_H = 252;
    private static final int SIDEBAR_W = 92;

    private static final int CARD_H = 56;
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
    // Cache species paths for per-frame hunt highlighting.
    private final Map<String, String> speciesPaths = new HashMap<>();

    private final long openTime = System.currentTimeMillis();
    private int selectedTab; // 0 = ALL, 1..n = category, n+1 = STATS, n+2 = NOTIFY
    private Sort sort = Sort.NEWEST;
    private float scroll;
    private ThemedButton sortButton;
    private int categoryOffset;
    private int categoryCapacity;
    private boolean revealSelectedCategory = true;

    /** Cached until the tab, sort or distance-sort tick changes. */
    private List<S2C_SyncHistoryPacket.Entry> visible = List.of();
    private boolean visibleStale = true;

    // Cache viewer coordinates once per client tick for distance sorting.
    private String viewerDimension = "";
    private double viewerX;
    private double viewerZ;

    public TrackerScreen(S2C_SyncHistoryPacket data) {
        super(Component.translatable("gui.cobbletracker.title"));
        // Notify toggles require a mutable copy of packet rows.

        this.categories = new ArrayList<>(data.categories());
        this.entries = data.entries();
        this.stats = data.stats();
        for (S2C_SyncHistoryPacket.Category c : categories) {
            categoryById.put(c.id(), c);
        }

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

    // Clamp layout to the current GUI-scaled viewport.

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
        return panelH() - HEADER_H - 36;
    }

    private int statsTab() {
        return categories.size() + 1;
    }

    private int notifyTab() {
        return categories.size() + 2;
    }

    private boolean onCardTab() {
        return selectedTab != statsTab() && selectedTab != notifyTab();
    }

    private int tabStep() {
        int room = panelH() - HEADER_H - 10;
        int wanted = categories.size() + 3; 
        int step = wanted <= 0 ? TAB_STEP_MAX : room / wanted;
        return Math.max(TAB_STEP_MIN, Math.min(TAB_STEP_MAX, step));
    }

    @Override
    public void tick() {
        super.tick();
        refreshViewer();
        if (sort == Sort.DISTANCE) {
            visibleStale = true; 
        }
    }

    private void refreshViewer() {
        if (minecraft == null || minecraft.player == null) {
            viewerDimension = "";
            return;
        }
        viewerDimension = minecraft.player.level().dimension().location().toString();
        viewerX = minecraft.player.getX();
        viewerZ = minecraft.player.getZ();
    }

    @Override
    protected void init() {
        refreshViewer();
        int lx = left();
        int ty = top();
        int step = tabStep();
        int sidebarBottom = ty + panelH() - 6;

        int tabY = ty + HEADER_H + 6;
        int notifyY = sidebarBottom - TAB_H;
        int statsY = notifyY - step;
        int categoriesY = tabY + step;
        int capacity = Math.max(1, (statsY - 4 - categoriesY - TAB_H) / step + 1);
        int maximumOffset = Math.max(0, categories.size() - capacity);
        if ((revealSelectedCategory || categoryCapacity != capacity)
                && selectedTab >= 1 && selectedTab <= categories.size()) {
            int selected = selectedTab - 1;
            if (selected < categoryOffset) {
                categoryOffset = selected;
            } else if (selected >= categoryOffset + capacity) {
                categoryOffset = selected - capacity + 1;
            }
        }
        categoryCapacity = capacity;
        categoryOffset = Math.max(0, Math.min(maximumOffset, categoryOffset));
        revealSelectedCategory = false;
        int allWidth = sidebarW() - 12;
        if (maximumOffset > 0) {
            allWidth -= 28;
            addCategoryArrow(lx + sidebarW() - 32, tabY, "↑", -1, categoryOffset > 0);
            addCategoryArrow(lx + sidebarW() - 18, tabY, "↓", 1, categoryOffset < maximumOffset);
        }
        Component allLabel = Component.translatable("gui.cobbletracker.tab.all");
        addRenderableWidget(new ThemedButton(lx + 6, tabY, allWidth, TAB_H,
                Component.literal(this.font.plainSubstrByWidth(allLabel.getString(), allWidth - 4)),
                selectedTab == 0, () -> selectTab(0), allLabel));
        for (int i = categoryOffset; i < Math.min(categories.size(), categoryOffset + capacity); i++) {
            S2C_SyncHistoryPacket.Category category = categories.get(i);
            addTab(lx + 6, categoriesY + (i - categoryOffset) * step,
                    Component.literal(category.display().toUpperCase(Locale.ROOT)), i + 1);
        }
        addTab(lx + 6, statsY, Component.translatable("gui.cobbletracker.tab.stats"), statsTab());
        addTab(lx + 6, notifyY, Component.translatable("gui.cobbletracker.tab.notify"), notifyTab());

        // Tab changes rebuild widgets; clear the previous sort-button reference.

        sortButton = null;
        if (onCardTab()) {
            sortButton = new ThemedButton(contentLeft() + contentWidth() - 88, ty + 3, 60, 16,
                    sort.label(), false, this::cycleSort,
                    Component.translatable("gui.cobbletracker.sort"));
            addRenderableWidget(sortButton);
        }

        addRenderableWidget(new ThemedButton(lx + panelW() - 24, ty + 3, 18, 16,
                Component.literal("✕"), false, this::onClose,
                Component.translatable("gui.cobbletracker.close")));
    }

    private void addTab(int x, int y, Component label, int tabIndex) {
        addRenderableWidget(new ThemedButton(x, y, sidebarW() - 12, TAB_H,
                Component.literal(this.font.plainSubstrByWidth(label.getString(), sidebarW() - 16)),
                selectedTab == tabIndex, () -> selectTab(tabIndex), label));
    }

    private void addCategoryArrow(int x, int y, String label, int direction, boolean enabled) {
        ThemedButton button = new ThemedButton(x, y, 12, TAB_H, Component.literal(label), false,
                () -> scrollCategories(direction * categoryCapacity),
                Component.literal(direction < 0 ? "Previous categories" : "Next categories"));
        button.active = enabled;
        addRenderableWidget(button);
    }

    private void scrollCategories(int amount) {
        int next = Math.max(0, Math.min(Math.max(0, categories.size() - categoryCapacity), categoryOffset + amount));
        if (next != categoryOffset) {
            categoryOffset = next;
            rebuildWidgets();
        }
    }

    private void selectTab(int tab) {
        this.selectedTab = tab;
        this.scroll = 0;
        this.visibleStale = true;
        this.revealSelectedCategory = true;
        rebuildWidgets();
    }

    private void cycleSort() {
        sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
        if (sortButton != null) {
            sortButton.setMessage(sort.label());
        }
        scroll = 0;
        visibleStale = true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Draw the backdrop below this panel without vanilla blur.
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

        g.fill(lx + 2, ty + HEADER_H + 2, lx + sidebarW(), ty + panelH() - 2, Ui.withAlpha(t.scrim(), 0x66));
        Ui.divider(g, contentLeft() - 4, ty + HEADER_H + 18, contentWidth() + 8);

        if (selectedTab == statsTab()) {
            renderStats(g, mouseX, mouseY);
        } else if (selectedTab == notifyTab()) {
            renderNotify(g, mouseX, mouseY);
        } else {
            renderCards(g, mouseX, mouseY, appear);
        }

        super.render(g, mouseX, mouseY, partialTick); 

        String themeHint = "/ct theme " + String.join("/", Theme.names());
        g.drawString(this.font, this.font.plainSubstrByWidth(themeHint, contentWidth()),
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

        boolean hunted = HuntState.isHunted(speciesPath(e));
        String name = e.species() + (e.shiny() ? " ✨" : "") + (hunted ? " ⌖" : "");
        int textWidth = Math.max(0, w - 50);
        String badge = this.font.plainSubstrByWidth(levelBadge(e), textWidth / 2);
        int nameWidth = Math.max(0, textWidth - this.font.width(badge) - (badge.isEmpty() ? 0 : 4));
        g.drawString(this.font, this.font.plainSubstrByWidth(name, nameWidth), tx, y + 6,
                hunted ? HuntState.HUNT_COLOR : t.text(), false);

        S2C_SyncHistoryPacket.Category cat = categoryById.get(e.category());
        String tier = cat != null ? cat.display() : e.category();
        String ago = this.font.plainSubstrByWidth(timeAgo(e.spawnTime()), textWidth / 2);
        int tierWidth = Math.max(0, textWidth - this.font.width(ago) - 12);
        Ui.pill(g, this.font, this.font.plainSubstrByWidth(tier, tierWidth), tx, y + 18,
                Ui.withAlpha(accent, 0xCC), 0xFF10101A);

        String loc = coords(e) + "  ·  " + SpriteClientUtils.prettyName(e.biome());
        String caught = "";
        if (e.caught()) {
            String who = e.catcherName() == null || e.catcherName().isBlank() ? "" : e.catcherName();
            Component caughtLabel = who.isEmpty()
                    ? Component.translatable("gui.cobbletracker.caught")
                    : Component.translatable("gui.cobbletracker.caught.by", who);
            caught = this.font.plainSubstrByWidth(caughtLabel.getString(), textWidth / 2);
        }
        int locationWidth = Math.max(0, textWidth - this.font.width(caught) - (caught.isEmpty() ? 0 : 4));
        g.drawString(this.font, this.font.plainSubstrByWidth(loc, locationWidth), tx, y + 31, t.textMuted(), false);

        String stats = statsLine(e);
        if (!stats.isEmpty()) {
            g.drawString(this.font, this.font.plainSubstrByWidth(stats, textWidth), tx, y + 42, t.textDim(), false);
        }

        if (!badge.isEmpty()) {
            g.drawString(this.font, badge, x + w - this.font.width(badge) - 6, y + 6, t.textDim(), false);
        }
        g.drawString(this.font, ago, x + w - this.font.width(ago) - 6, y + 18, t.textMuted(), false);
        if (!caught.isEmpty()) {
            g.drawString(this.font, caught, x + w - this.font.width(caught) - 6, y + 31, t.positive(), false);
        }
    }

    private static String levelBadge(S2C_SyncHistoryPacket.Entry e) {
        StringBuilder sb = new StringBuilder(10);
        if (e.level() > 0) {
            sb.append("Lv.").append(e.level());
        }
        String symbol = switch (e.gender()) {
            case "male" -> "♂";
            case "female" -> "♀";
            default -> "";
        };
        if (!symbol.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(symbol);
        }
        return sb.toString();
    }

    private static String statsLine(S2C_SyncHistoryPacket.Entry e) {
        StringBuilder sb = new StringBuilder(48);
        appendPart(sb, capitalise(e.nature()));
        appendPart(sb, capitalise(e.ability()));
        if (!e.ivs().isEmpty()) {
            String ivs = "IVs " + e.ivs();
            int perfect = countPerfect(e.ivs());
            if (perfect > 0) {
                ivs = ivs + " (" + perfect + "×31)";
            }
            appendPart(sb, ivs);
        }
        if (!e.evs().isEmpty()) {
            appendPart(sb, "EVs " + e.evs());
        }
        return sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("  ·  ");
        }
        sb.append(part);
    }

    private static int countPerfect(String ivs) {
        int perfect = 0;
        for (String part : ivs.split("/")) {
            try {
                if (Integer.parseInt(part.trim()) >= 31) {
                    perfect++;
                }
            } catch (NumberFormatException ignored) {

            }
        }
        return perfect;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void renderStats(GuiGraphics g, int mouseX, int mouseY) {
        Theme t = Ui.theme();
        int cx = contentLeft();
        int cy = contentTop();
        int cw = contentWidth();
        int ch = contentHeight();
        Ui.sectionHeader(g, this.font, Component.translatable("gui.cobbletracker.stats.title").getString(), cx, cy);
        int viewHeight = ch - 14;
        clampScroll(stats.size() * 14, viewHeight);
        int y = cy + 14 - (int) scroll;
        int rank = 1;
        int max = stats.isEmpty() ? 1 : Math.max(1, stats.get(0).caught());
        int barX = cx + Math.min(128, cw * 2 / 3);
        String hoveredPlayer = null;
        g.enableScissor(cx, cy + 14, cx + cw, cy + ch);
        for (S2C_SyncHistoryPacket.Stat s : stats) {
            if (y > cy + ch) {
                break;
            }
            g.drawString(this.font, "#" + rank, cx, y, t.textMuted(), false);
            g.drawString(this.font, this.font.plainSubstrByWidth(s.player(), Math.max(0, barX - cx - 28)),
                    cx + 24, y, t.text(), false);
            if (mouseX >= cx + 24 && mouseX < barX && mouseY >= Math.max(y, cy + 14)
                    && mouseY < Math.min(y + 10, cy + ch)) {
                hoveredPlayer = s.player();
            }
            String n = Integer.toString(s.caught());
            int barW = Math.max(0, cx + cw - barX - this.font.width(n) - 8);
            Ui.bar(g, barX, y + 1, barW, 6, s.caught() / (float) max, t.accent());
            g.drawString(this.font, n, cx + cw - this.font.width(n) - 2, y, t.textDim(), false);
            y += 14;
            rank++;
        }
        g.disableScissor();
        Ui.scrollbar(g, cx + cw - 2, cy + 14, viewHeight, stats.size() * 14, viewHeight, scroll);
        if (stats.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.cobbletracker.stats.empty"),
                    cx + cw / 2, cy + ch / 2, t.textMuted());
        }
        if (hoveredPlayer != null) {
            g.renderTooltip(this.font, Component.literal(hoveredPlayer), mouseX, mouseY);
        }
    }

    private static final int NOTIFY_ROW_H = 44;
    private float notifyScroll;

    private int notifyToggleWidth() {
        return Math.min(46, Math.max(20, (contentWidth() - 16) / 5));
    }

    private void renderNotify(GuiGraphics g, int mouseX, int mouseY) {
        Theme t = Ui.theme();
        int cx = contentLeft();
        int cy = contentTop();
        int cw = contentWidth();
        int ch = contentHeight();
        Ui.sectionHeader(g, this.font, Component.translatable("gui.cobbletracker.notify.title").getString(), cx, cy);
        if (categories.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.cobbletracker.notify.empty"),
                    cx + cw / 2, cy + ch / 2, t.textMuted());
            return;
        }
        int w = notifyToggleWidth();
        int y = cy + 16 - (int) notifyScroll;
        g.enableScissor(cx, cy + 14, cx + cw, cy + ch);
        for (S2C_SyncHistoryPacket.Category c : categories) {
            if (y + NOTIFY_ROW_H >= cy + 14 && y < cy + ch) {
                g.fill(cx, y, cx + 2, y + NOTIFY_ROW_H - 4, c.color());
                g.drawString(this.font, this.font.plainSubstrByWidth(c.display(), cw - 12), cx + 8, y + 2, t.text());
                boolean[] active = {c.notifyEnabled(), !c.notifyMuted(), c.channel(0), c.channel(1), c.channel(2)};
                String[] keys = {"enabled", "sound", "chat", "title_channel", "actionbar"};
                for (int i = 0; i < keys.length; i++) {
                    int x = cx + i * (w + 4);
                    int ty = y + 15;
                    boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= ty && mouseY < ty + 18;
                    int fill = active[i] ? Ui.withAlpha(t.accent(), hovered ? 0xFF : 0xCC) : Ui.withAlpha(t.scrim(), 0xAA);
                    g.fill(x, ty, x + w, ty + 18, fill);
                    String label = Component.translatable("gui.cobbletracker.notify." + keys[i]).getString();
                    g.drawCenteredString(this.font, this.font.plainSubstrByWidth(label, w - 2), x + w / 2,
                            ty + 5, active[i] ? t.buttonText() : t.textMuted());
                }
                if (!c.serverEnabled()) {
                    g.drawString(this.font, Component.translatable("gui.cobbletracker.notify.serveroff"),
                            cx + 8, y + 34, t.textMuted(), false);
                }
            }
            y += NOTIFY_ROW_H;
        }
        g.disableScissor();
        Ui.scrollbar(g, cx + cw - 2, cy + 14, ch - 14, categories.size() * NOTIFY_ROW_H,
                ch - 14, notifyScroll);
    }

    private boolean notifyClicked(double mouseX, double mouseY) {
        int cx = contentLeft();
        int cy = contentTop();
        if (mouseX < cx || mouseX >= cx + contentWidth() || mouseY < cy + 14 || mouseY >= cy + contentHeight()) return false;
        int w = notifyToggleWidth();
        int row = (int) (mouseY - cy - 16 + notifyScroll) / NOTIFY_ROW_H;
        if (row < 0 || row >= categories.size()) return false;
        int y = cy + 16 - (int) notifyScroll + row * NOTIFY_ROW_H;
        if (mouseY < y + 15 || mouseY >= y + 33) return false;
        S2C_SyncHistoryPacket.Category c = categories.get(row);
        for (int i = 0; i < 5; i++) {
            int x = cx + i * (w + 4);
            if (mouseX < x || mouseX >= x + w) continue;
            if (i == 0) {
                applyNotify(row, c.withPrefs(!c.notifyEnabled(), c.notifyMuted(), c.notifyRadius()),
                        c.notifyEnabled() ? "off" : "on");
            } else if (i == 1) {
                applyNotify(row, c.withPrefs(c.notifyEnabled(), !c.notifyMuted(), c.notifyRadius()),
                        "sound " + (c.notifyMuted() ? "on" : "off"));
            } else {
                int channel = i - 2;
                String name = new String[] {"chat", "title", "actionbar"}[channel];
                applyNotify(row, c.withChannel(channel, !c.channel(channel)),
                        name + " " + (c.channel(channel) ? "off" : "on"));
            }
            return true;
        }
        return false;
    }

    private void applyNotify(int index, S2C_SyncHistoryPacket.Category updated, String args) {
        categories.set(index, updated);
        categoryById.put(updated.id(), updated);
        if (minecraft != null && minecraft.player != null && minecraft.player.connection != null) {
            minecraft.player.connection.sendCommand("cobbletracker notify " + updated.id() + " " + args);
        }
    }

    private List<S2C_SyncHistoryPacket.Entry> visibleEntries() {
        if (visibleStale) {
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

    /** Returns {@link Double#MAX_VALUE} for another dimension so it sorts last. */
    private double distanceOf(S2C_SyncHistoryPacket.Entry e) {
        if (viewerDimension.isEmpty() || !viewerDimension.equals(e.world())) {
            return Double.MAX_VALUE;
        }
        return Geometry.horizontalDistance(e.x() - viewerX, e.z() - viewerZ);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && selectedTab == notifyTab() && notifyClicked(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && onCardTab()) {
            List<S2C_SyncHistoryPacket.Entry> shown = visibleEntries();
            int cx = contentLeft();
            int y = contentTop() - (int) scroll;
            for (S2C_SyncHistoryPacket.Entry e : shown) {
                if (hitsCard(mouseX, mouseY, cx, y, contentWidth())) {
                    if (hasShiftDown()) {
                        toggleHunt(e);
                    } else {
                        copyCoords(e);
                    }
                    return true;
                }
                y += CARD_H + CARD_GAP;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

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

    private void toggleHunt(S2C_SyncHistoryPacket.Entry e) {
        String path = speciesPath(e);
        if (path.isEmpty() || minecraft == null) {
            return;
        }
        HuntState.toggle(path);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(
                    HuntState.isHunted(path) ? "gui.cobbletracker.hunt.added" : "gui.cobbletracker.hunt.removed",
                    e.species()), true);
        }
    }

    /** Cached resource path used for hunt matching. */
    private String speciesPath(S2C_SyncHistoryPacket.Entry e) {
        String id = e.speciesId() == null ? "" : e.speciesId();
        return speciesPaths.computeIfAbsent(id, raw -> {
            int colon = raw.indexOf(':');
            return (colon < 0 ? raw : raw.substring(colon + 1)).toLowerCase(Locale.ROOT);
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= left() + 2 && mouseX < left() + sidebarW()
                && mouseY >= top() + HEADER_H + 2 && mouseY < top() + panelH() - 2) {
            if (scrollY != 0) {
                scrollCategories(scrollY > 0 ? -1 : 1);
            }
            return true;
        }
        if (selectedTab == notifyTab()) {
            float maximum = Math.max(0, categories.size() * NOTIFY_ROW_H - (contentHeight() - 16));
            notifyScroll = Math.max(0, Math.min(maximum, notifyScroll - (float) scrollY * NOTIFY_ROW_H));
            return true;
        }
        if (selectedTab == statsTab()) {
            scroll -= (float) scrollY * 14;
            clampScroll(stats.size() * 14, contentHeight() - 14);
            return true;
        }
        if (onCardTab()) {
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
