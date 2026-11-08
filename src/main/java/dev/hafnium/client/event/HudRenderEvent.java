package dev.hafnium.client.event;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** Posted once per frame while the in-game HUD renders. A single instance is reused; do not keep references. */
public final class HudRenderEvent {
    public static final HudRenderEvent INSTANCE = new HudRenderEvent();

    private GuiGraphics graphics;
    private DeltaTracker delta;

    private HudRenderEvent() {
    }

    public HudRenderEvent set(GuiGraphics graphics, DeltaTracker delta) {
        this.graphics = graphics;
        this.delta = delta;
        return this;
    }

    public GuiGraphics graphics() {
        return graphics;
    }

    public DeltaTracker delta() {
        return delta;
    }
}
