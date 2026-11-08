/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.mixin.anubis;

import com.llamalad7.mixinextras.sugar.Local;
import dev.hafnium.client.anubis.util.TracerEngine;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.event.WorldRenderEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Posts {@link WorldRenderEvent} where vanilla emits its debug gizmos, so modules can add Gizmos for this frame. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;emitGizmos(Lnet/minecraft/client/renderer/culling/Frustum;DDDF)V"),
            index = 0)
    private Frustum hafnium$onRenderLevel(Frustum frustum, @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation,
                                          @Local(argsOnly = true, ordinal = 1) Matrix4f projection) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        TracerEngine.beginFrame(viewRotation, projection);
        dev.hafnium.client.anubis.util.WorldQuads.beginFrame(Minecraft.getInstance().gameRenderer.getMainCamera().position());
        try (var capture = dev.hafnium.client.anubis.util.ShaderOverlay.capture()) {
            WorldRenderEvent.dispatch(HafniumCore.get().events(), partialTick, frustum);
        }
        return frustum;
    }

    /** Draws the quads (tracers, fogless lines) collected during {@link WorldRenderEvent} on top of the level. */
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void hafnium$afterLevel(CallbackInfo ci, @Local(argsOnly = true) com.mojang.blaze3d.buffers.GpuBufferSlice fog,
                                    @Local(argsOnly = true) net.minecraft.client.Camera camera,
                                    @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation,
                                    @Local(argsOnly = true, ordinal = 1) Matrix4f projection) {
        dev.hafnium.client.anubis.util.WorldQuads.render(viewRotation, projection);
        if (dev.hafnium.client.anubis.util.ShaderOverlay.pending()) {
            dev.hafnium.client.anubis.util.ShaderOverlay.render(camera, viewRotation, projection, fog, onTopFog(fog, camera));
        }
    }

    /** No fluid fog for always-on-top shapes when the camera is underwater/in lava (as Anubis). */
    private static com.mojang.blaze3d.buffers.GpuBufferSlice onTopFog(com.mojang.blaze3d.buffers.GpuBufferSlice fog, net.minecraft.client.Camera camera) {
        return camera.getFluidInCamera() == net.minecraft.world.level.material.FogType.NONE ? fog
                : ((AccessorGameRenderer) Minecraft.getInstance().gameRenderer).hafnium$getFogRenderer()
                        .getBuffer(net.minecraft.client.renderer.fog.FogRenderer.FogMode.NONE);
    }
}
