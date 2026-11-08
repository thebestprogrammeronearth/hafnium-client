package dev.hafnium.client.core;

/**
 * Marker for deeply immutable data that may be handed to background workers. The task API only lets worker threads
 * see values of this type, so they never touch live game or world-model objects.
 *
 * <p>Implementations must be final, hold only immutable data (records, {@code List.copyOf}, primitives, strings) and
 * be fully built on the client thread before being shared.
 */
public interface ImmutableSnapshot {
}
