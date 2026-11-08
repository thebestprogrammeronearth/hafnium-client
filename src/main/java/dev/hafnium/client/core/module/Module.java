package dev.hafnium.client.core.module;

import dev.hafnium.client.core.setting.KeybindSetting;
import dev.hafnium.client.core.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Base class for every Hafnium module.
 *
 * <p>Lifecycle, all driven by {@link ModuleManager} on the client thread:
 * <ol>
 *   <li>{@link #onEnable(ModuleContext)}: subscribe to events through the context. Throwing puts the module in
 *       {@link ModuleState#FAILED} and undoes its subscriptions.</li>
 *   <li>While enabled, handlers receive events. A handler that throws fails the module the same way.</li>
 *   <li>{@link #onDisable()}: release anything not covered by the context. Subscriptions are removed afterwards
 *       automatically.</li>
 * </ol>
 *
 * <p>Enabling a module only makes it react to events. It must never start an autonomous task by itself; tasks are
 * started explicitly by the user through the task system.
 */
public abstract class Module {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z0-9][a-z0-9-]*)+");

    private final String id;
    private final String name;
    private final String searchKey;
    private final Category category;
    private final String description;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<Setting<?>> settingsView = Collections.unmodifiableList(settings);
    private final KeybindSetting bind = new KeybindSetting("Bind");

    private volatile ModuleState state = ModuleState.DISABLED;
    private volatile String lastError;
    ModuleContext context;
    private boolean frozen;

    /**
     * @param name display name; the id becomes {@code <category>.<lower-cased name>}, e.g. {@code render.esp}
     */
    protected Module(String name, Category category, String description) {
        this(category.idPrefix() + "." + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"), name, category, description);
    }

    protected Module(String id, String name, Category category, String description) {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid module id '" + id + "' (expected e.g. 'render.esp')");
        }
        this.id = id;
        this.name = Objects.requireNonNull(name);
        this.searchKey = name.toLowerCase(Locale.ROOT);
        this.category = Objects.requireNonNull(category);
        this.description = Objects.requireNonNull(description);
    }

    /** Registers a setting. Call from the subclass constructor. */
    protected final <S extends Setting<?>> S setting(S setting) {
        if (frozen) {
            throw new IllegalStateException(id + ": settings must be added before the module is registered");
        }
        for (Setting<?> s : settings) {
            if (s.id().equals(setting.id())) {
                throw new IllegalArgumentException(id + ": duplicate setting id '" + setting.id() + "'");
            }
        }
        if (setting.id().equals(bind.id())) {
            throw new IllegalArgumentException(id + ": setting id 'bind' is reserved");
        }
        settings.add(setting);
        return setting;
    }

    protected void onEnable(ModuleContext context) throws Exception {
    }

    protected void onDisable() throws Exception {
    }

    // ------------------------------------------------------------------ description

    public final String id() {
        return id;
    }

    public final String name() {
        return name;
    }

    /** Lower-cased name, cached for allocation-free search. */
    public final String searchKey() {
        return searchKey;
    }

    public final Category category() {
        return category;
    }

    public final String description() {
        return description;
    }

    public Implementation implementation() {
        return Implementation.IMPLEMENTED;
    }

    /** False for settings-only modules (left-click opens their settings instead of toggling). */
    public boolean isToggleable() {
        return true;
    }

    /** All settings, with the keybind last (toggleable modules only). */
    public final List<Setting<?>> settings() {
        freeze();
        return settingsView;
    }

    /** Called on registration: appends the keybind (toggleable modules) and locks the settings list. */
    final synchronized void freeze() {
        if (!frozen) {
            frozen = true;
            if (isToggleable()) {
                settings.add(bind);
            }
        }
    }

    public final Setting<?> setting(String settingId) {
        for (Setting<?> s : settings()) {
            if (s.id().equals(settingId)) {
                return s;
            }
        }
        return null;
    }

    public final KeybindSetting bind() {
        return bind;
    }

    // ------------------------------------------------------------------ state (written only by ModuleManager)

    public final ModuleState state() {
        return state;
    }

    public final boolean isEnabled() {
        return state == ModuleState.ENABLED;
    }

    /** Message of the last failure, or null. Cleared when the module is enabled again. */
    public final String lastError() {
        return lastError;
    }

    final void setState(ModuleState state, String error) {
        this.state = state;
        this.lastError = error;
    }

    @Override
    public String toString() {
        return id;
    }
}
