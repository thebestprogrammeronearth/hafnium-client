package dev.hafnium.client.navigation;

import dev.hafnium.client.core.navigation.CollisionSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;

/** Captures only loaded client chunks; never accesses Minecraft from a worker. */
public final class CollisionCapture {
    public CollisionSnapshot capture(CollisionSnapshot.Bounds bounds,long revision) {
        Minecraft mc=Minecraft.getInstance();
        if(!mc.isSameThread()) throw new IllegalStateException("Collision capture requires client thread");
        if(mc.level==null || mc.player==null) throw new IllegalStateException("Not in a world");
        var level=mc.level;
        Map<CollisionSnapshot.Cell,CollisionSnapshot.Terrain> cells=new HashMap<>();
        var context=CollisionContext.of(mc.player);
        for(int x=bounds.minX();x<bounds.maxX();x++) for(int z=bounds.minZ();z<bounds.maxZ();z++)
            for(int y=bounds.minY();y<bounds.maxY();y++) {
                BlockPos pos=new BlockPos(x,y,z);
                if(!level.hasChunk(x>>4,z>>4) || level.isOutsideBuildHeight(pos)
                        || x<level.getWorldBorder().getMinX() || x+1>level.getWorldBorder().getMaxX()
                        || z<level.getWorldBorder().getMinZ() || z+1>level.getWorldBorder().getMaxZ()) continue;
                var state=level.getBlockState(pos);
                var shapes=state.getCollisionShape(level,pos,context).toAabbs().stream()
                        .map(b->new CollisionSnapshot.Box(b.minX+pos.getX(),b.minY+pos.getY(),b.minZ+pos.getZ(),
                                b.maxX+pos.getX(),b.maxY+pos.getY(),b.maxZ+pos.getZ())).toList();
                boolean hazard=state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.CACTUS)||state.is(Blocks.FIRE)
                        ||state.is(Blocks.SOUL_FIRE)||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE)
                        ||state.is(Blocks.SWEET_BERRY_BUSH)||state.is(Blocks.POWDER_SNOW)||state.is(Blocks.WITHER_ROSE);
                cells.put(new CollisionSnapshot.Cell(x,y,z),new CollisionSnapshot.Terrain(shapes,
                        !state.getFluidState().isEmpty() || hazard,hazard));
            }
        return new CollisionSnapshot(level.dimension().identifier().toString(),revision,bounds,cells);
    }
}
