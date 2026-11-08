package dev.hafnium.client.core.task;

/** The world a task depended on changed under it (disconnect, dimension change, chunk unload). */
public class TaskInvalidatedException extends TaskException {
    public TaskInvalidatedException(String message) {
        super(message);
    }
}
