package dev.hafnium.client.core;

import dev.hafnium.client.core.event.Cancellable;
import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.event.EventPriority;
import dev.hafnium.client.core.event.Subscription;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {
    record Ping(int n) {
    }

    static final class Stop implements Cancellable {
        boolean cancelled;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    @Test
    void deliversInPriorityThenRegistrationOrder() {
        EventBus bus = new EventBus();
        List<String> order = new ArrayList<>();
        bus.subscribe(Ping.class, EventPriority.LOW, "a", e -> order.add("low"));
        bus.subscribe(Ping.class, EventPriority.NORMAL, "b", e -> order.add("normal-1"));
        bus.subscribe(Ping.class, EventPriority.HIGH, "c", e -> order.add("high"));
        bus.subscribe(Ping.class, EventPriority.NORMAL, "d", e -> order.add("normal-2"));
        bus.post(new Ping(1));
        assertEquals(List.of("high", "normal-1", "normal-2", "low"), order);
    }

    @Test
    void dispatchIsByExactClass() {
        EventBus bus = new EventBus();
        AtomicInteger objects = new AtomicInteger();
        bus.subscribe(Object.class, "o", e -> objects.incrementAndGet());
        bus.post(new Ping(1));
        assertEquals(0, objects.get());
    }

    @Test
    void closedSubscriptionStopsReceivingAndCloseIsIdempotent() {
        EventBus bus = new EventBus();
        AtomicInteger hits = new AtomicInteger();
        Subscription s = bus.subscribe(Ping.class, "o", e -> hits.incrementAndGet());
        bus.post(new Ping(1));
        s.close();
        s.close();
        bus.post(new Ping(2));
        assertEquals(1, hits.get());
        assertFalse(s.isActive());
        assertEquals(0, bus.subscriberCount(Ping.class));
    }

    @Test
    void unsubscribingDuringDispatchSkipsLaterHandler() {
        EventBus bus = new EventBus();
        AtomicInteger second = new AtomicInteger();
        AtomicReference<Subscription> victim = new AtomicReference<>();
        bus.subscribe(Ping.class, EventPriority.HIGH, "first", e -> victim.get().close());
        victim.set(bus.subscribe(Ping.class, EventPriority.LOW, "second", e -> second.incrementAndGet()));
        bus.post(new Ping(1));
        assertEquals(0, second.get(), "a handler closed earlier in the same dispatch must not run");
    }

    @Test
    void failingHandlerIsReportedWithOwnerAndOthersStillRun() {
        EventBus bus = new EventBus();
        List<Object> failedOwners = new ArrayList<>();
        bus.setFailureHandler((owner, event, error) -> failedOwners.add(owner));
        AtomicInteger after = new AtomicInteger();
        bus.subscribe(Ping.class, EventPriority.HIGH, "bad", e -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(Ping.class, EventPriority.LOW, "good", e -> after.incrementAndGet());
        bus.post(new Ping(1));
        assertEquals(List.of("bad"), failedOwners);
        assertEquals(1, after.get());
    }

    @Test
    void cancellationSkipsLowerPriorityHandlers() {
        EventBus bus = new EventBus();
        AtomicInteger low = new AtomicInteger();
        bus.subscribe(Stop.class, EventPriority.HIGH, "h", Stop::cancel);
        bus.subscribe(Stop.class, EventPriority.LOW, "l", e -> low.incrementAndGet());
        assertTrue(bus.post(new Stop()).isCancelled());
        assertEquals(0, low.get());
    }

    @Test
    void concurrentSubscribeWhilePostingIsSafe() throws Exception {
        EventBus bus = new EventBus();
        AtomicInteger delivered = new AtomicInteger();
        int threads = 4;
        int perThread = 500;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int t = 0; t < threads; t++) {
            Thread th = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        Subscription s = bus.subscribe(Ping.class, i % 5 - 2, this, e -> delivered.incrementAndGet());
                        if (i % 2 == 0) {
                            s.close();
                        }
                    }
                } catch (Throwable e) {
                    failure.set(e);
                }
            });
            workers.add(th);
            th.start();
        }
        start.countDown();
        for (int i = 0; i < 2000; i++) {
            bus.post(new Ping(i));
        }
        for (Thread th : workers) {
            th.join();
        }
        assertNull(failure.get());
        assertEquals(threads * perThread / 2, bus.subscriberCount(Ping.class));
    }
}
