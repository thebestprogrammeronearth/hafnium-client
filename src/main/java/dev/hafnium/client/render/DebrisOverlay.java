package dev.hafnium.client.render;

import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.world.Pos;
import dev.hafnium.client.modules.world.AncientDebrisModule;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.shapes.Shapes;

/** Copies bounded render data during extraction; drawing never queries live world data. */
public final class DebrisOverlay {
    private record Marker(int x, int y, int z, int color) {}
    private record Frame(List<Marker> markers, boolean tracers, float width) {}
    private static volatile Frame frame = new Frame(List.of(), false, 2);
    public static void register(HafniumCore core) {
        WorldRenderEvents.END_EXTRACTION.register(ctx -> {
            var module = core.modules().get(AncientDebrisModule.class);
            frame = module.isEnabled() ? new Frame(module.visible()
                                                           .stream()
                                                           .map(d
                                                                   -> new Marker(Pos.x(d.key().pos()),
                                                                           Pos.y(d.key().pos()), Pos.z(d.key().pos()),
                                                                           d.stale() ? 0x88888888 : module.color.get()))
                                                           .toList(),
                                                 module.tracers.get(), module.width.get().floatValue())
                                       : new Frame(List.of(), false, 2);
        });
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ctx -> {
            Frame f = frame;
            if (f.markers.isEmpty())
                return;
            var camera = ctx.worldState().cameraRenderState;
            var buffer = ctx.consumers().getBuffer(RenderTypes.lines());
            var pose = ctx.matrices();
            for (var m : f.markers) {
                double x = m.x - camera.pos.x, y = m.y - camera.pos.y, z = m.z - camera.pos.z;
                ShapeRenderer.renderShape(pose, buffer, Shapes.block(), x, y, z, m.color, f.width);
                if (f.tracers) {
                    var origin = new org.joml.Vector3f(0, 0, -.3f).rotate(camera.orientation);
                    var end = new org.joml.Vector3f((float) x + .5f, (float) y + .5f, (float) z + .5f);
                    var normal = new org.joml.Vector3f(end).sub(origin).normalize();
                    buffer.addVertex(pose.last(), origin.x, origin.y, origin.z)
                            .setColor(m.color)
                            .setNormal(pose.last(), normal)
                            .setLineWidth(f.width);
                    buffer.addVertex(pose.last(), end.x, end.y, end.z)
                            .setColor(m.color)
                            .setNormal(pose.last(), normal)
                            .setLineWidth(f.width);
                }
            }
        });
    }
}
