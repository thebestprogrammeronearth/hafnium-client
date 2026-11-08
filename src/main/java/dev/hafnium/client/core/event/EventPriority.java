package dev.hafnium.client.core.event;

/** Handler order: higher runs first. Any int works; these are conventional values. */
public final class EventPriority {
    public static final int HIGHEST = 200;
    public static final int HIGH = 100;
    public static final int NORMAL = 0;
    public static final int LOW = -100;
    public static final int LOWEST = -200;

    private EventPriority() {
    }
}
