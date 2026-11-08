package dev.hafnium.client.mixin;

import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.event.GameKeyEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Posts {@link GameKeyEvent} for key presses made while playing (no screen open), used for module keybinds. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void hafnium$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (action == GLFW.GLFW_PRESS && mc.screen == null && window == mc.getWindow().handle()) {
            HafniumCore.get().events().post(new GameKeyEvent(event.key(), event.modifiers()));
        }
    }
}
