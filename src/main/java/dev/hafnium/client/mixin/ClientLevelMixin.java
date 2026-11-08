package dev.hafnium.client.mixin;

import dev.hafnium.client.HafniumClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds server-confirmed block updates (single and section updates both go through this method) to the world
 * observer. Client-side predictions are not observations and are not hooked.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
    private void hafnium$onServerBlock(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        HafniumClient.onServerBlockUpdate((ClientLevel) (Object) this, pos, state);
    }
}
