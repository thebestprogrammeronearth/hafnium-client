package dev.hafnium.client.core.task;

/**
 * Exclusive resources a task may hold. Two tasks needing the same resource never run at the same time; the
 * higher-priority one wins and an interruptible holder is suspended.
 */
public enum TaskResource {
    MOVEMENT,
    LOOK,
    INTERACTION,
    INVENTORY,
    CHAT
}
