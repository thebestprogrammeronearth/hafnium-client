package dev.hafnium.client.core.permission;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * All server profiles plus the template for new servers. Pure data: persistence is done by a listener
 * ({@link #onChange}) supplied by the game adapter.
 */
public final class ServerProfiles {
    private final Map<String, ServerProfile> profiles = new LinkedHashMap<>();
    private final List<Consumer<ServerProfiles>> listeners = new CopyOnWriteArrayList<>();
    private ServerProfile defaults = ServerProfile.DEFAULTS;

    public synchronized ServerProfile defaults() {
        return defaults;
    }

    /** The profile for {@code serverId}, or a copy of the defaults if the user has not configured that server. */
    public synchronized ServerProfile forServer(String serverId) {
        String id = ServerProfile.normalize(serverId);
        ServerProfile p = profiles.get(id);
        return p != null ? p : defaults.withServerId(id);
    }

    public synchronized boolean isConfigured(String serverId) {
        return profiles.containsKey(ServerProfile.normalize(serverId));
    }

    /** Stores a profile; one with the default id replaces the template for new servers. */
    public void put(ServerProfile profile) {
        Objects.requireNonNull(profile);
        synchronized (this) {
            if (profile.serverId().equals(ServerProfile.DEFAULT_ID)) {
                defaults = profile;
            } else {
                profiles.put(profile.serverId(), profile);
            }
        }
        fire();
    }

    public void remove(String serverId) {
        synchronized (this) {
            profiles.remove(ServerProfile.normalize(serverId));
        }
        fire();
    }

    public synchronized Collection<ServerProfile> all() {
        return List.copyOf(profiles.values());
    }

    /** Replaces everything (used when loading from disk); does not notify listeners. */
    public synchronized void load(ServerProfile newDefaults, Collection<ServerProfile> all) {
        profiles.clear();
        defaults = newDefaults.withServerId(ServerProfile.DEFAULT_ID);
        for (ServerProfile p : all) {
            if (!p.serverId().equals(ServerProfile.DEFAULT_ID)) {
                profiles.put(p.serverId(), p);
            }
        }
    }

    public void onChange(Consumer<ServerProfiles> listener) {
        listeners.add(listener);
    }

    private void fire() {
        for (Consumer<ServerProfiles> l : listeners) {
            l.accept(this);
        }
    }
}
