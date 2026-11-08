package dev.hafnium.client.world;

import dev.hafnium.client.core.world.BlockKind;
import dev.hafnium.client.core.world.HazardType;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides which block states the world model tracks. Results are cached per {@link BlockState}; call {@link #reset()}
 * when tags may have changed (joining a server).
 */
public final class BlockClassifier {
    /** Classification of one block state; {@link #NONE} for untracked states. */
    public record Kind(BlockKind kind, HazardType hazard, boolean mature, String blockId) {
        static final Kind NONE = new Kind(null, null, false, "");

        public boolean tracked() {
            return kind != null;
        }
    }

    private final Reference2ObjectOpenHashMap<BlockState, Kind> cache = new Reference2ObjectOpenHashMap<>();

    public void reset() {
        cache.clear();
    }

    public boolean isTracked(BlockState state) {
        return classify(state).tracked();
    }

    public Kind classify(BlockState state) {
        Kind k = cache.get(state);
        if (k == null) {
            k = compute(state);
            cache.put(state, k);
        }
        return k;
    }

    private static Kind compute(BlockState state) {
        Block b = state.getBlock();
        if (state.isAir()) {
            return Kind.NONE;
        }
        HazardType hazard = hazard(state, b);
        if (hazard != null) {
            return new Kind(BlockKind.HAZARD, hazard, false, id(b));
        }
        if (isOre(state, b)) {
            return new Kind(BlockKind.ORE, null, false, id(b));
        }
        if (b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock || b instanceof EnderChestBlock
                || b instanceof HopperBlock || b instanceof AbstractFurnaceBlock || b instanceof DispenserBlock
                || b instanceof BrewingStandBlock) {
            return new Kind(BlockKind.CONTAINER, null, false, id(b));
        }
        if (b instanceof CropBlock crop) {
            return new Kind(BlockKind.CROP, null, crop.isMaxAge(state), id(b));
        }
        if (b instanceof NetherWartBlock) {
            return new Kind(BlockKind.CROP, null, state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE, id(b));
        }
        return Kind.NONE;
    }

    private static HazardType hazard(BlockState state, Block b) {
        if (b == Blocks.LAVA) {
            return HazardType.LAVA;
        }
        if (state.is(BlockTags.FIRE)) {
            return HazardType.FIRE;
        }
        if (b == Blocks.MAGMA_BLOCK) {
            return HazardType.MAGMA;
        }
        if (b == Blocks.CACTUS) {
            return HazardType.CACTUS;
        }
        if (b == Blocks.POWDER_SNOW) {
            return HazardType.POWDER_SNOW;
        }
        if (b == Blocks.SWEET_BERRY_BUSH) {
            return HazardType.SWEET_BERRY_BUSH;
        }
        if (b == Blocks.WITHER_ROSE) {
            return HazardType.WITHER_ROSE;
        }
        if (state.is(BlockTags.CAMPFIRES)) {
            return HazardType.CAMPFIRE;
        }
        if (b == Blocks.POINTED_DRIPSTONE) {
            return HazardType.POINTED_DRIPSTONE;
        }
        return null;
    }

    private static boolean isOre(BlockState state, Block b) {
        return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)
                || b == Blocks.NETHER_QUARTZ_ORE || b == Blocks.ANCIENT_DEBRIS;
    }

    private static String id(Block b) {
        return BuiltInRegistries.BLOCK.getKey(b).toString();
    }
}
