package dev.hafnium.client.config;

import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.HafniumCore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * In-memory config list. Nothing is written to disk yet: "save" snapshots which modules are enabled, and "load"
 * enables/disables modules through the module manager to match.
 */
public final class MockConfigManager {
    private static final MockConfigManager INSTANCE = new MockConfigManager();
    private static final long MINUTE = 60_000L;

    private final List<MockConfig> configs = new ArrayList<>();
    private final List<MockConfig> view = Collections.unmodifiableList(configs);
    private MockConfig active;

    public static MockConfigManager get() {
        return INSTANCE;
    }

    private MockConfigManager() {
        long now = System.currentTimeMillis();
        configs.add(new MockConfig("default", now - 3 * MINUTE));
        configs.add(new MockConfig("pvp", now - 2 * 60 * MINUTE));
        configs.add(new MockConfig("building", now - 26 * 60 * MINUTE));
        configs.add(new MockConfig("survival", now - 5 * 24 * 60 * MINUTE));
        configs.add(new MockConfig("showcase", now - 12 * 24 * 60 * MINUTE));
        active = configs.get(0);
    }

    public List<MockConfig> configs() {
        return view;
    }

    public MockConfig active() {
        return active;
    }

    public boolean exists(String name) {
        for (MockConfig c : configs) {
            if (c.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public MockConfig create(String name) {
        MockConfig c = new MockConfig(name, System.currentTimeMillis());
        snapshot(c);
        configs.add(c);
        return c;
    }

    public void save(MockConfig c) {
        snapshot(c);
        c.markSaved(System.currentTimeMillis());
        active = c;
    }

    public void load(MockConfig c) {
        for (Module m : HafniumCore.get().modules().all()) {
            if (m.isToggleable()) {
                Boolean state = c.moduleStates().get(key(m));
                HafniumCore.get().modules().setEnabled(m, state != null && state);
            }
        }
        active = c;
    }

    public void delete(MockConfig c) {
        configs.remove(c);
        if (active == c) {
            active = configs.isEmpty() ? null : configs.get(0);
        }
    }

    private static void snapshot(MockConfig c) {
        c.moduleStates().clear();
        for (Module m : HafniumCore.get().modules().all()) {
            if (m.isEnabled()) {
                c.moduleStates().put(key(m), true);
            }
        }
    }

    private static String key(Module m) {
        return m.id();
    }

    /** "just now", "3 min ago", "2 h ago", "5 d ago". */
    public static String ago(long time) {
        long d = Math.max(0, System.currentTimeMillis() - time);
        if (d < MINUTE) {
            return "just now";
        }
        if (d < 60 * MINUTE) {
            return d / MINUTE + " min ago";
        }
        if (d < 24 * 60 * MINUTE) {
            return d / (60 * MINUTE) + " h ago";
        }
        return String.format(Locale.ROOT, "%d d ago", d / (24 * 60 * MINUTE));
    }
}
