package dev.hafnium.client.core.world;

import dev.hafnium.client.core.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured memory of what the client has observed, per dimension. Fed by the game adapter from data the client
 * legitimately receives (loaded chunks, server-confirmed block updates, visible entities); it never requests or
 * infers data the server has not sent.
 *
 * <p>Memory is bounded by {@link WorldModelLimits}, which can be changed at runtime. Confined to the client thread;
 * background work uses {@link DimensionModel#snapshot}. Changes are posted as {@link WorldChangeEvent}s.
 */
public final class WorldModel {
    private static final Logger LOG = LoggerFactory.getLogger("Hafnium/World");

    private final EventBus bus;
    private final Map<String, DimensionModel> dimensions = new HashMap<>();
    private volatile WorldModelLimits limits;
    private DimensionModel current;
    private int trackedBlocks;
    private long truncations;

    public WorldModel(EventBus bus, WorldModelLimits limits) {
        this.bus = bus;
        this.limits = Objects.requireNonNull(limits);
    }

    public WorldModelLimits limits() {
        return limits;
    }

    /** Applies new limits, evicting cached data immediately if they shrank. */
    public void setLimits(WorldModelLimits newLimits) {
        this.limits = Objects.requireNonNull(newLimits);
        enforceCacheLimit();
        if (trackedBlocks > newLimits.maxTrackedBlocks()) {
            makeRoom(0);
        }
        for (DimensionModel d : dimensions.values()) {
            d.enforceEntityLimit();
        }
    }

    /** The dimension the player is in, or null when not in a world. */
    public DimensionModel current() {
        return current;
    }

    public DimensionModel dimension(String id) {
        return dimensions.get(id);
    }

    /** Switches to {@code dimensionId}; the previous dimension's observations become cached (stale). */
    public DimensionModel enter(String dimensionId, long tick) {
        if (current != null && current.id().equals(dimensionId)) {
            return current;
        }
        String previous = null;
        if (current != null) {
            previous = current.id();
            current.clearEntities();
            current.unloadAll(tick);
        }
        current = dimensions.computeIfAbsent(dimensionId, id -> new DimensionModel(id, this));
        post(new WorldChangeEvent.DimensionChanged(dimensionId, previous));
        enforceCacheLimit();
        return current;
    }

    /** Forgets everything (disconnect). */
    public void clear() {
        String id = current == null ? "" : current.id();
        if (current != null) {
            current.clearEntities();
        }
        dimensions.clear();
        current = null;
        trackedBlocks = 0;
        post(new WorldChangeEvent.Cleared(id));
    }

    public int trackedBlockCount() {
        return trackedBlocks;
    }

    /** How many times observations were truncated because of the tracked-block limit. */
    public long truncationCount() {
        return truncations;
    }

    // ------------------------------------------------------------------ limit bookkeeping (package)

    void post(WorldChangeEvent e) {
        if (bus != null) {
            bus.post(e);
        }
    }

    void adjustTracked(int delta) {
        trackedBlocks += delta;
    }

    /**
     * Frees space for {@code wanted} more tracked blocks by evicting the oldest cached chunks.
     *
     * @return how many may actually be stored (less than {@code wanted} if even eviction cannot make room)
     */
    int makeRoom(int wanted) {
        int max = limits.maxTrackedBlocks();
        if (trackedBlocks + wanted <= max) {
            return wanted;
        }
        List<DimensionModel.ChunkRecord> cached = cachedOldestFirst();
        for (DimensionModel.ChunkRecord rec : cached) {
            if (trackedBlocks + wanted <= max) {
                break;
            }
            evict(rec);
        }
        int room = Math.max(0, Math.min(wanted, max - trackedBlocks));
        if (room < wanted) {
            truncations++;
            if (truncations == 1 || truncations % 100 == 0) {
                LOG.warn("World model tracked-block limit ({}) reached; observations are being truncated", max);
            }
        }
        return room;
    }

    void enforceCacheLimit() {
        int cached = 0;
        for (DimensionModel d : dimensions.values()) {
            cached += d.cachedChunkCount();
        }
        int excess = cached - limits.maxCachedChunks();
        if (excess <= 0) {
            return;
        }
        List<DimensionModel.ChunkRecord> oldest = cachedOldestFirst();
        for (int i = 0; i < excess && i < oldest.size(); i++) {
            evict(oldest.get(i));
        }
    }

    private List<DimensionModel.ChunkRecord> cachedOldestFirst() {
        List<DimensionModel.ChunkRecord> cached = new ArrayList<>();
        for (DimensionModel d : dimensions.values()) {
            d.collectCached(cached);
        }
        cached.sort(Comparator.comparingLong(r -> r.unloadedTick));
        return cached;
    }

    private void evict(DimensionModel.ChunkRecord rec) {
        trackedBlocks -= rec.owner.evict(rec);
    }
}
