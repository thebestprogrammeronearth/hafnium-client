package dev.hafnium.client.schematic;

import net.minecraft.world.level.block.state.BlockState;

/** A loaded structure: non-air blocks with positions relative to its minimum corner. Immutable. */
public record Schematic(String name, int sizeX, int sizeY, int sizeZ, int[] xs, int[] ys, int[] zs, BlockState[] states) {
    public int blocks() {
        return states.length;
    }
}
