package dev.hafnium.client.gui;

import net.minecraft.client.input.KeyEvent;

/**
 * Input focus shared by all ClickGUI components: at most one component owns an ongoing mouse drag, and at most one
 * captures keyboard input (the search bar or a keybind selector that is listening).
 */
public final class GuiInput {
    public interface DragHandler {
        void drag(double mx, double my);

        default void release() {
        }
    }

    public interface KeyHandler {
        /** @return true if the key was consumed */
        boolean key(KeyEvent event);

        default boolean chr(int codepoint) {
            return false;
        }

        /** Called when focus moves elsewhere. */
        default void unfocus() {
        }
    }

    private static DragHandler drag;
    private static KeyHandler keys;

    private GuiInput() {
    }

    public static void startDrag(DragHandler handler) {
        drag = handler;
    }

    public static DragHandler drag() {
        return drag;
    }

    public static void endDrag() {
        if (drag != null) {
            DragHandler d = drag;
            drag = null;
            d.release();
        }
    }

    public static void focusKeys(KeyHandler handler) {
        if (keys != null && keys != handler) {
            KeyHandler old = keys;
            keys = null;
            old.unfocus();
        }
        keys = handler;
    }

    public static void releaseKeys(KeyHandler handler) {
        if (keys == handler) {
            keys = null;
        }
    }

    public static KeyHandler keys() {
        return keys;
    }

    public static boolean hasKeyFocus(KeyHandler handler) {
        return keys == handler;
    }

    public static void reset() {
        if (keys != null) {
            focusKeys(null);
        }
        drag = null;
    }
}
