package dev.hafnium.client.core.control;

/** Mode change or emergency stop. */
public sealed interface ControlEvent {
    record ModeChanged(OperatingMode previous, OperatingMode current) implements ControlEvent {
    }

    record EmergencyStop(String reason) implements ControlEvent {
    }
}
