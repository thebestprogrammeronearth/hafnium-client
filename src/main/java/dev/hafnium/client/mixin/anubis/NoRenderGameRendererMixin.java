/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.mixin.anubis;

import dev.hafnium.client.modules.render.FarmFpsModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class NoRenderGameRendererMixin {
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void hafnium$cancelTotemAnimation(ItemStack stack, CallbackInfo ci) {
        if (FarmFpsModule.hideTotemAnimation()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
    private float hafnium$cancelNauseaWobble(float portal, float nausea, Operation<Float> original) {
        return FarmFpsModule.hideNauseaPortal() ? 0.0F : original.call(portal, nausea);
    }
}
