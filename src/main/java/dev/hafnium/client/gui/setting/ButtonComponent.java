package dev.hafnium.client.gui.setting;

import dev.hafnium.client.core.setting.ActionSetting;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;

/** A full-width button that runs an {@link ActionSetting}. */
public final class ButtonComponent extends SettingComponent {
    private final ActionSetting setting;

    public ButtonComponent(ActionSetting setting) {
        this.setting = setting;
    }

    @Override
    public float height() {
        return ROW;
    }

    @Override
    public void render(double mx, double my) {
        float h = hover.eased();
        float bh = 12f;
        float by = rowCenter() - bh / 2f;
        GuiRenderUtil.roundRect(x, by, w, bh, 3f, ThemeManager.lerp(ThemeManager.TRACK, ThemeManager.withAlpha(ThemeManager.accent(), 0.45f), h));
        GuiRenderUtil.textCentered(FontRole.SMALL, setting.name(), x + w / 2f, rowCenter(), h > 0.5f ? ThemeManager.TEXT : ThemeManager.accentHover());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !overRow(mx, my)) {
            return false;
        }
        setting.run();
        return true;
    }
}
