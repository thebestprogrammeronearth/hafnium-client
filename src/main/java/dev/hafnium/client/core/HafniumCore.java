package dev.hafnium.client.core;

import dev.hafnium.client.core.control.ControlEvent;
import dev.hafnium.client.core.control.ControlState;
import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.module.ModuleManager;
import dev.hafnium.client.core.permission.PermissionChangedEvent;
import dev.hafnium.client.core.permission.PermissionGuard;
import dev.hafnium.client.core.permission.PermissionNotices;
import dev.hafnium.client.core.permission.PermissionService;
import dev.hafnium.client.core.permission.ServerProfiles;
import dev.hafnium.client.core.task.TaskScheduler;
import dev.hafnium.client.core.world.WorldModel;
import dev.hafnium.client.core.world.WorldModelLimits;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared services, and the one place they are wired together:
 * <ul>
 *   <li>{@link EventBus}, {@link ModuleManager} (Stage 1)</li>
 *   <li>{@link TaskScheduler}, {@link ControlState} (mode + emergency stop), {@link WorldModel} (Stage 2)</li>
 *   <li>{@link PermissionService} — kept separate: it reaches the scheduler only through {@link PermissionGuard}
 *       and a re-check request when permissions or the operating mode change. Joining a different world or server
 *       resets the mode to MANUAL. {@link PermissionNotices} turns changes and denials into user-facing messages.</li>
 * </ul>
 */
public final class HafniumCore {
    private static volatile HafniumCore instance;

    private final ClientThread clientThread;
    private final EventBus events;
    private final ThreadPoolExecutor workers;
    private final TaskScheduler scheduler;
    private final ModuleManager modules;
    private final PermissionService permissions;
    private final ControlState control;
    private final PermissionNotices notices;
    private final ServerProfiles profiles = new ServerProfiles();
    private final WorldModel world;
    private final dev.hafnium.client.core.navigation.NavigationCore navigation;

    public HafniumCore(ClientThread clientThread, WorldModelLimits limits) {
        this.clientThread = Objects.requireNonNull(clientThread);
        this.events = new EventBus();
        AtomicInteger n = new AtomicInteger();
        this.workers = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(256), r -> {
            Thread t = new Thread(r, "Hafnium-Worker-" + n.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
        this.workers.allowCoreThreadTimeOut(true);
        this.scheduler = new TaskScheduler(events, clientThread, workers);
        this.navigation = new dev.hafnium.client.core.navigation.NavigationCore(clientThread, scheduler);
        this.modules = new ModuleManager(events, clientThread, scheduler);
        this.control = new ControlState(events, scheduler);
        this.permissions = new PermissionService(events, control::mode);
        this.notices = new PermissionNotices(events, permissions);
        this.world = new WorldModel(events, limits);

        scheduler.setGuard(new PermissionGuard(permissions));
        // Revocation takes effect immediately, not on the next tick.
        events.subscribe(PermissionChangedEvent.class, this, e -> {
            if (e.environmentChanged()) {
                // Account safety: a new world or server always starts in MANUAL; the mode never carries over.
                control.setMode(OperatingMode.MANUAL);
            }
            scheduler.recheckGuards();
        });
        events.subscribe(ControlEvent.ModeChanged.class, this, e -> scheduler.recheckGuards());
        WorldTaskInvalidator.install(events, scheduler);
        events.subscribe(dev.hafnium.client.core.world.WorldChangeEvent.DimensionChanged.class, this, e -> navigation.invalidate());
        events.subscribe(dev.hafnium.client.core.world.WorldChangeEvent.Cleared.class, this, e -> navigation.invalidate());
        events.subscribe(dev.hafnium.client.core.world.WorldChangeEvent.ChunkUnloaded.class, this, e -> navigation.invalidate());
    }

    /** Installs the process-wide instance (called once by the mod initializer). */
    public static void install(HafniumCore core) {
        if (instance != null) {
            throw new IllegalStateException("HafniumCore is already installed");
        }
        instance = core;
    }

    public static HafniumCore get() {
        HafniumCore c = instance;
        if (c == null) {
            throw new IllegalStateException("HafniumCore has not been initialised yet");
        }
        return c;
    }

    /** Client shutdown: stop modules and tasks (releasing all resources), then the worker threads. */
    public void shutdown() {
        modules.disableAll();
        scheduler.shutdown("Client shutting down");
        workers.shutdownNow();
    }

    public ClientThread clientThread() {
        return clientThread;
    }

    public EventBus events() {
        return events;
    }

    public ModuleManager modules() {
        return modules;
    }

    public TaskScheduler scheduler() {
        return scheduler;
    }

    public PermissionService permissions() {
        return permissions;
    }

    /** The user's per-server profiles (persisted by the game adapter). */
    public ServerProfiles profiles() {
        return profiles;
    }

    public PermissionNotices notices() {
        return notices;
    }

    public ControlState control() {
        return control;
    }

    public dev.hafnium.client.core.navigation.NavigationCore navigation() { return navigation; }

    public WorldModel world() {
        return world;
    }
}
