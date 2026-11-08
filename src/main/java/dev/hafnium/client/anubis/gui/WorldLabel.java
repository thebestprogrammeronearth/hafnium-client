/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.anubis.gui;

import dev.hafnium.client.anubis.util.TracerEngine;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Projects world positions to screen coordinates for labels drawn on the HUD. */
public final class WorldLabel {
    private WorldLabel() {
    }

    /** Screen position (in the given screen units) of a world point, or null when behind the camera or far off-screen. */
    public static float[] project(Minecraft mc, Vec3 world, float screenW, float screenH) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized() || !TracerEngine.isInFront(camera, world)) {
            return null;
        }
        Vec3 ndc = mc.gameRenderer.projectPointToScreen(world);
        if (!Double.isFinite(ndc.x) || !Double.isFinite(ndc.y)) {
            return null;
        }
        float x = (float) (ndc.x + 1.0) * 0.5F * screenW;
        float y = (float) (1.0 - ndc.y) * 0.5F * screenH;
        return x < -200.0F || x > screenW + 200.0F || y < -100.0F || y > screenH + 100.0F ? null : new float[]{x, y};
    }
}
