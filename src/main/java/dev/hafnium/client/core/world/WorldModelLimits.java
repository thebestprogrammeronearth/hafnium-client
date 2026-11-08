package dev.hafnium.client.core.world;

/**
 * Memory caps for the world model.
 *
 * @param maxCachedChunks  unloaded chunks kept for later queries (oldest evicted first)
 * @param maxTrackedBlocks tracked blocks across all dimensions; cached chunks are evicted to make room, after that new
 *                         observations are truncated and the chunk is flagged incomplete
 * @param maxEntities      entities tracked at once (per dimension)
 * @param entityRange      blocks around the player within which entities are observed
 */
public record WorldModelLimits(int maxCachedChunks, int maxTrackedBlocks, int maxEntities, int entityRange) {
    public static final WorldModelLimits DEFAULT = new WorldModelLimits(4096, 200_000, 512, 96);

    public WorldModelLimits {
        if (maxCachedChunks < 0 || maxTrackedBlocks < 0 || maxEntities < 0 || entityRange < 1) {
            throw new IllegalArgumentException("Invalid world model limits: " + maxCachedChunks + ", "
                    + maxTrackedBlocks + ", " + maxEntities + ", " + entityRange);
        }
    }

    /** Rough upper bound of heap used at these limits, for display. */
    public long estimatedBytes() {
        return (long) maxTrackedBlocks * 96 + (long) maxCachedChunks * 160 + (long) maxEntities * 160;
    }
}
