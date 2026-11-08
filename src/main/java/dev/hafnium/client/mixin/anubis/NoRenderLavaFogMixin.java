/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.mixin.anubis;

import dev.hafnium.client.modules.render.FarmFpsModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LavaFogEnvironment.class)
public abstract class NoRenderLavaFogMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void hafnium$clearLavaFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (FarmFpsModule.hideLavaFog()) {
            fog.environmentalStart = -8.0F;
            fog.environmentalEnd = renderDistance;
            fog.skyEnd = renderDistance;
            fog.cloudEnd = renderDistance;
        }
    }
}
