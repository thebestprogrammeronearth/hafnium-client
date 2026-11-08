package dev.hafnium.client.core.permission;

/** How a task exercises its gameplay capabilities. */
public enum Autonomy {
    /**
     * Server-compliant gameplay assistance: an action the player started and is supervising, performed through
     * normal gameplay interactions (Assist or Autonomous mode).
     */
    ASSISTED,
    /** Server-authorized automation: agent-driven gameplay (Autonomous mode, and only where authorized). */
    AUTONOMOUS
}
