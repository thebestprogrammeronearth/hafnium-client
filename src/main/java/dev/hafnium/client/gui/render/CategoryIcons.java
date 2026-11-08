package dev.hafnium.client.gui.render;

import dev.hafnium.client.core.module.Category;

/** Header icon per category (kept in the GUI layer so the core stays free of rendering code). */
public final class CategoryIcons {
    private CategoryIcons() {
    }

    public static Icon of(Category category) {
        return switch (category) {
            case COMBAT -> Icon.SWORDS;
            case MOVEMENT -> Icon.RUNNER;
            case RENDER -> Icon.EYE;
            case PLAYER -> Icon.USER;
            case MISC -> Icon.GRID;
            case WORLD -> Icon.GLOBE;
            case AUTOMATION -> Icon.BOT;
            case CLIENT -> Icon.FOLDER;
        };
    }
}
