package dev.hafnium.client.core.setting;

import java.awt.Color;
import java.util.Locale;

/** An opaque colour edited as hue/saturation/value, so the picker keeps its hue at grey levels. */
public final class ColorSetting extends Setting<Integer> {
    private float hue;
    private float saturation;
    private float brightness;
    private String hex;

    public int argb() {
        return get();
    }

    public ColorSetting(String name, int argb) {
        super(name, 0xFF000000 | argb);
        setArgb(argb);
    }

    @Override
    public void set(Integer argb) {
        setArgb(argb);
    }

    public void setArgb(int argb) {
        float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        update();
    }

    public void setHsv(float h, float s, float v) {
        hue = clamp(h);
        saturation = clamp(s);
        brightness = clamp(v);
        update();
    }

    public float hue() {
        return hue;
    }

    public float saturation() {
        return saturation;
    }

    public float brightness() {
        return brightness;
    }

    public String hex() {
        return hex;
    }

    private void update() {
        int rgb = 0xFF000000 | (Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF);
        hex = String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
        assign(rgb);
    }

    private static float clamp(float f) {
        return Math.max(0f, Math.min(1f, f));
    }
}
