package dev.hafnium.client.core.world;

import dev.hafnium.client.core.event.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorldModelTest {
    EventBus bus;
    List<WorldChangeEvent> events;
    WorldModel model;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        events = new ArrayList<>();
        for (Class<?> c : WorldChangeEvent.class.getPermittedSubclasses()) {
            subscribe(c);
        }
        model = new WorldModel(bus, new WorldModelLimits(2, 10, 3, 64));
    }

    @SuppressWarnings("unchecked")
    private <T> void subscribe(Class<T> c) {
        bus.subscribe(c, "t", e -> events.add((WorldChangeEvent) e));
    }

    static ObservedBlock block(int x, int y, int z, BlockKind kind) {
        return new ObservedBlock(Pos.pack(x, y, z), "minecraft:test", kind, kind == BlockKind.HAZARD ? HazardType.LAVA : null, false, 0);
    }

    static final ChunkTerrain FLAT = new ChunkTerrain(-61, -61, -61f, 0, 0, 0);

    @Test
    void posPacksNegativeAndExtremeCoordinates() {
        int[][] cases = {{0, 0, 0}, {-1, -64, -1}, {30_000_000, 319, -30_000_000}, {-33554432, -2048, 33554431}, {123, -7, -456}};
        for (int[] c : cases) {
            long p = Pos.pack(c[0], c[1], c[2]);
            assertArrayEquals(c, new int[]{Pos.x(p), Pos.y(p), Pos.z(p)});
        }
        long k = Pos.chunkKey(-5, 7);
        assertEquals(-5, Pos.chunkX(k));
        assertEquals(7, Pos.chunkZ(k));
        assertEquals(Pos.chunkKey(-1, -1), Pos.chunkOf(Pos.pack(-1, 5, -16)));
    }

    @Test
    void chunkObservationsAreQueryableNearestFirst() {
        DimensionModel d = model.enter("minecraft:overworld", 0);
        d.observeChunk(0, 0, FLAT, List.of(block(5, 10, 5, BlockKind.ORE), block(1, 10, 1, BlockKind.ORE), block(2, 10, 2, BlockKind.HAZARD)), 1);
        List<ObservedBlock> ores = d.findBlocks(BlockKind.ORE, 0, 10, 0, 32, 10, false);
        assertEquals(2, ores.size());
        assertEquals(1, ores.get(0).x());
        assertEquals(1, d.hazardsNear(0, 10, 0, 8).size());
        assertEquals(FLAT, d.terrain(0, 0));
        assertEquals(3, model.trackedBlockCount());
        assertThrows(IllegalArgumentException.class, () -> d.observeChunk(1, 1, FLAT, List.of(block(0, 0, 0, BlockKind.ORE)), 1));
    }

    @Test
    void blockChangesEmitEventsOnlyForRealChanges() {
        DimensionModel d = model.enter("minecraft:overworld", 0);
        d.observeChunk(0, 0, FLAT, List.of(), 1);
        events.clear();
        long p = Pos.pack(3, 3, 3);
        d.blockChanged(p, block(3, 3, 3, BlockKind.ORE));
        d.blockChanged(p, block(3, 3, 3, BlockKind.ORE));
        d.blockChanged(p, null);
        d.blockChanged(p, null);
        assertEquals(2, events.size());
        WorldChangeEvent.BlockChanged mined = (WorldChangeEvent.BlockChanged) events.get(1);
        assertNotNull(mined.before());
        assertNull(mined.after());
        assertEquals(0, model.trackedBlockCount());
        d.blockChanged(Pos.pack(40, 3, 40), block(40, 3, 40, BlockKind.ORE));
        assertNull(d.blockAt(40, 3, 40), "changes in never-scanned chunks are left to the scan");
    }

    @Test
    void unloadedChunksBecomeStaleAndAreEvictedOldestFirst() {
        DimensionModel d = model.enter("minecraft:overworld", 0);
        for (int i = 0; i < 4; i++) {
            d.observeChunk(i, 0, FLAT, List.of(block(i * 16, 0, 0, BlockKind.ORE)), i);
        }
        d.unloadChunk(0, 0, 10);
        assertFalse(d.isChunkLoaded(0, 0));
        assertNull(d.blockAt(0, 0, 0), "stale observations are excluded from live queries");
        assertEquals(1, d.findBlocks(BlockKind.ORE, 0, 0, 0, 8, 10, true).size(), "but still available when asked");
        d.unloadChunk(1, 0, 11);
        d.unloadChunk(2, 0, 12); // cache limit 2 -> chunk 0 evicted
        assertFalse(d.isChunkKnown(0, 0));
        assertTrue(d.isChunkKnown(1, 0));
        assertEquals(3, model.trackedBlockCount());
    }

    @Test
    void trackedBlockLimitEvictsCacheThenTruncates() {
        DimensionModel d = model.enter("minecraft:overworld", 0);
        List<ObservedBlock> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(block(i, 0, 0, BlockKind.ORE));
        }
        d.observeChunk(0, 0, FLAT, six, 1);
        d.unloadChunk(0, 0, 2);
        List<ObservedBlock> eight = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            eight.add(block(16 + i, 0, 0, BlockKind.ORE));
        }
        d.observeChunk(1, 0, FLAT, eight, 3);
        assertFalse(d.isChunkKnown(0, 0), "cached chunk evicted to make room");
        assertTrue(d.isChunkComplete(1, 0));
        List<ObservedBlock> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            five.add(block(32 + i, 0, 0, BlockKind.ORE));
        }
        d.observeChunk(2, 0, FLAT, five, 4);
        assertFalse(d.isChunkComplete(2, 0), "only 2 of 5 fit and the chunk is flagged incomplete");
        assertEquals(10, model.trackedBlockCount());
        assertEquals(1, model.truncationCount());
    }

    @Test
    void shrinkingLimitsAppliesImmediately() {
        model.setLimits(new WorldModelLimits(10, 100, 10, 64));
        DimensionModel d = model.enter("minecraft:overworld", 0);
        for (int i = 0; i < 6; i++) {
            d.observeChunk(i, 0, FLAT, List.of(block(i * 16, 0, 0, BlockKind.ORE)), i);
            d.unloadChunk(i, 0, i);
        }
        for (int i = 0; i < 6; i++) {
            d.seeEntity(i, UUID.randomUUID(), "minecraft:cow", EntityKind.PASSIVE, i);
        }
        model.setLimits(new WorldModelLimits(2, 100, 3, 64));
        assertEquals(2, d.chunkCount());
        assertEquals(3, d.entityCount());
    }

    @Test
    void entitiesAreCappedUpdatedInPlaceAndPruned() {
        DimensionModel d = model.enter("minecraft:overworld", 0);
        ObservedEntity a = d.seeEntity(1, UUID.randomUUID(), "minecraft:zombie", EntityKind.HOSTILE, 1);
        a.update(1, 2, 3, 0, 0, 0, 20f, 1);
        assertSame(a, d.seeEntity(1, a.uuid(), "minecraft:zombie", EntityKind.HOSTILE, 2));
        d.seeEntity(2, UUID.randomUUID(), "minecraft:cow", EntityKind.PASSIVE, 1).update(50, 0, 0, 0, 0, 0, 10f, 1);
        d.seeEntity(3, UUID.randomUUID(), "minecraft:cow", EntityKind.PASSIVE, 1).update(5, 0, 0, 0, 0, 0, 10f, 1);
        assertNull(d.seeEntity(4, UUID.randomUUID(), "minecraft:cow", EntityKind.PASSIVE, 1), "limit 3");
        assertEquals(1, d.droppedEntityCount());
        assertEquals(List.of(3, 2), d.entitiesNear(EntityKind.PASSIVE, 0, 0, 0, 100).stream().map(ObservedEntity::id).toList());
        a.update(1, 2, 3, 0, 0, 0, 20f, 5);
        d.pruneEntities(5);
        assertEquals(1, d.entityCount());
        assertNotNull(d.entity(1));
    }

    @Test
    void dimensionChangeClearsEntitiesAndStalesChunks() {
        DimensionModel over = model.enter("minecraft:overworld", 0);
        over.observeChunk(0, 0, FLAT, List.of(block(1, 1, 1, BlockKind.CONTAINER)), 1);
        over.seeEntity(1, UUID.randomUUID(), "minecraft:cow", EntityKind.PASSIVE, 1);
        events.clear();
        DimensionModel nether = model.enter("minecraft:the_nether", 2);
        assertNotSame(over, nether);
        assertEquals(0, over.entityCount());
        assertFalse(over.isChunkLoaded(0, 0));
        assertTrue(events.stream().anyMatch(e -> e instanceof WorldChangeEvent.EntityRemoved));
        assertTrue(events.stream().anyMatch(e -> e instanceof WorldChangeEvent.DimensionChanged dc
                && dc.previous().equals("minecraft:overworld")));
        model.clear();
        assertNull(model.current());
        assertNull(model.dimension("minecraft:overworld"));
        assertEquals(0, model.trackedBlockCount());
    }

    @Test
    void snapshotsAreDetachedCopies() {
        DimensionModel d = model.enter("minecraft:overworld", 0);
        d.observeChunk(0, 0, FLAT, List.of(block(1, 1, 1, BlockKind.ORE)), 1);
        ObservedEntity e = d.seeEntity(7, UUID.randomUUID(), "minecraft:pig", EntityKind.PASSIVE, 1);
        e.update(2, 1, 2, 0, 0, 0, 10f, 1);
        WorldSnapshot snap = d.snapshot(0, 0, 0, 32, false, 1);
        d.blockChanged(Pos.pack(1, 1, 1), null);
        e.update(9, 9, 9, 0, 0, 0, 1f, 2);
        assertEquals(1, snap.blocks().size());
        assertEquals(2.0, snap.entities().get(0).x());
        assertTrue(snap.isChunkLoaded(0, 0));
        assertEquals(FLAT, snap.terrain(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> snap.blocks().clear());
        assertThrows(UnsupportedOperationException.class, () -> snap.entities().clear());
    }

    @Test
    void scopesDetectInvalidation() {
        WorldScope s = WorldScope.chunks("minecraft:overworld", java.util.Set.of(Pos.chunkKey(2, 3)));
        assertTrue(s.invalidatedByChunkUnload("minecraft:overworld", 2, 3));
        assertFalse(s.invalidatedByChunkUnload("minecraft:the_nether", 2, 3));
        assertTrue(s.invalidatedByDimension("minecraft:the_nether"));
        assertFalse(WorldScope.NONE.invalidatedByDimension("x"));
    }
}
