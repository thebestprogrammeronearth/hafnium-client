package dev.hafnium.client.core.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.hafnium.client.core.ImmutableSnapshot;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** Passed to every {@link Task} callback. Client thread only. */
public final class TaskContext {
    private static final Logger LOG = LoggerFactory.getLogger("Hafnium/Tasks");

    private final TaskHandle handle;
    private final TaskScheduler scheduler;
    private final Executor asyncExecutor;

    TaskContext(TaskHandle handle, TaskScheduler scheduler, Executor asyncExecutor) {
        this.handle = handle;
        this.scheduler = scheduler;
        this.asyncExecutor = asyncExecutor;
    }

    public TaskHandle handle() {
        return handle;
    }

    /** Attempt number, starting at 1. */
    public int attempt() {
        return handle.attempts();
    }

    /** Ticks spent running in the current attempt (suspended time excluded). */
    public long runningTicks() {
        return handle.runningTicks;
    }

    public long currentTick() {
        return scheduler.currentTick();
    }

    public void progress(double fraction, String message) {
        handle.setProgress(fraction, message);
    }

    public void status(String message) {
        handle.setStatus(message);
    }

    public Logger log() {
        return LOG;
    }

    /**
     * Runs {@code work} over {@code snapshot} on a worker thread. Workers only ever receive an
     * {@link ImmutableSnapshot} (e.g. a {@code WorldSnapshot} built on this thread), so they cannot reach live game
     * or world-model state. The work is cancelled automatically when the attempt ends.
     */
    public <S extends ImmutableSnapshot, T> AsyncResult<T> async(S snapshot, Function<? super S, ? extends T> work) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(work, "work");
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> work.apply(snapshot), asyncExecutor);
        AsyncResult<T> result = new AsyncResult<>(future);
        handle.asyncWork.add(result);
        return result;
    }
}
