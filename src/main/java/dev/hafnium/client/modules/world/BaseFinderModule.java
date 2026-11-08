/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.modules.world;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.longs.Long2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import dev.hafnium.client.event.BlockUpdateEvent;
import dev.hafnium.client.event.ChunkLoadEvent;
import dev.hafnium.client.event.WorldRenderEvent;
import dev.hafnium.client.core.event.ClientTickEvent;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.modules.client.PerformanceModule;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.ColorSetting;
import dev.hafnium.client.core.setting.ModeSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.anubis.util.ChunkAreas;
import dev.hafnium.client.anubis.util.ChunkOverlay;
import dev.hafnium.client.anubis.util.LevelUtil;
import dev.hafnium.client.anubis.util.PerformanceBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Base Finder: Anubis's Sus Chunk Finder ("Classic" method). Crops and plants only grow in chunks a player keeps
 * loaded, so overgrown kelp, vines, bamboo, cocoa, bee nests and amethyst mark areas where someone spent time.
 *
 * <p>Not ported, because they read blocks a server's anti-xray hides: the "ANBS+ Scan" method and the amethyst count
 * inferred from server light ({@code GeodeGlowScan}, {@code HiddenAmethyst}, {@code ServerBlockLight}), and
 * "Detect Mined Clusters" (geode light). Only blocks the client can see are counted.
 */
public final class BaseFinderModule extends Module {
   private static final long SCAN_BUDGET_NANOS = 2000000L;
   private static final int MIN_SCANNED_NEIGHBOURS = 3;
   private static final int NO_PACKET_SENSITIVITY = 1;
   private static final int VISIBLE_REFRESH_TICKS = 20;
   private static final int LIGHT_RESCAN_TICKS = 20;
   private static final int FULL_RESCAN_TICKS = 1200;
   private static final int VISIBLE_MIN_TICKS = 5;
   private static final int LIGHT_WAIT_LIMIT_TICKS = 200;
   private static final int PRUNE_TICKS = 200;
   private static final int PRUNE_MARGIN = 4;
   private static final int PACKET_MIN_Y = -16;
   private static final int PACKET_MAX_Y = 30;
   private static final String METHOD_CLASSIC = "Classic";
   private static final int GLOW_CELLS_PER_POINT = 12;
   private static final int GEODE_TOP_Y = 47;
   private static final int MIN_EXPOSED_BUDDING = 10;
   private static final int MIN_EXPOSED_LINING = 160;
   private static final int GEODE_SETTLE_TICKS = 40;
   private static final int GEODE_CELL_SHIFT = 2;
   private static final int TORCH_LIGHT = 14;
   private static final Direction[] SIDES = Direction.values();
   private static final int AMETHYST = 0;
   private static final int KELP = 1;
   private static final int CAVE_VINES = 2;
   private static final int VINES = 3;
   private static final int BAMBOO = 4;
   private static final int COCOA = 5;
   private static final int BEE_NEST = 6;
   private static final int GROWTH_KINDS = 7;
   private static final int GROWN = 1;
   private static final int UNGROWN = -1;
   private static final int NO_SIGN = 0;
   private static final int BAMBOO_GROWTH_LIGHT = 9;
   private static final String DONUT_BRAND = "DonutFolia";
   private static final int STYLE_VOTES = 3;
   private final NumberSetting spreadRadius = this.setting(new NumberSetting("Simulation Distance", 5.0F, 2.0F, 16.0F, 1.0F));
   private final NumberSetting sensitivity = this.setting(new NumberSetting("Sensitivity", 6.0F, 1.0F, 20.0F, 1.0F));
   private final BoolSetting kelp = this.growthSign("growth-kelp", "Kelp");
   private final BoolSetting caveVines = this.growthSign("growth-cave-vines", "Cave Vines");
   private final BoolSetting vines = this.growthSign("growth-vines", "Vines");
   private final BoolSetting bamboo = this.growthSign("growth-bamboo", "Bamboo");
   private final BoolSetting cocoa = this.growthSign("growth-cocoa", "Cocoa");
   private final BoolSetting beeNests = this.growthSign("growth-bee-nests", "Bee Nests");
   private final BoolSetting[] growthSigns = new BoolSetting[]{null, this.kelp, this.caveVines, this.vines, this.bamboo, this.cocoa, this.beeNests};
   private final ColorSetting chunkColor = this.setting(new ColorSetting("Chunk Color", 1627325444));
   private final BoolSetting tracers = this.setting(new BoolSetting("Tracers", true));
   private final ChunkAreas chunkAreas = new ChunkAreas();
   private final ChunkOverlay chunkOverlay = new ChunkOverlay();
   private final Long2IntOpenHashMap[] heat = perKind();
   private final Long2IntOpenHashMap[] vetoes = perKind();
   private final Long2ObjectOpenHashMap<int[]> chunkGrown = new Long2ObjectOpenHashMap();
   private final Long2IntOpenHashMap chunkUngrown = new Long2IntOpenHashMap();
   private final Long2BooleanOpenHashMap chunkHasPacket = new Long2BooleanOpenHashMap();
   private final LongOpenHashSet scannedChunks = new LongOpenHashSet();
   private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
   private final LongOpenHashSet queued = new LongOpenHashSet();
   private Long2IntOpenHashMap visible = new Long2IntOpenHashMap();
   private boolean visibleDirty = true;
   private int visibleAge;
   private ClientLevel lastLevel;
   private int appliedRadius;
   private int ticks;

   public BaseFinderModule() {
      super("Base Finder", Category.WORLD, "Flags chunks where players spent time: overgrown kelp, vines, bamboo, cocoa, bee nests and amethyst.");
      this.sensitivity.onChange(() -> this.visibleDirty = true);

      for (BoolSetting sign : this.growthSigns) {
         if (sign != null) {
            sign.onChange(this::rescan);
         }
      }

   }

   private BoolSetting growthSign(String id, String name) {
      return this.setting(new BoolSetting(name, true));
   }

   private static Long2IntOpenHashMap[] perKind() {
      Long2IntOpenHashMap[] maps = new Long2IntOpenHashMap[7];

      for (int kind = 0; kind < 7; kind++) {
         maps[kind] = new Long2IntOpenHashMap();
      }

      return maps;
   }

   private int countedKinds() {
      int kinds = 1;

      for (int kind = 0; kind < 7; kind++) {
         if (this.growthSigns[kind] != null && this.growthSigns[kind].get()) {
            kinds |= 1 << kind;
         }
      }

      return kinds;
   }

   private void rescan() {
      this.clearAll();
      this.queueLoadedChunks();
   }

   @Override
   protected void onEnable(ModuleContext ctx) {
      ctx.subscribe(ChunkLoadEvent.class, e -> this.enqueue(ChunkPos.asLong(e.x(), e.z())));
      ctx.subscribe(ClientTickEvent.class, e -> { if (e == ClientTickEvent.END) this.onTick(null); });
      ctx.subscribe(WorldRenderEvent.class, this::onRender3D);

      this.clearAll();
      this.lastLevel = Minecraft.getInstance().level;
      this.queueLoadedChunks();
   }

   @Override
   protected void onDisable() {
      this.clearAll();
      this.lastLevel = null;
   }

   private void clearAll() {
      for (int kind = 0; kind < 7; kind++) {
         this.heat[kind].clear();
         this.vetoes[kind].clear();
      }

      this.chunkGrown.clear();
      this.chunkUngrown.clear();
      this.chunkHasPacket.clear();
      this.scannedChunks.clear();
      this.queue.clear();
      this.queued.clear();
      this.visible = new Long2IntOpenHashMap();
      this.visibleDirty = true;
      this.appliedRadius = this.spreadRadius.getInt();
   }

   private void queueLoadedChunks() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         int radius = mc.options.renderDistance().get() + 1;
         ChunkPos center = mc.player.chunkPosition();

         for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
               if (LevelUtil.isLoaded(mc.level, x, z)) {
                  this.enqueue(ChunkPos.asLong(x, z));
               }
            }
         }
      }
   }

   private void enqueue(long key) {
      if (this.queued.add(key)) {
         this.queue.enqueue(key);
      }
   }

   private void onTick(Object event) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      if (level != this.lastLevel) {
         this.clearAll();
         this.lastLevel = level;
         this.queueLoadedChunks();
      }

      if (level != null) {
         this.ticks++;
         if (this.spreadRadius.getInt() != this.appliedRadius) {
            this.clearAll();
            this.queueLoadedChunks();
         }

         if (this.ticks % 1200 == 0) {
            this.queueLoadedChunks();
         }

         boolean optimized = PerformanceModule.optimizationsEnabled();
         long started = System.nanoTime();
         long deadline = optimized ? PerformanceBudget.acquire(2000000L) : started + 2000000L;

         while (!this.queue.isEmpty() && System.nanoTime() < deadline) {
            long key = this.queue.dequeueLong();
            this.queued.remove(key);
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (LevelUtil.isLoaded(level, cx, cz)) {
               this.updateChunk(key, level.getChunk(cx, cz), level);
               this.scannedChunks.add(key);
               this.visibleDirty = true;
            }
         }

         if (optimized) {
            PerformanceBudget.record(started);
         }

         this.visibleAge++;
         if (this.visibleDirty && this.visibleAge >= 5 || this.visibleAge >= 20) {
            this.visible = this.computeVisible(level);
            this.visibleDirty = false;
            this.visibleAge = 0;
         }

         if (this.ticks % 200 == 0) {
            this.pruneFar(Minecraft.getInstance());
         }
      }
   }

   private void pruneFar(Minecraft mc) {
      if (mc.player != null) {
         int reach = mc.options.getEffectiveRenderDistance() + 2 * this.spreadRadius.getInt() + 4;
         int px = mc.player.getBlockX() >> 4;
         int pz = mc.player.getBlockZ() >> 4;
         LongArrayList far = new LongArrayList();
         LongIterator i = this.scannedChunks.iterator();

         while (i.hasNext()) {
            long key = (Long)i.next();
            if (Math.max(Math.abs(ChunkPos.getX(key) - px), Math.abs(ChunkPos.getZ(key) - pz)) > reach) {
               far.add(key);
            }
         }

         for (int ix = 0; ix < far.size(); ix++) {
            this.forget(far.getLong(ix));
         }

         if (!far.isEmpty()) {
            this.visibleDirty = true;
         }

         if (mc.level != null) {
         }
      }
   }

   private void forget(long key) {
      int r = this.spreadRadius.getInt();
      int cx = ChunkPos.getX(key);
      int cz = ChunkPos.getZ(key);
      this.withdraw(cx, cz, r, (int[])this.chunkGrown.remove(key), this.chunkUngrown.remove(key));
      this.chunkHasPacket.remove(key);
      this.scannedChunks.remove(key);
   }

   private void updateChunk(long key, LevelChunk chunk, ClientLevel level) {
      int r = this.spreadRadius.getInt();
      int cx = ChunkPos.getX(key);
      int cz = ChunkPos.getZ(key);
      this.withdraw(cx, cz, r, (int[])this.chunkGrown.remove(key), this.chunkUngrown.remove(key));
      this.chunkHasPacket.put(key, hasBlocksInRange(chunk, -16, 30));
      BaseFinderModule.ChunkScore raw = this.scoreChunk(chunk, level);
      // Anubis also added amethyst it inferred from server light (HiddenAmethyst); Hafnium only counts visible blocks.
      int[] grown = raw.grown();
      int ungrown = raw.ungrown();
      this.chunkGrown.put(key, grown);
      this.chunkUngrown.put(key, ungrown);
      this.spread(cx, cz, r, grown, ungrown);
   }

   private void spread(int cx, int cz, int r, int[] grown, int ungrown) {
      for (int kind = 0; kind < 7; kind++) {
         Long2IntOpenHashMap kindHeat = this.heat[kind];
         Long2IntOpenHashMap kindVetoes = this.vetoes[kind];
         int amount = grown[kind];
         if (amount > 0) {
            forArea(cx, cz, r, area -> kindHeat.addTo(area, amount));
         }

         if ((ungrown & 1 << kind) != 0) {
            forArea(cx, cz, r, area -> kindVetoes.addTo(area, 1));
         }
      }
   }

   private void withdraw(int cx, int cz, int r, int[] grown, int ungrown) {
      for (int kind = 0; kind < 7; kind++) {
         Long2IntOpenHashMap kindHeat = this.heat[kind];
         Long2IntOpenHashMap kindVetoes = this.vetoes[kind];
         int amount = grown == null ? 0 : grown[kind];
         if (amount > 0) {
            forArea(cx, cz, r, area -> subtract(kindHeat, area, amount));
         }

         if ((ungrown & 1 << kind) != 0) {
            forArea(cx, cz, r, area -> subtract(kindVetoes, area, 1));
         }
      }
   }

   private static void forArea(int cx, int cz, int r, LongConsumer action) {
      for (int dx = -r; dx <= r; dx++) {
         for (int dz = -r; dz <= r; dz++) {
            action.accept(ChunkPos.asLong(cx + dx, cz + dz));
         }
      }
   }

   private static void subtract(Long2IntOpenHashMap map, long key, int amount) {
      int left = map.get(key) - amount;
      if (left > 0) {
         map.put(key, left);
      } else {
         map.remove(key);
      }
   }

   private static boolean hasBlocksInRange(LevelChunk chunk, int minY, int maxY) {
      LevelChunkSection[] sections = chunk.getSections();
      int minSectionY = chunk.getMinSectionY();

      for (int i = 0; i < sections.length; i++) {
         LevelChunkSection section = sections[i];
         if (section != null && !section.hasOnlyAir()) {
            int baseY = minSectionY + i << 4;
            if (baseY + 15 >= minY && baseY <= maxY) {
               for (int y = 0; y < 16; y++) {
                  int worldY = baseY + y;
                  if (worldY >= minY && worldY <= maxY) {
                     for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                           if (!section.getBlockState(x, y, z).isAir()) {
                              return true;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private BaseFinderModule.ChunkScore scoreChunk(LevelChunk chunk, ClientLevel level) {
      int[] grown = new int[7];
      {
         LevelChunkSection[] sections = chunk.getSections();
         int minSectionY = chunk.getMinSectionY();
         ChunkPos pos = chunk.getPos();
         int counted = this.countedKinds();
         int ungrown = 0;
         MutableBlockPos cursor = new MutableBlockPos();

         for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section != null && !section.hasOnlyAir() && section.maybeHas(statex -> isCounted(statex, counted))) {
               int baseY = minSectionY + i << 4;

               for (int x = 0; x < 16; x++) {
                  for (int y = 0; y < 16; y++) {
                     for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        int kind = growthKind(state);
                        if (kind >= 0 && (counted & 1 << kind) != 0) {
                           cursor.set(pos.getMinBlockX() + x, baseY + y, pos.getMinBlockZ() + z);
                           int sign = readSign(level, cursor, state, kind);
                           if (sign == 1) {
                              grown[kind]++;
                           } else if (sign == -1) {
                              ungrown |= 1 << kind;
                           }
                        }
                     }
                  }
               }
            }
         }

         return new BaseFinderModule.ChunkScore(grown, ungrown);
      }
   }

   private static boolean isCounted(BlockState state, int counted) {
      int kind = growthKind(state);
      return kind >= 0 && (counted & 1 << kind) != 0;
   }

   private static int growthKind(BlockState state) {
      Block block = state.getBlock();
      if (block == Blocks.AMETHYST_CLUSTER || isBud(state)) {
         return 0;
      } else if (block == Blocks.KELP) {
         return 1;
      } else if (block == Blocks.CAVE_VINES) {
         return 2;
      } else if (block == Blocks.VINE) {
         return 3;
      } else if (block == Blocks.BAMBOO) {
         return 4;
      } else if (block == Blocks.COCOA) {
         return 5;
      } else {
         return block == Blocks.BEE_NEST ? 6 : -1;
      }
   }

   private static int readSign(ClientLevel level, MutableBlockPos at, BlockState state, int kind) {
      return switch (kind) {
         case 0 -> {
            if (state.is(Blocks.AMETHYST_CLUSTER)) {
               yield 1;
            } else if (!state.hasProperty(BlockStateProperties.FACING)) {
               yield 0;
            } else {
               Direction facing = state.getValue(BlockStateProperties.FACING);
               yield level.getBlockState(at.move(facing.getOpposite())).is(Blocks.BUDDING_AMETHYST) ? -1 : 1;
            }
         }
         case 1 -> state.getValue(BlockStateProperties.AGE_25) != 25 && level.getBlockState(at.move(Direction.UP)).is(Blocks.WATER) ? -1 : 1;
         case 2 -> state.getValue(BlockStateProperties.AGE_25) != 25 && level.getBlockState(at.move(Direction.DOWN)).isAir() ? -1 : 1;
         case 3 -> {
            BlockState below = level.getBlockState(at.move(Direction.DOWN));
            yield below.is(Blocks.VINE) ? 0 : (!below.isAir() ? 1 : (hangsFromSide(state) ? -1 : 1));
         }
         case 4 -> bambooSign(level, at, state);
         case 5 -> state.getValue(BlockStateProperties.AGE_2) == 2 ? 1 : -1;
         case 6 -> state.getValue(BeehiveBlock.HONEY_LEVEL) == 5 ? 1 : -1;
         default -> 0;
      };
   }

   private static boolean hangsFromSide(BlockState vine) {
      return vine.getValue(BlockStateProperties.NORTH)
         || vine.getValue(BlockStateProperties.EAST)
         || vine.getValue(BlockStateProperties.SOUTH)
         || vine.getValue(BlockStateProperties.WEST);
   }

   private static int bambooSign(ClientLevel level, MutableBlockPos at, BlockState state) {
      int x = at.getX();
      int y = at.getY();
      int z = at.getZ();
      BlockState above = level.getBlockState(at.move(Direction.UP));
      if (above.is(Blocks.BAMBOO)) {
         return 0;
      } else if (state.getValue(BlockStateProperties.STAGE) == 1) {
         return 1;
      } else if (above.isAir() && level.getRawBrightness(at, 0) >= 9) {
         int height = 1;

         while (height < 16 && level.getBlockState(at.set(x, y - height, z)).is(Blocks.BAMBOO)) {
            height++;
         }

         return height < 16 ? -1 : 0;
      } else {
         return 0;
      }
   }

   private static boolean isBud(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.SMALL_AMETHYST_BUD || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.LARGE_AMETHYST_BUD;
   }

   private static boolean isAmethystGrowth(BlockState state) {
      return state.is(Blocks.AMETHYST_CLUSTER) || isBud(state);
   }

   private Long2IntOpenHashMap computeVisible(ClientLevel level) {
      Long2IntOpenHashMap total = new Long2IntOpenHashMap();

      for (int kind = 0; kind < 7; kind++) {
         Long2IntOpenHashMap kindVetoes = this.vetoes[kind];
         ObjectIterator reach = this.heat[kind].long2IntEntrySet().iterator();

         while (reach.hasNext()) {
            Entry entry = (Entry)reach.next();
            if (!kindVetoes.containsKey(entry.getLongKey())) {
               total.addTo(entry.getLongKey(), entry.getIntValue());
            }
         }
      }

      Long2IntOpenHashMap eligible = new Long2IntOpenHashMap();
      ObjectIterator var14 = total.long2IntEntrySet().iterator();

      while (var14.hasNext()) {
         Entry entry = (Entry)var14.next();
         long key = entry.getLongKey();
         int cx = ChunkPos.getX(key);
         int cz = ChunkPos.getZ(key);
         if (LevelUtil.isLoaded(level, cx, cz) && this.scannedNeighbours(cx, cz) >= 3) {
            eligible.put(key, entry.getIntValue());
         }
      }

      int withPacket = this.sensitivity.getInt();
      int reach = this.appliedRadius;
      Long2IntOpenHashMap kept = new Long2IntOpenHashMap();
      ObjectIterator var7 = eligible.long2IntEntrySet().iterator();

      while (var7.hasNext()) {
         Entry entry = (Entry)var7.next();
         long key = entry.getLongKey();
         int value = entry.getIntValue();
         boolean reached = value >= withPacket || !this.chunkHasPacket.getOrDefault(key, true) && this.amethystHeat(key) >= 1;
         if (reached && isPeak(eligible, key, value, reach)) {
            kept.put(key, value);
         }
      }

      return kept;
   }

   private int amethystHeat(long key) {
      return this.vetoes[0].containsKey(key) ? 0 : this.heat[0].get(key);
   }

   private static boolean isPeak(Long2IntOpenHashMap field, long key, int value, int reach) {
      int cx = ChunkPos.getX(key);
      int cz = ChunkPos.getZ(key);

      for (int dx = -reach; dx <= reach; dx++) {
         for (int dz = -reach; dz <= reach; dz++) {
            if ((dx != 0 || dz != 0) && field.get(ChunkPos.asLong(cx + dx, cz + dz)) > value) {
               return false;
            }
         }
      }

      return true;
   }

   private int scannedNeighbours(int cx, int cz) {
      int count = 0;

      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if ((dx != 0 || dz != 0) && this.scannedChunks.contains(ChunkPos.asLong(cx + dx, cz + dz))) {
               count++;
            }
         }
      }

      return count;
   }

   public LongSet flaggedChunks() {
      return LongSets.unmodifiable(this.visible.keySet());
   }

   private void onRender3D(WorldRenderEvent event) {
      if (Minecraft.getInstance().level != null) {
         this.chunkOverlay.draw(this.visible.keySet(), this.chunkColor.argb(), event.partialTick);
         if (this.tracers.get()) {
            this.chunkAreas.drawTracers(this.visible.keySet(), 63.0, ARGB.opaque(this.chunkColor.argb()));
         }

      }
   }

      private record ChunkScore(int[] grown, int ungrown) {
   }

}
