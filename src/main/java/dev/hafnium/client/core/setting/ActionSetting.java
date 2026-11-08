package dev.hafnium.client.core.setting;

/** A button in a module's settings that runs an action (for example "Open Editor"). Holds no value. */
public final class ActionSetting extends Setting<Boolean> {
    private final Runnable action;

    public ActionSetting(String name, Runnable action) {
        super(name, false);
        this.action = action;
    }

    public void run() {
        action.run();
    }
}
