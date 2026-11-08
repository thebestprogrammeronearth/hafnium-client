package dev.hafnium.client.core.permission;

import java.util.Set;

/**
 * An optional automation policy a server sent over {@code hafnium:automation_policy}. When present it is a complete
 * allow-list: listed {@link Capability.Tier#SERVER_GRANT} capabilities become available, and anything not listed is
 * switched off on that server. It cannot grant {@link Capability.Tier#SERVER_PRIVILEGE} capabilities.
 */
public record ServerPolicy(String serverId, Set<Capability> allowed, long receivedAtMillis) {
    public ServerPolicy {
        allowed = Set.copyOf(allowed);
    }
}
