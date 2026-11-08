package dev.hafnium.client.hud;

/** An on-screen overlay the HUD Editor can move and resize. Coordinates are GUI units; size is unscaled. */
public interface HudElement {
    String id();

    String name();

    /** Whether the overlay is currently shown (its module is on and it has something to draw). */
    boolean visible();

    float width();

    float height();

    float scale();

    /** Top-left corner on a screen of the given size. */
    float x(float screenW, float screenH);

    float y(float screenW, float screenH);

    /** Places the top-left corner at (x, y) on a screen of the given size. */
    void moveTo(float x, float y, float screenW, float screenH);

    void setScale(float scale);

    void reset();
}
