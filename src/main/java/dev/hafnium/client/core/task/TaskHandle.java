package dev.hafnium.client.core.task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Public view of a submitted task. Readable from any thread; {@link #cancel} is safe from any thread (it takes
 * effect on the scheduler's next tick).
 */
public final class TaskHandle {
    private final long id;
    private final Task task;
    private final TaskOptions options;
    private final TaskScheduler scheduler;
    private final CompletableFuture<TaskState> completion = new CompletableFuture<>();

    private volatile TaskState state = TaskState.PENDING;
    private volatile double progress;
    private volatile String status = "";
    private volatile String error;
    private volatile int attempts;

    // ---- scheduler-private bookkeeping (client thread only)
    final long submittedTick;
    long runningTicks;
    long retryAtTick;
    boolean started;
    TaskContext context;
    final List<AsyncResult<?>> asyncWork = new ArrayList<>();
    final List<TaskHandle> dependents = new ArrayList<>();

    TaskHandle(long id, Task task, TaskOptions options, TaskScheduler scheduler, long submittedTick) {
        this.id = id;
        this.task = task;
        this.options = options;
        this.scheduler = scheduler;
        this.submittedTick = submittedTick;
    }

    TaskScheduler scheduler() {
        return scheduler;
    }

    public long id() {
        return id;
    }

    public String name() {
        return task.name();
    }

    public Task task() {
        return task;
    }

    public TaskOptions options() {
        return options;
    }

    public TaskState state() {
        return state;
    }

    public boolean isDone() {
        return state.isTerminal();
    }

    /** 0..1 as last reported by the task. */
    public double progress() {
        return progress;
    }

    /** Human-readable status line, e.g. "Waiting for dependencies" or what the task reported. */
    public String status() {
        return status;
    }

    public String error() {
        return error;
    }

    public int attempts() {
        return attempts;
    }

    /** Completes with the terminal state. Completed on the client thread. */
    public CompletableFuture<TaskState> completion() {
        return completion;
    }

    public void cancel(String reason) {
        scheduler.cancel(this, reason);
    }

    // ---- mutators for the scheduler / context

    void setState(TaskState s) {
        this.state = s;
        if (s.isTerminal()) {
            completion.complete(s);
        }
    }

    void setProgress(double p, String message) {
        this.progress = Math.max(0, Math.min(1, p));
        if (message != null) {
            this.status = message;
        }
    }

    void setStatus(String message) {
        this.status = message;
    }

    void setError(String error) {
        this.error = error;
    }

    void incrementAttempts() {
        attempts++;
    }

    @Override
    public String toString() {
        return "Task#" + id + "(" + name() + ", " + state + ")";
    }
}
