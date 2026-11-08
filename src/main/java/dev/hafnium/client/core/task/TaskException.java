package dev.hafnium.client.core.task;

/** Thrown by a task to fail the current attempt with a clear message. */
public class TaskException extends RuntimeException {
    public TaskException(String message) {
        super(message);
    }

    public TaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
