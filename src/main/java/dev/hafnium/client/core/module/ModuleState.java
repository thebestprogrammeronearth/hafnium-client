package dev.hafnium.client.core.module;

public enum ModuleState {
    DISABLED,
    ENABLED,
    /** Enabling or an event handler threw; the module was shut down. See {@link Module#lastError()}. */
    FAILED
}
