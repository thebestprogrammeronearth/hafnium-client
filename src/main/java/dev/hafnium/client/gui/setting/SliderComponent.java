package dev.hafnium.client.gui.setting;

import dev.hafnium.client.gui.GuiInput;
import dev.hafnium.client.gui.anim.Animation;
import dev.hafnium.client.gui.anim.AnimationUtil;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.core.setting.NumberSetting;

public final class SliderComponent extends SettingComponent implements GuiInput.DragHandler {
    private static final float HEIGHT = 24f;
    private static final float TRACK_Y = 18f;

    private final NumberSetting setting;
    private final Animation active = new Animation(150, false);
    private float shown;

    public SliderComponent(NumberSetting setting) {
        this.setting = setting;
        this.shown = (float) setting.fraction();
    }

    @Override
    public float height() {
        return HEIGHT;
    }

    @Override
    public void update(float dtMs, double mx, double my, boolean interactive) {
        hover.setTarget(interactive && GuiRenderUtil.inside(mx, my, x, y, w, HEIGHT));
        hover.update(dtMs);
        active.setTarget(GuiInput.drag() == this);
        active.update(dtMs);
        // Glide the knob toward the value; while dragging it follows almost directly.
        float target = (float) setting.fraction();
        float k = GuiInput.drag() == this ? 1f : 1f - (float) Math.exp(-dtMs / 45f);
        shown = AnimationUtil.lerp(shown, target, k);
    }

    @Override
    public void render(double mx, double my) {
        float cy = y + 7f;
        float h = Math.max(hover.eased(), active.eased());
        GuiRenderUtil.text(FontRole.SMALL, setting.name(), x, cy,
                ThemeManager.lerp(ThemeManager.TEXT_SECONDARY, ThemeManager.TEXT, h));
        GuiRenderUtil.textRight(FontRole.SMALL, setting.text(), x + w, cy, ThemeManager.TEXT);

        float ty = y + TRACK_Y;
        float th = 3f;
        GuiRenderUtil.roundRect(x, ty - th / 2f, w, th, th / 2f, ThemeManager.TRACK);
        float fw = Math.max(th, w * shown);
        GuiRenderUtil.roundRect(x, ty - th / 2f, fw, th, th / 2f,
                ThemeManager.lerp(ThemeManager.accent(), ThemeManager.accentHover(), h));
        float kr = 3.5f + 0.5f * active.eased();
        float kx = x + Math.max(kr, Math.min(w - kr, w * shown));
        GuiRenderUtil.circle(kx, ty, kr + 1f, ThemeManager.withAlpha(ThemeManager.accent(), 0.35f + 0.4f * h));
        GuiRenderUtil.circle(kx, ty, kr, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && GuiRenderUtil.inside(mx, my, x - 2, y, w + 4, HEIGHT)) {
            GuiInput.startDrag(this);
            drag(mx, my);
            return true;
        }
        return false;
    }

    @Override
    public void drag(double mx, double my) {
        setting.setFraction((mx - x) / w);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (GuiRenderUtil.inside(mx, my, x, y, w, HEIGHT)) {
            setting.set(setting.get() + Math.signum(amount) * setting.step());
            return true;
        }
        return false;
    }
}
