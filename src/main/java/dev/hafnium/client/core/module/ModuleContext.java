package dev.hafnium.client.core.module;

import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.event.EventPriority;
import dev.hafnium.client.core.event.Subscription;
import dev.hafnium.client.core.task.Task;
import dev.hafnium.client.core.task.TaskHandle;
import dev.hafnium.client.core.task.TaskOptions;
import dev.hafnium.client.core.task.TaskScheduler;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handed to {@link Module#onEnable}. Everything subscribed through it is owned by the module and removed
 * automatically when the module is disabled or fails, so modules cannot leak handlers.
 */
public final class ModuleContext {
    private final Module module;
    private final EventBus bus;
    private final TaskScheduler scheduler;
    private final Logger logger;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final List<Runnable> cleanups = new ArrayList<>();
    private boolean closed;
    private boolean enabling = true;

    ModuleContext(Module module, EventBus bus, TaskScheduler scheduler, Logger logger) {
        this.module = module;
        this.bus = bus;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    /**
     * Submits a task owned by this module; it is cancelled automatically when the module stops.
     *
     * <p>Not allowed from {@link Module#onEnable}: enabling a module must never start work by itself. Submit in
     * response to an explicit user action (a keybind, a GUI button, a command) instead.
     */
    public TaskHandle submitTask(Task task, TaskOptions options) {
        checkOpen();
        if (enabling) {
            throw new IllegalStateException(module.id() + ": tasks cannot be started from onEnable(); "
                    + "start them from an explicit user action");
        }
        return scheduler.submit(task, options.toBuilder().owner(module).build());
    }

    void enableFinished() {
        enabling = false;
    }

    public <E> void subscribe(Class<E> type, Consumer<? super E> handler) {
        subscribe(type, EventPriority.NORMAL, handler);
    }

    public <E> void subscribe(Class<E> type, int priority, Consumer<? super E> handler) {
        checkOpen();
        subscriptions.add(bus.subscribe(type, priority, module, handler));
    }

    /** Registers extra cleanup (e.g. removing a setting listener) to run when the module stops. */
    public void onClose(Runnable cleanup) {
        checkOpen();
        cleanups.add(cleanup);
    }

    public Logger logger() {
        return logger;
    }

    public Module module() {
        return module;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException(module.id() + ": context used after the module stopped");
        }
    }

    /** Removes every subscription and runs cleanups. Safe to call more than once. */
    void close() {
        if (closed) {
            return;
        }
        closed = true;
        scheduler.cancelOwnedBy(module, module.id() + " stopped");
        for (Subscription s : subscriptions) {
            s.close();
        }
        subscriptions.clear();
        for (int i = cleanups.size() - 1; i >= 0; i--) {
            try {
                cleanups.get(i).run();
            } catch (RuntimeException e) {
                logger.error("{}: cleanup failed", module.id(), e);
            }
        }
        cleanups.clear();
    }
}
