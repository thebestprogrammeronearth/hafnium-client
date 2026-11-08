package dev.hafnium.client.core.task;

/** Posted on the event bus whenever a task changes state ({@code previous} is null on submission). */
public record TaskEvent(TaskHandle task, TaskState previous, TaskState current) {
}
