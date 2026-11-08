package dev.hafnium.client.core.task;

import dev.hafnium.client.core.permission.Autonomy;
import dev.hafnium.client.core.permission.Capability;
import dev.hafnium.client.core.world.WorldScope;

import java.util.Set;

/**
 * A unit of cooperative, tick-driven work run by the {@link TaskScheduler} on the client thread.
 *
 * <p>{@link #tick} must return quickly. Expensive computation goes through {@link TaskContext#async}, which runs it
 * on a worker thread over an immutable snapshot; the task polls the result on later ticks.
 *
 * <p>Call order per attempt: {@code start}, then {@code tick} every tick while running (with {@code suspend} /
 * {@code resume} around preemption), then exactly one {@code stop}. After a failed attempt {@code recover} decides
 * whether to retry.
 */
public interface Task {
    String name();

    /** Exclusive resources this task needs while running. */
    default Set<TaskResource> resources() {
        return Set.of();
    }

    /** Gameplay capabilities the task exercises; checked by the permission layer before and while running. */
    default Set<Capability> capabilities() {
        return Set.of();
    }

    /**
     * How the capabilities are exercised: {@link Autonomy#ASSISTED} for an action the player started and supervises,
     * {@link Autonomy#AUTONOMOUS} (the default, and the stricter of the two) for agent-driven work. Irrelevant for
     * tasks without capabilities.
     */
    default Autonomy autonomy() {
        return Autonomy.AUTONOMOUS;
    }

    /** The part of the world this task depends on; it is invalidated when that part goes away. */
    default WorldScope worldScope() {
        return WorldScope.NONE;
    }

    default void start(TaskContext ctx) throws Exception {
    }

    /** @return true when the task has finished successfully */
    boolean tick(TaskContext ctx) throws Exception;

    /** Preempted by a higher-priority task: release anything that conflicts (e.g. let go of movement keys). */
    default void suspend(TaskContext ctx) {
    }

    default void resume(TaskContext ctx) {
    }

    /** Always called once at the end of an attempt that was started. Release everything here. */
    default void stop(TaskContext ctx, StopReason reason) {
    }

    /** Called after a failed or timed-out attempt when retries remain. */
    default Recovery recover(TaskContext ctx, Throwable error) {
        return Recovery.RETRY;
    }
}
