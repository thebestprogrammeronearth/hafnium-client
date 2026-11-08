package dev.hafnium.client.config;

import dev.hafnium.client.core.ImmutableSnapshot;
import dev.hafnium.client.core.task.*;
import java.io.IOException;
import java.nio.file.*;

/** Debounced, serialized writes through the existing scheduler's workers. Client-thread API. */
public final class DeferredJsonFile {
    private record Write(Path path, String json, long version) implements ImmutableSnapshot {}
    private static final java.util.List<DeferredJsonFile> FILES = new java.util.ArrayList<>();
    private long version, written;
    private String latest;
    private final Path path;
    private String pending;
    private long due;
    private TaskHandle active;
    public DeferredJsonFile(Path path) {
        this.path = path;
        FILES.add(this);
    }
    public static void tickAll(TaskScheduler scheduler) {
        for (var file : FILES) file.tick(scheduler);
    }
    public static void flushAll() {
        for (var file : FILES)
            if (file.latest != null)
                try {
                    file.commit(new Write(file.path, file.latest, file.version));
                } catch (IOException e) {
                    org.slf4j.LoggerFactory.getLogger("Hafnium/Files").error("Save failed", e);
                }
    }
    private synchronized void commit(Write w) throws IOException {
        if (w.version <= written)
            return;
        write(w.path, w.json);
        written = w.version;
    }
    public String read() throws IOException {
        if (!Files.exists(path))
            return null;
        if (Files.size(path) > 4_000_000)
            throw new IOException("Configuration exceeds 4 MB");
        return Files.readString(path);
    }
    public void changed(String json, long tick) {
        if (pending == null)
            due = tick + 20;
        pending = json;
        latest = json;
        version++;
    }
    public void tick(TaskScheduler scheduler) {
        if (pending == null || scheduler.currentTick() < due || active != null && !active.isDone())
            return;
        String json = pending;
        long requestedVersion = version;
        pending = null;
        active = scheduler.submit(new Task() {
            AsyncResult<Boolean> result;
            public String name() {
                return "Save " + path.getFileName();
            }
            public void start(TaskContext ctx) {
                result = ctx.async(new Write(path, json, requestedVersion), w -> {
                    try {
                        commit(w);
                        return true;
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
            }
            public boolean tick(TaskContext ctx) {
                if (!result.isDone())
                    return false;
                return result.get();
            }
        });
    }
    public static void write(Path path, String json) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = Files.createTempFile(path.getParent(), "hafnium-", ".tmp");
        try {
            Files.writeString(tmp, json);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
