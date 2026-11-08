/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.modules.client;

import dev.hafnium.client.anubis.util.donut.DonutGoliaths;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.ColorSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.render.Icon;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.hud.LayoutElement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

/**
 * Region Map: the DonutSMP region ("goliath") map with your position and facing, the region you are in and a colour
 * legend. Anubis's Region Map module and RegionMapRenderer, drawn with Hafnium's renderer instead of ImGui (same
 * layout, colours and settings). Sizes in the settings are pixels at 1080p, as in Anubis.
 */
public final class RegionMapModule extends Module {
    private static final float PX = 0.5f; // Anubis pixels (1080p reference) -> GUI units at the default GUI scale
    private static final float PAD = 10.0F * PX;
    private static final float INFO_GAP = 8.0F * PX;
    private static final float INFO_LINE_H = 22.0F * PX;
    private static final float LEGEND_GAP = 4.0F * PX;
    private static final float LEGEND_ROW_H = 16.0F * PX;
    private static final float LEGEND_SWATCH = 9.0F * PX;
    private static final float LABEL_GAP = 8.0F * PX;
    private static final String MINUS = "−";
    private static final String NO_REGION = "—";
    // Anubis stores these as ImGui ABGR; converted to ARGB.
    private static final int[] REGION_COLORS = {0xFF9FCE63, 0xFF00A663, 0xFFC88CF0, 0xFF2F6EBA, 0xFF4FADEA, 0xFFF5C242, 0xFFFC8803};
    private static final int[] LEGEND_ORDER = {0, 1, 2, 4, 3, 5, 6};
    private static final int GOLIATH_DIVIDER = 0x2E000000;
    private static final int REGION_DIVIDER = 0x47000000;
    private static final int TEXT_VALUE = 0xFFF5F5F7;
    private static final int TEXT_NAME = 0xFFE9E9EE;
    private static final int TEXT_LABEL = 0xFFA3A3AB;
    private static final List<int[]> GOLIATH_LINES = new ArrayList<>();
    private static final List<int[]> REGION_LINES = new ArrayList<>();

    private final NumberSetting squareSize = setting(new NumberSetting("Cell Size", 6.0, 3.0, 16.0, 0.5));
    private final NumberSetting playerScale = setting(new NumberSetting("Player Scale", 1.0, 0.5, 3.0, 0.1));
    private final NumberSetting textScale = setting(new NumberSetting("Text Scale", 1.0, 0.1, 2.0, 0.1));
    private final NumberSetting opacity = setting(new NumberSetting("Opacity", 1.0, 0.1, 1.0, 0.05));
    private final BoolSetting shadow = setting(new BoolSetting("Shadow", true));
    private final BoolSetting showRegionIds = setting(new BoolSetting("Show Goliath Numbers", true));
    private final BoolSetting showLegend = setting(new BoolSetting("Show Legend", true));
    private final BoolSetting showCoordinates = setting(new BoolSetting("Show Coordinates", true));
    private final ColorSetting playerIndicatorColor = setting(new ColorSetting("Player Indicator", 0xFFFF3232));
    private final LayoutElement hud = new LayoutElement("region-map", "Region Map", this::isEnabled, 1f, 0f, -4f, 4f);

    static {
        for (int line = 1; line < DonutGoliaths.GRID; line++) {
            dividerRuns(line, true);
            dividerRuns(line, false);
        }
    }

    public RegionMapModule() {
        super("Region Map", Category.CLIENT, "DonutSMP goliath map with directional indicator.");
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        ctx.subscribe(HudRenderEvent.class, this::render);
    }

    private static void dividerRuns(int line, boolean vertical) {
        int grid = DonutGoliaths.GRID;
        int start = 0;
        int kind = dividerKind(line, 0, vertical);
        for (int along = 1; along <= grid; along++) {
            int next = along < grid ? dividerKind(line, along, vertical) : -1;
            if (next != kind) {
                if (kind == 2) {
                    REGION_LINES.add(new int[]{vertical ? 1 : 0, line, start, along});
                } else if (kind == 1) {
                    GOLIATH_LINES.add(new int[]{vertical ? 1 : 0, line, start, along});
                }
                kind = next;
                start = along;
            }
        }
    }

    private static int dividerKind(int line, int along, boolean vertical) {
        int a = vertical ? DonutGoliaths.square(line - 1, along) : DonutGoliaths.square(along, line - 1);
        int b = vertical ? DonutGoliaths.square(line, along) : DonutGoliaths.square(along, line);
        if (a < 0 == b < 0 && a / 1000 == b / 1000) {
            return a != b ? 1 : 0;
        }
        return 2;
    }

    private float gridSize() {
        return DonutGoliaths.GRID * squareSize.getFloat() * PX;
    }

    private static int fade(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, alpha)));
        return a << 24 | argb & 0xFFFFFF;
    }

    private float labelColumn() {
        float region = GuiRenderUtil.textWidth(FontRole.SMALL, "Region");
        return showCoordinates.get() ? Math.max(GuiRenderUtil.textWidth(FontRole.SMALL, "Coordinate"), region) : region;
    }

    private float measureInfoWidth() {
        float labelCol = labelColumn() + LABEL_GAP;
        float values = showCoordinates.get() ? GuiRenderUtil.textWidth(FontRole.TEXT, "−000000, −000000") : 0f;
        float legend = 0f;
        for (String name : DonutGoliaths.REGIONS) {
            values = Math.max(values, GuiRenderUtil.textWidth(FontRole.TEXT, "00  ·  " + name));
            legend = Math.max(legend, 15f * PX + GuiRenderUtil.textWidth(FontRole.SMALL, name));
        }
        return (float) Math.ceil(Math.max(labelCol + values, showLegend.get() ? legend : 0f));
    }

    private int infoLines() {
        return showCoordinates.get() ? 2 : 1;
    }

    private float measureInfoHeight() {
        float height = INFO_LINE_H * infoLines();
        if (showLegend.get()) {
            height += LEGEND_GAP + LEGEND_ORDER.length * LEGEND_ROW_H;
        }
        return height;
    }

    private void render(HudRenderEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        float gridSize = gridSize();
        float width = Math.round(Math.max(gridSize, measureInfoWidth()) + PAD * 2f);
        float height = Math.round(gridSize + PAD * 2f + INFO_GAP + measureInfoHeight());
        ThemeManager.sync();
        hud.begin(e.graphics());
        try {
            float alpha = opacity.getFloat();
            if (shadow.get()) {
                GuiRenderUtil.roundRect(1f, 1.5f, width, height, 4f, fade(0x66000000, alpha));
            }
            GuiRenderUtil.roundRect(0, 0, width, height, 4f, fade(ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.88f), alpha));
            GuiRenderUtil.roundOutline(0, 0, width, height, 4f, fade(ThemeManager.withAlpha(ThemeManager.BORDER, 0.6f), alpha));
            float square = squareSize.getFloat() * PX;
            float gridX = Math.round((width - gridSize) * 0.5f);
            float gridY = PAD;
            for (int region = 0; region < DonutGoliaths.CELLS.length; region++) {
                int color = fade(REGION_COLORS[region], alpha);
                for (int[] c : DonutGoliaths.CELLS[region]) {
                    GuiRenderUtil.rect(gridX + c[0] * square, gridY + c[1] * square, (c[2] - c[0]) * square, (c[3] - c[1]) * square, color);
                }
            }
            drawRuns(GOLIATH_LINES, gridX, gridY, square, fade(GOLIATH_DIVIDER, alpha));
            drawRuns(REGION_LINES, gridX, gridY, square, fade(REGION_DIVIDER, alpha));
            if (showRegionIds.get()) {
                for (int[][] goliaths : DonutGoliaths.CELLS) {
                    for (int index = 0; index < goliaths.length; index++) {
                        int[] c = goliaths[index];
                        tileNumber(index + 1, gridX + c[0] * square, gridY + c[1] * square,
                                (c[2] - c[0]) * square, (c[3] - c[1]) * square, alpha);
                    }
                }
            }
            playerPin(mc, player, gridX, gridY, gridSize, alpha);
            infoPanel(mc, player, PAD, gridY + gridSize + INFO_GAP, alpha);
            hud.size(width, height);
        } finally {
            hud.end();
        }
    }

    private static void drawRuns(List<int[]> runs, float gridX, float gridY, float square, int color) {
        float w = 0.5f;
        for (int[] run : runs) {
            if (run[0] == 1) {
                float lx = gridX + run[1] * square;
                GuiRenderUtil.rect(lx - w / 2f, gridY + run[2] * square, w, (run[3] - run[2]) * square, color);
            } else {
                float ly = gridY + run[1] * square;
                GuiRenderUtil.rect(gridX + run[2] * square, ly - w / 2f, (run[3] - run[2]) * square, w, color);
            }
        }
    }

    /** Goliath number centred in its tile; skipped when the tile is too small to read it (Anubis: < 6 px). */
    private void tileNumber(int number, float tx, float ty, float w, float h, float alpha) {
        if (textScale.getFloat() * Math.min(w, h) < 6f * PX * 1.5f) {
            return;
        }
        GuiRenderUtil.pose().pushMatrix();
        float s = Math.min(1f, textScale.getFloat() * 0.85f);
        float cx = tx + w / 2f, cy = ty + h / 2f;
        GuiRenderUtil.pose().translate(GuiRenderUtil.px(cx), GuiRenderUtil.px(cy));
        GuiRenderUtil.pose().scale(s, s);
        GuiRenderUtil.textCentered(FontRole.TINY, String.valueOf(number), 0, 0, fade(TEXT_VALUE, alpha));
        GuiRenderUtil.pose().popMatrix();
    }

    private void playerPin(Minecraft mc, LocalPlayer player, float gridX, float gridY, float gridSize, float alpha) {
        if (!inOverworld(mc)) {
            return;
        }
        double span = DonutGoliaths.HALF * 2.0;
        double fx = (player.getX() + DonutGoliaths.HALF) / span;
        double fz = (player.getZ() + DonutGoliaths.HALF) / span;
        if (fx < 0.0 || fx >= 1.0 || fz < 0.0 || fz >= 1.0) {
            return;
        }
        float cx = gridX + (float) (fx * gridSize);
        float cy = gridY + (float) (fz * gridSize);
        float size = 12f * PX * playerScale.getFloat() * 1.6f;
        float radians = (float) Math.toRadians(player.getYRot() + 180.0);
        GuiRenderUtil.iconRotated(Icon.NAV_ARROW, cx + 0.4f, cy + 0.4f, size + 1f, radians, fade(0x99000000, alpha));
        GuiRenderUtil.iconRotated(Icon.NAV_ARROW, cx, cy, size, radians, fade(playerIndicatorColor.argb(), alpha));
    }

    private void infoPanel(Minecraft mc, LocalPlayer player, float x, float y, float alpha) {
        float valueX = x + labelColumn() + LABEL_GAP;
        float lineCy = y + INFO_LINE_H / 2f;
        if (showCoordinates.get()) {
            GuiRenderUtil.text(FontRole.SMALL, "Coordinate", x, lineCy, fade(TEXT_LABEL, alpha));
            GuiRenderUtil.text(FontRole.TEXT, number((long) Math.floor(player.getX())) + ", " + number((long) Math.floor(player.getZ())),
                    valueX, lineCy, fade(TEXT_VALUE, alpha));
            lineCy += INFO_LINE_H;
        }
        DonutGoliaths.Goliath here = inOverworld(mc) ? DonutGoliaths.at(player.getX(), player.getZ()) : null;
        String region = here == null ? NO_REGION : here.index() + 1 + "  ·  " + DonutGoliaths.REGIONS[here.region()];
        GuiRenderUtil.text(FontRole.SMALL, "Region", x, lineCy, fade(TEXT_LABEL, alpha));
        GuiRenderUtil.text(FontRole.TEXT, region, valueX, lineCy, fade(TEXT_NAME, alpha));
        if (showLegend.get()) {
            float legendY = y + INFO_LINE_H * infoLines() + LEGEND_GAP;
            for (int i = 0; i < LEGEND_ORDER.length; i++) {
                int regionIndex = LEGEND_ORDER[i];
                float rowY = legendY + i * LEGEND_ROW_H;
                GuiRenderUtil.roundRect(x, rowY + (LEGEND_ROW_H - LEGEND_SWATCH) / 2f, LEGEND_SWATCH, LEGEND_SWATCH, 1f,
                        fade(REGION_COLORS[regionIndex], alpha));
                GuiRenderUtil.text(FontRole.SMALL, DonutGoliaths.REGIONS[regionIndex], x + LEGEND_SWATCH + 3f, rowY + LEGEND_ROW_H / 2f,
                        fade(TEXT_NAME, alpha));
            }
        }
    }

    private static boolean inOverworld(Minecraft mc) {
        return mc.level != null && mc.level.dimension() == Level.OVERWORLD;
    }

    private static String number(long value) {
        return value < 0L ? MINUS + -value : Long.toString(value);
    }

    /** Region label at the player's position, for tests and other modules ("—" outside the map). */
    public static String regionAt(double x, double z) {
        DonutGoliaths.Goliath here = DonutGoliaths.at(x, z);
        return here == null ? NO_REGION : here.label();
    }
}
