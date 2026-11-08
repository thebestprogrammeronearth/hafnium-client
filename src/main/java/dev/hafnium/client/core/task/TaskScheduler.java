package dev.hafnium.client.core.task;

import dev.hafnium.client.core.ClientThread;
import dev.hafnium.client.core.event.EventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Cooperative, tick-driven task scheduler.
 *
 * <p>Every {@link #tick()} (one per client tick, on the client thread):
 * <ol>
 *   <li>applies queued submissions/cancellations from other threads;</li>
 *   <li>resolves dependencies (a failed or cancelled dependency fails/cancels its dependents), deadlines and retry
 *       backoffs;</li>
 *   <li>re-checks the {@link Guard} for running tasks;</li>
 *   <li>allocates exclusive {@link TaskResource}s by priority: non-interruptible running tasks keep theirs, then higher
 *       priority wins and an interruptible holder is suspended (it resumes when the resources free up);</li>
 *   <li>ticks every running task, enforcing per-attempt timeouts and routing failures through
 *       {@link Task#recover} and the {@link RetryPolicy}.</li>
 * </ol>
 *
 * <p>The scheduler knows nothing about permissions: the permission layer plugs in through {@link Guard}.
 */
public final class TaskScheduler {
    /** Prefix of {@link TaskHandle#error()} when the guard (permission layer) stopped or refused a task. */
    public static final String NOT_PERMITTED_PREFIX = "Not permitted: ";

    private static final Logger LOG = LoggerFactory.getLogger("Hafnium/Tasks");
    private static final int HISTORY_LIMIT = 128;
    private static final TaskHandle[] EMPTY = new TaskHandle[0];

    /** Decides whether a task may start / keep running. Returns null to allow, or a human-readable denial reason. */
    @FunctionalInterface
    public interface Guard {
        String deny(Task task);
    }

    private final EventBus bus;
    private final ClientThread clientThread;
    private final Executor asyncExecutor;
    private final AtomicLong ids = new AtomicLong(1);
    private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private final List<TaskHandle> active = new ArrayList<>();
    private final ArrayDeque<TaskRecord> history = new ArrayDeque<>();
    private final Map<TaskResource, TaskHandle> holders = new EnumMap<>(TaskResource.class);
    private final List<TaskHandle> runOrder = new ArrayList<>();
    private final EnumSet<TaskResource> used = EnumSet.noneOf(TaskResource.class);

    private volatile Guard guard = task -> null;
    private volatile long tick;
    private boolean processing;
    private volatile boolean shutdown;

    private static final Comparator<TaskHandle> ALLOCATION_ORDER = Comparator
            .comparing((TaskHandle h) -> !(h.state() == TaskState.RUNNING && !h.options().interruptible()))
            .thenComparing(h -> -h.options().priority())
            .thenComparing(h -> !h.started)
            .thenComparingLong(TaskHandle::id);

    public TaskScheduler(EventBus bus, ClientThread clientThread, Executor asyncExecutor) {
        this.bus = Objects.requireNonNull(bus);
        this.clientThread = Objects.requireNonNull(clientThread);
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor);
    }

    public void setGuard(Guard guard) {
        this.guard = Objects.requireNonNull(guard);
    }

    public long currentTick() {
        return tick;
    }

    // ------------------------------------------------------------------ public API (any thread)

    public TaskHandle submit(Task task) {
        return submit(task, TaskOptions.DEFAULT);
    }

    public TaskHandle submit(Task task, TaskOptions options) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(options, "options");
        if (shutdown) {
            throw new IllegalStateException("TaskScheduler is shut down");
        }
        for (TaskHandle dep : options.dependencies()) {
            if (dep.scheduler() != this) {
                throw new IllegalArgumentException("Dependency " + dep + " belongs to another scheduler");
            }
        }
        TaskHandle h = new TaskHandle(ids.getAndIncrement(), task, options, this, tick);
        h.setStatus("Queued");
        run(() -> add(h));
        return h;
    }

    void cancel(TaskHandle h, String reason) {
        run(() -> {
            if (!h.isDone()) {
                finish(h, TaskState.CANCELLED, reason == null ? "Cancelled" : reason);
            }
        });
    }

    /** Cancels every unfinished task submitted with this owner (e.g. a module being disabled). */
    public void cancelOwnedBy(Object owner, String reason) {
        run(() -> {
            for (TaskHandle h : active.toArray(EMPTY)) {
                if (h.options().owner() == owner && !h.isDone()) {
                    finish(h, TaskState.CANCELLED, reason);
                }
            }
        });
    }

    /** Cancels unfinished tasks matching {@code which} (no retry). */
    public void cancelMatching(Predicate<TaskHandle> which, String reason) {
        run(() -> {
            for (TaskHandle h : active.toArray(EMPTY)) {
                if (!h.isDone() && which.test(h)) {
                    finish(h, TaskState.CANCELLED, reason);
                }
            }
        });
    }

    /** Emergency stop: cancels every unfinished task. */
    public void cancelAll(String reason) {
        run(() -> {
            for (TaskHandle h : active.toArray(EMPTY)) {
                if (!h.isDone()) {
                    finish(h, TaskState.CANCELLED, reason);
                }
            }
        });
    }

    /**
     * Invalidates matching unfinished tasks: a started task's current attempt fails with
     * {@link TaskInvalidatedException} (so {@link Task#recover} and the retry policy decide what happens); a task that
     * has not started yet fails outright.
     */
    public void invalidate(Predicate<TaskHandle> which, String reason) {
        run(() -> {
            for (TaskHandle h : active.toArray(EMPTY)) {
                if (h.isDone() || !which.test(h)) {
                    continue;
                }
                if (h.started) {
                    attemptFailed(h, StopReason.FAILED, new TaskInvalidatedException(reason));
                } else {
                    finish(h, TaskState.FAILED, "Invalidated: " + reason);
                }
            }
        });
    }

    /**
     * Re-runs the {@link Guard} for running and suspended tasks right away instead of waiting for the next tick.
     * Called when the guard's inputs change (e.g. a permission was revoked).
     */
    public void recheckGuards() {
        run(this::checkGuards);
    }

    /** Cancels everything, releases all resources and rejects further submissions. */
    public void shutdown(String reason) {
        shutdown = true;
        run(() -> {
            for (TaskHandle h : active.toArray(EMPTY)) {
                if (!h.isDone()) {
                    finish(h, TaskState.CANCELLED, reason);
                }
            }
            holders.clear();
        });
    }

    public boolean isShutdown() {
        return shutdown;
    }

    /** Snapshot of unfinished tasks in submission order. */
    public List<TaskHandle> activeTasks() {
        if (!clientThread.isCurrent()) {
            throw new IllegalStateException("activeTasks() must be called on the client thread");
        }
        return List.copyOf(active);
    }

    /** Snapshot of the most recent finished tasks, oldest first. */
    public synchronized List<TaskRecord> history() {
        return List.copyOf(history);
    }

    /** The task currently holding a resource, or null. Client thread only. */
    public TaskHandle holderOf(TaskResource resource) {
        return holders.get(resource);
    }

    private void run(Runnable r) {
        if (clientThread.isCurrent() && !processing) {
            r.run();
        } else {
            commands.add(r);
        }
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        if (!clientThread.isCurrent()) {
            throw new IllegalStateException("TaskScheduler.tick() must run on the client thread");
        }
        Runnable cmd;
        while ((cmd = commands.poll()) != null) {
            cmd.run();
        }
        tick++;
        processing = true;
        try {
            process();
        } finally {
            processing = false;
        }
        // Apply anything the tasks queued during processing so it is visible right after this tick.
        while ((cmd = commands.poll()) != null) {
            cmd.run();
        }
    }

    private void add(TaskHandle h) {
        // Dependencies must already exist when a task is created, so cycles cannot be built through this API.
        // Check anyway: a cycle would leave every task in it PENDING forever.
        for (TaskHandle dep : h.options().dependencies()) {
            if (dep == h || dependsOn(dep, h)) {
                finish(h, TaskState.FAILED, "Dependency cycle through '" + dep.name() + "'");
                return;
            }
        }
        active.add(h);
        for (TaskHandle dep : h.options().dependencies()) {
            dep.dependents.add(h);
        }
        bus.post(new TaskEvent(h, null, TaskState.PENDING));
        h.setStatus(h.options().dependencies().isEmpty() ? "Queued" : "Waiting for dependencies");
        resolveDependencies(h);
    }

    private void process() {
        // 1. deadlines, dependencies, backoff
        for (TaskHandle h : active.toArray(EMPTY)) {
            if (h.isDone()) {
                continue;
            }
            int deadline = h.options().deadlineTicks();
            if (deadline > 0 && tick - h.submittedTick >= deadline) {
                finish(h, TaskState.TIMED_OUT, "Deadline of " + deadline + " ticks exceeded");
                continue;
            }
            if (h.state() == TaskState.PENDING) {
                resolveDependencies(h);
            } else if (h.state() == TaskState.BACKOFF && tick >= h.retryAtTick) {
                transition(h, TaskState.READY);
                h.setStatus("Retrying (attempt " + (h.attempts() + 1) + ")");
            }
        }

        // 2. guard re-check for tasks that are running or suspended
        checkGuards();
        Guard g = guard;

        // 3. resource allocation
        List<TaskHandle> candidates = new ArrayList<>();
        for (TaskHandle h : active) {
            TaskState s = h.state();
            if (s == TaskState.READY || s == TaskState.RUNNING || s == TaskState.SUSPENDED) {
                candidates.add(h);
            }
        }
        candidates.sort(ALLOCATION_ORDER);
        used.clear();
        holders.clear();
        runOrder.clear();
        for (TaskHandle h : candidates) {
            Set<TaskResource> need = h.task().resources();
            if (Collections.disjoint(used, need)) {
                used.addAll(need);
                for (TaskResource r : need) {
                    holders.put(r, h);
                }
                runOrder.add(h);
            } else if (h.state() == TaskState.RUNNING) {
                releaseResources(h);
                transition(h, TaskState.SUSPENDED);
                h.setStatus("Suspended by a higher-priority task");
                try {
                    h.task().suspend(h.context);
                } catch (RuntimeException e) {
                    LOG.error("{}: suspend() threw", h, e);
                }
            } else if (h.state() == TaskState.READY) {
                // Fail fast: a task that would be refused anyway should not sit waiting for resources.
                String denied = safeDeny(g, h);
                if (denied != null) {
                    finish(h, TaskState.FAILED, NOT_PERMITTED_PREFIX + denied);
                } else {
                    h.setStatus("Waiting for resources");
                }
            }
        }
        for (TaskHandle h : runOrder) {
            if (h.state() == TaskState.READY) {
                String denied = safeDeny(g, h);
                if (denied != null) {
                    finish(h, TaskState.FAILED, NOT_PERMITTED_PREFIX + denied);
                    continue;
                }
                startAttempt(h);
            } else if (h.state() == TaskState.SUSPENDED) {
                transition(h, TaskState.RUNNING);
                h.setStatus("Resumed");
                try {
                    h.task().resume(h.context);
                } catch (Exception e) {
                    attemptFailed(h, StopReason.FAILED, e);
                }
            }
        }

        // 4. run
        for (TaskHandle h : runOrder) {
            if (h.state() != TaskState.RUNNING) {
                continue;
            }
            boolean done;
            try {
                done = h.task().tick(h.context);
            } catch (Exception e) {
                attemptFailed(h, StopReason.FAILED, e);
                continue;
            }
            if (h.state() != TaskState.RUNNING) {
                continue; // cancelled from inside its own tick
            }
            h.runningTicks++;
            if (done) {
                stopAttempt(h, StopReason.SUCCEEDED);
                h.setProgress(1, null);
                finish(h, TaskState.SUCCEEDED, null);
            } else {
                int timeout = h.options().timeoutTicks();
                if (timeout > 0 && h.runningTicks >= timeout) {
                    attemptFailed(h, StopReason.TIMED_OUT, new TaskException("Timed out after " + timeout + " ticks"));
                }
            }
        }
    }

    private void checkGuards() {
        Guard g = guard;
        for (TaskHandle h : active.toArray(EMPTY)) {
            if (h.state() == TaskState.RUNNING || h.state() == TaskState.SUSPENDED) {
                String denied = safeDeny(g, h);
                if (denied != null) {
                    stopAttempt(h, StopReason.NOT_PERMITTED);
                    finish(h, TaskState.FAILED, NOT_PERMITTED_PREFIX + denied);
                }
            }
        }
    }

    private static boolean dependsOn(TaskHandle from, TaskHandle target) {
        ArrayDeque<TaskHandle> stack = new ArrayDeque<>(from.options().dependencies());
        java.util.Set<TaskHandle> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (!stack.isEmpty()) {
            TaskHandle t = stack.pop();
            if (t == target) {
                return true;
            }
            if (seen.add(t)) {
                stack.addAll(t.options().dependencies());
            }
        }
        return false;
    }

    private void releaseResources(TaskHandle h) {
        holders.values().removeIf(v -> v == h);
    }

    private String safeDeny(Guard g, TaskHandle h) {
        try {
            return g.deny(h.task());
        } catch (RuntimeException e) {
            LOG.error("Task guard threw for {}; denying", h, e);
            return "guard error: " + e;
        }
    }

    private void resolveDependencies(TaskHandle h) {
        boolean allDone = true;
        for (TaskHandle dep : h.options().dependencies()) {
            TaskState s = dep.state();
            if (s == TaskState.SUCCEEDED) {
                continue;
            }
            if (s.isTerminal()) {
                finish(h, s == TaskState.CANCELLED ? TaskState.CANCELLED : TaskState.FAILED,
                        "Dependency '" + dep.name() + "' " + s.name().toLowerCase(java.util.Locale.ROOT));
                return;
            }
            allDone = false;
        }
        if (allDone) {
            transition(h, TaskState.READY);
            h.setStatus("Waiting for resources");
        }
    }

    private void startAttempt(TaskHandle h) {
        h.incrementAttempts();
        h.runningTicks = 0;
        h.context = new TaskContext(h, this, asyncExecutor);
        h.started = true;
        transition(h, TaskState.RUNNING);
        h.setStatus("Running");
        try {
            h.task().start(h.context);
        } catch (Exception e) {
            attemptFailed(h, StopReason.FAILED, e);
        }
    }

    private void stopAttempt(TaskHandle h, StopReason reason) {
        releaseResources(h);
        if (!h.started) {
            return;
        }
        h.started = false;
        for (AsyncResult<?> a : h.asyncWork) {
            a.cancel();
        }
        h.asyncWork.clear();
        try {
            h.task().stop(h.context, reason);
        } catch (RuntimeException e) {
            LOG.error("{}: stop({}) threw", h, reason, e);
        }
    }

    private void attemptFailed(TaskHandle h, StopReason reason, Throwable error) {
        stopAttempt(h, reason);
        String msg = describe(error);
        h.setError(msg);
        boolean retry = false;
        if (h.attempts() < h.options().retry().maxAttempts()) {
            try {
                retry = h.task().recover(h.context, error) == Recovery.RETRY;
            } catch (RuntimeException e) {
                LOG.error("{}: recover() threw; giving up", h, e);
            }
        }
        if (retry) {
            int backoff = h.options().retry().backoffBefore(h.attempts() + 1);
            h.retryAtTick = tick + backoff;
            transition(h, TaskState.BACKOFF);
            h.setStatus("Attempt " + h.attempts() + " failed (" + msg + "); retrying in " + backoff + " ticks");
            LOG.warn("{} attempt {} failed: {}; retrying in {} ticks", h, h.attempts(), msg, backoff);
        } else {
            finish(h, reason == StopReason.TIMED_OUT ? TaskState.TIMED_OUT : TaskState.FAILED, msg);
        }
    }

    private void finish(TaskHandle h, TaskState terminal, String error) {
        if (h.isDone()) {
            return;
        }
        if (h.started) {
            stopAttempt(h, switch (terminal) {
                case SUCCEEDED -> StopReason.SUCCEEDED;
                case TIMED_OUT -> StopReason.TIMED_OUT;
                case CANCELLED -> StopReason.CANCELLED;
                default -> StopReason.FAILED;
            });
        }
        if (error != null) {
            h.setError(error);
            h.setStatus(error);
        } else if (terminal == TaskState.SUCCEEDED) {
            h.setStatus("Done");
        }
        active.remove(h);
        releaseResources(h);
        transition(h, terminal);
        synchronized (this) {
            history.addLast(new TaskRecord(h.id(), h.name(), h.options().owner(), terminal, h.attempts(), h.error(),
                    h.submittedTick, tick));
            while (history.size() > HISTORY_LIMIT) {
                history.removeFirst();
            }
        }
        if (terminal == TaskState.FAILED || terminal == TaskState.TIMED_OUT) {
            LOG.warn("{} finished {}: {}", h, terminal, h.error());
        }
        // Dependents cannot succeed any more (or must be resolved now that this succeeded).
        for (TaskHandle d : h.dependents.toArray(EMPTY)) {
            if (d.state() == TaskState.PENDING) {
                resolveDependencies(d);
            }
        }
    }

    private void transition(TaskHandle h, TaskState next) {
        TaskState prev = h.state();
        if (prev == next) {
            return;
        }
        h.setState(next);
        bus.post(new TaskEvent(h, prev, next));
    }

    private static String describe(Throwable t) {
        if (t instanceof TaskException && t.getMessage() != null) {
            return t.getMessage();
        }
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null || m.isBlank() ? "" : ": " + m);
    }
}
