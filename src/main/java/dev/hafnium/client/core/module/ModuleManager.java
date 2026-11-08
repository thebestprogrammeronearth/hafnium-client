package dev.hafnium.client.core.module;

import dev.hafnium.client.core.ClientThread;
import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.event.EventPriority;
import dev.hafnium.client.core.event.GameKeyEvent;
import dev.hafnium.client.core.setting.KeybindSetting;
import dev.hafnium.client.core.task.TaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry and lifecycle controller for modules.
 *
 * <p>State changes always run on the client thread; calls from other threads are handed to it and applied there.
 * Handler failures reported by the event bus are routed back to the owning module, which is then shut down and
 * marked {@link ModuleState#FAILED}.
 */
public final class ModuleManager {

    private static final Logger LOG = LoggerFactory.getLogger("Hafnium/Modules");

    private final EventBus bus;
    private final ClientThread clientThread;
    private final TaskScheduler scheduler;
    private final Map<String, Module> byId = new LinkedHashMap<>();
    private final List<Module> all = new ArrayList<>();
    private final List<Module> allView = Collections.unmodifiableList(all);
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);
    private final Map<Category, List<Module>> byCategoryView = new EnumMap<>(Category.class);

    public ModuleManager(EventBus bus, ClientThread clientThread, TaskScheduler scheduler) {
        this.bus = Objects.requireNonNull(bus);
        this.clientThread = Objects.requireNonNull(clientThread);
        this.scheduler = Objects.requireNonNull(scheduler);
        for (Category c : Category.values()) {
            List<Module> list = new ArrayList<>();
            byCategory.put(c, list);
            byCategoryView.put(c, Collections.unmodifiableList(list));
        }
        bus.setFailureHandler(this::onHandlerFailure);
        bus.subscribe(GameKeyEvent.class, EventPriority.HIGH, this, this::onGameKey);
    }

    // ------------------------------------------------------------------ registry

    public synchronized <M extends Module> M register(M module) {
        if (byId.containsKey(module.id())) {
            throw new IllegalArgumentException("Duplicate module id '" + module.id() + "'");
        }
        module.freeze();
        byId.put(module.id(), module);
        all.add(module);
        byCategory.get(module.category()).add(module);
        return module;
    }

    public List<Module> all() {
        return allView;
    }

    public List<Module> byCategory(Category category) {
        return byCategoryView.get(category);
    }

    public Module find(String id) {
        return byId.get(id);
    }

    public <M extends Module> M get(Class<M> type) {
        for (Module m : all) {
            if (type.isInstance(m)) {
                return type.cast(m);
            }
        }
        throw new IllegalStateException("No module of type " + type.getSimpleName() + " is registered");
    }

    // ------------------------------------------------------------------ lifecycle

    public void toggle(Module module) {
        onClientThread(() -> applyEnabled(module, !module.isEnabled()));
    }

    public void setEnabled(Module module, boolean enabled) {
        onClientThread(() -> applyEnabled(module, enabled));
    }

    /** Disables every module (used when the client shuts down). */
    public void disableAll() {
        onClientThread(() -> {
            for (Module m : all) {
                if (m.isEnabled()) {
                    applyEnabled(m, false);
                }
            }
        });
    }

    private void onClientThread(Runnable r) {
        if (clientThread.isCurrent()) {
            r.run();
        } else {
            clientThread.execute(r);
        }
    }

    private void applyEnabled(Module m, boolean enable) {
        if (!byId.containsKey(m.id()) || byId.get(m.id()) != m) {
            throw new IllegalArgumentException("Module " + m.id() + " is not registered with this manager");
        }
        if (enable && !m.isToggleable()) {
            return;
        }
        if (enable == m.isEnabled()) {
            return;
        }
        if (enable) {
            start(m);
        } else {
            stop(m, ModuleState.DISABLED, null);
        }
    }

    private void start(Module m) {
        ModuleState previous = m.state();
        ModuleContext ctx = new ModuleContext(m, bus, scheduler, LoggerFactory.getLogger("Hafnium/" + m.id()));
        m.context = ctx;
        try {
            m.onEnable(ctx);
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError fatal) {
                throw fatal;
            }
            ctx.close();
            m.context = null;
            String msg = describe(t);
            m.setState(ModuleState.FAILED, "Could not enable: " + msg);
            LOG.error("{} failed to enable: {}", m.id(), msg, t);
            bus.post(new ModuleStateEvent(m, previous, ModuleState.FAILED));
            return;
        }
        ctx.enableFinished();
        m.setState(ModuleState.ENABLED, null);
        LOG.debug("{} enabled", m.id());
        bus.post(new ModuleStateEvent(m, previous, ModuleState.ENABLED));
    }

    private void stop(Module m, ModuleState target, String error) {
        ModuleState previous = m.state();
        if (previous == ModuleState.ENABLED) {
            try {
                m.onDisable();
            } catch (Throwable t) {
                if (t instanceof VirtualMachineError fatal) {
                    throw fatal;
                }
                LOG.error("{}: onDisable threw; continuing shutdown", m.id(), t);
                if (error == null && target == ModuleState.DISABLED) {
                    target = ModuleState.FAILED;
                    error = "Error while disabling: " + describe(t);
                }
            }
        }
        if (m.context != null) {
            m.context.close();
            m.context = null;
        }
        m.setState(target, error);
        if (previous != target) {
            bus.post(new ModuleStateEvent(m, previous, target));
        }
    }

    private void onHandlerFailure(Object owner, Object event, Throwable error) {
        String where = event.getClass().getSimpleName();
        if (owner instanceof Module m && m.isEnabled()) {
            String msg = "Error handling " + where + ": " + describe(error);
            LOG.error("{} failed while handling {}; disabling it", m.id(), where, error);
            onClientThread(() -> {
                if (m.isEnabled()) {
                    stop(m, ModuleState.FAILED, msg);
                }
            });
        } else {
            LOG.error("Event handler of {} failed on {}", owner, where, error);
        }
    }

    private void onGameKey(GameKeyEvent e) {
        if (e.key() == KeybindSetting.NONE) {
            return;
        }
        for (Module m : all) {
            if (m.isToggleable() && m.bind().get() == e.key()) {
                applyEnabled(m, !m.isEnabled());
            }
        }
    }

    private static String describe(Throwable t) {
        String msg = t.getMessage();
        return t.getClass().getSimpleName() + (msg == null || msg.isBlank() ? "" : ": " + msg);
    }
}
