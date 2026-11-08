package dev.hafnium.client.core.permission;

/**
 * Availability of one capability in the current environment, independent of the operating mode (which is shown
 * separately): whether it may be used as supervised assistance and as autonomous automation, and why.
 */
public record CapabilityStatus(Capability capability, Decision assisted, Decision autonomous) {
    public enum Level { AUTONOMOUS, ASSIST_ONLY, UNAVAILABLE }

    public Level level() {
        if (autonomous.allowed()) {
            return Level.AUTONOMOUS;
        }
        return assisted.allowed() ? Level.ASSIST_ONLY : Level.UNAVAILABLE;
    }

    /** The reason that best explains the current level. */
    public String reason() {
        return switch (level()) {
            case AUTONOMOUS -> autonomous.reason();
            case ASSIST_ONLY -> autonomous.reason();
            case UNAVAILABLE -> assisted.reason();
        };
    }
}
