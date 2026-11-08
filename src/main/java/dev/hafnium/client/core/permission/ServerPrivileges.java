package dev.hafnium.client.core.permission;

/**
 * Privileges the connected server has assigned to this player through vanilla game mechanics, as the client last saw
 * them. These are reports from the server (operator level arrives as an entity event, game mode as a game event); the
 * client never sets them itself. The server still enforces both regardless of what the client believes.
 *
 * @param operatorCommands the player has at least the "gamemaster" command level (operator level 2)
 * @param creative         the player is in creative mode
 */
public record ServerPrivileges(boolean operatorCommands, boolean creative) {
    public static final ServerPrivileges NONE = new ServerPrivileges(false, false);
}
