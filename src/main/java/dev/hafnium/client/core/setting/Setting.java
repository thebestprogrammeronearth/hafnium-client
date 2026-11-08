package dev.hafnium.client.core.setting;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A typed, observable module setting. Values are validated by {@link #sanitize}; listeners run after every actual
 * change. Settings are read and written on the client thread.
 */
public abstract class Setting<T> {
    private final String id;
    private final String name;
    private final T defaultValue;
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    protected T value;

    protected Setting(String name, T defaultValue) {
        this.name = Objects.requireNonNull(name, "name");
        this.id = toId(name);
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.value = defaultValue;
    }

    /** "Anti Kick" -> "anti-kick". */
    public static String toId(String name) {
        String id = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Setting name '" + name + "' has no usable characters for an id");
        }
        return id;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public T get() {
        return value;
    }

    public T defaultValue() {
        return defaultValue;
    }

    /** Validates (and may adjust) a new value. Throw {@link IllegalArgumentException} to reject it. */
    protected T sanitize(T candidate) {
        return candidate;
    }

    public void set(T newValue) {
        assign(sanitize(Objects.requireNonNull(newValue, "value")));
    }

    /** Stores an already-validated value and notifies listeners if it changed. */
    protected final void assign(T newValue) {
        if (Objects.equals(value, newValue)) {
            return;
        }
        value = newValue;
        for (Consumer<T> l : listeners) {
            l.accept(newValue);
        }
    }

    public void reset() {
        set(defaultValue);
    }

    /** @return a handle that removes the listener */
    public Runnable addListener(Consumer<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
