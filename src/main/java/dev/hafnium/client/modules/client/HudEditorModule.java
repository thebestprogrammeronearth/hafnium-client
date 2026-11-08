package dev.hafnium.client.modules.client;

import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.setting.ActionSetting;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.gui.HudEditorScreen;
import dev.hafnium.client.hud.HudElement;
import dev.hafnium.client.hud.HudLayout;
import net.minecraft.client.Minecraft;

/** Move and resize every Hafnium overlay (Watermark, Capabilities, SpotifyHUD, Region Map, stats and lists). */
public final class HudEditorModule extends Module {
    public final BoolSetting snap = setting(new BoolSetting("Snap", true));
    public final ActionSetting open = setting(new ActionSetting("Open Editor", this::openEditor));
    public final ActionSetting resetAll = setting(new ActionSetting("Reset All", () -> {
        for (HudElement e : HudLayout.elements()) {
            e.reset();
        }
    }));

    public HudEditorModule() {
        super("HUD Editor", Category.CLIENT, "Move and resize every on-screen overlay. Drag, scroll to resize, right-click to reset.");
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    public void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new HudEditorScreen(mc.screen, snap.get()));
    }
}
