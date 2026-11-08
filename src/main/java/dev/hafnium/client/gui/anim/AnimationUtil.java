package dev.hafnium.client.gui.anim;

/** Time-based easing helpers. Everything works on primitive floats so animating never allocates. */
public final class AnimationUtil {
    private AnimationUtil() {
    }

    /**
     * Moves {@code current} linearly toward {@code target} so a full 0..1 transition takes {@code durationMs}.
     * Pair with an easing function at draw time.
     */
    public static float step(float current, float target, float dtMs, float durationMs) {
        if (durationMs <= 0) {
            return target;
        }
        float delta = dtMs / durationMs;
        if (current < target) {
            return Math.min(target, current + delta);
        }
        return Math.max(target, current - delta);
    }

    public static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    public static float easeInOutCubic(float t) {
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
