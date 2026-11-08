/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.modules.render;

import dev.hafnium.client.anubis.esp.BlockLayer;
import dev.hafnium.client.anubis.esp.ChunkScanner;
import dev.hafnium.client.anubis.esp.EspStyle;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.ColorSetting;
import dev.hafnium.client.event.ChunkLoadEvent;
import dev.hafnium.client.event.WorldRenderEvent;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Highlights storage blocks and spawners with per-type colours.
 *
 * <p>Anubis's version ("Storage ESP Bypass") also read container data straight out of chunk and block-entity packets,
 * which reveals containers that a server's anti-xray hides. That path is not ported: Hafnium only uses blocks and
 * block entities that exist in the client's world.
 */
public final class StorageEspModule extends Module implements ChunkScanner.Consumer {
    private final EspStyle style = new EspStyle(this::setting, EspStyle.OUTLINE, 0.25F, 256.0F);
    private final BoolSetting sTracers = setting(new BoolSetting("Tracers", false));
    private final Type chest = new Type("Chest", true, 0xFF9C5B00);
    private final Type enderChest = new Type("Ender Chest", true, 0xFF7500FF);
    private final Type shulker = new Type("Shulker Box", true, 0xFF86009E);
    private final Type spawner = new Type("Spawner", true, 0xFF8A7EA6);
    private final Type furnace = new Type("Furnace", false, 0xFF646464);
    private final Type barrel = new Type("Barrel", true, 0xFF644B32);
    private final Type enchanting = new Type("Enchanting Table", true, 0xFFC80000);
    private final Type hopper = new Type("Hopper", false, 0xFF505050);
    private final Type[] types = {chest, enderChest, shulker, spawner, furnace, barrel, enchanting, hopper};
    private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
        @Override
        public int color(Object kind) {
            return ((Type) kind).color.argb();
        }

        @Override
        public int tracerColor(Object kind) {
            Type t = (Type) kind;
            return t.tracers.get() ? t.tracerColor.argb() : 0;
        }
    }, () -> Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension());
    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Type>> detections = new HashMap<>();
    private ResourceKey<Level> shownDimension;

    public StorageEspModule() {
        super("Storage ESP", Category.RENDER, "Highlights storage blocks and spawners with per-type colours.");
    }

    private Type typeOf(Block block) {
        if (block instanceof ChestBlock) return chest;
        if (block instanceof EnderChestBlock) return enderChest;
        if (block instanceof ShulkerBoxBlock) return shulker;
        if (block instanceof SpawnerBlock) return spawner;
        if (block instanceof AbstractFurnaceBlock) return furnace;
        if (block instanceof BarrelBlock) return barrel;
        if (block instanceof EnchantingTableBlock) return enchanting;
        return block instanceof HopperBlock ? hopper : null;
    }

    private Type typeOf(BlockEntityType<?> type) {
        if (type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST) return chest;
        if (type == BlockEntityType.ENDER_CHEST) return enderChest;
        if (type == BlockEntityType.SHULKER_BOX) return shulker;
        if (type == BlockEntityType.MOB_SPAWNER) return spawner;
        if (type == BlockEntityType.FURNACE || type == BlockEntityType.BLAST_FURNACE || type == BlockEntityType.SMOKER) return furnace;
        if (type == BlockEntityType.BARREL) return barrel;
        if (type == BlockEntityType.ENCHANTING_TABLE) return enchanting;
        return type == BlockEntityType.HOPPER ? hopper : null;
    }

    private void rescan() {
        if (!isEnabled()) {
            return;
        }
        layer.clear();
        ClientLevel level = Minecraft.getInstance().level;
        shownDimension = level == null ? null : level.dimension();
        Long2ObjectOpenHashMap<Type> found = shownDimension == null ? null : detections.get(shownDimension);
        if (found != null) {
            for (Long2ObjectMap.Entry<Type> entry : found.long2ObjectEntrySet()) {
                if (entry.getValue().enabled.get()) {
                    layer.put(entry.getLongKey(), entry.getValue());
                }
            }
        }
    }

    private boolean anyTracer() {
        if (!sTracers.get()) {
            return false;
        }
        for (Type type : types) {
            if (type.enabled.get() && type.tracers.get()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        detections.clear();
        layer.clear();
        ChunkScanner.register(this);
        ctx.subscribe(WorldRenderEvent.class, this::onRender3D);
        ctx.subscribe(ChunkLoadEvent.class, this::onChunkLoad);
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        shownDimension = level == null ? null : level.dimension();
        if (level != null && mc.player != null) {
            ChunkPos center = mc.player.chunkPosition();
            int radius = mc.options.getEffectiveRenderDistance() + 1;
            for (int x = center.x - radius; x <= center.x + radius; x++) {
                for (int z = center.z - radius; z <= center.z + radius; z++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk != null) {
                        scanBlockEntities(chunk);
                    }
                }
            }
        }
    }

    @Override
    protected void onDisable() {
        ChunkScanner.unregister(this);
        detections.clear();
        layer.clear();
        shownDimension = null;
    }

    /** Number of highlighted clusters (for tests). */
    public int trackedBlocks() {
        return layer.size();
    }

    private void found(BlockPos pos, Type type) {
        ClientLevel level = Minecraft.getInstance().level;
        if (type == null || level == null) {
            return;
        }
        long key = pos.asLong();
        Type previous = detections.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>()).put(key, type);
        if (previous != type) {
            if (previous != null) {
                layer.remove(key);
            }
            if (type.enabled.get()) {
                layer.put(key, type);
            }
        }
    }

    private void gone(BlockPos pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            Long2ObjectOpenHashMap<Type> found = detections.get(level.dimension());
            if (found != null && found.remove(pos.asLong()) != null) {
                layer.remove(pos.asLong());
            }
        }
    }

    @Override
    public boolean wants(BlockState state) {
        return typeOf(state.getBlock()) != null;
    }

    @Override
    public void found(long pos, BlockState state) {
        found(BlockPos.of(pos), typeOf(state.getBlock()));
    }

    @Override
    public void removed(long pos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(BlockPos.of(pos)).isAir()) {
            gone(BlockPos.of(pos));
        }
    }

    @Override
    public void reset() {
        detections.clear();
        layer.clear();
    }

    private void scanBlockEntities(LevelChunk chunk) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            found(blockEntity.getBlockPos(), typeOf(blockEntity.getType()));
        }
    }

    /** Anubis scanned a chunk's block entities when its packet was applied; Hafnium does it when the chunk loads. */
    private void onChunkLoad(ChunkLoadEvent event) {
        if (event.level.dimension() != shownDimension) {
            rescan();
        }
        scanBlockEntities(event.chunk);
    }

    private void onRender3D(WorldRenderEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.dimension() != shownDimension) {
            rescan();
        }
        layer.render(event, style);
        if (anyTracer()) {
            layer.renderTracers(style);
        }
    }

    private final class Type {
        final BoolSetting enabled;
        final BoolSetting tracers;
        final ColorSetting color;
        final ColorSetting tracerColor;

        Type(String label, boolean on, int argb) {
            enabled = setting(new BoolSetting(label, on));
            color = setting(new ColorSetting(label + " Color", argb));
            tracers = setting(new BoolSetting(label + " Tracers", false));
            tracerColor = setting(new ColorSetting(label + " Tracer Color", argb));
            color.visibleWhen(enabled::get);
            tracers.visibleWhen(() -> sTracers.get() && enabled.get());
            tracerColor.visibleWhen(() -> sTracers.get() && enabled.get() && tracers.get());
            enabled.onChange(StorageEspModule.this::rescan);
        }
    }
}
