package dev.hafnium.client.modules.client;

import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.permission.Capability;
import dev.hafnium.client.core.permission.CapabilityStatus;
import dev.hafnium.client.core.permission.Environment;
import dev.hafnium.client.core.permission.PermissionService;
import dev.hafnium.client.core.setting.ModeSetting;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.modules.automation.OperatingModeModule;
import net.minecraft.client.Minecraft;

import java.util.Map;

/**
 * HUD indicator of which gameplay capabilities Hafnium may use here: "Auto" (autonomous automation authorized),
 * "Assist" (supervised assistance only) or "Off", plus the operating mode and server. Client-side features are not
 * listed because they are always available.
 */
public final class CapabilitiesModule extends Module {
    public static final int AUTO_COLOR = 0xFF7BE0A0;
    public static final int ASSIST_COLOR = 0xFFFFC66B;

    public final ModeSetting layout = setting(new ModeSetting("Layout", "Full", "Full", "Compact"));

    private volatile float bottom;

    /** GUI y just below the panel drawn in the last frame (0 when nothing is drawn), so other HUD lists can stack under it. */
    public float bottom() {
        return isEnabled() ? bottom : 0f;
    }

    public CapabilitiesModule() {
        super("Capabilities", Category.CLIENT, "HUD indicator: which gameplay capabilities are available on this"
                + " server (Auto / Assist / Off) and the operating mode.");
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        ctx.subscribe(HudRenderEvent.class, this::render);
    }

    /** Text of the summary line, e.g. "ASSIST · play.example.net"; also used by tests. */
    public static String headline(PermissionService p) {
        Environment env = p.environment();
        String where = switch (env.kind()) {
            case NONE -> "not in a world";
            case INTEGRATED_HOST -> "hosting (authorized)";
            case REMOTE -> env.serverId() + (p.serverPolicy() != null ? " (policy)" : " (no policy)");
        };
        return OperatingModeModule.label(p.mode()).toUpperCase(java.util.Locale.ROOT) + " · " + where;
    }

    public static String levelLabel(CapabilityStatus s) {
        return switch (s.level()) {
            case AUTONOMOUS -> "Auto";
            case ASSIST_ONLY -> "Assist";
            case UNAVAILABLE -> "Off";
        };
    }

    private static int levelColor(CapabilityStatus s) {
        return switch (s.level()) {
            case AUTONOMOUS -> AUTO_COLOR;
            case ASSIST_ONLY -> ASSIST_COLOR;
            case UNAVAILABLE -> ThemeManager.DANGER;
        };
    }

    private void render(HudRenderEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            bottom = 0f;
            return;
        }
        PermissionService p = HafniumCore.get().permissions();
        Map<Capability, CapabilityStatus> statuses = p.statuses();
        boolean watermark = HafniumCore.get().modules().get(WatermarkModule.class).isEnabled();
        int scale = Math.max(1, mc.getWindow().getGuiScale());
        ThemeManager.sync();
        GuiRenderUtil.begin(e.graphics(), scale, scale);
        try {
            float x = 4f;
            float y = watermark ? 24f : 4f;
            String head = headline(p);
            int modeColor = p.mode() == OperatingMode.MANUAL ? ThemeManager.TEXT_SECONDARY
                    : p.mode() == OperatingMode.ASSIST ? ASSIST_COLOR : AUTO_COLOR;
            if (layout.get().equals("Compact")) {
                int auto = 0;
                int assist = 0;
                int off = 0;
                for (CapabilityStatus s : statuses.values()) {
                    switch (s.level()) {
                        case AUTONOMOUS -> auto++;
                        case ASSIST_ONLY -> assist++;
                        case UNAVAILABLE -> off++;
                    }
                }
                String line = head + " · Auto " + auto + " · Assist " + assist + " · Off " + off;
                float w = GuiRenderUtil.textWidth(FontRole.SMALL, line) + 12f;
                GuiRenderUtil.roundRect(x, y, w, 13f, 3f, ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.8f));
                GuiRenderUtil.roundRect(x, y, 2f, 13f, 1f, modeColor);
                GuiRenderUtil.text(FontRole.SMALL, line, x + 7f, y + 6.5f, ThemeManager.TEXT);
                bottom = y + 13f;
                return;
            }
            float rowH = 10f;
            float nameW = 0;
            for (Capability c : statuses.keySet()) {
                nameW = Math.max(nameW, GuiRenderUtil.textWidth(FontRole.SMALL, c.displayName()));
            }
            float w = Math.max(GuiRenderUtil.textWidth(FontRole.SMALL, head) + 14f, 10f + nameW + 46f);
            float h = 16f + statuses.size() * rowH + 4f;
            bottom = y + h;
            GuiRenderUtil.roundRect(x, y, w, h, 3f, ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.8f));
            GuiRenderUtil.roundOutline(x, y, w, h, 3f, ThemeManager.withAlpha(ThemeManager.BORDER, 0.6f));
            GuiRenderUtil.circle(x + 6f, y + 8f, 2f, modeColor);
            GuiRenderUtil.text(FontRole.SMALL, head, x + 11f, y + 8f, ThemeManager.TEXT);
            float ry = y + 16f + rowH / 2f;
            for (CapabilityStatus s : statuses.values()) {
                int color = levelColor(s);
                GuiRenderUtil.circle(x + 6f, ry, 1.5f, color);
                GuiRenderUtil.text(FontRole.SMALL, s.capability().displayName(), x + 11f, ry, ThemeManager.TEXT_SECONDARY);
                GuiRenderUtil.textRight(FontRole.SMALL, levelLabel(s), x + w - 6f, ry, color);
                ry += rowH;
            }
        } finally {
            GuiRenderUtil.end();
        }
    }
}
