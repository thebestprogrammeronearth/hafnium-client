/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.mixin.anubis;

import dev.hafnium.client.modules.render.FarmFpsModule;
import net.minecraft.client.renderer.blockentity.TrialSpawnerRenderer;
import net.minecraft.client.renderer.blockentity.state.SpawnerRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrialSpawnerRenderer.class)
public abstract class NoRenderSpawnerMobMixin {
    @Inject(method = "extractSpawnerData", at = @At("HEAD"), cancellable = true)
    private static void hafnium$cancelSpawnerMobExtract(SpawnerRenderState state, float partialTick, Entity displayEntity,
            EntityRenderDispatcher dispatcher, double oSpin, double spin, CallbackInfo ci) {
        if (FarmFpsModule.hideSpawnerMob()) {
            ci.cancel();
        }
    }
}
