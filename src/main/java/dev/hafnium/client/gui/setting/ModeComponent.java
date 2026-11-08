package dev.hafnium.client.gui.setting;

import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.render.Icon;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.core.setting.ModeSetting;

/** Left-click cycles forward, right-click cycles back. */
public final class ModeComponent extends SettingComponent {
    private final ModeSetting setting;

    public ModeComponent(ModeSetting setting) {
        this.setting = setting;
    }

    @Override
    public float height() {
        return ROW;
    }

    @Override
    public void render(double mx, double my) {
        float cy = rowCenter();
        float h = hover.eased();
        GuiRenderUtil.text(FontRole.SMALL, setting.name(), x, cy,
                ThemeManager.lerp(ThemeManager.TEXT_SECONDARY, ThemeManager.TEXT, h));
        float icon = 7f;
        int iconColor = ThemeManager.withAlpha(ThemeManager.TEXT_SECONDARY, 0.5f + 0.5f * h);
        GuiRenderUtil.icon(Icon.CHEVRON_RIGHT, x + w - icon, cy - icon / 2f, icon, iconColor);
        float vw = GuiRenderUtil.textRight(FontRole.SMALL, setting.get(), x + w - icon - 2f, cy,
                ThemeManager.lerp(ThemeManager.accent(), ThemeManager.accentHover(), h));
        GuiRenderUtil.icon(Icon.CHEVRON_LEFT, x + w - icon * 2 - 4f - vw, cy - icon / 2f, icon, iconColor);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overRow(mx, my) && (button == 0 || button == 1)) {
            setting.cycle(button == 0 ? 1 : -1);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (overRow(mx, my)) {
            setting.cycle(amount < 0 ? 1 : -1);
            return true;
        }
        return false;
    }
}
