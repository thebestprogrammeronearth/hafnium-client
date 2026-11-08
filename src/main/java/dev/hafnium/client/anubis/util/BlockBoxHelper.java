/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.anubis.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public final class BlockBoxHelper {
   private static final double HALF_BLOCK = 0.5;
   private static final float MIN_SIZE = 0.1F;
   private static final float MAX_SIZE = 2.0F;

   private BlockBoxHelper() {
   }

   public static AABB scaledBlockBox(BlockPos pos, float size) {
      float clamped = Math.max(0.1F, Math.min(2.0F, size));
      double halfSize = clamped * 0.5;
      double centerX = pos.getX() + 0.5;
      double centerY = pos.getY() + 0.5;
      double centerZ = pos.getZ() + 0.5;
      return new AABB(centerX - halfSize, centerY - halfSize, centerZ - halfSize, centerX + halfSize, centerY + halfSize, centerZ + halfSize);
   }
}
