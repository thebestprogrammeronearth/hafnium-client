package dev.hafnium.client.core.setting;

import java.util.List;

/** One of a fixed list of modes, cycled in place (left-click next, right-click previous). */
public final class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String defaultValue, String... modes) {
        super(name, defaultValue);
        this.modes = List.of(modes);
        if (!this.modes.contains(defaultValue)) {
            throw new IllegalArgumentException("Default '" + defaultValue + "' is not a mode of '" + name + "'");
        }
    }

    public boolean is(String mode) {
        return get().equals(mode);
    }

    public List<String> modes() {
        return modes;
    }

    @Override
    protected String sanitize(String candidate) {
        if (!modes.contains(candidate)) {
            throw new IllegalArgumentException("'" + candidate + "' is not a mode of '" + name() + "': " + modes);
        }
        return candidate;
    }

    public void cycle(int dir) {
        int i = modes.indexOf(value);
        set(modes.get(Math.floorMod(i + dir, modes.size())));
    }
}
