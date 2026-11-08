package dev.hafnium.client.gui.component;

import dev.hafnium.client.gui.ClickGuiState;
import dev.hafnium.client.gui.GuiInput;
import dev.hafnium.client.gui.anim.Animation;
import dev.hafnium.client.gui.render.CategoryIcons;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.render.Icon;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.HafniumCore;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** A draggable dropdown panel: header (icon, name, chevron) and a list of module rows. */
public final class CategoryPanel implements GuiInput.DragHandler {
    public static final float WIDTH = 123f;
    public static final float HEADER_H = 30f;
    private static final float PAD_TOP = 3.5f;
    private static final float PAD_BOTTOM = 3.5f;
    private static final float RADIUS = 4.5f;
    private static final float DRAG_THRESHOLD = 2f;

    private final Category category;
    private final ClickGuiState.PanelState state;
    private final List<ModuleButton> buttons = new ArrayList<>();
    private final Animation expand;
    private final Animation headerHover = new Animation(150, false);
    private final Animation focus;
    private final Animation hasRows = new Animation(170, true);

    private boolean searching;
    private float scroll;
    private float bodyH;
    private float maxBodyH = Float.MAX_VALUE;

    // drag bookkeeping
    private double pressX;
    private double pressY;
    private float startX;
    private float startY;
    private boolean moved;
    private float slotX;
    private float slotY;
    private Runnable onFocus = () -> { };

    public CategoryPanel(Category category) {
        this.category = category;
        this.state = ClickGuiState.panel(category);
        this.expand = new Animation(220, state.expanded);
        this.focus = new Animation(200, ClickGuiState.focused() == category);
        for (Module m : HafniumCore.get().modules().byCategory(category)) {
            buttons.add(new ModuleButton(m));
        }
    }

    public Category category() {
        return category;
    }

    public List<ModuleButton> buttons() {
        return buttons;
    }

    public float x() {
        return state.x;
    }

    public float y() {
        return state.y;
    }

    /** Whether the panel is (or is animating to) unfolded. */
    public boolean isExpanded() {
        return state.expanded;
    }

    public void setOnFocus(Runnable onFocus) {
        this.onFocus = onFocus;
    }

    /**
     * Positions the panel at its default slot plus the user's drag offset, kept on screen.
     *
     * @param bottomReserve space kept free at the bottom of the screen (the CONFIGS button)
     */
    public void place(float slotX, float slotY, float screenW, float screenH, float bottomReserve) {
        this.slotX = slotX;
        this.slotY = slotY;
        state.x = Math.max(-WIDTH + 30f, Math.min(screenW - 30f, slotX + state.offsetX));
        state.y = Math.max(0f, Math.min(screenH - HEADER_H, slotY + state.offsetY));
        maxBodyH = Math.max(40f, screenH - (state.y + HEADER_H) - bottomReserve);
    }

    /** @return number of rows that match */
    public int setQuery(String query) {
        searching = !query.isEmpty();
        int n = 0;
        for (ModuleButton b : buttons) {
            b.setQuery(query);
            if (b.matches()) {
                n++;
            }
        }
        hasRows.setTarget(n > 0);
        scroll = 0;
        return n;
    }

    private boolean showsBody() {
        return state.expanded || searching;
    }

    /** Full unfolded height with no settings open; used for the default (masonry) layout. */
    public float estimatedHeight() {
        return HEADER_H + PAD_TOP + PAD_BOTTOM + Math.max(1, buttons.size()) * ModuleButton.ROW_H;
    }

    private float contentHeight() {
        if (buttons.isEmpty()) {
            return PAD_TOP + PAD_BOTTOM + ModuleButton.ROW_H;
        }
        float h = PAD_TOP + PAD_BOTTOM;
        for (ModuleButton b : buttons) {
            h += b.height();
        }
        return h;
    }

    public float totalHeight() {
        return HEADER_H + bodyH;
    }

    public boolean contains(double mx, double my) {
        return GuiRenderUtil.inside(mx, my, state.x, state.y, WIDTH, totalHeight());
    }

    private boolean overHeader(double mx, double my) {
        return GuiRenderUtil.inside(mx, my, state.x, state.y, WIDTH, HEADER_H);
    }

    public void update(float dtMs, double mx, double my, boolean interactive) {
        expand.setTarget(showsBody());
        expand.update(dtMs);
        focus.setTarget(ClickGuiState.focused() == category);
        focus.update(dtMs);
        hasRows.update(dtMs);
        headerHover.setTarget(interactive && overHeader(mx, my));
        headerHover.update(dtMs);

        float content = contentHeight();
        float visibleContent = Math.min(content, maxBodyH);
        scroll = Math.max(0, Math.min(scroll, content - visibleContent));
        bodyH = visibleContent * expand.smooth() * hasRows.smooth();

        boolean bodyInteractive = interactive && expand.target() && expand.linear() > 0.99f;
        float bodyTop = state.y + HEADER_H;
        boolean mouseInBody = GuiRenderUtil.inside(mx, my, state.x, bodyTop, WIDTH, bodyH);
        float ry = bodyTop + PAD_TOP - scroll;
        for (ModuleButton b : buttons) {
            b.layout(state.x, ry, WIDTH);
            b.update(dtMs, mx, my, bodyInteractive && mouseInBody);
            ry += b.height();
        }
    }

    public void render(double mx, double my) {
        float x = state.x;
        float y = state.y;
        float opacity = ThemeManager.panelOpacity();
        float dim = searching ? 0.45f + 0.55f * hasRows.smooth() : 1f;
        float prevAlpha = GuiRenderUtil.alpha();
        GuiRenderUtil.setAlpha(prevAlpha * dim);

        if (bodyH > 0.25f) {
            float fullH = HEADER_H + bodyH;
            GuiRenderUtil.roundRect(x, y, WIDTH, fullH, RADIUS, ThemeManager.withAlpha(ThemeManager.PANEL, opacity));
            GuiRenderUtil.roundOutline(x, y, WIDTH, fullH, RADIUS, ThemeManager.withAlpha(ThemeManager.BORDER, 0.9f));
            renderBody(mx, my);
        }

        // Header box
        float f = focus.eased();
        float hv = headerHover.eased();
        int headerFill = ThemeManager.lerp(ThemeManager.withAlpha(ThemeManager.HEADER, Math.min(1f, opacity + 0.06f)),
                ThemeManager.accentStrong(), f);
        headerFill = ThemeManager.lerp(headerFill, ThemeManager.lerp(headerFill, 0xFFFFFFFF, 0.06f), hv);
        GuiRenderUtil.roundRect(x, y, WIDTH, HEADER_H, RADIUS, headerFill);
        int border = ThemeManager.lerp(ThemeManager.lerp(0xFF6C6080, 0xFF8A7CA0, hv), ThemeManager.accent(), f);
        GuiRenderUtil.roundOutline(x, y, WIDTH, HEADER_H, RADIUS, border);

        float cy = y + HEADER_H / 2f;
        int fg = ThemeManager.lerp(ThemeManager.TEXT, 0xFFFFFFFF, f);
        float iconSize = 15f;
        GuiRenderUtil.icon(CategoryIcons.of(category), x + 10.5f, cy - iconSize / 2f, iconSize, fg);
        // Keep the label clear of the chevron: long names use the compact header font.
        FontRole headerFont = GuiRenderUtil.textWidth(FontRole.HEADER, category.headerLabel()) > WIDTH - 34f - 25f
                ? FontRole.HEADER_COMPACT : FontRole.HEADER;
        GuiRenderUtil.text(headerFont, category.headerLabel(), x + 34f, cy, fg);
        float rot = (float) (-Math.PI / 2) * (1f - expand.smooth());
        GuiRenderUtil.iconRotated(Icon.CHEVRON_DOWN, x + WIDTH - 15f, cy, 12f, rot, fg);

        GuiRenderUtil.setAlpha(prevAlpha);
    }

    private void renderBody(double mx, double my) {
        float bodyTop = state.y + HEADER_H;
        boolean clip = expand.isAnimating() || hasRows.isAnimating() || contentHeight() > maxBodyH;
        if (!clip) {
            for (ModuleButton b : buttons) {
                if (b.isAnimating()) {
                    clip = true;
                    break;
                }
            }
        }
        if (clip) {
            GuiRenderUtil.pushScissor(state.x, bodyTop, WIDTH, bodyH - 1f / GuiRenderUtil.scale());
        }
        if (buttons.isEmpty()) {
            GuiRenderUtil.text(FontRole.SMALL, "No modules yet", state.x + 12f, bodyTop + PAD_TOP + ModuleButton.ROW_H / 2f,
                    ThemeManager.TEXT_MUTED);
        }
        int last = -1;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            if (buttons.get(i).matches()) {
                last = i;
                break;
            }
        }
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).render(mx, my, i < last);
        }
        if (clip) {
            GuiRenderUtil.popScissor();
        }
        float content = contentHeight();
        if (content > maxBodyH && expand.linear() > 0.99f) {
            float trackH = bodyH - 8f;
            float thumbH = Math.max(12f, trackH * (bodyH / content));
            float thumbY = bodyTop + 4f + (trackH - thumbH) * (scroll / (content - bodyH));
            GuiRenderUtil.roundRect(state.x + WIDTH - 3.5f, thumbY, 1.5f, thumbH, 0.75f,
                    ThemeManager.withAlpha(ThemeManager.accent(), 0.5f));
        }
    }

    // ------------------------------------------------------------------ input

    public boolean mouseClicked(double mx, double my, int button) {
        if (overHeader(mx, my)) {
            onFocus.run();
            if (button == 0) {
                pressX = mx;
                pressY = my;
                startX = state.x;
                startY = state.y;
                moved = false;
                GuiInput.startDrag(this);
            } else if (button == 1) {
                toggleExpanded();
            }
            return true;
        }
        if (!contains(mx, my)) {
            return false;
        }
        onFocus.run();
        if (expand.target()) {
            for (ModuleButton b : buttons) {
                if (b.mouseClicked(mx, my, button)) {
                    return true;
                }
            }
        }
        return true;
    }

    private void toggleExpanded() {
        if (!searching) {
            state.expanded = !state.expanded;
        }
    }

    @Override
    public void drag(double mx, double my) {
        if (!moved && Math.hypot(mx - pressX, my - pressY) < DRAG_THRESHOLD) {
            return;
        }
        moved = true;
        state.x = startX + (float) (mx - pressX);
        state.y = startY + (float) (my - pressY);
        state.offsetX = state.x - slotX;
        state.offsetY = state.y - slotY;
    }

    @Override
    public void release() {
        if (!moved) {
            toggleExpanded();
        }
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my)) {
            return false;
        }
        for (ModuleButton b : buttons) {
            if (b.mouseScrolled(mx, my, amount)) {
                return true;
            }
        }
        scroll -= (float) amount * 18f;
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        for (ModuleButton b : buttons) {
            if (b.keyPressed(event)) {
                return true;
            }
        }
        return false;
    }

    /** Hovered visible module row, or null. */
    public Module hoveredModule(double mx, double my) {
        if (!expand.target() || expand.linear() < 1f || !contains(mx, my)) {
            return null;
        }
        for (ModuleButton b : buttons) {
            if (b.isRowHovered(mx, my)) {
                return b.module();
            }
        }
        return null;
    }

    public ModuleButton find(String name) {
        for (ModuleButton b : buttons) {
            if (b.module().name().equals(name)) {
                return b;
            }
        }
        return null;
    }

    public void expand() {
        state.expanded = true;
    }
}
