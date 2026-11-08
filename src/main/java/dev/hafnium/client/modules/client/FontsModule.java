package dev.hafnium.client.modules.client;

import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.setting.ModeSetting;

/** Settings-only module: which font the Hafnium GUI uses. */
public final class FontsModule extends Module {
    public final ModeSetting font = setting(new ModeSetting("Font", "Inter", "Inter", "Minecraft"));

    public FontsModule() {
        super("Fonts", Category.CLIENT, "Font used by the Hafnium GUI.");
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    public boolean useInter() {
        return "Inter".equals(font.get());
    }
}
