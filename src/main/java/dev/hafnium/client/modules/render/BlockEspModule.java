/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.modules.render;

import dev.hafnium.client.anubis.esp.BlockLayer;
import dev.hafnium.client.anubis.esp.ChunkScanner;
import dev.hafnium.client.anubis.esp.EspStyle;
import dev.hafnium.client.anubis.setting.BlockColorListSetting;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.event.WorldRenderEvent;
import java.util.Set;
import java.util.function.LongPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Highlights chosen blocks in loaded chunks; touching blocks merge into one outlined shape. */
public final class BlockEspModule extends Module implements ChunkScanner.Consumer {
    private final EspStyle style = new EspStyle(this::setting, EspStyle.OUTLINE, 0.2F, 2048.0F);
    private final BoolSetting tracers = setting(new BoolSetting("Tracers", false));
    public final BlockColorListSetting blockColors = setting(BlockColorListSetting.createDefault());
    private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
        @Override
        public int color(Object kind) {
            return blockColors.colorArgb((Block) kind);
        }

        @Override
        public int tracerColor(Object kind) {
            return blockColors.tracerColorArgb((Block) kind);
        }
    }, () -> Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension());
    private Set<String> scannedIds = Set.of();

    public BlockEspModule() {
        super("Block ESP", Category.RENDER, "Highlights chosen blocks; touching blocks merge into one shape.");
        blockColors.onChange(this::rescan);
    }

    private void rescan() {
        if (isEnabled()) {
            Set<String> ids = blockColors.enabledIds();
            if (!ids.equals(scannedIds)) {
                boolean removed = !ids.containsAll(scannedIds);
                scannedIds = ids;
                if (removed) {
                    layer.clear();
                }
                ChunkScanner.rescan();
            }
        }
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        scannedIds = blockColors.enabledIds();
        ChunkScanner.register(this);
        ctx.subscribe(WorldRenderEvent.class, this::onRender3D);
    }

    @Override
    protected void onDisable() {
        ChunkScanner.unregister(this);
        layer.clear();
    }

    /** Number of block clusters currently held (for tests and debugging). */
    public int trackedBlocks() {
        return layer.size();
    }

    /** Clusters drawn / shape groups culled in the last frame. */
    public int[] lastFrame() {
        return new int[]{layer.lastDrawn(), layer.lastCulled()};
    }

    @Override
    public boolean wants(BlockState state) {
        return blockColors.isEnabled(state.getBlock());
    }

    @Override
    public void found(long pos, BlockState state) {
        layer.put(pos, state.getBlock());
    }

    @Override
    public void removed(long pos) {
        layer.remove(pos);
    }

    @Override
    public void reset() {
        layer.clear();
    }

    @Override
    public void chunkScanned(long chunkKey, LongPredicate stillThere) {
        layer.retainChunk(chunkKey, stillThere);
    }

    private void onRender3D(WorldRenderEvent event) {
        layer.render(event, style);
        if (tracers.get() && blockColors.hasAnyTracerEnabled()) {
            layer.renderTracers(style);
        }
    }
}
