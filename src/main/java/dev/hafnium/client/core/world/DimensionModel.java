package dev.hafnium.client.core.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything observed in one dimension: per-chunk terrain and tracked blocks, plus visible entities.
 *
 * <p>Unloaded chunks stay cached (flagged not loaded, excluded from queries unless asked for) within the
 * {@link WorldModelLimits}. Confined to the client thread; hand {@link #snapshot} results to other threads.
 */
public final class DimensionModel {
    private final String id;
    private final WorldModel world;
    private final Long2ObjectOpenHashMap<ChunkRecord> chunks = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<ObservedEntity> entities = new Int2ObjectOpenHashMap<>();
    private final Collection<ObservedEntity> entitiesView = Collections.unmodifiableCollection(entities.values());
    private int loadedChunks;
    private int droppedEntities;

    static final class ChunkRecord {
        final DimensionModel owner;
        final int cx;
        final int cz;
        final Long2ObjectOpenHashMap<ObservedBlock> blocks = new Long2ObjectOpenHashMap<>();
        ChunkTerrain terrain;
        boolean loaded;
        boolean complete = true;
        long observedTick;
        long unloadedTick;

        ChunkRecord(DimensionModel owner, int cx, int cz) {
            this.owner = owner;
            this.cx = cx;
            this.cz = cz;
        }
    }

    DimensionModel(String id, WorldModel world) {
        this.id = id;
        this.world = world;
    }

    public String id() {
        return id;
    }

    // ------------------------------------------------------------------ updates (from the observer)

    /** Replaces everything known about a chunk with a fresh full scan. */
    public void observeChunk(int cx, int cz, ChunkTerrain terrain, Collection<ObservedBlock> tracked, long tick) {
        long key = Pos.chunkKey(cx, cz);
        ChunkRecord rec = chunks.get(key);
        if (rec == null) {
            rec = new ChunkRecord(this, cx, cz);
            chunks.put(key, rec);
        }
        if (!rec.loaded) {
            rec.loaded = true;
            loadedChunks++;
        }
        world.adjustTracked(-rec.blocks.size());
        rec.blocks.clear();
        int room = world.makeRoom(tracked.size());
        rec.complete = room >= tracked.size();
        int stored = 0;
        for (ObservedBlock b : tracked) {
            if (Pos.chunkOf(b.pos()) != key) {
                throw new IllegalArgumentException("Block " + Pos.toString(b.pos()) + " is outside chunk " + cx + "," + cz);
            }
            if (stored >= room) {
                break;
            }
            if (rec.blocks.put(b.pos(), b) == null) {
                stored++;
            }
        }
        world.adjustTracked(rec.blocks.size());
        rec.terrain = terrain;
        rec.observedTick = tick;
        world.post(new WorldChangeEvent.ChunkObserved(id, cx, cz, rec.blocks.size()));
    }

    /** The client unloaded a chunk: its observations become cached (stale) and stop appearing in default queries. */
    public void unloadChunk(int cx, int cz, long tick) {
        ChunkRecord rec = chunks.get(Pos.chunkKey(cx, cz));
        if (rec == null || !rec.loaded) {
            return;
        }
        rec.loaded = false;
        rec.unloadedTick = tick;
        loadedChunks--;
        world.post(new WorldChangeEvent.ChunkUnloaded(id, cx, cz));
        world.enforceCacheLimit();
    }

    /**
     * A single server-confirmed block change. {@code observed} is the tracked classification of the new block, or
     * null if it is not tracked (e.g. ore mined to air). Ignored for chunks not currently loaded/scanned.
     */
    public void blockChanged(long pos, ObservedBlock observed) {
        ChunkRecord rec = chunks.get(Pos.chunkOf(pos));
        if (rec == null || !rec.loaded) {
            return;
        }
        ObservedBlock before = rec.blocks.get(pos);
        if (observed == null) {
            if (before == null) {
                return;
            }
            rec.blocks.remove(pos);
            world.adjustTracked(-1);
        } else {
            if (before != null && before.sameAs(observed)) {
                return;
            }
            if (before == null) {
                if (world.makeRoom(1) < 1) {
                    rec.complete = false;
                    return;
                }
                world.adjustTracked(1);
            }
            rec.blocks.put(pos, observed);
        }
        world.post(new WorldChangeEvent.BlockChanged(id, pos, before, observed));
    }

    /**
     * Returns the record for an entity, creating (and announcing) it the first time it is seen. Returns null when the
     * entity limit is reached; such entities are simply not tracked.
     */
    public ObservedEntity seeEntity(int entityId, UUID uuid, String typeId, EntityKind kind, long tick) {
        ObservedEntity e = entities.get(entityId);
        if (e == null) {
            if (entities.size() >= world.limits().maxEntities()) {
                droppedEntities++;
                return null;
            }
            e = new ObservedEntity(entityId, uuid, typeId, kind, tick);
            entities.put(entityId, e);
            world.post(new WorldChangeEvent.EntityAppeared(id, e));
        }
        return e;
    }

    /** Drops entities not seen since {@code tick} (removed, or out of observation range). */
    public void pruneEntities(long tick) {
        ObjectIterator<ObservedEntity> it = entities.values().iterator();
        while (it.hasNext()) {
            ObservedEntity e = it.next();
            if (e.lastSeenTick < tick) {
                it.remove();
                world.post(new WorldChangeEvent.EntityRemoved(id, e.id(), e.typeId(), e.kind()));
            }
        }
    }

    void clearEntities() {
        for (ObservedEntity e : entities.values()) {
            world.post(new WorldChangeEvent.EntityRemoved(id, e.id(), e.typeId(), e.kind()));
        }
        entities.clear();
    }

    /** The client dropped all chunks of this dimension (e.g. a new level object); observations become cached. */
    public void unloadAllChunks(long tick) {
        for (ChunkRecord rec : chunks.values()) {
            if (rec.loaded) {
                world.post(new WorldChangeEvent.ChunkUnloaded(id, rec.cx, rec.cz));
            }
        }
        unloadAll(tick);
        world.enforceCacheLimit();
    }

    /** Leaving the dimension: everything becomes cached/stale. */
    void unloadAll(long tick) {
        for (ChunkRecord rec : chunks.values()) {
            if (rec.loaded) {
                rec.loaded = false;
                rec.unloadedTick = tick;
            }
        }
        loadedChunks = 0;
    }

    /** Adds this dimension's unloaded chunks to {@code out} (eviction candidates). */
    void collectCached(List<ChunkRecord> out) {
        for (ChunkRecord rec : chunks.values()) {
            if (!rec.loaded) {
                out.add(rec);
            }
        }
    }

    /** @return number of tracked blocks freed */
    int evict(ChunkRecord rec) {
        if (chunks.remove(Pos.chunkKey(rec.cx, rec.cz)) == null) {
            return 0;
        }
        return rec.blocks.size();
    }

    int cachedChunkCount() {
        return chunks.size() - loadedChunks;
    }

    /** Entities beyond the limit are dropped; trims existing ones after the limit was lowered. */
    void enforceEntityLimit() {
        int max = world.limits().maxEntities();
        if (entities.size() <= max) {
            return;
        }
        List<ObservedEntity> all = new ArrayList<>(entities.values());
        all.sort(Comparator.comparingLong(ObservedEntity::firstSeenTick).reversed());
        for (int i = max; i < all.size(); i++) {
            ObservedEntity e = all.get(i);
            entities.remove(e.id());
            world.post(new WorldChangeEvent.EntityRemoved(id, e.id(), e.typeId(), e.kind()));
        }
    }

    /** Keys of chunks currently marked loaded (copied, so callers may unload while iterating the result). */
    public long[] loadedChunkKeys() {
        long[] out = new long[loadedChunks];
        int i = 0;
        for (ChunkRecord rec : chunks.values()) {
            if (rec.loaded && i < out.length) {
                out[i++] = Pos.chunkKey(rec.cx, rec.cz);
            }
        }
        return i == out.length ? out : java.util.Arrays.copyOf(out, i);
    }

    // ------------------------------------------------------------------ queries (client thread)

    public boolean isChunkLoaded(int cx, int cz) {
        ChunkRecord r = chunks.get(Pos.chunkKey(cx, cz));
        return r != null && r.loaded;
    }

    public boolean isChunkKnown(int cx, int cz) {
        return chunks.containsKey(Pos.chunkKey(cx, cz));
    }

    /** False when the tracked-block limit truncated this chunk's observations. */
    public boolean isChunkComplete(int cx, int cz) {
        ChunkRecord r = chunks.get(Pos.chunkKey(cx, cz));
        return r != null && r.complete;
    }

    /** Terrain of a chunk (possibly cached from an earlier visit), or null if never observed. */
    public ChunkTerrain terrain(int cx, int cz) {
        ChunkRecord r = chunks.get(Pos.chunkKey(cx, cz));
        return r == null ? null : r.terrain;
    }

    /** Tracked block at a position in a loaded chunk, or null. */
    public ObservedBlock blockAt(int x, int y, int z) {
        long p = Pos.pack(x, y, z);
        ChunkRecord r = chunks.get(Pos.chunkOf(p));
        return r == null || !r.loaded ? null : r.blocks.get(p);
    }

    /**
     * Tracked blocks of {@code kind} (null = any) within {@code radius}, nearest first, at most {@code limit}.
     * Cached observations from unloaded chunks are included only if {@code includeCached}.
     */
    public List<ObservedBlock> findBlocks(BlockKind kind, double x, double y, double z, double radius, int limit,
                                          boolean includeCached) {
        List<ObservedBlock> out = new ArrayList<>();
        forChunksInRadius(x, z, radius, includeCached, rec -> {
            for (ObservedBlock b : rec.blocks.values()) {
                if ((kind == null || b.kind() == kind) && Pos.distSq(b.pos(), x, y, z) <= radius * radius) {
                    out.add(b);
                }
            }
        });
        out.sort(Comparator.comparingDouble(b -> Pos.distSq(b.pos(), x, y, z)));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    public List<ObservedBlock> hazardsNear(double x, double y, double z, double radius) {
        return findBlocks(BlockKind.HAZARD, x, y, z, radius, Integer.MAX_VALUE, false);
    }

    public ObservedEntity entity(int entityId) {
        return entities.get(entityId);
    }

    public Collection<ObservedEntity> entities() {
        return entitiesView;
    }

    /** Entities of {@code kind} (null = any) within {@code radius}, nearest first. */
    public List<ObservedEntity> entitiesNear(EntityKind kind, double x, double y, double z, double radius) {
        double r2 = radius * radius;
        List<ObservedEntity> out = new ArrayList<>();
        for (ObservedEntity e : entities.values()) {
            if ((kind == null || e.kind() == kind) && e.distSq(x, y, z) <= r2) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparingDouble(e -> e.distSq(x, y, z)));
        return out;
    }

    /** Immutable copy of everything within {@code radius} of a point, for background workers. */
    public WorldSnapshot snapshot(double x, double y, double z, double radius, boolean includeCached, long tick) {
        List<ObservedBlock> blocks = new ArrayList<>();
        Map<Long, ChunkTerrain> terrain = new HashMap<>();
        Set<Long> loaded = new HashSet<>();
        Set<Long> incomplete = new HashSet<>();
        double r2 = radius * radius;
        forChunksInRadius(x, z, radius, includeCached, rec -> {
            long key = Pos.chunkKey(rec.cx, rec.cz);
            if (rec.terrain != null) {
                terrain.put(key, rec.terrain);
            }
            if (rec.loaded) {
                loaded.add(key);
            }
            if (!rec.complete) {
                incomplete.add(key);
            }
            for (ObservedBlock b : rec.blocks.values()) {
                if (Pos.distSq(b.pos(), x, y, z) <= r2) {
                    blocks.add(b);
                }
            }
        });
        List<EntitySnapshot> ents = new ArrayList<>();
        for (ObservedEntity e : entities.values()) {
            if (e.distSq(x, y, z) <= r2) {
                ents.add(EntitySnapshot.of(e));
            }
        }
        return new WorldSnapshot(id, tick, x, y, z, radius, blocks, terrain, loaded, incomplete, ents);
    }

    private void forChunksInRadius(double x, double z, double radius, boolean includeCached,
                                   java.util.function.Consumer<ChunkRecord> action) {
        int minCx = (int) Math.floor((x - radius) / 16);
        int maxCx = (int) Math.floor((x + radius) / 16);
        int minCz = (int) Math.floor((z - radius) / 16);
        int maxCz = (int) Math.floor((z + radius) / 16);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                ChunkRecord rec = chunks.get(Pos.chunkKey(cx, cz));
                if (rec != null && (rec.loaded || includeCached)) {
                    action.accept(rec);
                }
            }
        }
    }

    // ------------------------------------------------------------------ stats

    public int chunkCount() {
        return chunks.size();
    }

    public int loadedChunkCount() {
        return loadedChunks;
    }

    public int trackedBlockCount() {
        int n = 0;
        for (ChunkRecord r : chunks.values()) {
            n += r.blocks.size();
        }
        return n;
    }

    public int entityCount() {
        return entities.size();
    }

    /** Entities not tracked because the entity limit was reached. */
    public int droppedEntityCount() {
        return droppedEntities;
    }
}
