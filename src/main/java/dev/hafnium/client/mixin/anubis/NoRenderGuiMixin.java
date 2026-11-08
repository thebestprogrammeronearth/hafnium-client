/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.mixin.anubis;

import dev.hafnium.client.modules.render.FarmFpsModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class NoRenderGuiMixin {
    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void hafnium$cancelTextureOverlay(GuiGraphics graphics, Identifier sprite, float alpha, CallbackInfo ci) {
        if (FarmFpsModule.hidePumpkinPowderSnow()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void hafnium$cancelPortalOverlay(GuiGraphics graphics, float alpha, CallbackInfo ci) {
        if (FarmFpsModule.hideNauseaPortal()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void hafnium$cancelConfusionOverlay(GuiGraphics graphics, float alpha, CallbackInfo ci) {
        if (FarmFpsModule.hideNauseaPortal()) {
            ci.cancel();
        }
    }
}
