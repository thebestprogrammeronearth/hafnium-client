package dev.hafnium.client.core.module;

/** Posted after a module changes state. */
public record ModuleStateEvent(Module module, ModuleState previous, ModuleState current) {
}
