package dev.hafnium.client.core.world;

/**
 * Summary of a chunk's surface from its heightmap.
 *
 * @param minSurfaceY   lowest top-block Y among the 256 columns
 * @param maxSurfaceY   highest top-block Y
 * @param meanSurfaceY  average top-block Y
 * @param waterColumns  columns whose top block is water
 * @param lavaColumns   columns whose top block is lava
 * @param voidColumns   columns with no solid block at all (open to the void)
 */
public record ChunkTerrain(int minSurfaceY, int maxSurfaceY, float meanSurfaceY, int waterColumns, int lavaColumns,
                           int voidColumns) {
    public int relief() {
        return maxSurfaceY - minSurfaceY;
    }
}
