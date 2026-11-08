/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.mixin.anubis;

import dev.hafnium.client.modules.render.FarmFpsModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public abstract class NoRenderLightTextureMixin {
    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float hafnium$hideDarknessPulse(float blendFactor) {
        return FarmFpsModule.hideBlindnessDarkness() ? 0.0F : blendFactor;
    }
}
