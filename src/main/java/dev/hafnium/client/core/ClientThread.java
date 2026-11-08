package dev.hafnium.client.core;

/**
 * The thread that owns game state. Module lifecycle changes and gameplay-facing work must run on it; other threads
 * hand work over with {@link #execute}.
 */
public interface ClientThread {
    boolean isCurrent();

    /** Runs the task on the client thread; it may run immediately or be queued for the next opportunity. */
    void execute(Runnable task);

    /** For tests and tools that have no separate game thread. */
    ClientThread DIRECT = new ClientThread() {
        @Override
        public boolean isCurrent() {
            return true;
        }

        @Override
        public void execute(Runnable task) {
            task.run();
        }
    };
}
