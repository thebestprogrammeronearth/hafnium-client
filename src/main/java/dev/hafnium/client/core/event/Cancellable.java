package dev.hafnium.client.core.event;

/** An event that a handler can cancel; lower-priority handlers are then skipped. */
public interface Cancellable {
    boolean isCancelled();

    void cancel();
}
