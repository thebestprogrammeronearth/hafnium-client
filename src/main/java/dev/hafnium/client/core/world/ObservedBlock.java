package dev.hafnium.client.core.world;

/**
 * A tracked block as last seen by the client.
 *
 * @param pos    packed position ({@link Pos})
 * @param hazard set when {@code kind == HAZARD}
 * @param mature for crops: fully grown
 * @param tick   observation tick
 */
public record ObservedBlock(long pos, String blockId, BlockKind kind, HazardType hazard, boolean mature, long tick) {
    public int x() {
        return Pos.x(pos);
    }

    public int y() {
        return Pos.y(pos);
    }

    public int z() {
        return Pos.z(pos);
    }

    /** Same block, same state class: used to skip redundant change events. */
    public boolean sameAs(ObservedBlock o) {
        return o != null && o.pos == pos && o.kind == kind && o.hazard == hazard && o.mature == mature
                && o.blockId.equals(blockId);
    }
}
