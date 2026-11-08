package dev.hafnium.client.core.world;

/** Changes to the world model, posted on the event bus (client thread). */
public sealed interface WorldChangeEvent {
    String dimension();

    record ChunkObserved(String dimension, int chunkX, int chunkZ, int trackedBlocks) implements WorldChangeEvent {
    }

    record ChunkUnloaded(String dimension, int chunkX, int chunkZ) implements WorldChangeEvent {
    }

    /** A tracked block appeared, changed or disappeared ({@code before}/{@code after} null when absent). */
    record BlockChanged(String dimension, long pos, ObservedBlock before, ObservedBlock after) implements WorldChangeEvent {
    }

    record EntityAppeared(String dimension, ObservedEntity entity) implements WorldChangeEvent {
    }

    record EntityRemoved(String dimension, int entityId, String typeId, EntityKind kind) implements WorldChangeEvent {
    }

    record DimensionChanged(String dimension, String previous) implements WorldChangeEvent {
    }

    record Cleared(String dimension) implements WorldChangeEvent {
    }
}
