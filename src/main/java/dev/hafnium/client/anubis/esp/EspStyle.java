/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.anubis.esp;

import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.ModeSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.core.setting.Setting;

/** Shared look of block ESPs: outline or boxes, fill, line width, glow, distance fade, reveal animation and range. */
public final class EspStyle {
    public static final String OUTLINE = "Outline";
    public static final String BOXES = "Boxes";
    public final ModeSetting shape;
    public final NumberSetting boxSize;
    public final NumberSetting fillAlpha;
    public final NumberSetting lineWidth;
    public final BoolSetting glow;
    public final BoolSetting fade;
    public final BoolSetting reveal;
    public final NumberSetting range;

    /** Registers each setting on the owning module, e.g. {@code new EspStyle(this::setting, ...)}. */
    @FunctionalInterface
    public interface Registrar {
        <S extends Setting<?>> S add(S setting);
    }

    public EspStyle(Registrar r, String defaultShape, float defaultFillAlpha, float defaultRange) {
        this.shape = r.add(new ModeSetting("Shape", defaultShape, OUTLINE, BOXES));
        this.boxSize = r.add(new NumberSetting("Box Size", 1.0, 0.1, 2.0, 0.1));
        this.fillAlpha = r.add(new NumberSetting("Fill Alpha", defaultFillAlpha, 0.0, 1.0, 0.05));
        this.lineWidth = r.add(new NumberSetting("Line Width", 1.5, 0.5, 5.0, 0.5));
        this.glow = r.add(new BoolSetting("Glow", true));
        this.fade = r.add(new BoolSetting("Distance Fade", true));
        this.reveal = r.add(new BoolSetting("Reveal Animation", true));
        this.range = r.add(new NumberSetting("Range", defaultRange, 16.0, 2048.0, 1.0));
        this.boxSize.visibleWhen(() -> this.shape.is(BOXES));
    }

    public boolean outline() {
        return this.shape.is(OUTLINE);
    }
}
