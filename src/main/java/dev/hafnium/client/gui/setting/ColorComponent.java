package dev.hafnium.client.gui.setting;

import dev.hafnium.client.gui.GuiInput;
import dev.hafnium.client.gui.anim.Animation;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.core.setting.ColorSetting;

import java.awt.Color;

/** Swatch that expands into a saturation/value square and a hue strip. */
public final class ColorComponent extends SettingComponent {
    private static final float SV_H = 40f;
    private static final float HUE_H = 6f;
    private static final float GAP = 4f;

    private final ColorSetting setting;
    private final Animation open = new Animation(200, false);

    private final GuiInput.DragHandler svDrag = new GuiInput.DragHandler() {
        @Override
        public void drag(double mx, double my) {
            float s = (float) ((mx - x) / w);
            float v = 1f - (float) ((my - svY()) / SV_H);
            setting.setHsv(setting.hue(), s, v);
        }
    };
    private final GuiInput.DragHandler hueDrag = new GuiInput.DragHandler() {
        @Override
        public void drag(double mx, double my) {
            float hue = (float) ((mx - x) / w);
            setting.setHsv(Math.max(0f, Math.min(0.999f, hue)), setting.saturation(), setting.brightness());
        }
    };

    public ColorComponent(ColorSetting setting) {
        this.setting = setting;
    }

    private float pickerHeight() {
        return SV_H + GAP + HUE_H + GAP;
    }

    private float svY() {
        return y + ROW + 1f;
    }

    private float hueY() {
        return svY() + SV_H + GAP;
    }

    @Override
    public float height() {
        return ROW + pickerHeight() * open.smooth();
    }

    @Override
    public boolean isAnimating() {
        return open.isAnimating();
    }

    @Override
    public void update(float dtMs, double mx, double my, boolean interactive) {
        super.update(dtMs, mx, my, interactive);
        open.update(dtMs);
    }

    @Override
    public void render(double mx, double my) {
        float cy = rowCenter();
        float h = hover.eased();
        float o = open.smooth();
        GuiRenderUtil.text(FontRole.SMALL, setting.name(), x, cy,
                ThemeManager.lerp(ThemeManager.TEXT_SECONDARY, ThemeManager.TEXT, h));
        float sw = 15f;
        float sh = 8f;
        float sx = x + w - sw;
        GuiRenderUtil.roundRect(sx, cy - sh / 2f, sw, sh, 2.5f, setting.get());
        GuiRenderUtil.roundOutline(sx, cy - sh / 2f, sw, sh, 2.5f, ThemeManager.withAlpha(0xFFFFFFFF, 0.25f + 0.35f * h));
        if (o > 0.01f) {
            GuiRenderUtil.textRight(FontRole.SMALL, setting.hex(), sx - 4f, cy,
                    ThemeManager.withAlpha(ThemeManager.TEXT_SECONDARY, o));
        }
        if (o <= 0.001f) {
            return;
        }

        GuiRenderUtil.pushScissor(x - 2, y + ROW, w + 4, pickerHeight() * o);
        // Saturation/value square: one vertical gradient per pixel column (hue at full value -> black).
        int x0 = GuiRenderUtil.px(x);
        int x1 = GuiRenderUtil.px(x + w);
        int y0 = GuiRenderUtil.px(svY());
        int y1 = GuiRenderUtil.px(svY() + SV_H);
        int cols = Math.max(1, x1 - x0 - 1);
        for (int px = x0; px < x1; px++) {
            float s = (px - x0) / (float) cols;
            int top = 0xFF000000 | (Color.HSBtoRGB(setting.hue(), s, 1f) & 0xFFFFFF);
            GuiRenderUtil.gradientPx(px, y0, px + 1, y1, top, 0xFF000000);
        }
        // Hue strip
        int hy0 = GuiRenderUtil.px(hueY());
        int hy1 = GuiRenderUtil.px(hueY() + HUE_H);
        for (int px = x0; px < x1; px++) {
            float hue = (px - x0) / (float) cols;
            GuiRenderUtil.fillPx(px, hy0, px + 1, hy1, 0xFF000000 | (Color.HSBtoRGB(hue, 1f, 1f) & 0xFFFFFF));
        }
        // Cursors
        float cx = x + setting.saturation() * w;
        float cyv = svY() + (1f - setting.brightness()) * SV_H;
        GuiRenderUtil.circle(cx, cyv, 3f, 0xFFFFFFFF);
        GuiRenderUtil.circle(cx, cyv, 2f, setting.get());
        float hx = x + setting.hue() * w;
        GuiRenderUtil.roundRect(hx - 1.5f, hueY() - 1.5f, 3f, HUE_H + 3f, 1.5f, 0xFFFFFFFF);
        GuiRenderUtil.popScissor();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overRow(mx, my) && (button == 0 || button == 1)) {
            open.setTarget(!open.target());
            return true;
        }
        if (!open.target() || button != 0) {
            return false;
        }
        if (GuiRenderUtil.inside(mx, my, x, svY(), w, SV_H)) {
            GuiInput.startDrag(svDrag);
            svDrag.drag(mx, my);
            return true;
        }
        if (GuiRenderUtil.inside(mx, my, x, hueY() - 1, w, HUE_H + 2)) {
            GuiInput.startDrag(hueDrag);
            hueDrag.drag(mx, my);
            return true;
        }
        return false;
    }
}
