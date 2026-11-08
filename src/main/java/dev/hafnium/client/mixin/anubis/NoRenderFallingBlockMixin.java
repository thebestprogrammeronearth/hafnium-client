/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.mixin.anubis;

import dev.hafnium.client.modules.render.FarmFpsModule;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FallingBlockRenderer.class)
public abstract class NoRenderFallingBlockMixin {
    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/item/FallingBlockEntity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z", at = @At("HEAD"), cancellable = true)
    private void hafnium$cancelFallingBlock(FallingBlockEntity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (FarmFpsModule.hideFallingBlocks()) {
            cir.setReturnValue(false);
        }
    }
}
