package dev.hafnium.client.gui.setting;

import dev.hafnium.client.gui.anim.Animation;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.core.setting.BoolSetting;

public final class BoolComponent extends SettingComponent {
    private static final float SW = 15f;
    private static final float SH = 8f;

    private final BoolSetting setting;
    private final Animation state;

    public BoolComponent(BoolSetting setting) {
        this.setting = setting;
        this.state = new Animation(160, setting.get());
    }

    @Override
    public float height() {
        return ROW;
    }

    @Override
    public void update(float dtMs, double mx, double my, boolean interactive) {
        super.update(dtMs, mx, my, interactive);
        state.setTarget(setting.get());
        state.update(dtMs);
    }

    @Override
    public void render(double mx, double my) {
        float cy = rowCenter();
        float h = hover.eased();
        GuiRenderUtil.text(FontRole.SMALL, setting.name(), x, cy,
                ThemeManager.lerp(ThemeManager.TEXT_SECONDARY, ThemeManager.TEXT, h));
        float t = state.eased();
        float sx = x + w - SW;
        float sy = cy - SH / 2f;
        GuiRenderUtil.roundRect(sx, sy, SW, SH, SH / 2f, ThemeManager.lerp(ThemeManager.TRACK, ThemeManager.accent(), t));
        float knobR = 2.75f;
        float kx = sx + SH / 2f + (SW - SH) * t;
        GuiRenderUtil.circle(kx, cy, knobR, ThemeManager.lerp(ThemeManager.TEXT_SECONDARY, 0xFFFFFFFF, t));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overRow(mx, my) && button == 0) {
            setting.toggle();
            return true;
        }
        return false;
    }
}
