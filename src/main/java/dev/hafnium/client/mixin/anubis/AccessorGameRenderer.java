/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.mixin.anubis;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface AccessorGameRenderer {
    @Invoker("getFov")
    float hafnium$getFov(Camera camera, float partialTick, boolean useFovSetting);

    @Accessor("fogRenderer")
    FogRenderer hafnium$getFogRenderer();

    @Invoker("shouldRenderBlockOutline")
    boolean hafnium$shouldRenderBlockOutline();
}
