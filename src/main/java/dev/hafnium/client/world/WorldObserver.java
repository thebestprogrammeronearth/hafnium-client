package dev.hafnium.client.world;

import dev.hafnium.client.core.world.ChunkTerrain;
import dev.hafnium.client.core.world.DimensionModel;
import dev.hafnium.client.core.world.EntityKind;
import dev.hafnium.client.core.world.ObservedBlock;
import dev.hafnium.client.core.world.ObservedEntity;
import dev.hafnium.client.core.world.Pos;
import dev.hafnium.client.core.world.WorldModel;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;

/**
 * Feeds the {@link WorldModel} from what the client has received. Runs on the client thread:
 * <ul>
 *   <li>Loaded chunks are queued and scanned within a per-tick time budget. Sections whose palette contains no tracked
 *       state are skipped without reading blocks.</li>
 *   <li>Server-confirmed block updates (ClientLevelMixin) update single positions.</li>
 *   <li>Entities within the configured range are refreshed every tick, updating records in place.</li>
 *   <li>Unloads, dimension changes and level changes invalidate the matching observations.</li>
 * </ul>
 */
public final class WorldObserver {
    /** Max time spent scanning chunks per tick. */
    private static final long SCAN_BUDGET_NANOS = 1_500_000L;
    /**
     * How often loaded chunks are cross-checked against the client chunk cache. Needed because the client can drop
     * chunks without any unload callback: after a teleport the cache re-centres and the server's "forget chunk"
     * packets for the old area fall outside the cache range, so no CHUNK_UNLOAD event fires for them.
     */
    private static final int RECONCILE_INTERVAL = 10;

    private final WorldModel model;
    private final BlockClassifier classifier = new BlockClassifier();
    private final LongArrayFIFOQueue scanQueue = new LongArrayFIFOQueue();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    private final Reference2ObjectOpenHashMap<EntityType<?>, String> entityIds = new Reference2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final List<ObservedBlock> scratch = new ArrayList<>();
    private ClientLevel level;
    private ResourceKey<Level> dimension;
    private long tick;
    private long chunksScanned;
    private long silentUnloads;
    private long scanNanos;
    private long maxTickScanNanos;

    public WorldObserver(WorldModel model) {
        this.model = model;
    }

    public long chunksScanned() {
        return chunksScanned;
    }

    /** Average time to scan one chunk, in microseconds. */
    public double averageScanMicros() {
        return chunksScanned == 0 ? 0 : scanNanos / 1000.0 / chunksScanned;
    }

    /** Longest time spent scanning in a single tick, in milliseconds. */
    public double maxTickScanMillis() {
        return maxTickScanNanos / 1e6;
    }

    public int pendingScans() {
        return queued.size();
    }

    // ------------------------------------------------------------------ lifecycle hooks

    public void onJoin() {
        classifier.reset();
        reset();
    }

    public void onDisconnect() {
        reset();
        model.clear();
        level = null;
        dimension = null;
    }

    private void reset() {
        scanQueue.clear();
        queued.clear();
    }

    public void onChunkLoad(ClientLevel lvl, LevelChunk chunk) {
        if (lvl != level) {
            return; // picked up when the level switch is noticed in tick()
        }
        long key = Pos.chunkKey(chunk.getPos().x, chunk.getPos().z);
        if (queued.add(key)) {
            scanQueue.enqueue(key);
        }
    }

    public void onChunkUnload(ClientLevel lvl, LevelChunk chunk) {
        if (lvl != level || model.current() == null) {
            return;
        }
        long key = Pos.chunkKey(chunk.getPos().x, chunk.getPos().z);
        queued.remove(key); // stale queue entries are skipped when dequeued
        model.current().unloadChunk(chunk.getPos().x, chunk.getPos().z, tick);
    }

    /** From ClientLevelMixin: a server-confirmed block state at {@code pos}. */
    public void onBlockChanged(ClientLevel lvl, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (lvl != level || model.current() == null) {
            return;
        }
        BlockClassifier.Kind k = classifier.classify(state);
        long p = Pos.pack(pos.getX(), pos.getY(), pos.getZ());
        model.current().blockChanged(p, k.tracked() ? new ObservedBlock(p, k.blockId(), k.kind(), k.hazard(), k.mature(), tick) : null);
    }

    // ------------------------------------------------------------------ per tick

    public void tick(Minecraft mc) {
        tick++;
        ClientLevel lvl = mc.level;
        if (lvl == null || mc.player == null) {
            return;
        }
        if (lvl != level) {
            switchLevel(lvl);
        }
        if (tick % RECONCILE_INTERVAL == 0) {
            reconcileLoadedChunks(lvl);
        }
        scanChunks(lvl);
        observeEntities(mc, lvl);
    }

    private void reconcileLoadedChunks(ClientLevel lvl) {
        DimensionModel dim = model.current();
        for (long key : dim.loadedChunkKeys()) {
            int cx = Pos.chunkX(key);
            int cz = Pos.chunkZ(key);
            if (lvl.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) == null) {
                dim.unloadChunk(cx, cz, tick);
                silentUnloads++;
            }
        }
    }

    /** Chunks found missing by reconciliation (dropped by the client without an unload callback). */
    public long silentUnloads() {
        return silentUnloads;
    }

    private void switchLevel(ClientLevel lvl) {
        ResourceKey<Level> dim = lvl.dimension();
        if (level != null && dim == dimension) {
            // Same dimension, new level object (e.g. respawn): everything must be re-observed.
            model.current().unloadAllChunks(tick);
        }
        level = lvl;
        dimension = dim;
        model.enter(dim.identifier().toString(), tick);
        reset();
        // Queue every chunk already loaded around the player.
        int view = Minecraft.getInstance().options.getEffectiveRenderDistance() + 1;
        int pcx = Minecraft.getInstance().player.chunkPosition().x;
        int pcz = Minecraft.getInstance().player.chunkPosition().z;
        for (int dx = -view; dx <= view; dx++) {
            for (int dz = -view; dz <= view; dz++) {
                LevelChunk c = lvl.getChunkSource().getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
                if (c != null) {
                    onChunkLoad(lvl, c);
                }
            }
        }
    }

    private void scanChunks(ClientLevel lvl) {
        DimensionModel dim = model.current();
        long start = System.nanoTime();
        while (!scanQueue.isEmpty()) {
            long key = scanQueue.dequeueLong();
            if (!queued.remove(key)) {
                continue; // unloaded while queued
            }
            int cx = Pos.chunkX(key);
            int cz = Pos.chunkZ(key);
            LevelChunk chunk = lvl.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk == null) {
                continue;
            }
            long t0 = System.nanoTime();
            scanChunk(lvl, chunk, dim);
            scanNanos += System.nanoTime() - t0;
            chunksScanned++;
            if (System.nanoTime() - start > SCAN_BUDGET_NANOS) {
                break;
            }
        }
        maxTickScanNanos = Math.max(maxTickScanNanos, System.nanoTime() - start);
    }

    private void scanChunk(ClientLevel lvl, LevelChunk chunk, DimensionModel dim) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        int baseX = cx << 4;
        int baseZ = cz << 4;
        scratch.clear();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(classifier::isTracked)) {
                continue;
            }
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockClassifier.Kind k = classifier.classify(section.getBlockState(x, y, z));
                        if (k.tracked()) {
                            long p = Pos.pack(baseX + x, baseY + y, baseZ + z);
                            scratch.add(new ObservedBlock(p, k.blockId(), k.kind(), k.hazard(), k.mature(), tick));
                        }
                    }
                }
            }
        }
        dim.observeChunk(cx, cz, terrain(lvl, chunk), scratch, tick);
        scratch.clear();
    }

    private ChunkTerrain terrain(ClientLevel lvl, LevelChunk chunk) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;
        int water = 0;
        int lava = 0;
        int voids = 0;
        int bottom = lvl.getMinY();
        int baseX = chunk.getPos().x << 4;
        int baseZ = chunk.getPos().z << 4;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z); // y of the highest non-air block
                if (top < bottom) {
                    voids++;
                    top = bottom;
                } else {
                    FluidState fluid = chunk.getBlockState(cursor.set(baseX + x, top, baseZ + z)).getFluidState();
                    if (fluid.is(FluidTags.WATER)) {
                        water++;
                    } else if (fluid.is(FluidTags.LAVA)) {
                        lava++;
                    }
                }
                min = Math.min(min, top);
                max = Math.max(max, top);
                sum += top;
            }
        }
        return new ChunkTerrain(min, max, sum / 256f, water, lava, voids);
    }

    private void observeEntities(Minecraft mc, ClientLevel lvl) {
        DimensionModel dim = model.current();
        double range = model.limits().entityRange();
        double r2 = range * range;
        Player self = mc.player;
        for (Entity e : lvl.entitiesForRendering()) {
            if (e == self || e.isRemoved() || e.distanceToSqr(self) > r2) {
                continue;
            }
            ObservedEntity o = dim.entity(e.getId());
            if (o == null) {
                o = dim.seeEntity(e.getId(), e.getUUID(), typeId(e.getType()), kindOf(e), tick);
                if (o == null) {
                    continue; // entity limit reached
                }
                if (e instanceof Player p) {
                    o.setName(p.getGameProfile().name());
                }
            }
            var v = e.getDeltaMovement();
            o.update(e.getX(), e.getY(), e.getZ(), v.x, v.y, v.z,
                    e instanceof LivingEntity le ? le.getHealth() : Float.NaN, tick);
        }
        dim.pruneEntities(tick);
    }

    private String typeId(EntityType<?> type) {
        String id = entityIds.get(type);
        if (id == null) {
            id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            entityIds.put(type, id);
        }
        return id;
    }

    static EntityKind kindOf(Entity e) {
        if (e instanceof Player) {
            return EntityKind.PLAYER;
        }
        if (e instanceof Enemy) {
            return EntityKind.HOSTILE;
        }
        if (e instanceof ItemEntity) {
            return EntityKind.ITEM;
        }
        if (e instanceof Projectile) {
            return EntityKind.PROJECTILE;
        }
        if (e instanceof VehicleEntity) {
            return EntityKind.VEHICLE;
        }
        MobCategory cat = e.getType().getCategory();
        if (cat == MobCategory.MONSTER) {
            return EntityKind.HOSTILE;
        }
        if (cat != MobCategory.MISC) {
            return EntityKind.PASSIVE;
        }
        return EntityKind.OTHER;
    }
}
