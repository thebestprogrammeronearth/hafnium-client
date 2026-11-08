package dev.hafnium.client.core.permission;

/**
 * Posted when the environment, a server policy, the player's server privileges, the server profile or user
 * restrictions change. {@code environmentChanged} is true when this change moved to a different world or server.
 */
public record PermissionChangedEvent(Environment environment, ServerPolicy serverPolicy, ServerPrivileges privileges,
                                     ServerProfile profile, boolean environmentChanged) {
}
