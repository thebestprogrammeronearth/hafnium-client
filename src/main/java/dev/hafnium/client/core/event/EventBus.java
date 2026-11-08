package dev.hafnium.client.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Synchronous, typed event bus.
 *
 * <ul>
 *   <li>Dispatch is by exact event class (no hierarchy walk), in descending priority, then registration order.</li>
 *   <li>Subscribing and unsubscribing are thread-safe and may happen during dispatch; a dispatch in progress uses the
 *       handler list as it was when it started.</li>
 *   <li>A handler that throws does not stop delivery to the others. The failure goes to the {@link FailureHandler}
 *       together with the subscription's owner (for example the module that subscribed).</li>
 *   <li>Events are delivered on the thread that posts them; game events are posted on the client thread.</li>
 * </ul>
 */
public final class EventBus {
    private static final Logger LOG = LoggerFactory.getLogger("Hafnium/EventBus");

    /** Receives handler failures. */
    @FunctionalInterface
    public interface FailureHandler {
        void handlerFailed(Object owner, Object event, Throwable error);
    }

    private final Map<Class<?>, CopyOnWriteArrayList<Handler<?>>> handlers = new ConcurrentHashMap<>();
    private volatile FailureHandler failureHandler = (owner, event, error) ->
            LOG.error("Event handler of {} failed on {}", owner, event.getClass().getSimpleName(), error);
    private long sequence;

    public void setFailureHandler(FailureHandler handler) {
        this.failureHandler = Objects.requireNonNull(handler);
    }

    public <E> Subscription subscribe(Class<E> type, Object owner, Consumer<? super E> handler) {
        return subscribe(type, EventPriority.NORMAL, owner, handler);
    }

    public <E> Subscription subscribe(Class<E> type, int priority, Object owner, Consumer<? super E> handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(handler, "handler");
        CopyOnWriteArrayList<Handler<?>> list = handlers.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>());
        Handler<E> h;
        synchronized (list) {
            h = new Handler<>(list, priority, nextSequence(), owner, handler);
            int i = 0;
            while (i < list.size() && list.get(i).priority >= priority) {
                i++;
            }
            list.add(i, h);
        }
        return h;
    }

    private synchronized long nextSequence() {
        return sequence++;
    }

    /** Delivers {@code event} to its handlers and returns it (so callers can read results / cancellation). */
    @SuppressWarnings("unchecked")
    public <E> E post(E event) {
        CopyOnWriteArrayList<Handler<?>> list = handlers.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return event;
        }
        Cancellable cancellable = event instanceof Cancellable c ? c : null;
        for (Handler<?> raw : list) {
            if (cancellable != null && cancellable.isCancelled()) {
                break;
            }
            Handler<E> h = (Handler<E>) raw;
            if (!h.active) {
                continue;
            }
            try {
                h.consumer.accept(event);
            } catch (VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable t) {
                try {
                    failureHandler.handlerFailed(h.owner, event, t);
                } catch (RuntimeException reporting) {
                    LOG.error("Failure handler threw while reporting a handler failure", reporting);
                }
            }
        }
        return event;
    }

    public boolean hasSubscribers(Class<?> type) {
        CopyOnWriteArrayList<Handler<?>> list = handlers.get(type);
        return list != null && !list.isEmpty();
    }

    public int subscriberCount(Class<?> type) {
        CopyOnWriteArrayList<Handler<?>> list = handlers.get(type);
        return list == null ? 0 : list.size();
    }

    /** Number of live subscriptions owned by {@code owner}, across all event types. */
    public int countOwnedBy(Object owner) {
        int n = 0;
        for (CopyOnWriteArrayList<Handler<?>> list : handlers.values()) {
            for (Handler<?> h : list) {
                if (h.owner == owner) {
                    n++;
                }
            }
        }
        return n;
    }

    private static final class Handler<E> implements Subscription {
        final CopyOnWriteArrayList<Handler<?>> list;
        final int priority;
        final long sequence;
        final Object owner;
        final Consumer<? super E> consumer;
        volatile boolean active = true;

        Handler(CopyOnWriteArrayList<Handler<?>> list, int priority, long sequence, Object owner, Consumer<? super E> consumer) {
            this.list = list;
            this.priority = priority;
            this.sequence = sequence;
            this.owner = owner;
            this.consumer = consumer;
        }

        @Override
        public void close() {
            if (active) {
                active = false;
                synchronized (list) {
                    list.remove(this);
                }
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }
}
