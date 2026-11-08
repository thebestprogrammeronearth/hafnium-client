package dev.hafnium.client.core.world;

import java.util.Set;

/**
 * The part of the world a task depends on. When the player leaves {@code dimension}, or one of {@code chunks}
 * unloads, the task is invalidated (its current attempt fails and {@code Task.recover} decides what happens next).
 * {@link #NONE} means the task does not depend on the world (pure computation).
 */
public record WorldScope(String dimension, Set<Long> chunks) {
    public static final WorldScope NONE = new WorldScope(null, Set.of());

    public WorldScope {
        chunks = Set.copyOf(chunks);
    }

    public static WorldScope dimension(String dimension) {
        return new WorldScope(dimension, Set.of());
    }

    public static WorldScope chunks(String dimension, Set<Long> chunkKeys) {
        return new WorldScope(dimension, chunkKeys);
    }

    public boolean dependsOnWorld() {
        return dimension != null;
    }

    public boolean invalidatedByDimension(String newDimension) {
        return dimension != null && !dimension.equals(newDimension);
    }

    public boolean invalidatedByChunkUnload(String dim, int cx, int cz) {
        return dimension != null && dimension.equals(dim) && chunks.contains(Pos.chunkKey(cx, cz));
    }
}
