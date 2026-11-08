package dev.hafnium.client.gui;

import dev.hafnium.client.core.module.Category;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Panel layout kept for the rest of the game session, so reopening the GUI restores positions and folds. */
public final class ClickGuiState {
    public static final class PanelState {
        /** Current position in layout units (default slot + drag offset). */
        public float x;
        public float y;
        /**
         * How far the user dragged the panel from its default slot. Stored as an offset, not an absolute position, so
         * the arrangement survives window resizes and GUI-scale changes.
         */
        public float offsetX;
        public float offsetY;
        public boolean expanded = true;
    }

    private static final Map<Category, PanelState> PANELS = new EnumMap<>(Category.class);
    private static final List<Category> ORDER = new ArrayList<>(List.of(Category.values()));
    private static Category focused = Category.RENDER;

    static {
        for (Category c : Category.values()) {
            PANELS.put(c, new PanelState());
        }
    }

    private ClickGuiState() {
    }

    public static PanelState panel(Category c) {
        return PANELS.get(c);
    }

    /** Back-to-front draw order. */
    public static List<Category> order() {
        return ORDER;
    }

    public static void bringToFront(Category c) {
        ORDER.remove(c);
        ORDER.add(c);
    }

    public static Category focused() {
        return focused;
    }

    public static void setFocused(Category c) {
        focused = c;
    }
}
