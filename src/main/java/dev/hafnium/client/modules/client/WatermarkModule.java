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

    private final dev.hafnium.client.hud.LayoutElement hud =
            new dev.hafnium.client.hud.LayoutElement("watermark", "Watermark", this::isEnabled, 0f, 0f, 2f, 2f);

    private void render(HudRenderEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        ThemeManager.sync();
        hud.begin(e.graphics());
        try {
            float x = 0f;
            float y = 0f;
            float s = 20f;
            GuiRenderUtil.image(LOGO, 80, x, y, s, s);
            float tx = x + s + 3f;
            float w = GuiRenderUtil.text(FontRole.TEXT, "Hafnium", tx, y + s / 2f, ThemeManager.TEXT);
            float right = tx + w;
            if (version.get() && !versionText.isEmpty()) {
                right = tx + w + 4f + GuiRenderUtil.text(FontRole.SMALL, versionText, tx + w + 4f, y + s / 2f, ThemeManager.TEXT_SECONDARY);
            }
            hud.size(right, s);
        } finally {
            hud.end();
        }
    }
}
