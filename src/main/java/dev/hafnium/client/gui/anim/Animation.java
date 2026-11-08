package dev.hafnium.client.gui.anim;

/** A 0..1 progress value that moves toward a target over a fixed duration, read through an easing curve. */
public final class Animation {
    private final float durationMs;
    private float progress;
    private float target;

    public Animation(float durationMs, boolean initial) {
        this.durationMs = durationMs;
        this.progress = initial ? 1f : 0f;
        this.target = this.progress;
    }

    public void setTarget(boolean on) {
        target = on ? 1f : 0f;
    }

    public void snap(boolean on) {
        target = on ? 1f : 0f;
        progress = target;
    }

    public void update(float dtMs) {
        if (progress != target) {
            progress = AnimationUtil.step(progress, target, dtMs, durationMs);
        }
    }

    public boolean target() {
        return target > 0.5f;
    }

    public boolean isAnimating() {
        return progress != target;
    }

    public float linear() {
        return progress;
    }

    public float eased() {
        return AnimationUtil.easeOutCubic(progress);
    }

    public float smooth() {
        return AnimationUtil.easeInOutCubic(progress);
    }
}
