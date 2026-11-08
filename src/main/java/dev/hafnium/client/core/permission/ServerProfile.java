package dev.hafnium.client.core.permission;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * What the user has recorded about one multiplayer server's rules. A profile can only <em>restrict</em>: it can confirm
 * that the server's rules allow gameplay assistance and mark capabilities as prohibited there, but it never authorizes
 * autonomous automation, player combat or privileged actions — only the server can do that.
 *
 * @param serverId           normalized server address (see {@link #normalize})
 * @param assistanceAllowed  the user confirmed the server's rules allow gameplay assistance
 * @param prohibited         capabilities the server's rules prohibit; blocked in every mode
 */
public record ServerProfile(String serverId, boolean assistanceAllowed, Set<Capability> prohibited) {
    public static final String DEFAULT_ID = "*";

    /** Template for servers the user has not configured: assistance off until confirmed, nothing else recorded. */
    public static final ServerProfile DEFAULTS = new ServerProfile(DEFAULT_ID, false, Set.of());

    public ServerProfile {
        serverId = normalize(serverId);
        prohibited = prohibited.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(prohibited));
    }

    public boolean prohibits(Capability c) {
        return prohibited.contains(c);
    }

    public ServerProfile withServerId(String id) {
        return new ServerProfile(id, assistanceAllowed, prohibited);
    }

    public ServerProfile withAssistanceAllowed(boolean allowed) {
        return new ServerProfile(serverId, allowed, prohibited);
    }

    public ServerProfile withProhibited(Capability c, boolean prohibit) {
        EnumSet<Capability> next = prohibited.isEmpty() ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(prohibited);
        if (prohibit) {
            next.add(c);
        } else {
            next.remove(c);
        }
        return new ServerProfile(serverId, assistanceAllowed, next);
    }

    /** Lower-case address without the default port, so "Play.Example.net:25565" and "play.example.net" match. */
    public static String normalize(String address) {
        String a = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        if (a.endsWith(":25565")) {
            a = a.substring(0, a.length() - ":25565".length());
        }
        if (a.endsWith(".")) {
            a = a.substring(0, a.length() - 1);
        }
        return a.isEmpty() ? DEFAULT_ID : a;
    }
}
