package dev.hafnium.client.core;

import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.task.TaskScheduler;
import dev.hafnium.client.core.world.WorldChangeEvent;

/**
 * Connects world changes to task invalidation, so neither the scheduler nor the world model knows about the other.
 * <ul>
 *   <li>Disconnect (model cleared): world-dependent tasks are cancelled.</li>
 *   <li>Dimension change: tasks scoped to another dimension are invalidated.</li>
 *   <li>Chunk unload: tasks whose scope includes that chunk are invalidated.</li>
 * </ul>
 * Invalidation fails the current attempt; the task's recovery logic and retry policy decide what happens next.
 */
public final class WorldTaskInvalidator {
    private WorldTaskInvalidator() {
    }

    public static void install(EventBus bus, TaskScheduler scheduler) {
        bus.subscribe(WorldChangeEvent.Cleared.class, WorldTaskInvalidator.class, e ->
                scheduler.cancelMatching(h -> h.task().worldScope().dependsOnWorld(), "Disconnected from the world"));
        bus.subscribe(WorldChangeEvent.DimensionChanged.class, WorldTaskInvalidator.class, e -> {
            if (e.previous() != null) {
                scheduler.invalidate(h -> h.task().worldScope().invalidatedByDimension(e.dimension()),
                        "Left dimension " + e.previous());
            }
        });
        bus.subscribe(WorldChangeEvent.ChunkUnloaded.class, WorldTaskInvalidator.class, e ->
                scheduler.invalidate(h -> h.task().worldScope().invalidatedByChunkUnload(e.dimension(), e.chunkX(), e.chunkZ()),
                        "Chunk " + e.chunkX() + "," + e.chunkZ() + " unloaded"));
    }
}
