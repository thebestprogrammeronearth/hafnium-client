/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.modules.client;

import dev.hafnium.client.anubis.esp.ChunkScanner;
import dev.hafnium.client.anubis.util.PerformanceBudget;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.event.WorldRenderEvent;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Budgets background scanning (chunk scans run incrementally within "Scan Budget" per tick instead of all at once),
 * frustum-culls ESP shapes, and can show live stats. Used by every ESP ported from Anubis.
 */
public final class PerformanceModule extends Module {
    private static volatile boolean optimizing;
    private static volatile PerformanceModule instance;
    public final BoolSetting optimize = setting(new BoolSetting("Optimize Client", true));
    public final BoolSetting showStats = setting(new BoolSetting("Show Stats", true));
    public final BoolSetting frustumCulling = setting(new BoolSetting("Frustum Culling", true));
    public final NumberSetting scanBudget = setting(new NumberSetting("Scan Budget", 2.0, 0.5, 8.0, 0.5, " ms"));
    private long smoothedNanos;
    private long nextStatsUpdate;
    private String statsText = "";

    public PerformanceModule() {
        super("Performance", Category.CLIENT,
                "Reduces background work while keeping effects and features. Disable to compare FPS.");
        instance = this;
        this.optimize.onChange(this::updateOptimizationState);
    }

    public static boolean optimizationsEnabled() {
        return optimizing;
    }

    public static boolean frustumCulling() {
        PerformanceModule m = instance;
        return m == null || m.frustumCulling.get();
    }

    public static long scanBudgetNanos() {
        PerformanceModule m = instance;
        return m == null ? 2_000_000L : (long) (m.scanBudget.get() * 1_000_000.0);
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        // Hafnium marks the module enabled only after onEnable returns, so set the flag directly here.
        optimizing = optimize.get();
        nextStatsUpdate = 0L;
        ctx.subscribe(HudRenderEvent.class, this::renderStats);
    }

    @Override
    protected void onDisable() {
        optimizing = false;
    }

    private void updateOptimizationState() {
        optimizing = isEnabled() && optimize.get();
        nextStatsUpdate = 0L;
    }

    private void renderStats(HudRenderEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!showStats.get() || mc.options.hideGui) {
            return;
        }
        smoothedNanos += (WorldRenderEvent.lastDurationNanos() - smoothedNanos) / 8L;
        long now = System.nanoTime();
        if (now >= nextStatsUpdate) {
            String scans = optimizationsEnabled()
                    ? String.format(Locale.ROOT, "  ·  scans %.2f ms", PerformanceBudget.lastTickNanos() / 1_000_000.0) : "";
            statsText = String.format(Locale.ROOT, "%s  ·  %d FPS  ·  3D CPU %.2f ms%s  ·  queue %d",
                    optimizationsEnabled() ? "Optimized" : "Standard", mc.getFps(), smoothedNanos / 1_000_000.0, scans,
                    ChunkScanner.pending());
            nextStatsUpdate = now + 250_000_000L;
        }
        int scale = Math.max(1, mc.getWindow().getGuiScale());
        GuiRenderUtil.begin(e.graphics(), scale, scale);
        try {
            float w = GuiRenderUtil.textWidth(FontRole.SMALL, statsText);
            float x = e.graphics().guiWidth() - w - 6f;
            float y = e.graphics().guiHeight() - 8f;
            GuiRenderUtil.text(FontRole.SMALL, statsText, x + 0.5f, y + 0.5f, 0xA0000000);
            GuiRenderUtil.text(FontRole.SMALL, statsText, x, y, ThemeManager.TEXT_SECONDARY);
        } finally {
            GuiRenderUtil.end();
        }
    }
}
