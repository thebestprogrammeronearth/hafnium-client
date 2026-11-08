package dev.hafnium.client.gui;

import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.hud.HudElement;
import dev.hafnium.client.hud.HudLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * HUD Editor: every visible overlay gets a box. Drag to move (snaps to screen edges, the centre lines and other
 * overlays), scroll to resize, right-click to reset. Positions are kept per corner, so they survive window and GUI
 * scale changes. The overlays themselves keep drawing underneath, so what you see is what you get.
 */
public final class HudEditorScreen extends Screen {
    private static final float SNAP = 4f;
    private static final float MARGIN = 2f;
    private final Screen parent;
    private final boolean snap;
    private HudElement dragging;
    private float grabX, grabY;
    private Float guideX, guideY;

    public HudEditorScreen(Screen parent, boolean snap) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
        this.snap = snap;
    }

    private List<HudElement> shown() {
        List<HudElement> out = new ArrayList<>();
        for (HudElement e : HudLayout.elements()) {
            if (e.visible()) {
                out.add(e);
            }
        }
        return out;
    }

    private HudElement at(double mx, double my) {
        List<HudElement> shown = shown();
        for (int i = shown.size() - 1; i >= 0; i--) {
            HudElement e = shown.get(i);
            float x = e.x(width, height), y = e.y(width, height);
            if (mx >= x && my >= y && mx < x + e.width() * e.scale() && my < y + e.height() * e.scale()) {
                return e;
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int scale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        ThemeManager.sync();
        GuiRenderUtil.begin(g, scale, scale);
        try {
            HudElement hovered = dragging != null ? dragging : at(mouseX, mouseY);
            if (guideX != null) {
                GuiRenderUtil.rect(guideX - 0.25f, 0, 0.5f, height, ThemeManager.withAlpha(ThemeManager.accent(), 0.7f));
            }
            if (guideY != null) {
                GuiRenderUtil.rect(0, guideY - 0.25f, width, 0.5f, ThemeManager.withAlpha(ThemeManager.accent(), 0.7f));
            }
            for (HudElement e : shown()) {
                float x = e.x(width, height), y = e.y(width, height);
                float w = e.width() * e.scale(), h = e.height() * e.scale();
                boolean hot = e == hovered;
                GuiRenderUtil.rect(x, y, w, h, ThemeManager.withAlpha(ThemeManager.accent(), hot ? 0.18f : 0.08f));
                GuiRenderUtil.roundOutline(x - 0.5f, y - 0.5f, w + 1f, h + 1f, 2f,
                        ThemeManager.withAlpha(hot ? ThemeManager.accentHover() : ThemeManager.accent(), hot ? 1f : 0.6f));
                String label = e.name() + (Math.abs(e.scale() - 1f) > 0.001f ? "  " + Math.round(e.scale() * 100) + "%" : "");
                float ly = y > 10f ? y - 5f : y + h + 5f;
                GuiRenderUtil.text(FontRole.TINY, label, x, ly, hot ? ThemeManager.TEXT : ThemeManager.TEXT_SECONDARY);
            }
            String hint = "Drag to move  ·  Scroll to resize  ·  Right-click to reset  ·  Esc to finish";
            float tw = GuiRenderUtil.textWidth(FontRole.SMALL, hint);
            float hx = (width - tw) / 2f, hy = height / 2f;
            GuiRenderUtil.roundRect(hx - 8f, hy - 8f, tw + 16f, 16f, 4f, ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.85f));
            GuiRenderUtil.text(FontRole.SMALL, hint, hx, hy, ThemeManager.TEXT_SECONDARY);
            if (shown().isEmpty()) {
                String none = "No overlays are showing. Turn on a module with an overlay first.";
                GuiRenderUtil.textCentered(FontRole.SMALL, none, width / 2f, hy + 16f, ThemeManager.TEXT_SECONDARY);
            }
        } finally {
            GuiRenderUtil.end();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x40000000);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        HudElement e = at(event.x(), event.y());
        if (e == null) {
            return false;
        }
        if (event.button() == 1) {
            e.reset();
            return true;
        }
        if (event.button() == 0) {
            dragging = e;
            grabX = (float) event.x() - e.x(width, height);
            grabY = (float) event.y() - e.y(width, height);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null) {
            return false;
        }
        float w = dragging.width() * dragging.scale(), h = dragging.height() * dragging.scale();
        float x = (float) event.x() - grabX, y = (float) event.y() - grabY;
        guideX = null;
        guideY = null;
        if (snap) {
            float[] sx = snapAxis(x, w, width, true);
            float[] sy = snapAxis(y, h, height, false);
            x = sx[0];
            y = sy[0];
            guideX = Float.isNaN(sx[1]) ? null : sx[1];
            guideY = Float.isNaN(sy[1]) ? null : sy[1];
        }
        x = Math.max(0, Math.min(x, width - w));
        y = Math.max(0, Math.min(y, height - h));
        dragging.moveTo(x, y, width, height);
        return true;
    }

    /** Snaps one axis to the margins, the screen centre and other overlays' edges. Returns {position, guide or NaN}. */
    private float[] snapAxis(float pos, float size, float screen, boolean horizontal) {
        List<float[]> targets = new ArrayList<>();
        targets.add(new float[]{MARGIN, MARGIN});
        targets.add(new float[]{screen - MARGIN - size, screen - MARGIN});
        targets.add(new float[]{(screen - size) / 2f, screen / 2f});
        for (HudElement o : shown()) {
            if (o == dragging) {
                continue;
            }
            float start = horizontal ? o.x(width, height) : o.y(width, height);
            float end = start + (horizontal ? o.width() : o.height()) * o.scale();
            targets.add(new float[]{start, start});
            targets.add(new float[]{end - size, end});
            targets.add(new float[]{end + MARGIN, end});
            targets.add(new float[]{start - MARGIN - size, start});
        }
        float best = SNAP;
        float[] result = {pos, Float.NaN};
        for (float[] t : targets) {
            float d = Math.abs(pos - t[0]);
            if (d < best) {
                best = d;
                result = new float[]{t[0], t[1]};
            }
        }
        return result;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        guideX = null;
        guideY = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        HudElement e = at(mx, my);
        if (e == null || v == 0) {
            return false;
        }
        float x = e.x(width, height), y = e.y(width, height);
        e.setScale(e.scale() + (v > 0 ? 0.05f : -0.05f));
        e.moveTo(Math.max(0, Math.min(x, width - e.width() * e.scale())), Math.max(0, Math.min(y, height - e.height() * e.scale())), width, height);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** For tests. */
    public HudElement dragging() {
        return dragging;
    }
}
