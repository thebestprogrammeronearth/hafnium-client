package dev.hafnium.client.core.task;

/** What {@link Task#recover} wants after a failed attempt. Retries are still bounded by the {@link RetryPolicy}. */
public enum Recovery {
    RETRY,
    FAIL
}
