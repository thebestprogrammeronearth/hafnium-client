/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.anubis.util;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;

public final class LevelUtil {
   private LevelUtil() {
   }

   public static boolean isOverworld(ClientLevel level) {
      return level.dimension() == Level.OVERWORLD || level.dimensionTypeRegistration().is(BuiltinDimensionTypes.OVERWORLD);
   }

   public static boolean isLoaded(ClientLevel level, int chunkX, int chunkZ) {
      return level.getChunkSource().hasChunk(chunkX, chunkZ);
   }
}
