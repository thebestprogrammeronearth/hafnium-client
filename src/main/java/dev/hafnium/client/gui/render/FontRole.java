package dev.hafnium.client.gui.render;

/**
 * Text styles. With the Inter font each role maps to its own TTF definition (assets/hafnium/font/&lt;id&gt;_&lt;n&gt;.json)
 * rasterised at the exact pixel scale n, so glyphs land 1:1 on screen pixels. With the vanilla font the role is
 * drawn with the Minecraft font scaled by {@link #vanillaScale}.
 */
public enum FontRole {
    TEXT("text", 10f, 1f, false),
    SMALL("small", 8.5f, 1f, false),
    HEADER("header", 10.5f, 1f, true),
    /** Used for header labels too long for {@link #HEADER} (e.g. AUTOMATION). */
    HEADER_COMPACT("header_compact", 9.5f, 1f, true),
    TITLE("title", 17.5f, 2f, true),
    LOGO("logo", 16f, 2f, false),
    TINY("tiny", 7f, 1f, false);

    /** Inter cap height relative to the em size. */
    private static final float INTER_CAP = 0.727f;

    final String id;
    final float size;
    final float vanillaScale;
    final boolean bold;

    FontRole(String id, float size, float vanillaScale, boolean bold) {
        this.id = id;
        this.size = size;
        this.vanillaScale = vanillaScale;
        this.bold = bold;
    }

    float capHeight(boolean custom) {
        return custom ? size * INTER_CAP : 7f * vanillaScale;
    }
}
