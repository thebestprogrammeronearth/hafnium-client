/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.anubis.gui;

import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;

/**
 * Two-line world label (name over coloured detail runs), centred on an anchor and sitting on top of it. Anubis draws
 * this with ImGui; Hafnium draws the same layout with its own text renderer. Call between
 * {@link GuiRenderUtil#begin} and {@link GuiRenderUtil#end}; coordinates are GUI units.
 */
public final class WorldText {
    public static final int NAME = 0xFFFFFFFF;
    public static final int LABEL = 0xFFEBEBEB;
    private static final float NAME_H = 10f;
    private static final float DETAIL_H = 8.5f;

    private WorldText() {
    }

    public static int hudAccent() {
        return ThemeManager.accent();
    }

    public static void draw(float cx, float bottom, String name, String[] runs, int[] colors, float alpha) {
        if (alpha <= 0.001F) {
            return;
        }
        boolean detail = runs.length > 0;
        float nameCy = bottom - 1f - (detail ? DETAIL_H + 1f : 0f) - NAME_H * 0.5f;
        shadowed(FontRole.TEXT, name, cx - GuiRenderUtil.textWidth(FontRole.TEXT, name) * 0.5f, nameCy, scale(NAME, alpha));
        if (detail) {
            float w = 0f;
            for (String run : runs) {
                w += GuiRenderUtil.textWidth(FontRole.SMALL, run);
            }
            float x = cx - w * 0.5f;
            float cy = nameCy + NAME_H * 0.5f + 1f + DETAIL_H * 0.5f;
            for (int i = 0; i < runs.length; i++) {
                shadowed(FontRole.SMALL, runs[i], x, cy, scale(colors[i], alpha));
                x += GuiRenderUtil.textWidth(FontRole.SMALL, runs[i]);
            }
        }
    }

    private static void shadowed(FontRole role, String s, float x, float cy, int color) {
        GuiRenderUtil.text(role, s, x + 0.5f, cy + 0.5f, scale(0xC0000000, (color >>> 24) / 255f));
        GuiRenderUtil.text(role, s, x, cy, color);
    }

    public static int scale(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, alpha)));
        return a << 24 | argb & 0xFFFFFF;
    }
}
