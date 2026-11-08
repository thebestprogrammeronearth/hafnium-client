package dev.hafnium.client.core.control;

/**
 * How much control automation has. Stage 2 only stores and broadcasts the mode; agents (later stages) read it.
 * <ul>
 *   <li>MANUAL — the player controls the game; enabled modules do only their own job.</li>
 *   <li>ASSIST — the player keeps control; selected modules may offer contextual help.</li>
 *   <li>AUTONOMOUS — agents may run objectives the user started, where the permission layer allows.</li>
 * </ul>
 */
public enum OperatingMode {
    MANUAL,
    ASSIST,
    AUTONOMOUS
}
