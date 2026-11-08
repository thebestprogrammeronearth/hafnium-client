package dev.hafnium.client.core.permission;

/**
 * Where the client is playing, as established by the game itself (not by any user setting).
 *
 * @param kind     {@link Kind#INTEGRATED_HOST} when this client runs the server (singleplayer or a LAN world it
 *                 opened); {@link Kind#REMOTE} for any other server, including someone else's LAN world
 * @param serverId address or world name, for display and for scoping server grants
 */
public record Environment(Kind kind, String serverId) {
    public enum Kind { NONE, INTEGRATED_HOST, REMOTE }

    public static final Environment NONE = new Environment(Kind.NONE, "");
}
