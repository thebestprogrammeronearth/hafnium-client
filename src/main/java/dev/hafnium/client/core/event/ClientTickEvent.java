package dev.hafnium.client.core.event;

/** Posted at the start and end of every client tick (20 per second), on the client thread. Reused; do not store. */
public final class ClientTickEvent {
    public static final ClientTickEvent START = new ClientTickEvent(true);
    public static final ClientTickEvent END = new ClientTickEvent(false);

    private final boolean start;

    private ClientTickEvent(boolean start) {
        this.start = start;
    }

    public boolean isStart() {
        return start;
    }
}
