package dev.hafnium.client.core.task;

/** Why {@link Task#stop} is being called. */
public enum StopReason {
    SUCCEEDED,
    /** The attempt threw or reported failure (a retry may follow). */
    FAILED,
    /** The attempt exceeded its timeout (a retry may follow). */
    TIMED_OUT,
    CANCELLED,
    /** The permission layer (or another guard) no longer allows the task. */
    NOT_PERMITTED
}
