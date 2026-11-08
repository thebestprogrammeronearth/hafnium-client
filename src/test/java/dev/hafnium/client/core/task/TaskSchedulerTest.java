package dev.hafnium.client.core.task;

import dev.hafnium.client.core.ClientThread;
import dev.hafnium.client.core.ImmutableSnapshot;
import dev.hafnium.client.core.event.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static dev.hafnium.client.core.task.TaskResource.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskSchedulerTest {
    EventBus bus;
    TaskScheduler s;
    List<TaskEvent> events;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        s = new TaskScheduler(bus, ClientThread.DIRECT, Runnable::run);
        events = new ArrayList<>();
        bus.subscribe(TaskEvent.class, "test", events::add);
    }

    void ticks(int n) {
        for (int i = 0; i < n; i++) {
            s.tick();
        }
    }

    @Test
    void runsToCompletionWithLifecycleAndEvents() {
        ScriptedTask t = new ScriptedTask("t", 3, MOVEMENT);
        TaskHandle h = s.submit(t);
        assertEquals(TaskState.READY, h.state());
        ticks(3);
        assertEquals(TaskState.SUCCEEDED, h.state());
        assertEquals(List.of("start1", "stop:SUCCEEDED"), t.log);
        assertEquals(1.0, h.progress());
        assertTrue(h.completion().isDone());
        assertNull(s.holderOf(MOVEMENT), "resources released on success");
        assertEquals(List.of(TaskState.PENDING, TaskState.READY, TaskState.RUNNING, TaskState.SUCCEEDED),
                events.stream().map(TaskEvent::current).toList());
        assertEquals(TaskState.SUCCEEDED, s.history().get(0).result());
    }

    @Test
    void higherPriorityPreemptsInterruptibleHolderWhichResumesLater() {
        ScriptedTask low = new ScriptedTask("low", 10, MOVEMENT);
        TaskHandle lh = s.submit(low, TaskOptions.builder().priority(1).build());
        ticks(2);
        assertEquals(TaskState.RUNNING, lh.state());
        ScriptedTask high = new ScriptedTask("high", 3, MOVEMENT, LOOK);
        TaskHandle hh = s.submit(high, TaskOptions.builder().priority(10).build());
        s.tick();
        assertEquals(TaskState.SUSPENDED, lh.state());
        assertEquals(TaskState.RUNNING, hh.state());
        assertSame(hh, s.holderOf(MOVEMENT));
        assertTrue(low.log.contains("suspend"));
        ticks(2);
        assertEquals(TaskState.SUCCEEDED, hh.state());
        s.tick();
        assertEquals(TaskState.RUNNING, lh.state());
        assertTrue(low.log.contains("resume"));
        assertSame(lh, s.holderOf(MOVEMENT));
        assertNull(s.holderOf(LOOK));
    }

    @Test
    void nonInterruptibleHolderKeepsResourcesAgainstHigherPriority() {
        TaskHandle lh = s.submit(new ScriptedTask("low", 4, MOVEMENT),
                TaskOptions.builder().priority(1).interruptible(false).build());
        s.tick();
        TaskHandle hh = s.submit(new ScriptedTask("high", 1, MOVEMENT), TaskOptions.builder().priority(99).build());
        ticks(2);
        assertEquals(TaskState.RUNNING, lh.state());
        assertEquals(TaskState.READY, hh.state());
        assertEquals("Waiting for resources", hh.status());
        ticks(2);
        assertEquals(TaskState.SUCCEEDED, lh.state());
        s.tick();
        assertEquals(TaskState.SUCCEEDED, hh.state());
    }

    @Test
    void contentionEqualPriorityIsFifoAndDisjointResourcesRunTogether() {
        TaskHandle a = s.submit(new ScriptedTask("a", 3, MOVEMENT));
        TaskHandle b = s.submit(new ScriptedTask("b", 3, MOVEMENT));
        TaskHandle c = s.submit(new ScriptedTask("c", 3, INVENTORY));
        s.tick();
        assertEquals(TaskState.RUNNING, a.state());
        assertEquals(TaskState.READY, b.state());
        assertEquals(TaskState.RUNNING, c.state(), "disjoint resources run concurrently");
        ticks(2);
        assertEquals(TaskState.SUCCEEDED, a.state());
        ticks(3);
        assertEquals(TaskState.SUCCEEDED, b.state());
    }

    @Test
    void timeoutFailsAttemptStopsTaskAndReleasesResources() {
        ScriptedTask t = new ScriptedTask("slow", -1, MOVEMENT);
        TaskHandle h = s.submit(t, TaskOptions.builder().timeoutTicks(5).build());
        ticks(4);
        assertEquals(TaskState.RUNNING, h.state());
        s.tick();
        assertEquals(TaskState.TIMED_OUT, h.state());
        assertEquals(5, t.ticked);
        assertTrue(t.log.contains("stop:TIMED_OUT"));
        assertNull(s.holderOf(MOVEMENT));
        assertTrue(h.error().contains("Timed out"));
    }

    @Test
    void suspendedTimeDoesNotCountTowardsTimeout() {
        ScriptedTask low = new ScriptedTask("low", -1, MOVEMENT);
        TaskHandle lh = s.submit(low, TaskOptions.builder().timeoutTicks(4).build());
        ticks(2);
        s.submit(new ScriptedTask("high", 10, MOVEMENT), TaskOptions.builder().priority(5).build());
        ticks(10);
        assertEquals(TaskState.SUSPENDED, lh.state(), "10 suspended ticks must not time it out");
        ticks(3);
        assertEquals(TaskState.TIMED_OUT, lh.state());
        assertEquals(4, low.ticked);
    }

    @Test
    void deadlineCoversWaitingTime() {
        TaskHandle blocker = s.submit(new ScriptedTask("blocker", -1, MOVEMENT), TaskOptions.builder().interruptible(false).build());
        TaskHandle waiter = s.submit(new ScriptedTask("waiter", 1, MOVEMENT), TaskOptions.builder().deadlineTicks(6).build());
        ticks(6);
        assertEquals(TaskState.TIMED_OUT, waiter.state());
        assertTrue(waiter.error().contains("Deadline"));
        assertEquals(TaskState.RUNNING, blocker.state());
    }

    @Test
    void retriesWithExponentialBackoffThenSucceeds() {
        ScriptedTask t = new ScriptedTask("flaky", 1, MOVEMENT);
        t.failOnAttempt = a -> a <= 2;
        TaskHandle h = s.submit(t, TaskOptions.builder().retry(new RetryPolicy(3, 2, 2.0, 16)).build());
        s.tick(); // attempt 1 fails
        assertEquals(TaskState.BACKOFF, h.state());
        assertNull(s.holderOf(MOVEMENT), "resources released during backoff");
        ticks(2); // backoff 2 -> READY then attempt 2 fails
        assertEquals(TaskState.BACKOFF, h.state());
        assertEquals(2, h.attempts());
        ticks(3);
        assertEquals(TaskState.BACKOFF, h.state(), "second backoff is 4 ticks");
        ticks(1);
        assertEquals(TaskState.SUCCEEDED, h.state());
        assertEquals(3, h.attempts());
        assertEquals(List.of("start1", "stop:FAILED", "recover", "start2", "stop:FAILED", "recover", "start3", "stop:SUCCEEDED"), t.log);
    }

    @Test
    void retriesExhaustedOrRefusedByRecoveryFail() {
        ScriptedTask always = new ScriptedTask("always", 1);
        always.failOnAttempt = a -> true;
        TaskHandle h = s.submit(always, TaskOptions.builder().retry(RetryPolicy.attempts(2, 0)).build());
        ticks(5);
        assertEquals(TaskState.FAILED, h.state());
        assertEquals(2, h.attempts());
        assertEquals("boom on attempt 2", h.error());

        ScriptedTask refuse = new ScriptedTask("refuse", 1);
        refuse.failOnAttempt = a -> true;
        refuse.recovery = Recovery.FAIL;
        TaskHandle r = s.submit(refuse, TaskOptions.builder().retry(RetryPolicy.attempts(5, 0)).build());
        ticks(3);
        assertEquals(TaskState.FAILED, r.state());
        assertEquals(1, r.attempts());
    }

    @Test
    void dependenciesRunInOrder() {
        TaskHandle a = s.submit(new ScriptedTask("a", 2));
        TaskHandle b = s.submit(new ScriptedTask("b", 1), TaskOptions.builder().after(a).build());
        assertEquals(TaskState.PENDING, b.state());
        ticks(2);
        assertEquals(TaskState.SUCCEEDED, a.state());
        assertEquals(TaskState.READY, b.state());
        s.tick();
        assertEquals(TaskState.SUCCEEDED, b.state());
    }

    @Test
    void cancellationCascadesThroughDependents() {
        ScriptedTask at = new ScriptedTask("a", -1, MOVEMENT);
        TaskHandle a = s.submit(at);
        TaskHandle b = s.submit(new ScriptedTask("b", 1), TaskOptions.builder().after(a).build());
        TaskHandle c = s.submit(new ScriptedTask("c", 1), TaskOptions.builder().after(b).build());
        TaskHandle d = s.submit(new ScriptedTask("d", 1), TaskOptions.builder().after(a, c).build());
        ticks(2);
        a.cancel("user");
        for (TaskHandle h : List.of(a, b, c, d)) {
            assertEquals(TaskState.CANCELLED, h.state(), h.name());
        }
        assertTrue(at.log.contains("stop:CANCELLED"));
        assertNull(s.holderOf(MOVEMENT));
        assertTrue(b.error().contains("'a'"));
    }

    @Test
    void failureCascadesAsFailure() {
        ScriptedTask bad = new ScriptedTask("bad", 1);
        bad.failOnAttempt = x -> true;
        TaskHandle a = s.submit(bad);
        TaskHandle b = s.submit(new ScriptedTask("b", 1), TaskOptions.builder().after(a).build());
        s.tick();
        assertEquals(TaskState.FAILED, b.state());
        assertTrue(b.error().contains("Dependency 'bad' failed"));
    }

    @Test
    void cancellingSuspendedTaskStopsItAndKeepsHolderConsistent() {
        ScriptedTask low = new ScriptedTask("low", -1, MOVEMENT);
        TaskHandle lh = s.submit(low);
        s.tick();
        TaskHandle hh = s.submit(new ScriptedTask("high", -1, MOVEMENT), TaskOptions.builder().priority(5).build());
        s.tick();
        assertEquals(TaskState.SUSPENDED, lh.state());
        lh.cancel("no longer needed");
        assertEquals(List.of("start1", "suspend", "stop:CANCELLED"), low.log);
        assertSame(hh, s.holderOf(MOVEMENT));
        hh.cancel("done");
        assertNull(s.holderOf(MOVEMENT));
    }

    @Test
    void guardDeniesStartAndRunning() {
        String[] deny = {null};
        s.setGuard(task -> deny[0]);
        ScriptedTask t = new ScriptedTask("t", -1, MOVEMENT);
        TaskHandle h = s.submit(t);
        s.tick();
        assertEquals(TaskState.RUNNING, h.state());
        deny[0] = "revoked";
        s.recheckGuards();
        assertEquals(TaskState.FAILED, h.state(), "re-check acts immediately, without a tick");
        assertEquals("Not permitted: revoked", h.error());
        assertTrue(t.log.contains("stop:NOT_PERMITTED"));
        assertNull(s.holderOf(MOVEMENT));

        TaskHandle h2 = s.submit(new ScriptedTask("t2", 1));
        s.tick();
        assertEquals(TaskState.FAILED, h2.state());
        assertEquals(0, h2.attempts(), "a denied task never starts");
    }

    @Test
    void invalidationFailsAttemptThroughRecovery() {
        ScriptedTask t = new ScriptedTask("t", -1, MOVEMENT);
        TaskHandle h = s.submit(t, TaskOptions.builder().retry(RetryPolicy.attempts(2, 1)).build());
        s.tick();
        s.invalidate(x -> x == h, "chunk unloaded");
        assertEquals(TaskState.BACKOFF, h.state());
        assertNull(s.holderOf(MOVEMENT));
        assertTrue(t.log.contains("recover"));
        ticks(2);
        s.invalidate(x -> x == h, "chunk unloaded again");
        assertEquals(TaskState.FAILED, h.state());
        assertEquals("chunk unloaded again", h.error());
    }

    @Test
    void shutdownCancelsEverythingReleasesResourcesAndRejectsSubmissions() {
        ScriptedTask t = new ScriptedTask("t", -1, MOVEMENT, LOOK);
        TaskHandle h = s.submit(t);
        TaskHandle waiting = s.submit(new ScriptedTask("w", 1), TaskOptions.builder().after(h).build());
        s.tick();
        s.shutdown("bye");
        assertEquals(TaskState.CANCELLED, h.state());
        assertEquals(TaskState.CANCELLED, waiting.state());
        assertTrue(t.log.contains("stop:CANCELLED"));
        assertNull(s.holderOf(MOVEMENT));
        assertNull(s.holderOf(LOOK));
        assertThrows(IllegalStateException.class, () -> s.submit(new ScriptedTask("late", 1)));
    }

    @Test
    void exceptionInStartGoesThroughRecovery() {
        Task t = new ScriptedTask("x", 1) {
            @Override
            public void start(TaskContext ctx) {
                throw new IllegalStateException("cannot start");
            }
        };
        TaskHandle h = s.submit(t);
        s.tick();
        assertEquals(TaskState.FAILED, h.state());
        assertTrue(h.error().contains("cannot start"));
    }

    @Test
    void submissionsFromOtherThreadsAreQueuedForTheClientThread() throws Exception {
        Thread owner = Thread.currentThread();
        Queue<Runnable> queue = new ArrayDeque<>();
        ClientThread thread = new ClientThread() {
            @Override
            public boolean isCurrent() {
                return Thread.currentThread() == owner;
            }

            @Override
            public synchronized void execute(Runnable task) {
                queue.add(task);
            }
        };
        TaskScheduler qs = new TaskScheduler(bus, thread, Runnable::run);
        TaskHandle[] h = new TaskHandle[1];
        Thread worker = new Thread(() -> h[0] = qs.submit(new ScriptedTask("remote", 1)));
        worker.start();
        worker.join();
        assertEquals(TaskState.PENDING, h[0].state(), "not applied off-thread");
        qs.tick();
        assertEquals(TaskState.SUCCEEDED, h[0].state());
    }

    @Test
    void tasksMaySubmitAndCancelDuringTheirOwnTick() {
        List<TaskHandle> spawned = new ArrayList<>();
        Task parent = new ScriptedTask("parent", 1) {
            @Override
            public boolean tick(TaskContext ctx) {
                spawned.add(s.submit(new ScriptedTask("child", 1)));
                ctx.handle().cancel("self-cancel");
                return false;
            }
        };
        TaskHandle p = s.submit(parent);
        s.tick();
        assertEquals(TaskState.CANCELLED, p.state());
        assertEquals(1, spawned.size());
        s.tick();
        assertEquals(TaskState.SUCCEEDED, spawned.get(0).state());
    }

    record Numbers(List<Integer> values) implements ImmutableSnapshot {
        Numbers {
            values = List.copyOf(values);
        }
    }

    @Test
    void asyncWorkSeesOnlyTheSnapshotAndIsCancelledWithTheAttempt() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            TaskScheduler as = new TaskScheduler(bus, ClientThread.DIRECT, pool);
            CountDownLatch release = new CountDownLatch(1);
            AsyncResult<?>[] slow = new AsyncResult<?>[1];
            Task t = new Task() {
                AsyncResult<Integer> sum;

                @Override
                public String name() {
                    return "async";
                }

                @Override
                public boolean tick(TaskContext ctx) {
                    if (sum == null) {
                        sum = ctx.async(new Numbers(List.of(1, 2, 3)),
                                n -> n.values().stream().mapToInt(Integer::intValue).sum());
                        slow[0] = ctx.async(new Numbers(List.of()), n -> {
                            try {
                                release.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return 0;
                        });
                    }
                    return sum.isDone() && sum.get() == 6;
                }
            };
            TaskHandle h = as.submit(t);
            for (int i = 0; i < 200 && !h.isDone(); i++) {
                as.tick();
                Thread.sleep(2);
            }
            assertEquals(TaskState.SUCCEEDED, h.state());
            assertTrue(slow[0].isDone() && slow[0].isFailed(), "unfinished background work is cancelled when the task ends");
            release.countDown();
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void dependencyFromAnotherSchedulerIsRejected() {
        TaskScheduler other = new TaskScheduler(bus, ClientThread.DIRECT, Runnable::run);
        TaskHandle foreign = other.submit(new ScriptedTask("f", 1));
        assertThrows(IllegalArgumentException.class,
                () -> s.submit(new ScriptedTask("x", 1), TaskOptions.builder().after(foreign).build()));
    }

    /** Random priorities, resources, durations and dependencies: everything must finish (no deadlock/starvation). */
    @Test
    void randomizedContentionAlwaysDrains() {
        Random rnd = new Random(42);
        TaskResource[] all = TaskResource.values();
        List<TaskHandle> handles = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            List<TaskResource> res = new ArrayList<>();
            for (TaskResource r : all) {
                if (rnd.nextInt(3) == 0) {
                    res.add(r);
                }
            }
            ScriptedTask t = new ScriptedTask("t" + i, 1 + rnd.nextInt(5), res.toArray(new TaskResource[0]));
            TaskOptions.Builder b = TaskOptions.builder().priority(rnd.nextInt(5)).interruptible(rnd.nextBoolean());
            if (!handles.isEmpty() && rnd.nextInt(4) == 0) {
                b.after(handles.get(rnd.nextInt(handles.size())));
            }
            handles.add(s.submit(t, b.build()));
            if (rnd.nextInt(10) == 0) {
                s.tick();
            }
        }
        for (int i = 0; i < 5000 && handles.stream().anyMatch(h -> !h.isDone()); i++) {
            s.tick();
        }
        assertTrue(handles.stream().allMatch(TaskHandle::isDone), "every task reached a terminal state");
        assertTrue(handles.stream().allMatch(h -> h.state() == TaskState.SUCCEEDED));
        for (TaskResource r : all) {
            assertNull(s.holderOf(r));
        }
    }
}
