package dev.hafnium.client.core.setting;

import java.util.List;

/** One value picked from an expanding dropdown list. */
public final class ListSetting extends Setting<String> {
    private final List<String> options;

    public ListSetting(String name, String defaultValue, String... options) {
        super(name, defaultValue);
        this.options = List.of(options);
        if (!this.options.contains(defaultValue)) {
            throw new IllegalArgumentException("Default '" + defaultValue + "' is not an option of '" + name + "'");
        }
    }

    public List<String> options() {
        return options;
    }

    @Override
    protected String sanitize(String candidate) {
        if (!options.contains(candidate)) {
            throw new IllegalArgumentException("'" + candidate + "' is not an option of '" + name() + "': " + options);
        }
        return candidate;
    }
}
