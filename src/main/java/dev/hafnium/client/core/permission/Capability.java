package dev.hafnium.client.core.permission;

import java.util.Locale;

/**
 * Gameplay actions a task may perform, each with the {@link Tier} of authorization it needs.
 *
 * <p>Client-local work is deliberately <em>not</em> a capability: observing what the client already receives (the
 * world model), path and navigation calculations, inventory analysis, planning, rendering and GUI. A task that
 * declares no capabilities is never checked and runs in singleplayer and on any multiplayer server, with or without
 * Hafnium installed on the server. How a capability is exercised ({@link Autonomy}) matters as much as which one.
 */
public enum Capability {
    MOVEMENT(Tier.GAMEPLAY, "Movement"),
    LOOK(Tier.GAMEPLAY, "Look"),
    BLOCK_INTERACTION(Tier.GAMEPLAY, "Block Interaction"),
    INVENTORY(Tier.GAMEPLAY, "Inventory"),
    COMBAT_MOBS(Tier.GAMEPLAY, "Mob Combat"),
    COMBAT_PLAYERS(Tier.AUTHORIZED_ONLY, "Player Combat"),
    OPERATOR_COMMANDS(Tier.SERVER_PRIVILEGE, "Op Commands"),
    CREATIVE_INVENTORY(Tier.SERVER_PRIVILEGE, "Creative Items");

    /** What authorizes a capability. */
    public enum Tier {
        /**
         * Normal gameplay interactions. As supervised assistance they are allowed where the user's server profile
         * confirms the server's rules allow it (or the server/host authorizes it). Autonomous use requires
         * authorization: sending normal Minecraft packets is not taken as permission to automate gameplay.
         */
        GAMEPLAY,
        /**
         * Acts directly on other players. Requires authorization (hosting the world, or an explicit server grant over
         * {@code hafnium:automation_policy}) in every mode.
         */
        AUTHORIZED_ONLY,
        /**
         * Needs a privilege the server itself assigned to this player through vanilla mechanisms (operator level,
         * creative game mode). No client setting or policy packet can substitute for it. Autonomous use additionally
         * requires authorization.
         */
        SERVER_PRIVILEGE
    }

    private final Tier tier;
    private final String displayName;

    Capability(Tier tier, String displayName) {
        this.tier = tier;
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public Tier tier() {
        return tier;
    }

    /** Wire id, e.g. "combat_players". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Capability fromId(String id) {
        for (Capability c : values()) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }
}
