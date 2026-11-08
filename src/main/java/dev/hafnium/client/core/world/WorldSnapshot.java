package dev.hafnium.client.core.world;

import dev.hafnium.client.core.ImmutableSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable copy of a region of the world model, safe to hand to background workers. Built on the client thread by
 * {@link DimensionModel#snapshot}. Holds copies only; later world changes do not affect it.
 */
public final class WorldSnapshot implements ImmutableSnapshot {
    private final String dimension;
    private final long tick;
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double radius;
    private final List<ObservedBlock> blocks;
    private final Map<Long, ChunkTerrain> terrain;
    private final Set<Long> loadedChunks;
    private final Set<Long> incompleteChunks;
    private final List<EntitySnapshot> entities;

    WorldSnapshot(String dimension, long tick, double cx, double cy, double cz, double radius, List<ObservedBlock> blocks,
                  Map<Long, ChunkTerrain> terrain, Set<Long> loadedChunks, Set<Long> incompleteChunks,
                  List<EntitySnapshot> entities) {
        this.dimension = dimension;
        this.tick = tick;
        this.centerX = cx;
        this.centerY = cy;
        this.centerZ = cz;
        this.radius = radius;
        this.blocks = List.copyOf(blocks);
        this.terrain = Map.copyOf(terrain);
        this.loadedChunks = Set.copyOf(loadedChunks);
        this.incompleteChunks = Set.copyOf(incompleteChunks);
        this.entities = List.copyOf(entities);
    }

    public String dimension() {
        return dimension;
    }

    public long tick() {
        return tick;
    }

    public double radius() {
        return radius;
    }

    public double centerX() {
        return centerX;
    }

    public double centerY() {
        return centerY;
    }

    public double centerZ() {
        return centerZ;
    }

    public List<ObservedBlock> blocks() {
        return blocks;
    }

    public List<ObservedBlock> blocks(BlockKind kind) {
        List<ObservedBlock> out = new ArrayList<>();
        for (ObservedBlock b : blocks) {
            if (b.kind() == kind) {
                out.add(b);
            }
        }
        return out;
    }

    public ObservedBlock nearest(BlockKind kind, double x, double y, double z) {
        return blocks.stream().filter(b -> b.kind() == kind)
                .min(Comparator.comparingDouble(b -> Pos.distSq(b.pos(), x, y, z))).orElse(null);
    }

    public ChunkTerrain terrain(int cx, int cz) {
        return terrain.get(Pos.chunkKey(cx, cz));
    }

    public boolean isChunkLoaded(int cx, int cz) {
        return loadedChunks.contains(Pos.chunkKey(cx, cz));
    }

    /** Chunks whose tracked blocks were truncated by the memory limit. */
    public boolean isChunkComplete(int cx, int cz) {
        return !incompleteChunks.contains(Pos.chunkKey(cx, cz));
    }

    public List<EntitySnapshot> entities() {
        return entities;
    }

    public List<EntitySnapshot> entities(EntityKind kind) {
        List<EntitySnapshot> out = new ArrayList<>();
        for (EntitySnapshot e : entities) {
            if (e.kind() == kind) {
                out.add(e);
            }
        }
        return out;
    }
}
