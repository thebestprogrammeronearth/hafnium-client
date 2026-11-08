package dev.hafnium.client.core.task;

public enum TaskState {
    /** Waiting for dependencies. */
    PENDING(false),
    /** Runnable; waiting for its resources. */
    READY(false),
    RUNNING(false),
    /** Preempted by a higher-priority task that needed the same resources; resumes later. */
    SUSPENDED(false),
    /** Failed an attempt; waiting out the retry backoff. */
    BACKOFF(false),
    SUCCEEDED(true),
    FAILED(true),
    CANCELLED(true),
    TIMED_OUT(true);

    private final boolean terminal;

    TaskState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
