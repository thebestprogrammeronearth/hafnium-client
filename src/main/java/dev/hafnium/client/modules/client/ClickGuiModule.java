package dev.hafnium.client.modules.client;

import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.ColorSetting;
import dev.hafnium.client.core.setting.NumberSetting;

/** Settings-only module: appearance of the ClickGUI itself (read by ThemeManager every frame). */
public final class ClickGuiModule extends Module {
    public final NumberSetting opacity = setting(new NumberSetting("Opacity", 90, 40, 100, 1, "%"));
    public final BoolSetting blur = setting(new BoolSetting("Blur", true));
    public final ColorSetting accent = setting(new ColorSetting("Accent", 0xFFB77CFF));

    public ClickGuiModule() {
        super("ClickGUI", Category.CLIENT, "Appearance of this menu. Right Shift opens it.");
    }

    @Override
    public boolean isToggleable() {
        return false;
    }
}
