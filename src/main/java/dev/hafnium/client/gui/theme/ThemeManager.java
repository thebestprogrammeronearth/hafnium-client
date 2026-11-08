package dev.hafnium.client.gui.theme;

import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.modules.client.ClickGuiModule;
import dev.hafnium.client.modules.client.FontsModule;

/**
 * Hafnium palette. The base colours are fixed; the accent, panel opacity, blur and font follow the
 * Client &gt; ClickGUI and Client &gt; Fonts modules.
 */
public final class ThemeManager {
    public static final int BACKGROUND = 0xFF15131C;
    public static final int PANEL = 0xFF1B1823;
    public static final int HEADER = 0xFF211A2B;
    public static final int TEXT = 0xFFF3F0F8;
    public static final int TEXT_SECONDARY = 0xFFA6A0B2;
    public static final int TEXT_MUTED = 0xFF6E6879;
    public static final int BORDER = 0xFF4A4158;
    public static final int SEPARATOR = 0x12FFFFFF;
    public static final int DOT_OFF = 0xFF6B6577;
    public static final int TRACK = 0xFF342E3F;
    public static final int DANGER = 0xFFFF6B81;

    private static int accent = 0xFFB77CFF;
    private static int accentHover = 0xFFC99DFF;
    private static int accentStrong = 0xFF7D45D8;
    private static float panelOpacity = 0.9f;
    private static boolean blur = true;
    private static boolean customFont = true;

    private static ClickGuiModule clickGui;
    private static FontsModule fonts;

    private ThemeManager() {
    }

    /** Reads the Client > ClickGUI / Fonts settings. Called once per frame; no allocation. */
    public static void sync() {
        if (clickGui == null) {
            clickGui = HafniumCore.get().modules().get(ClickGuiModule.class);
            fonts = HafniumCore.get().modules().get(FontsModule.class);
        }
        int a = clickGui.accent.get();
        if (a != accent) {
            accent = a;
            accentHover = lerp(a, 0xFFFFFFFF, 0.28f);
            accentStrong = lerp(a, 0xFF3A1580, 0.42f);
        }
        panelOpacity = (float) (clickGui.opacity.get() / 100.0);
        blur = clickGui.blur.get();
        customFont = fonts.useInter();
    }

    public static int accent() {
        return accent;
    }

    public static int accentHover() {
        return accentHover;
    }

    /** Saturated fill used for the focused panel header. */
    public static int accentStrong() {
        return accentStrong;
    }

    public static float panelOpacity() {
        return panelOpacity;
    }

    public static boolean blur() {
        return blur;
    }

    public static boolean customFont() {
        return customFont;
    }

    // ---- colour helpers ----

    public static int withAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
