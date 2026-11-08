/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.anubis.setting;

import com.google.gson.JsonElement;

/**
 * Base for settings ported from Anubis whose value is a structured document (block lists, item lists, ...). It is a
 * Hafnium {@link dev.hafnium.client.core.setting.Setting} whose value is a revision counter, so the ClickGUI, module
 * manager and settings store treat it like any other setting; {@link #serialize()} / {@link #deserialize} carry the
 * actual data to {@code module-settings.json}.
 */
public abstract class Setting extends dev.hafnium.client.core.setting.Setting<Long> {
    private JsonElement defaultJson;

    protected Setting(String name) {
        super(name, 0L);
    }

    /** The change counter; increases on every {@link #notifyChanged()}. */
    public final long revision() {
        return get();
    }

    /** Call after mutating the setting's data: bumps the revision, notifies listeners and saves. */
    public final void notifyChanged() {
        assign(get() + 1);
    }

    @Override
    protected Long sanitize(Long candidate) {
        return candidate;
    }

    @Override
    public void reset() {
        resetToDefault();
        notifyChanged();
    }

    /** Restores the shipped defaults (without notifying). */
    protected abstract void resetToDefault();

    public abstract JsonElement serialize();

    /** Records the shipped value; called before saved data is loaded. */
    public final void captureDefault() {
        if (defaultJson == null) {
            defaultJson = serialize();
        }
    }

    /** The shipped value as JSON (null before {@link #captureDefault()}). */
    protected final JsonElement defaultJson() {
        return defaultJson;
    }

    /** What to store for {@code value}; settings may keep only their differences from the default. */
    public JsonElement compact(JsonElement value) {
        return value;
    }

    /** Loads saved data; does not notify. */
    protected abstract void deserializeValue(JsonElement element);

    public final void deserialize(JsonElement element) {
        captureDefault();
        deserializeValue(element);
        notifyChanged();
    }
}
