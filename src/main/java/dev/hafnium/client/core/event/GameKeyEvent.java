package dev.hafnium.client.core.event;

/** A key pressed while no screen is open (i.e. while playing). {@code key} is a GLFW key code. */
public record GameKeyEvent(int key, int modifiers) {
}
