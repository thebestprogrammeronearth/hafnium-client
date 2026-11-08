package dev.hafnium.client.config;

import java.util.HashMap;
import java.util.Map;

/** An in-memory config: a name, a "last saved" label and a snapshot of visual module states. */
public final class MockConfig {
    private String name;
    private long savedAt;
    private final Map<String, Boolean> moduleStates = new HashMap<>();

    public MockConfig(String name, long savedAt) {
        this.name = name;
        this.savedAt = savedAt;
    }

    public String name() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public long savedAt() {
        return savedAt;
    }

    public void markSaved(long time) {
        this.savedAt = time;
    }

    public Map<String, Boolean> moduleStates() {
        return moduleStates;
    }
}
