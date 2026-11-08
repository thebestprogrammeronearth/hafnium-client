package dev.hafnium.client.gui.render;

import dev.hafnium.client.gui.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Drawing helpers for the ClickGUI.
 *
 * <p>Layout is done in "units" (one unit = one GUI pixel at the reference GUI scale 2). Between {@link #begin} and
 * {@link #end} the pose is set to raw framebuffer pixels and every shape is snapped to whole pixels, which keeps
 * rounded corners, 1-pixel borders, icons and TTF text crisp at any GUI scale.
 */
public final class GuiRenderUtil {
    private static final int MAX_OVERSAMPLE = 6;

    private static GuiGraphics g;
    private static int scale = 2;
    private static float alpha = 1f;

    @SuppressWarnings("unchecked")
    private static final Map<String, Component>[][] TEXT_CACHE = new Map[FontRole.values().length][MAX_OVERSAMPLE + 1];
    private static final Style[][] STYLES = new Style[FontRole.values().length][MAX_OVERSAMPLE + 1];

    private static final CornerTable[][] CORNERS = new CornerTable[4][257];

    private GuiRenderUtil() {
    }

    // ------------------------------------------------------------------ frame setup

    public static void begin(GuiGraphics graphics, int guiScale, int pixelScale) {
        g = graphics;
        scale = pixelScale;
        g.pose().pushMatrix();
        g.pose().scale(1f / guiScale, 1f / guiScale);
    }

    public static void end() {
        g.pose().popMatrix();
        g = null;
        alpha = 1f;
    }

    public static GuiGraphics graphics() {
        return g;
    }

    public static Matrix3x2fStack pose() {
        return g.pose();
    }

    public static int scale() {
        return scale;
    }

    /** Global opacity applied to everything drawn (used by the open/close fade). */
    public static void setAlpha(float a) {
        alpha = a;
    }

    public static float alpha() {
        return alpha;
    }

    public static int px(float units) {
        return Math.round(units * scale);
    }

    private static int col(int argb) {
        return alpha >= 1f ? argb : ThemeManager.withAlpha(argb, alpha);
    }

    // ------------------------------------------------------------------ rectangles

    public static void rect(float x, float y, float w, float h, int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        g.fill(px(x), px(y), px(x + w), px(y + h), col(color));
    }

    /** One device pixel thick horizontal line. */
    public static void hLine(float x1, float x2, float y, int color) {
        int py = px(y);
        g.fill(px(x1), py, px(x2), py + 1, col(color));
    }

    public static void gradient(float x, float y, float w, float h, int top, int bottom) {
        g.fillGradient(px(x), px(y), px(x + w), px(y + h), col(top), col(bottom));
    }

    public static void fillPx(int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, col(color));
    }

    public static void gradientPx(int x0, int y0, int x1, int y1, int top, int bottom) {
        g.fillGradient(x0, y0, x1, y1, col(top), col(bottom));
    }

    public static void roundRect(float x, float y, float w, float h, float r, int color) {
        roundRect(x, y, w, h, r, color, true, true);
    }

    /** Filled rounded rectangle with anti-aliased corners; top/bottom corners can be squared off. */
    public static void roundRect(float x, float y, float w, float h, float r, int color, boolean roundTop, boolean roundBottom) {
        if ((color >>> 24) == 0) {
            return;
        }
        int x0 = px(x), y0 = px(y), x1 = px(x + w), y1 = px(y + h);
        int c = col(color);
        float rp = Math.min(r * scale, Math.min(x1 - x0, y1 - y0) / 2f);
        if (rp < 0.5f) {
            g.fill(x0, y0, x1, y1, c);
            return;
        }
        CornerTable t = corner(rp, 0);
        int R = t.size;
        int top = roundTop ? y0 + R : y0;
        int bottom = roundBottom ? y1 - R : y1;
        if (bottom > top) {
            g.fill(x0, top, x1, bottom, c);
        }
        for (int j = 0; j < R; j++) {
            if (roundTop) {
                fillRow(t, j, x0, x1, y0 + j, c);
            }
            if (roundBottom) {
                fillRow(t, j, x0, x1, y1 - 1 - j, c);
            }
        }
    }

    private static void fillRow(CornerTable t, int j, int x0, int x1, int py, int c) {
        int full = t.fullStart[j];
        if (x1 - full > x0 + full) {
            g.fill(x0 + full, py, x1 - full, py + 1, c);
        }
        float[] row = t.cov[j];
        for (int i = 0; i < full; i++) {
            float cov = row[i];
            if (cov > 0.02f) {
                int pc = ThemeManager.withAlpha(c, cov);
                g.fill(x0 + i, py, x0 + i + 1, py + 1, pc);
                g.fill(x1 - 1 - i, py, x1 - i, py + 1, pc);
            }
        }
    }

    /** One-pixel (or {@code thicknessPx}) anti-aliased rounded outline. */
    public static void roundOutline(float x, float y, float w, float h, float r, int color) {
        roundOutline(x, y, w, h, r, color, 1);
    }

    public static void roundOutline(float x, float y, float w, float h, float r, int color, int thicknessPx) {
        if ((color >>> 24) == 0) {
            return;
        }
        int x0 = px(x), y0 = px(y), x1 = px(x + w), y1 = px(y + h);
        int c = col(color);
        int tk = Math.max(1, Math.min(3, thicknessPx));
        float rp = Math.max(tk, Math.min(r * scale, Math.min(x1 - x0, y1 - y0) / 2f));
        CornerTable t = corner(rp, tk);
        int R = t.size;
        // straight edges
        g.fill(x0 + R, y0, x1 - R, y0 + tk, c);
        g.fill(x0 + R, y1 - tk, x1 - R, y1, c);
        g.fill(x0, y0 + R, x0 + tk, y1 - R, c);
        g.fill(x1 - tk, y0 + R, x1, y1 - R, c);
        // corners
        for (int j = 0; j < R; j++) {
            float[] row = t.cov[j];
            int i = 0;
            while (i < R) {
                float cov = row[i];
                if (cov <= 0.02f) {
                    i++;
                    continue;
                }
                if (cov >= 0.98f) {
                    int start = i;
                    while (i < R && row[i] >= 0.98f) {
                        i++;
                    }
                    ringSpan(x0, x1, y0, y1, j, start, i, c);
                } else {
                    ringSpan(x0, x1, y0, y1, j, i, i + 1, ThemeManager.withAlpha(c, cov));
                    i++;
                }
            }
        }
    }

    private static void ringSpan(int x0, int x1, int y0, int y1, int j, int i0, int i1, int c) {
        g.fill(x0 + i0, y0 + j, x0 + i1, y0 + j + 1, c);
        g.fill(x1 - i1, y0 + j, x1 - i0, y0 + j + 1, c);
        g.fill(x0 + i0, y1 - 1 - j, x0 + i1, y1 - j, c);
        g.fill(x1 - i1, y1 - 1 - j, x1 - i0, y1 - j, c);
    }

    public static void circle(float cx, float cy, float r, int color) {
        roundRect(cx - r, cy - r, r * 2, r * 2, r, color);
    }

    private static CornerTable corner(float rp, int ring) {
        int q = Math.min(256, Math.round(rp * 4));
        CornerTable t = CORNERS[ring][q];
        if (t == null) {
            t = new CornerTable(q / 4f, ring);
            CORNERS[ring][q] = t;
        }
        return t;
    }

    /** Per-pixel coverage for one quarter circle of a given pixel radius (fill when ring == 0, else ring width). */
    private static final class CornerTable {
        final int size;
        final float[][] cov;
        final int[] fullStart;

        CornerTable(float r, int ring) {
            size = (int) Math.ceil(r);
            cov = new float[size][size];
            fullStart = new int[size];
            for (int j = 0; j < size; j++) {
                fullStart[j] = size;
                for (int i = 0; i < size; i++) {
                    float dx = Math.max(r - (i + 0.5f), 0);
                    float dy = Math.max(r - (j + 0.5f), 0);
                    float d = (float) Math.hypot(dx, dy);
                    float outer = clamp(r - d + 0.5f);
                    float c = ring == 0 ? outer : outer - clamp((r - ring) - d + 0.5f);
                    cov[j][i] = c;
                    if (ring == 0 && c >= 0.999f && fullStart[j] == size) {
                        fullStart[j] = i;
                    }
                }
            }
        }

        private static float clamp(float v) {
            return v < 0 ? 0 : v > 1 ? 1 : v;
        }
    }

    // ------------------------------------------------------------------ icons

    public static void icon(Icon icon, float x, float y, float size, int color) {
        int s = Math.max(4, px(size));
        Identifier id = IconCache.get(icon, s);
        g.blit(RenderPipelines.GUI_TEXTURED, id, px(x), px(y), 0, 0, s, s, s, s, s, s, col(color));
    }

    /** Icon centred on (cx, cy) and rotated by {@code radians}. */
    public static void iconRotated(Icon icon, float cx, float cy, float size, float radians, int color) {
        int s = Math.max(4, px(size) & ~1);
        Identifier id = IconCache.get(icon, s);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(px(cx), px(cy));
        if (radians != 0) {
            pose.rotate(radians);
        }
        g.blit(RenderPipelines.GUI_TEXTURED, id, -s / 2, -s / 2, 0, 0, s, s, s, s, s, s, col(color));
        pose.popMatrix();
    }

    // ------------------------------------------------------------------ text

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private static int oversample(FontRole role) {
        return ThemeManager.customFont() ? Math.max(1, Math.min(MAX_OVERSAMPLE, scale)) : 0;
    }

    private static Component component(FontRole role, String s) {
        int o = oversample(role);
        Map<String, Component> cache = TEXT_CACHE[role.ordinal()][o];
        if (cache == null) {
            cache = new HashMap<>();
            TEXT_CACHE[role.ordinal()][o] = cache;
        }
        Component c = cache.get(s);
        if (c == null) {
            if (cache.size() > 2048) {
                cache.clear();
            }
            c = Component.literal(s).withStyle(style(role, o));
            cache.put(s, c);
        }
        return c;
    }

    private static Style style(FontRole role, int o) {
        Style st = STYLES[role.ordinal()][o];
        if (st == null) {
            if (o == 0) {
                st = Style.EMPTY.withBold(role.bold);
            } else {
                st = Style.EMPTY.withFont(new FontDescription.Resource(
                        Identifier.fromNamespaceAndPath("hafnium", role.id + "_" + o)));
            }
            STYLES[role.ordinal()][o] = st;
        }
        return st;
    }

    private static float roleScale(FontRole role) {
        return ThemeManager.customFont() ? 1f : role.vanillaScale;
    }

    public static float textWidth(FontRole role, String s) {
        if (s.isEmpty()) {
            return 0;
        }
        return font().getSplitter().stringWidth(component(role, s)) * roleScale(role);
    }

    public static float capHeight(FontRole role) {
        return role.capHeight(ThemeManager.customFont());
    }

    /**
     * Draws text whose capital letters are vertically centred on {@code centerY}.
     *
     * @return the text width in units
     */
    public static float text(FontRole role, String s, float x, float centerY, int color) {
        if (s.isEmpty() || (color >>> 24) == 0) {
            return 0;
        }
        boolean custom = ThemeManager.customFont();
        float k = roleScale(role);
        // The font baseline sits 7 units below the draw origin, for TTF and bitmap fonts alike.
        float topUnits = centerY + role.capHeight(custom) / 2f - 7f * k;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(px(x), px(topUnits));
        float s2 = scale * k;
        pose.scale(s2, s2);
        int c = col(color);
        // Text with near-zero alpha is skipped by vanilla; keep a floor so fades stay continuous.
        if ((c >>> 24) < 4) {
            c = (c & 0xFFFFFF) | (4 << 24);
        }
        g.drawString(font(), component(role, s), 0, 0, c, false);
        pose.popMatrix();
        return textWidth(role, s);
    }

    public static float textCentered(FontRole role, String s, float centerX, float centerY, int color) {
        float w = textWidth(role, s);
        text(role, s, centerX - w / 2f, centerY, color);
        return w;
    }

    public static float textRight(FontRole role, String s, float rightX, float centerY, int color) {
        float w = textWidth(role, s);
        text(role, s, rightX - w, centerY, color);
        return w;
    }

    // ------------------------------------------------------------------ clipping

    public static void pushScissor(float x, float y, float w, float h) {
        g.enableScissor(px(x), px(y), px(x + w), px(y + h));
    }

    public static void popScissor() {
        g.disableScissor();
    }

    public static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
