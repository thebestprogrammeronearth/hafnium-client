/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.mixin.anubis;

import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.SpawnData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BaseSpawner.class)
public interface AccessorBaseSpawner {
    @Accessor("nextSpawnData")
    SpawnData hafnium$nextSpawnData();

    @Accessor("requiredPlayerRange")
    int hafnium$requiredPlayerRange();
}
