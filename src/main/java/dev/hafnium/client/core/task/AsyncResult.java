package dev.hafnium.client.core.task;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** Result of work started with {@link TaskContext#async}; poll it from {@link Task#tick}. */
public final class AsyncResult<T> {
    private final CompletableFuture<T> future;

    AsyncResult(CompletableFuture<T> future) {
        this.future = future;
    }

    public boolean isDone() {
        return future.isDone();
    }

    public boolean isFailed() {
        return future.isCompletedExceptionally();
    }

    /**
     * @return the value once done
     * @throws IllegalStateException if not finished yet
     * @throws TaskException if the background work failed or was cancelled
     */
    public T get() {
        if (!future.isDone()) {
            throw new IllegalStateException("Async work has not finished; check isDone() first");
        }
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw new TaskException("Background work failed: " + e.getCause(), e.getCause());
        } catch (CancellationException e) {
            throw new TaskException("Background work was cancelled", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskException("Interrupted", e);
        }
    }

    void cancel() {
        future.cancel(true);
    }
}
