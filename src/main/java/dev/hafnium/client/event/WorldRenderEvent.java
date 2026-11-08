/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.event;

import dev.hafnium.client.core.event.EventBus;
import net.minecraft.client.renderer.culling.Frustum;

/**
 * Posted once per frame while the level renders, after the camera and frustum are set up (Anubis "Render3DEvent").
 * Handlers draw world-space shapes with Minecraft's {@code Gizmos} or the {@code TracerEngine}. One shared mutable
 * instance: read the fields during the handler only.
 */
public final class WorldRenderEvent {
    public float partialTick;
    /** The camera frustum, or null when unavailable. */
    public Frustum frustum;
    private static final WorldRenderEvent INSTANCE = new WorldRenderEvent();
    private static boolean rendering;
    private static long frame;
    private static long lastDurationNanos;

    private WorldRenderEvent() {
    }

    public static void dispatch(EventBus bus, float partialTick, Frustum frustum) {
        if (!bus.hasSubscribers(WorldRenderEvent.class)) {
            lastDurationNanos = 0L;
            return;
        }
        if (rendering) {
            throw new IllegalStateException("WorldRenderEvent cannot be nested");
        }
        rendering = true;
        long start = System.nanoTime();
        INSTANCE.partialTick = partialTick;
        INSTANCE.frustum = frustum;
        try {
            bus.post(INSTANCE);
        } finally {
            INSTANCE.frustum = null;
            lastDurationNanos = System.nanoTime() - start;
            frame++;
            rendering = false;
        }
    }

    public static long frame() {
        return frame;
    }

    /** CPU time the last frame's handlers took (shown by the Performance module). */
    public static long lastDurationNanos() {
        return lastDurationNanos;
    }
}
