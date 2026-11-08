package dev.hafnium.client.core.setting;

/** A GLFW key code ({@link #NONE} = unbound) plus its display name. The module manager toggles on this key in game. */
public final class KeybindSetting extends Setting<Integer> {
    public static final int NONE = -1;
    private String keyName = "None";

    public KeybindSetting(String name) {
        super(name, NONE);
    }

    public void bind(int key, String keyName) {
        this.keyName = key == NONE ? "None" : keyName;
        set(key);
    }

    public void clear() {
        bind(NONE, "None");
    }

    @Override
    public void reset() {
        clear();
    }

    public String keyName() {
        return keyName;
    }
}
