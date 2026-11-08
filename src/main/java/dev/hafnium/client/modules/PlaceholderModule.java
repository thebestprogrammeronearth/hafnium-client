package dev.hafnium.client.modules;

import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Implementation;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.setting.Setting;

/**
 * A module that exists in the GUI but has no behaviour yet. It goes through the normal lifecycle (so toggling,
 * keybinds and configs all work on it), but enabling it subscribes to nothing and changes nothing in game.
 */
public final class PlaceholderModule extends Module {
    public PlaceholderModule(String name, Category category, String description) {
        super(name, category, description);
    }

    public PlaceholderModule with(Setting<?> setting) {
        setting(setting);
        return this;
    }

    @Override
    public Implementation implementation() {
        return Implementation.PLACEHOLDER;
    }
}
