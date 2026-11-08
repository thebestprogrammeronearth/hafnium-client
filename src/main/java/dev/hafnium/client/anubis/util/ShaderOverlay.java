/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.anubis.util;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.gizmos.Gizmos.TemporaryCollection;
import net.minecraft.gizmos.SimpleGizmoCollector.GizmoInstance;
import net.minecraft.util.Util;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

public final class ShaderOverlay {
   private static final MethodHandle SHADER_PACK_IN_USE = irisProbe();
   private static final SimpleGizmoCollector COLLECTOR = new SimpleGizmoCollector();
   private static PerspectiveProjectionMatrixBuffer projectionBuffer;

   private ShaderOverlay() {
   }

   private static MethodHandle irisProbe() {
      if (!FabricLoader.getInstance().isModLoaded("iris")) {
         return null;
      } else {
         try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance);
         } catch (RuntimeException | LinkageError | ReflectiveOperationException var2) {
            return null;
         }
      }
   }

   public static boolean shaderPackInUse() {
      if (SHADER_PACK_IN_USE == null) {
         return false;
      } else {
         try {
            return (boolean)SHADER_PACK_IN_USE.invokeExact();
         } catch (Throwable var1) {
            return false;
         }
      }
   }

   /**
    * Collects the gizmos emitted while it is open; {@link #render} draws them at the end of the level render. Anubis
    * did this only with an Iris shader pack; Hafnium always does it, because gizmos emitted from the world-render hook
    * are otherwise not drawn in 1.21.11 (verified in-game).
    */
   public static TemporaryCollection capture() {
      {
         if (pending()) {
            COLLECTOR.drainGizmos();
         }

         return Gizmos.withCollector(COLLECTOR);
      }
   }

   public static boolean pending() {
      return !COLLECTOR.getGizmos().isEmpty();
   }

   public static void render(Camera camera, Matrix4f viewRotation, Matrix4f projection, GpuBufferSlice fog, GpuBufferSlice onTopFog) {
      if (pending()) {
         DrawableGizmoPrimitives standard = new DrawableGizmoPrimitives();
         DrawableGizmoPrimitives onTop = new DrawableGizmoPrimitives();
         long now = Util.getMillis();

         for (GizmoInstance instance : COLLECTOR.drainGizmos()) {
            instance.gizmo().emit(instance.isAlwaysOnTop() ? onTop : standard, instance.getAlphaMultiplier(now));
         }

         if (!standard.isEmpty() || !onTop.isEmpty()) {
            CameraRenderState state = new CameraRenderState();
            state.initialized = camera.isInitialized();
            state.pos = camera.position();
            state.blockPos = camera.blockPosition();
            state.orientation = new Quaternionf(camera.rotation());
            Minecraft mc = Minecraft.getInstance();
            if (projectionBuffer == null) {
               projectionBuffer = new PerspectiveProjectionMatrixBuffer("anubis overlay");
            }

            BufferSource buffers = mc.renderBuffers().bufferSource();
            PoseStack poseStack = new PoseStack();
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            GpuBufferSlice previousFog = RenderSystem.getShaderFog();
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
            modelView.pushMatrix();
            modelView.mul(viewRotation);

            try {
               if (!standard.isEmpty()) {
                  RenderSystem.setShaderFog(fog);
                  standard.render(poseStack, buffers, state, viewRotation);
                  buffers.endBatch();
               }

               if (!onTop.isEmpty()) {
                  RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(mc.getMainRenderTarget().getDepthTexture(), 1.0);
                  RenderSystem.setShaderFog(onTopFog);
                  onTop.render(poseStack, buffers, state, viewRotation);
                  buffers.endBatch();
               }
            } finally {
               modelView.popMatrix();
               RenderSystem.restoreProjectionMatrix();
               RenderSystem.setShaderFog(previousFog);
            }
         }
      }
   }
}
