package dev.hafnium.client.core.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** How a task is scheduled. Build with {@link #builder()}. */
public final class TaskOptions {
    public static final TaskOptions DEFAULT = builder().build();

    private final int priority;
    private final List<TaskHandle> dependencies;
    private final int timeoutTicks;
    private final int deadlineTicks;
    private final RetryPolicy retry;
    private final boolean interruptible;
    private final Object owner;

    private TaskOptions(Builder b) {
        this.priority = b.priority;
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(b.dependencies));
        this.timeoutTicks = b.timeoutTicks;
        this.deadlineTicks = b.deadlineTicks;
        this.retry = b.retry;
        this.interruptible = b.interruptible;
        this.owner = b.owner;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int priority() {
        return priority;
    }

    public List<TaskHandle> dependencies() {
        return dependencies;
    }

    /** Max running ticks per attempt (0 = none). Time spent suspended does not count. */
    public int timeoutTicks() {
        return timeoutTicks;
    }

    /** Max ticks from submission to completion, including waiting (0 = none). */
    public int deadlineTicks() {
        return deadlineTicks;
    }

    public RetryPolicy retry() {
        return retry;
    }

    public boolean interruptible() {
        return interruptible;
    }

    public Object owner() {
        return owner;
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.priority = priority;
        b.dependencies.addAll(dependencies);
        b.timeoutTicks = timeoutTicks;
        b.deadlineTicks = deadlineTicks;
        b.retry = retry;
        b.interruptible = interruptible;
        b.owner = owner;
        return b;
    }

    public static final class Builder {
        private int priority;
        private final List<TaskHandle> dependencies = new ArrayList<>();
        private int timeoutTicks;
        private int deadlineTicks;
        private RetryPolicy retry = RetryPolicy.NONE;
        private boolean interruptible = true;
        private Object owner;

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder after(TaskHandle... handles) {
            Collections.addAll(dependencies, handles);
            return this;
        }

        public Builder timeoutTicks(int ticks) {
            if (ticks < 0) {
                throw new IllegalArgumentException("timeout must be >= 0");
            }
            this.timeoutTicks = ticks;
            return this;
        }

        public Builder deadlineTicks(int ticks) {
            if (ticks < 0) {
                throw new IllegalArgumentException("deadline must be >= 0");
            }
            this.deadlineTicks = ticks;
            return this;
        }

        public Builder retry(RetryPolicy retry) {
            this.retry = java.util.Objects.requireNonNull(retry);
            return this;
        }

        /** Non-interruptible tasks keep their resources until they finish, even against higher priority. */
        public Builder interruptible(boolean interruptible) {
            this.interruptible = interruptible;
            return this;
        }

        public Builder owner(Object owner) {
            this.owner = owner;
            return this;
        }

        public TaskOptions build() {
            return new TaskOptions(this);
        }
    }
}
