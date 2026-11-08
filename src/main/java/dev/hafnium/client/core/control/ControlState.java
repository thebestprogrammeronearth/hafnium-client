package dev.hafnium.client.core.control;

import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.task.TaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Global automation control: the operating mode and the emergency stop. */
public final class ControlState {
    private static final Logger LOG = LoggerFactory.getLogger("Hafnium/Control");

    private final EventBus bus;
    private final TaskScheduler scheduler;
    private volatile OperatingMode mode = OperatingMode.MANUAL;
    private volatile long emergencyStops;

    public ControlState(EventBus bus, TaskScheduler scheduler) {
        this.bus = bus;
        this.scheduler = scheduler;
    }

    public OperatingMode mode() {
        return mode;
    }

    public void setMode(OperatingMode next) {
        OperatingMode prev = mode;
        if (prev != next) {
            mode = next;
            LOG.info("Operating mode {} -> {}", prev, next);
            bus.post(new ControlEvent.ModeChanged(prev, next));
        }
    }

    /** Cancels every task, drops to MANUAL and tells listeners (agents stop themselves on this event). */
    public void emergencyStop(String reason) {
        emergencyStops++;
        LOG.warn("EMERGENCY STOP: {}", reason);
        scheduler.cancelAll("Emergency stop: " + reason);
        setMode(OperatingMode.MANUAL);
        bus.post(new ControlEvent.EmergencyStop(reason));
    }

    public long emergencyStopCount() {
        return emergencyStops;
    }
}
