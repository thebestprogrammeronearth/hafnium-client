/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.event;

import dev.hafnium.client.core.event.EventBus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A block in the client level changed (server update or local prediction). Shared instance; read during the handler. */
public final class BlockUpdateEvent {
    public BlockPos pos;
    public BlockState oldState;
    public BlockState newState;
    private static final BlockUpdateEvent INSTANCE = new BlockUpdateEvent();

    private BlockUpdateEvent() {
    }

    public static void dispatch(EventBus bus, BlockPos pos, BlockState oldState, BlockState newState) {
        if (bus.hasSubscribers(BlockUpdateEvent.class)) {
            INSTANCE.pos = pos;
            INSTANCE.oldState = oldState;
            INSTANCE.newState = newState;
            bus.post(INSTANCE);
            INSTANCE.pos = null;
            INSTANCE.oldState = null;
            INSTANCE.newState = null;
        }
    }
}
