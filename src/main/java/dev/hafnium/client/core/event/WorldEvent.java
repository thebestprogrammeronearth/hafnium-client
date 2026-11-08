package dev.hafnium.client.core.event;

/** Joining or leaving a world/server. Posted on the client thread. */
public record WorldEvent(Type type) {
    public enum Type { JOIN, LEAVE }

    public static final WorldEvent JOIN = new WorldEvent(Type.JOIN);
    public static final WorldEvent LEAVE = new WorldEvent(Type.LEAVE);
}
