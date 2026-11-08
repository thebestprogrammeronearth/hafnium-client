package dev.hafnium.client.gui.setting;

import dev.hafnium.client.anubis.setting.StringListSetting;
import dev.hafnium.client.gui.ClickGuiScreen;
import dev.hafnium.client.gui.StringListScreen;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import net.minecraft.client.Minecraft;

/** Settings row for a text list: shows how many entries it has and opens the list editor. */
public final class StringListComponent extends SettingComponent {
    private final StringListSetting setting;

    public StringListComponent(StringListSetting setting) {
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
        GuiRenderUtil.text(FontRole.SMALL, setting.name(), x, cy, ThemeManager.lerp(ThemeManager.TEXT_SECONDARY, ThemeManager.TEXT, h));
        int n = setting.values().size();
        String right = (n == 0 ? "none" : n + (n == 1 ? " entry" : " entries")) + "  ›";
        GuiRenderUtil.textRight(FontRole.SMALL, right, x + w, cy,
                ThemeManager.lerp(n == 0 ? ThemeManager.TEXT_MUTED : ThemeManager.accent(), ThemeManager.accentHover(), h));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overRow(mx, my) && button == 0 && Minecraft.getInstance().screen instanceof ClickGuiScreen gui) {
            Minecraft.getInstance().setScreen(new StringListScreen(gui, setting, setting.hint()));
            return true;
        }
        return false;
    }
}
