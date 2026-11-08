package dev.hafnium.client.core.event;

/** Handle returned by {@link EventBus#subscribe}. Closing it is idempotent. */
public interface Subscription extends AutoCloseable {
    @Override
    void close();

    boolean isActive();
}
