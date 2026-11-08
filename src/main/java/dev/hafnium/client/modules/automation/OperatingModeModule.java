package dev.hafnium.client.modules.automation;

import dev.hafnium.client.core.control.ControlEvent;
import dev.hafnium.client.core.control.ControlState;
import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.setting.ModeSetting;

/**
 * Settings-only: the global operating mode. Manual = no gameplay actions; Assist = supervised assistance;
 * Autonomous = agents too, where authorized. Kept in sync both ways with {@link ControlState} (the End key and
 * server/world changes drop it back to Manual).
 */
public final class OperatingModeModule extends Module {
    public final ModeSetting mode = setting(new ModeSetting("Mode", "Manual", "Manual", "Assist", "Autonomous"));
    private boolean syncing;

    public OperatingModeModule() {
        super("Operating Mode", Category.AUTOMATION, "Manual: no gameplay actions. Assist: supervised help."
                + " Autonomous: agents, only where authorized. Resets to Manual on join and on emergency stop.");
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    public void bind(EventBus bus, ControlState control) {
        mode.addListener(v -> {
            if (!syncing) {
                control.setMode(parse(v));
            }
        });
        bus.subscribe(ControlEvent.ModeChanged.class, this, e -> show(e.current()));
        show(control.mode());
    }

    private void show(OperatingMode m) {
        syncing = true;
        try {
            mode.set(label(m));
        } finally {
            syncing = false;
        }
    }

    public static String label(OperatingMode m) {
        return switch (m) {
            case MANUAL -> "Manual";
            case ASSIST -> "Assist";
            case AUTONOMOUS -> "Autonomous";
        };
    }

    static OperatingMode parse(String s) {
        return switch (s) {
            case "Assist" -> OperatingMode.ASSIST;
            case "Autonomous" -> OperatingMode.AUTONOMOUS;
            default -> OperatingMode.MANUAL;
        };
    }
}
