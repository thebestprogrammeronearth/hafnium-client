/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.anubis.util;

import dev.hafnium.client.mixin.anubis.AccessorGameRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public final class FoglessLines {
   private static final double NEAR = 0.05;
   private final Vec3 eye;
   private final Vector3fc forward;
   private final double pixelPerDepth;

   private FoglessLines(Vec3 eye, Vector3fc forward, double pixelPerDepth) {
      this.eye = eye;
      this.forward = forward;
      this.pixelPerDepth = pixelPerDepth;
   }

   public static FoglessLines forFrame(float partialTick) {
      Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
      return new FoglessLines(camera.position(), camera.forwardVector(), pixelScale(partialTick));
   }

   public static double pixelScale(float partialTick) {
      Minecraft mc = Minecraft.getInstance();
      Camera camera = mc.gameRenderer.getMainCamera();
      float fov = ((AccessorGameRenderer)mc.gameRenderer).hafnium$getFov(camera, partialTick, true);
      return 2.0 * Math.tan(Math.toRadians(fov) * 0.5) / Math.max(1, mc.getWindow().getHeight());
   }

   public static FoglessLines forView(Vec3 eye, Vector3fc forward, double pixelPerDepth) {
      return new FoglessLines(eye, forward, pixelPerDepth);
   }

   public void line(Vec3 from, Vec3 to, int argb, float width) {
      this.line(from.x, from.y, from.z, to.x, to.y, to.z, argb, width);
   }

   public void line(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int argb, float width) {
      double depthFrom = this.depth(fromX, fromY, fromZ);
      double depthTo = this.depth(toX, toY, toZ);
      if (!(depthFrom < 0.05) || !(depthTo < 0.05)) {
         if (depthFrom < 0.05) {
            double t = (0.05 - depthFrom) / (depthTo - depthFrom);
            fromX += (toX - fromX) * t;
            fromY += (toY - fromY) * t;
            fromZ += (toZ - fromZ) * t;
            depthFrom = 0.05;
         } else if (depthTo < 0.05) {
            double t = (0.05 - depthTo) / (depthFrom - depthTo);
            toX += (fromX - toX) * t;
            toY += (fromY - toY) * t;
            toZ += (fromZ - toZ) * t;
            depthTo = 0.05;
         }

         double ax = fromX - this.eye.x;
         double ay = fromY - this.eye.y;
         double az = fromZ - this.eye.z;
         double bx = toX - fromX;
         double by = toY - fromY;
         double bz = toZ - fromZ;
         double sx = ay * bz - az * by;
         double sy = az * bx - ax * bz;
         double sz = ax * by - ay * bx;
         double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
         if (!(length < 1.0E-9)) {
            double scale = width * 0.5 * this.pixelPerDepth / length;
            double kf = scale * depthFrom;
            double kt = scale * depthTo;
            WorldQuads.layer(true)
               .quad(
                  fromX + sx * kf,
                  fromY + sy * kf,
                  fromZ + sz * kf,
                  toX + sx * kt,
                  toY + sy * kt,
                  toZ + sz * kt,
                  toX - sx * kt,
                  toY - sy * kt,
                  toZ - sz * kt,
                  fromX - sx * kf,
                  fromY - sy * kf,
                  fromZ - sz * kf,
                  argb
               );
         }
      }
   }

   public void box(AABB box, int argb, float width) {
      double x0 = box.minX;
      double y0 = box.minY;
      double z0 = box.minZ;
      double x1 = box.maxX;
      double y1 = box.maxY;
      double z1 = box.maxZ;

      for (double y : new double[]{y0, y1}) {
         this.line(new Vec3(x0, y, z0), new Vec3(x1, y, z0), argb, width);
         this.line(new Vec3(x1, y, z0), new Vec3(x1, y, z1), argb, width);
         this.line(new Vec3(x1, y, z1), new Vec3(x0, y, z1), argb, width);
         this.line(new Vec3(x0, y, z1), new Vec3(x0, y, z0), argb, width);
      }

      this.line(new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), argb, width);
      this.line(new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), argb, width);
      this.line(new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), argb, width);
      this.line(new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), argb, width);
   }

   private double depth(double x, double y, double z) {
      return (x - this.eye.x) * this.forward.x() + (y - this.eye.y) * this.forward.y() + (z - this.eye.z) * this.forward.z();
   }
}
