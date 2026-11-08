package dev.hafnium.client.hud;

import dev.hafnium.client.gui.render.GuiRenderUtil;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A HUD element whose position is kept in {@link HudLayout}: an anchor (left/centre/right, top/middle/bottom, picked
 * from where it was dropped) plus an offset, so it stays in the same corner when the window or GUI scale changes.
 * Draw with {@link #begin}/{@link #end}; inside, (0, 0) is the element's top-left corner and one unit is one GUI unit
 * times the element's scale. Call {@link #size} with what was drawn so the editor knows the box.
 */
public class LayoutElement implements HudElement {
    private final String id;
    private final String name;
    private final BooleanSupplier visible;
    private final float defAx, defAy, defOx, defOy;
    private float w = 40f, h = 12f;
    private long lastDrawn;

    public LayoutElement(String id, String name, BooleanSupplier visible, float anchorX, float anchorY, float offsetX, float offsetY) {
        this.id = id;
        this.name = name;
        this.visible = visible;
        this.defAx = anchorX;
        this.defAy = anchorY;
        this.defOx = offsetX;
        this.defOy = offsetY;
        HudLayout.register(this);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    /** Visible while its module says so and it drew within the last half second. */
    @Override
    public boolean visible() {
        return visible.getAsBoolean() && System.currentTimeMillis() - lastDrawn < 500;
    }

    @Override
    public float width() {
        return w;
    }

    @Override
    public float height() {
        return h;
    }

    public void size(float width, float height) {
        this.w = Math.max(1f, width);
        this.h = Math.max(1f, height);
        this.lastDrawn = System.currentTimeMillis();
    }

    private HudLayout.Placement placement() {
        HudLayout.Placement p = HudLayout.placement(id);
        return p != null ? p : new HudLayout.Placement(defAx, defAy, defOx, defOy, 1f);
    }

    @Override
    public float scale() {
        return placement().scale();
    }

    @Override
    public float x(float screenW, float screenH) {
        HudLayout.Placement p = placement();
        float sw = w * p.scale();
        return clamp(p.ax() * (screenW - sw) + p.ox(), screenW - sw);
    }

    @Override
    public float y(float screenW, float screenH) {
        HudLayout.Placement p = placement();
        float sh = h * p.scale();
        return clamp(p.ay() * (screenH - sh) + p.oy(), screenH - sh);
    }

    private static float clamp(float v, float max) {
        return Math.max(0f, Math.min(v, Math.max(0f, max)));
    }

    @Override
    public void moveTo(float x, float y, float screenW, float screenH) {
        float s = scale();
        float sw = w * s, sh = h * s;
        float ax = anchor((x + sw / 2f) / screenW);
        float ay = anchor((y + sh / 2f) / screenH);
        HudLayout.put(id, new HudLayout.Placement(ax, ay, x - ax * (screenW - sw), y - ay * (screenH - sh), s));
    }

    private static float anchor(float centre) {
        return centre < 1f / 3f ? 0f : centre > 2f / 3f ? 1f : 0.5f;
    }

    @Override
    public void setScale(float scale) {
        HudLayout.Placement p = placement();
        float s = Math.max(0.5f, Math.min(3f, Math.round(scale * 20f) / 20f));
        HudLayout.put(id, new HudLayout.Placement(p.ax(), p.ay(), p.ox(), p.oy(), s));
    }

    @Override
    public void reset() {
        HudLayout.remove(id);
    }

    /**
     * Starts drawing this element: sets up {@link GuiRenderUtil} and moves the origin to the element's corner.
     * Always pair with {@link #end()}.
     */
    public void begin(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        int guiScale = Math.max(1, mc.getWindow().getGuiScale());
        GuiRenderUtil.begin(graphics, guiScale, guiScale);
        float sw = graphics.guiWidth(), sh = graphics.guiHeight();
        GuiRenderUtil.pose().translate(GuiRenderUtil.px(x(sw, sh)), GuiRenderUtil.px(y(sw, sh)));
        float s = scale();
        GuiRenderUtil.pose().scale(s, s);
    }

    public void end() {
        GuiRenderUtil.end();
    }

    /** Like {@link #begin} for overlays drawn with vanilla {@code GuiGraphics} calls (GUI units). */
    public void beginVanilla(GuiGraphics graphics) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x(graphics.guiWidth(), graphics.guiHeight()), y(graphics.guiWidth(), graphics.guiHeight()));
        float s = scale();
        graphics.pose().scale(s, s);
    }

    public void endVanilla(GuiGraphics graphics) {
        graphics.pose().popMatrix();
    }
}
