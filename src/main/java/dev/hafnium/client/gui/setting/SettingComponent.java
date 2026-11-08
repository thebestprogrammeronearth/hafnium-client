package dev.hafnium.client.gui.setting;

import dev.hafnium.client.gui.anim.Animation;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import net.minecraft.client.input.KeyEvent;

/** Base class for the placeholder setting controls drawn inside a module's settings dropdown. */
public abstract class SettingComponent {
    public static final float ROW = 16f;

    protected float x;
    protected float y;
    protected float w;
    protected final Animation hover = new Animation(150, false);

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return w;
    }

    /** Current (possibly animating) height in units. */
    public abstract float height();

    public void layout(float x, float y, float w) {
        this.x = x;
        this.y = y;
        this.w = w;
    }

    public void update(float dtMs, double mx, double my, boolean interactive) {
        hover.setTarget(interactive && GuiRenderUtil.inside(mx, my, x, y, w, ROW));
        hover.update(dtMs);
    }

    public abstract void render(double mx, double my);

    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    /** Whether a child animation is still running (the parent uses this to decide on clipping). */
    public boolean isAnimating() {
        return false;
    }

    protected boolean overRow(double mx, double my) {
        return GuiRenderUtil.inside(mx, my, x, y, w, ROW);
    }

    protected float rowCenter() {
        return y + ROW / 2f;
    }
}
