package dev.hafnium.client.gui;

import dev.hafnium.client.HafniumClient;
import dev.hafnium.client.gui.anim.Animation;
import dev.hafnium.client.gui.anim.AnimationUtil;
import dev.hafnium.client.gui.component.CategoryPanel;
import dev.hafnium.client.gui.component.ModuleButton;
import dev.hafnium.client.gui.component.SearchBar;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.render.Icon;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Implementation;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

/**
 * Hafnium ClickGUI. Purely a frontend: every module and setting is a placeholder whose state only changes what is
 * drawn here.
 *
 * <p>Layout units: one unit is one GUI pixel at GUI scale 2. The screen picks the largest whole pixel scale (at most
 * the player's GUI scale) at which the six-panel row still fits, so everything stays pixel-sharp.
 */
public final class ClickGuiScreen extends Screen {
    private static final float GAP = 10f;
    private static final float ROW_W = CategoryPanel.WIDTH * 6 + GAP * 5;
    private static final float MIN_W = ROW_W + 32f;
    private static final float MIN_H = 400f;
    private static final float PANEL_TOP = 64f;
    private static final long TOOLTIP_DELAY = 650;
    /** Bottom space kept clear for the CONFIGS button (button height + margins). */
    private static final float BOTTOM_RESERVE = 15.5f + 26f + 8f;

    private final Screen parent;
    private final Map<Category, CategoryPanel> panels = new EnumMap<>(Category.class);
    private final SearchBar search;
    private final Animation open = new Animation(200, false);
    private final Animation hoverFolder = new Animation(150, false);
    private final Animation hoverGear = new Animation(150, false);
    private final Animation hoverClose = new Animation(150, false);
    private final Animation hoverConfigs = new Animation(150, false);
    private final Animation noResults = new Animation(160, false);

    private boolean closing;
    private int guiScale = 2;
    private int pixelScale = 2;
    private float uw;
    private float uh;
    private float originX;
    private int columns = 6;
    private float rowWidth = ROW_W;
    private final float[] columnBottom = new float[Category.values().length];
    private long lastFrame;
    private String query = "";
    private Module tooltipModule;
    private long tooltipSince;

    public ClickGuiScreen(Screen parent) {
        super(Component.translatable("hafnium.clickgui.title"));
        this.parent = parent;
        for (Category c : Category.values()) {
            CategoryPanel p = new CategoryPanel(c);
            p.setOnFocus(() -> {
                ClickGuiState.setFocused(c);
                ClickGuiState.bringToFront(c);
            });
            panels.put(c, p);
        }
        this.search = new SearchBar(this::applyQuery);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        computeScale();
        if (lastFrame == 0) {
            lastFrame = Util.getMillis();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (!closing) {
            closing = true;
            GuiInput.reset();
            open.setTarget(false);
        }
    }

    @Override
    public void removed() {
        GuiInput.endDrag();
    }

    private void finishClose() {
        GuiInput.reset();
        minecraft.setScreen(parent);
    }

    /** Opens the config manager on top of this screen. */
    public void openConfigs() {
        GuiInput.reset();
        minecraft.setScreen(new ConfigScreen(this));
    }

    private void computeScale() {
        guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
        int fbW = width * guiScale;
        int fbH = height * guiScale;
        int p = guiScale;
        while (p > 1 && (fbW / (float) p < MIN_W || fbH / (float) p < MIN_H)) {
            p--;
        }
        pixelScale = p;
        uw = fbW / (float) p;
        uh = fbH / (float) p;
        layoutPanels();
    }

    /**
     * Default slots: as many columns as fit (at least the six of the reference layout when the scale allows), each
     * panel placed in the currently shortest column. Dragged panels keep their offset from their slot.
     */
    private void layoutPanels() {
        float colW = CategoryPanel.WIDTH + GAP;
        int fit = (int) ((uw - 16f + GAP) / colW);
        columns = Math.max(1, Math.min(Category.values().length, fit));
        rowWidth = columns * CategoryPanel.WIDTH + (columns - 1) * GAP;
        originX = Math.max(8f, (uw - rowWidth) / 2f);
        for (int i = 0; i < columns; i++) {
            columnBottom[i] = PANEL_TOP;
        }
        for (Category c : Category.values()) {
            int col = 0;
            for (int i = 1; i < columns; i++) {
                if (columnBottom[i] < columnBottom[col] - 0.5f) {
                    col = i;
                }
            }
            CategoryPanel panel = panels.get(c);
            panel.place(originX + col * colW, columnBottom[col], uw, uh, BOTTOM_RESERVE);
            columnBottom[col] += panel.estimatedHeight() + GAP;
        }
    }

    public int guiScale() {
        return guiScale;
    }

    public int pixelScale() {
        return pixelScale;
    }

    private double ux(double guiX) {
        return guiX * guiScale / pixelScale;
    }

    private double uy(double guiY) {
        return guiY * guiScale / pixelScale;
    }

    private void applyQuery(String q) {
        query = q;
        int total = 0;
        for (CategoryPanel p : panels.values()) {
            total += p.setQuery(q);
        }
        noResults.setTarget(!q.isEmpty() && total == 0);
    }

    // ------------------------------------------------------------------ layout of the top bar

    private float logoX() {
        return originX + 2f;
    }

    private float rightEdge() {
        return originX + rowWidth;
    }

    private float searchX() {
        return uw / 2f - SearchBar.WIDTH / 2f;
    }

    public static final float BAR_CY = 27.5f;
    private static final float ICON_HIT = 15f;

    public float folderCx() {
        return rightEdge() - 94f;
    }

    public float gearCx() {
        return rightEdge() - 51.5f;
    }

    public float closeCx() {
        return rightEdge() - 11f;
    }

    public float configsW() {
        return 105f;
    }

    public float configsH() {
        return 26f;
    }

    public float configsX() {
        return uw / 2f - configsW() / 2f;
    }

    public float configsY() {
        return uh - 15.5f - configsH();
    }

    private static boolean nearIcon(double mx, double my, float cx, float cy) {
        return Math.abs(mx - cx) <= ICON_HIT && Math.abs(my - cy) <= ICON_HIT;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            renderPanorama(g, partialTick);
        }
        if (ThemeManager.blur()) {
            renderBlurredBackground(g);
        }
        float a = open.eased();
        g.fillGradient(0, 0, width, height,
                ThemeManager.withAlpha(0xFF15131C, 0.55f * a), ThemeManager.withAlpha(0xFF1A1426, 0.30f * a));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderGui(g, mouseX, mouseY, true);
    }

    /** Draws the whole GUI; {@code interactive} is false when another screen (configs) is on top. */
    void renderGui(GuiGraphics g, int mouseX, int mouseY, boolean interactive) {
        long now = Util.getMillis();
        float dt = Math.min(100f, Math.max(0f, now - lastFrame));
        lastFrame = now;
        ThemeManager.sync();
        if (!closing) {
            open.setTarget(true);
        }
        open.update(dt);
        if (closing && open.linear() <= 0f) {
            finishClose();
            return;
        }
        computeScale();

        double mx = interactive ? ux(mouseX) : -1000;
        double my = interactive ? uy(mouseY) : -1000;
        boolean live = interactive && !closing;

        // ---- update
        search.layout(searchX(), BAR_CY - SearchBar.HEIGHT / 2f);
        search.update(dt, mx, my, live);
        hoverFolder.setTarget(live && nearIcon(mx, my, folderCx(), BAR_CY));
        hoverGear.setTarget(live && nearIcon(mx, my, gearCx(), BAR_CY));
        hoverClose.setTarget(live && nearIcon(mx, my, closeCx(), BAR_CY));
        hoverConfigs.setTarget(live && GuiRenderUtil.inside(mx, my, configsX(), configsY(), configsW(), configsH()));
        hoverFolder.update(dt);
        hoverGear.update(dt);
        hoverClose.update(dt);
        hoverConfigs.update(dt);
        noResults.update(dt);

        CategoryPanel top = live ? topPanelAt(mx, my) : null;
        for (Category c : ClickGuiState.order()) {
            CategoryPanel p = panels.get(c);
            p.update(dt, mx, my, live && (p == top || GuiInput.drag() == p));
        }

        // ---- draw
        GuiRenderUtil.begin(g, guiScale, pixelScale);
        float a = open.eased();
        GuiRenderUtil.setAlpha(a);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        float s = AnimationUtil.lerp(0.965f, 1f, a);
        float cxp = uw * pixelScale / 2f;
        float cyp = uh * pixelScale / 2f;
        pose.translate(cxp, cyp);
        pose.scale(s, s);
        pose.translate(-cxp, -cyp);

        renderTopBar();
        for (Category c : ClickGuiState.order()) {
            panels.get(c).render(mx, my);
        }
        renderNoResults();
        renderConfigsButton();
        renderTooltip(mx, my, top, now, live);

        pose.popMatrix();
        GuiRenderUtil.end();
    }

    private static final net.minecraft.resources.Identifier LOGO =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("hafnium", "textures/gui/logo.png");

    private void renderTopBar() {
        // Periodic-table logo tile
        float lx = logoX();
        float ly = 13.5f;
        float ls = 35f;
        // The logo image includes its orbit and glow; its tile covers ~62% of the image width.
        float img = ls / 0.62f;
        GuiRenderUtil.image(LOGO, 160, lx + ls / 2f - img / 2f, ly + ls / 2f - img / 2f, img, img);

        // Divider and wordmark
        GuiRenderUtil.rect(lx + 53f, BAR_CY - 9.5f, 1f, 19f, ThemeManager.withAlpha(ThemeManager.TEXT, 0.55f));
        GuiRenderUtil.text(FontRole.TITLE, "Hafnium Client", lx + 68f, BAR_CY, ThemeManager.TEXT);

        search.render();

        topIcon(Icon.FOLDER, folderCx(), 23f, hoverFolder);
        topIcon(Icon.GEAR, gearCx(), 22f, hoverGear);
        topIcon(Icon.CLOSE, closeCx(), 28f, hoverClose);
    }

    private void topIcon(Icon icon, float cx, float size, Animation hover) {
        float h = hover.eased();
        if (h > 0.01f) {
            GuiRenderUtil.circle(cx, BAR_CY, 15f, ThemeManager.withAlpha(0xFFFFFFFF, 0.07f * h));
        }
        GuiRenderUtil.icon(icon, cx - size / 2f, BAR_CY - size / 2f, size,
                ThemeManager.lerp(0xFFE6E1EE, ThemeManager.accentHover(), h));
    }

    private void renderConfigsButton() {
        float x = configsX();
        float y = configsY();
        float w = configsW();
        float h = configsH();
        float hv = hoverConfigs.eased();
        GuiRenderUtil.roundRect(x, y, w, h, h / 2f,
                ThemeManager.lerp(ThemeManager.withAlpha(ThemeManager.HEADER, 0.85f), ThemeManager.withAlpha(ThemeManager.accentStrong(), 0.6f), hv * 0.6f));
        GuiRenderUtil.roundOutline(x, y, w, h, h / 2f, ThemeManager.lerp(ThemeManager.accent(), ThemeManager.accentHover(), hv));
        float cy = y + h / 2f;
        float icon = 14f;
        GuiRenderUtil.icon(Icon.FOLDER, x + 19f, cy - icon / 2f, icon, ThemeManager.TEXT);
        GuiRenderUtil.text(FontRole.HEADER, "CONFIGS", x + 43f, cy, ThemeManager.TEXT);
    }

    private void renderNoResults() {
        float n = noResults.eased();
        if (n <= 0.01f) {
            return;
        }
        float cy = PANEL_TOP + CategoryPanel.HEADER_H + 34f;
        float prev = GuiRenderUtil.alpha();
        GuiRenderUtil.setAlpha(prev * n);
        String msg = "No modules match “" + search.input().text().trim() + "”";
        String hint = "Try a shorter name, or clear the search";
        float w = Math.max(GuiRenderUtil.textWidth(FontRole.TEXT, msg), GuiRenderUtil.textWidth(FontRole.SMALL, hint)) + 28f;
        GuiRenderUtil.roundRect(uw / 2f - w / 2f, cy - 18f, w, 36f, 5f, ThemeManager.withAlpha(ThemeManager.PANEL, 0.92f));
        GuiRenderUtil.roundOutline(uw / 2f - w / 2f, cy - 18f, w, 36f, 5f, ThemeManager.BORDER);
        GuiRenderUtil.textCentered(FontRole.TEXT, msg, uw / 2f, cy - 5f, ThemeManager.TEXT);
        GuiRenderUtil.textCentered(FontRole.SMALL, hint, uw / 2f, cy + 7f, ThemeManager.TEXT_SECONDARY);
        GuiRenderUtil.setAlpha(prev);
    }

    private void renderTooltip(double mx, double my, CategoryPanel top, long now, boolean live) {
        Module hovered = live && top != null && GuiInput.drag() == null ? top.hoveredModule(mx, my) : null;
        if (hovered != tooltipModule) {
            tooltipModule = hovered;
            tooltipSince = now;
        }
        if (hovered == null || now - tooltipSince < TOOLTIP_DELAY) {
            return;
        }
        float t = AnimationUtil.clamp01((now - tooltipSince - TOOLTIP_DELAY) / 150f);
        String line1 = hovered.description();
        String line2;
        int line2Color = ThemeManager.TEXT_SECONDARY;
        if (hovered.state() == ModuleState.FAILED && hovered.lastError() != null) {
            line2 = hovered.lastError();
            line2Color = ThemeManager.DANGER;
        } else if (hovered.implementation() == Implementation.PLACEHOLDER) {
            line2 = "Placeholder — not implemented yet";
            line2Color = ThemeManager.accentHover();
        } else if (!hovered.isToggleable()) {
            line2 = "Settings only · click to edit";
        } else {
            line2 = "Left-click toggle · Right-click settings";
        }
        float w = Math.max(GuiRenderUtil.textWidth(FontRole.SMALL, line1), GuiRenderUtil.textWidth(FontRole.SMALL, line2)) + 14f;
        float h = 29f;
        float x = (float) Math.min(mx + 9f, uw - w - 4f);
        float y = (float) Math.min(my + 11f, uh - h - 4f);
        float prev = GuiRenderUtil.alpha();
        GuiRenderUtil.setAlpha(prev * t);
        GuiRenderUtil.roundRect(x, y, w, h, 4f, ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.96f));
        GuiRenderUtil.roundOutline(x, y, w, h, 4f, ThemeManager.withAlpha(ThemeManager.accent(), 0.6f));
        GuiRenderUtil.text(FontRole.SMALL, line1, x + 7f, y + 9.5f, ThemeManager.TEXT);
        GuiRenderUtil.text(FontRole.SMALL, line2, x + 7f, y + 20f, line2Color);
        GuiRenderUtil.setAlpha(prev);
    }

    private CategoryPanel topPanelAt(double mx, double my) {
        for (int i = ClickGuiState.order().size() - 1; i >= 0; i--) {
            CategoryPanel p = panels.get(ClickGuiState.order().get(i));
            if (p.contains(mx, my)) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) {
            return true;
        }
        double mx = ux(event.x());
        double my = uy(event.y());
        int button = event.button();
        GuiInput.KeyHandler previousFocus = GuiInput.keys();

        if (search.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0 && nearIcon(mx, my, closeCx(), BAR_CY)) {
            onClose();
            return true;
        }
        if (button == 0 && nearIcon(mx, my, folderCx(), BAR_CY)) {
            openConfigs();
            return true;
        }
        if (button == 0 && nearIcon(mx, my, gearCx(), BAR_CY)) {
            revealClickGuiSettings();
            return true;
        }
        if (button == 0 && GuiRenderUtil.inside(mx, my, configsX(), configsY(), configsW(), configsH())) {
            openConfigs();
            return true;
        }
        CategoryPanel top = topPanelAt(mx, my);
        if (top != null && top.mouseClicked(mx, my, button)) {
            // A click anywhere else ends text entry / keybind listening that was active before it.
            if (previousFocus != null && GuiInput.keys() == previousFocus && !(previousFocus instanceof dev.hafnium.client.gui.setting.KeybindComponent)) {
                GuiInput.focusKeys(null);
            }
            return true;
        }
        GuiInput.focusKeys(null);
        return true;
    }

    /** Gear icon: open Client > ClickGUI settings and bring that panel forward. */
    private void revealClickGuiSettings() {
        CategoryPanel client = panels.get(Category.CLIENT);
        client.expand();
        ClickGuiState.setFocused(Category.CLIENT);
        ClickGuiState.bringToFront(Category.CLIENT);
        ModuleButton b = client.find("ClickGUI");
        if (b != null) {
            b.setExpanded(!b.isExpanded());
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        GuiInput.endDrag();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        GuiInput.DragHandler d = GuiInput.drag();
        if (d != null) {
            d.drag(ux(event.x()), uy(event.y()));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double mx = ux(mouseX);
        double my = uy(mouseY);
        CategoryPanel top = topPanelAt(mx, my);
        return top != null && top.mouseScrolled(mx, my, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing) {
            return true;
        }
        GuiInput.KeyHandler keys = GuiInput.keys();
        // A listening keybind selector gets every key, Escape included (Escape clears the bind).
        if (keys instanceof dev.hafnium.client.gui.setting.KeybindComponent) {
            return keys.key(event);
        }
        if (event.isEscape()) {
            onClose();
            return true;
        }
        if (keys != null && keys.key(event)) {
            return true;
        }
        if (keys == null && (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT || HafniumClient.openKey().matches(event))) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_F && event.hasControlDown()) {
            search.input().focus();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (closing) {
            return true;
        }
        GuiInput.KeyHandler keys = GuiInput.keys();
        if (keys instanceof dev.hafnium.client.gui.setting.KeybindComponent) {
            return true;
        }
        if (keys == null && event.isAllowedChatCharacter() && event.codepoint() != ' ') {
            // Type-to-search: typing anywhere focuses the search bar.
            search.input().focus();
            keys = search.input();
        }
        return keys != null && keys.chr(event.codepoint());
    }

    // ------------------------------------------------------------------ read-only accessors (also used by the client gametests)

    /** Layout size in units (screen size divided by the pixel scale). */
    public float layoutWidth() {
        return uw;
    }

    public float layoutHeight() {
        return uh;
    }

    public CategoryPanel panel(Category c) {
        return panels.get(c);
    }

    public SearchBar searchBar() {
        return search;
    }
}
