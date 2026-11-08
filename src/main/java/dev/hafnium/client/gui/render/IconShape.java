package dev.hafnium.client.gui.render;

import java.util.ArrayList;
import java.util.List;

/** Stroke primitives on the 24x24 icon grid, evaluated as distance fields when rasterising. */
final class IconShape {
    private static final int LINE = 0;
    private static final int CIRCLE = 1;
    private static final int ARC = 2;
    private static final int RRECT = 3;

    private final List<float[]> prims = new ArrayList<>();

    IconShape line(float x1, float y1, float x2, float y2) {
        prims.add(new float[]{LINE, x1, y1, x2, y2});
        return this;
    }

    IconShape poly(float... pts) {
        for (int i = 0; i + 3 < pts.length; i += 2) {
            line(pts[i], pts[i + 1], pts[i + 2], pts[i + 3]);
        }
        return this;
    }

    IconShape circle(float cx, float cy, float r) {
        prims.add(new float[]{CIRCLE, cx, cy, r});
        return this;
    }

    /** Arc in degrees, screen orientation (y down), from a0 to a1 going clockwise. */
    IconShape arc(float cx, float cy, float r, float a0, float a1) {
        prims.add(new float[]{ARC, cx, cy, r, (float) Math.toRadians(a0), (float) Math.toRadians(a1)});
        return this;
    }

    IconShape rect(float x, float y, float w, float h, float r) {
        prims.add(new float[]{RRECT, x + w / 2, y + h / 2, w / 2, h / 2, r});
        return this;
    }

    IconShape gear(float cx, float cy, float rIn, float rOut, int teeth) {
        float step = (float) (Math.PI * 2 / teeth);
        float half = step * 0.18f;
        float base = step * 0.30f;
        float[] pts = new float[teeth * 8 + 2];
        int n = 0;
        for (int t = 0; t < teeth; t++) {
            float a = t * step;
            float[] angles = {a - base, a - half, a + half, a + base};
            float[] radii = {rIn, rOut, rOut, rIn};
            for (int k = 0; k < 4; k++) {
                pts[n++] = cx + (float) Math.cos(angles[k]) * radii[k];
                pts[n++] = cy + (float) Math.sin(angles[k]) * radii[k];
            }
        }
        pts[n++] = pts[0];
        pts[n] = pts[1];
        return poly(pts);
    }

    /** Distance from (px, py) to the nearest stroke centre line, in grid units. */
    float distance(float px, float py) {
        float best = Float.MAX_VALUE;
        for (float[] p : prims) {
            float d = switch ((int) p[0]) {
                case LINE -> segment(px, py, p[1], p[2], p[3], p[4]);
                case CIRCLE -> Math.abs((float) Math.hypot(px - p[1], py - p[2]) - p[3]);
                case ARC -> arc(px, py, p);
                default -> Math.abs(roundBox(px - p[1], py - p[2], p[3], p[4], p[5]));
            };
            best = Math.min(best, d);
        }
        return best;
    }

    private static float segment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len2 = dx * dx + dy * dy;
        float t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / len2));
        return (float) Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private static float arc(float px, float py, float[] p) {
        float cx = p[1], cy = p[2], r = p[3], a0 = p[4], a1 = p[5];
        float ang = (float) Math.atan2(py - cy, px - cx);
        float span = wrap(a1 - a0);
        if (wrap(ang - a0) <= span) {
            return Math.abs((float) Math.hypot(px - cx, py - cy) - r);
        }
        float d0 = (float) Math.hypot(px - (cx + Math.cos(a0) * r), py - (cy + Math.sin(a0) * r));
        float d1 = (float) Math.hypot(px - (cx + Math.cos(a1) * r), py - (cy + Math.sin(a1) * r));
        return Math.min(d0, d1);
    }

    private static float wrap(float a) {
        float twoPi = (float) (Math.PI * 2);
        a %= twoPi;
        return a < 0 ? a + twoPi : a;
    }

    private static float roundBox(float x, float y, float hw, float hh, float r) {
        float qx = Math.abs(x) - hw + r;
        float qy = Math.abs(y) - hh + r;
        float outside = (float) Math.hypot(Math.max(qx, 0), Math.max(qy, 0));
        return outside + Math.min(Math.max(qx, qy), 0) - r;
    }
}
