package dev.hafnium.client.modules.world;

import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.core.world.WorldModelLimits;

/** Settings-only: memory limits of the world model. Changes apply immediately. */
public final class ObservationModule extends Module {
    public final NumberSetting cachedChunks = setting(new NumberSetting("Cached Chunks", 4096, 256, 16384, 256));
    public final NumberSetting trackedBlocks = setting(new NumberSetting("Tracked Blocks", 200, 10, 1000, 10, "k"));
    public final NumberSetting entities = setting(new NumberSetting("Entities", 512, 32, 2048, 32));
    public final NumberSetting entityRange = setting(new NumberSetting("Entity Range", 96, 16, 128, 8, " b"));

    public ObservationModule() {
        super("Observation", Category.WORLD, "World model memory limits (what Hafnium remembers).");
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    public WorldModelLimits limits() {
        return new WorldModelLimits(cachedChunks.get().intValue(), trackedBlocks.get().intValue() * 1000,
                entities.get().intValue(), entityRange.get().intValue());
    }

    /** Runs {@code listener} whenever any limit changes. */
    public void onChange(Runnable listener) {
        cachedChunks.addListener(v -> listener.run());
        trackedBlocks.addListener(v -> listener.run());
        entities.addListener(v -> listener.run());
        entityRange.addListener(v -> listener.run());
    }
}
