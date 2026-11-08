/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.mixin.anubis;

import dev.hafnium.client.modules.render.FarmFpsModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class NoRenderLevelRendererMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void hafnium$hideEntityFire(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState state = cir.getReturnValue();
        if (state != null && FarmFpsModule.hideEntityFire()) {
            state.displayFireAnimation = false;
        }
    }

    @Inject(method = "doesMobEffectBlockSky", at = @At("HEAD"), cancellable = true)
    private void hafnium$keepSkyDuringBlindnessDarkness(Camera camera, CallbackInfoReturnable<Boolean> cir) {
        if (FarmFpsModule.hideBlindnessDarkness()) {
            cir.setReturnValue(false);
        }
    }
}
