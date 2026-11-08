package dev.hafnium.client.core.task;

/** Summary of a finished task, kept in the scheduler's bounded history. */
public record TaskRecord(long id, String name, Object owner, TaskState result, int attempts, String error,
                         long submittedTick, long finishedTick) {
}
