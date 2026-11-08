/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.mixin.anubis;

import dev.hafnium.client.modules.render.FarmFpsModule;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.WeatherRenderState;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public abstract class NoRenderWeatherMixin {
    @Inject(method = "render(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/WeatherRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void hafnium$hideWeather(MultiBufferSource bufferSource, Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
        if (FarmFpsModule.hideWeather()) {
            ci.cancel();
        }
    }

    @Inject(method = "tickRainParticles", at = @At("HEAD"), cancellable = true)
    private void hafnium$hideRainSplashes(ClientLevel level, Camera camera, int ticks, ParticleStatus particles, int radius, CallbackInfo ci) {
        if (FarmFpsModule.hideWeather()) {
            ci.cancel();
        }
    }
}
