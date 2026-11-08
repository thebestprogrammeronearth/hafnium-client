package dev.hafnium.client.core;

import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.event.GameKeyEvent;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.module.ModuleManager;
import dev.hafnium.client.core.module.ModuleState;
import dev.hafnium.client.core.module.ModuleStateEvent;
import dev.hafnium.client.core.setting.BoolSetting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ModuleManagerTest {
    record Tick() {
    }

    /** Client thread stand-in: the test thread is the "client thread"; other threads queue work. */
    static final class QueueThread implements ClientThread {
        final Thread owner = Thread.currentThread();
        final Queue<Runnable> queue = new ArrayDeque<>();

        @Override
        public boolean isCurrent() {
            return Thread.currentThread() == owner;
        }

        @Override
        public synchronized void execute(Runnable task) {
            queue.add(task);
        }

        synchronized void drain() {
            Runnable r;
            while ((r = queue.poll()) != null) {
                r.run();
            }
        }
    }

    static class Counter extends Module {
        final AtomicInteger enables = new AtomicInteger();
        final AtomicInteger disables = new AtomicInteger();
        final AtomicInteger ticks = new AtomicInteger();
        boolean failOnEnable;
        boolean failOnTick;

        Counter(String name) {
            super(name, Category.MISC, "test module");
        }

        @Override
        protected void onEnable(ModuleContext ctx) throws Exception {
            enables.incrementAndGet();
            ctx.subscribe(Tick.class, e -> {
                ticks.incrementAndGet();
                if (failOnTick) {
                    throw new IllegalStateException("tick exploded");
                }
            });
            if (failOnEnable) {
                throw new java.io.IOException("missing resource");
            }
        }

        @Override
        protected void onDisable() {
            disables.incrementAndGet();
        }
    }

    EventBus bus;
    QueueThread thread;
    ModuleManager manager;
    List<ModuleStateEvent> stateEvents;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        thread = new QueueThread();
        manager = new ModuleManager(bus, thread, new dev.hafnium.client.core.task.TaskScheduler(bus, thread, Runnable::run));
        stateEvents = new ArrayList<>();
        bus.subscribe(ModuleStateEvent.class, "test", stateEvents::add);
    }

    @Test
    void idsAreDerivedValidatedAndUnique() {
        Counter a = manager.register(new Counter("Auto Thing"));
        assertEquals("misc.auto-thing", a.id());
        assertThrows(IllegalArgumentException.class, () -> manager.register(new Counter("Auto Thing")));
        assertThrows(IllegalArgumentException.class, () -> new Module("Bad Id", "x", Category.MISC, "") {
        });
        assertSame(a, manager.find("misc.auto-thing"));
        assertTrue(manager.byCategory(Category.MISC).contains(a));
    }

    @Test
    void enableSubscribesAndDisableRemovesEverything() {
        Counter m = manager.register(new Counter("Lifecycle"));
        manager.setEnabled(m, true);
        assertEquals(ModuleState.ENABLED, m.state());
        assertEquals(1, m.enables.get());
        bus.post(new Tick());
        assertEquals(1, m.ticks.get());
        assertEquals(1, bus.countOwnedBy(m));

        manager.setEnabled(m, false);
        assertEquals(ModuleState.DISABLED, m.state());
        assertEquals(1, m.disables.get());
        assertEquals(0, bus.countOwnedBy(m), "subscriptions must be removed on disable");
        bus.post(new Tick());
        assertEquals(1, m.ticks.get());
        assertEquals(2, stateEvents.size());
        assertEquals(ModuleState.ENABLED, stateEvents.get(0).current());
        assertEquals(ModuleState.DISABLED, stateEvents.get(1).current());
    }

    @Test
    void enablingTwiceIsANoOp() {
        Counter m = manager.register(new Counter("Twice"));
        manager.setEnabled(m, true);
        manager.setEnabled(m, true);
        assertEquals(1, m.enables.get());
        assertEquals(1, bus.countOwnedBy(m));
    }

    @Test
    void failingOnEnableMarksFailedAndCleansUp() {
        Counter m = manager.register(new Counter("Broken"));
        m.failOnEnable = true;
        manager.setEnabled(m, true);
        assertEquals(ModuleState.FAILED, m.state());
        assertFalse(m.isEnabled());
        assertTrue(m.lastError().contains("IOException") && m.lastError().contains("missing resource"), m.lastError());
        assertEquals(0, bus.countOwnedBy(m), "partial subscriptions must be undone");
        assertEquals(0, m.disables.get(), "onDisable must not run for a module that never started");

        m.failOnEnable = false;
        manager.setEnabled(m, true);
        assertEquals(ModuleState.ENABLED, m.state());
        assertNull(m.lastError(), "re-enabling clears the error");
    }

    @Test
    void handlerFailureShutsDownOnlyThatModule() {
        Counter bad = manager.register(new Counter("Bad"));
        Counter good = manager.register(new Counter("Good"));
        bad.failOnTick = true;
        manager.setEnabled(bad, true);
        manager.setEnabled(good, true);
        bus.post(new Tick());
        assertEquals(ModuleState.FAILED, bad.state());
        assertTrue(bad.lastError().contains("tick exploded"), bad.lastError());
        assertEquals(1, bad.disables.get());
        assertEquals(0, bus.countOwnedBy(bad));
        assertEquals(ModuleState.ENABLED, good.state());
        bus.post(new Tick());
        assertEquals(2, good.ticks.get());
        assertEquals(1, bad.ticks.get());
    }

    @Test
    void settingsOnlyModulesCannotBeEnabled() {
        Module settingsOnly = manager.register(new Module("Prefs", Category.CLIENT, "settings only") {
            final BoolSetting flag = setting(new BoolSetting("Flag", true));

            @Override
            public boolean isToggleable() {
                return false;
            }
        });
        manager.setEnabled(settingsOnly, true);
        assertEquals(ModuleState.DISABLED, settingsOnly.state());
        assertNull(settingsOnly.setting("bind"), "settings-only modules have no keybind");
        assertNotNull(settingsOnly.setting("flag"));
    }

    @Test
    void settingsAreFrozenAfterRegistrationAndBindIsLast() {
        Module m = manager.register(new Module("Frozen", Category.MISC, "") {
            final BoolSetting a = setting(new BoolSetting("A", true));
        });
        assertEquals("bind", m.settings().get(m.settings().size() - 1).id());
        assertThrows(UnsupportedOperationException.class, () -> m.settings().add(new BoolSetting("B", true)));
    }

    @Test
    void keybindTogglesBoundModuleOnly() {
        Counter bound = manager.register(new Counter("Bound"));
        Counter other = manager.register(new Counter("Other"));
        bound.bind().bind(75, "K");
        bus.post(new GameKeyEvent(75, 0));
        assertTrue(bound.isEnabled());
        assertFalse(other.isEnabled());
        bus.post(new GameKeyEvent(75, 0));
        assertFalse(bound.isEnabled());
    }

    @Test
    void callsFromOtherThreadsRunOnTheClientThread() throws Exception {
        Counter m = manager.register(new Counter("Threaded"));
        List<Thread> enabledOn = new ArrayList<>();
        bus.subscribe(ModuleStateEvent.class, "probe", e -> enabledOn.add(Thread.currentThread()));
        Thread worker = new Thread(() -> manager.setEnabled(m, true));
        worker.start();
        worker.join();
        assertEquals(ModuleState.DISABLED, m.state(), "nothing may change off the client thread");
        assertEquals(1, thread.queue.size());
        thread.drain();
        assertEquals(ModuleState.ENABLED, m.state());
        assertEquals(List.of(thread.owner), enabledOn);
    }

    @Test
    void disableAllStopsEverything() {
        Counter a = manager.register(new Counter("A1"));
        Counter b = manager.register(new Counter("B1"));
        manager.setEnabled(a, true);
        manager.setEnabled(b, true);
        manager.disableAll();
        assertFalse(a.isEnabled());
        assertFalse(b.isEnabled());
        assertEquals(0, bus.countOwnedBy(a) + bus.countOwnedBy(b));
    }
}
