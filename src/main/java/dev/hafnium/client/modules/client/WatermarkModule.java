package dev.hafnium.client.modules.client;

import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import net.minecraft.client.Minecraft;

/** Draws the Hafnium tile and name in the top-left of the HUD while enabled. */
public final class WatermarkModule extends Module {
    public final BoolSetting version = setting(new BoolSetting("Version", true));
    private String versionText;

    public WatermarkModule() {
        super("Watermark", Category.CLIENT, "Shows the Hafnium logo on the HUD.");
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        if (versionText == null) {
            versionText = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("hafnium")
                    .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString()).orElse("");
        }
        ctx.subscribe(HudRenderEvent.class, this::render);
    }

    private static final net.minecraft.resources.Identifier LOGO =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("hafnium", "textures/gui/logo_small.png");

    private void render(HudRenderEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        int scale = Math.max(1, mc.getWindow().getGuiScale());
        ThemeManager.sync();
        GuiRenderUtil.begin(e.graphics(), scale, scale);
        try {
            float x = 2f;
            float y = 2f;
            float s = 20f;
            GuiRenderUtil.image(LOGO, 80, x, y, s, s);
            float tx = x + s + 3f;
            float w = GuiRenderUtil.text(FontRole.TEXT, "Hafnium", tx, y + s / 2f, ThemeManager.TEXT);
            if (version.get() && !versionText.isEmpty()) {
                GuiRenderUtil.text(FontRole.SMALL, versionText, tx + w + 4f, y + s / 2f, ThemeManager.TEXT_SECONDARY);
            }
        } finally {
            GuiRenderUtil.end();
        }
    }
}
