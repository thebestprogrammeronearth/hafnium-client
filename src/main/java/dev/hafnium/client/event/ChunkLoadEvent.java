package dev.hafnium.client.event;

import dev.hafnium.client.core.event.EventBus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * A chunk the server sent was loaded into the client level (its blocks and block entities are in place). Stands in for
 * Anubis's "chunk packet applied" event. Shared instance; read during the handler.
 */
public final class ChunkLoadEvent {
    public ClientLevel level;
    public LevelChunk chunk;
    private static final ChunkLoadEvent INSTANCE = new ChunkLoadEvent();

    private ChunkLoadEvent() {
    }

    public int x() {
        return chunk.getPos().x;
    }

    public int z() {
        return chunk.getPos().z;
    }

    public static void dispatch(EventBus bus, ClientLevel level, LevelChunk chunk) {
        if (bus.hasSubscribers(ChunkLoadEvent.class)) {
            INSTANCE.level = level;
            INSTANCE.chunk = chunk;
            bus.post(INSTANCE);
            INSTANCE.level = null;
            INSTANCE.chunk = null;
        }
    }
}
