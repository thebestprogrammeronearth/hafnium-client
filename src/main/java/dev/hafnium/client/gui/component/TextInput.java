package dev.hafnium.client.gui.component;

import dev.hafnium.client.gui.GuiInput;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Minimal single-line text entry (append/erase at the end, paste, clear) used by search and the config screen. */
public final class TextInput implements GuiInput.KeyHandler {
    private final int maxLength;
    private String text = "";
    private Consumer<String> onChange = s -> { };
    private Runnable onEnter = () -> { };

    public TextInput(int maxLength) {
        this.maxLength = maxLength;
    }

    public String text() {
        return text;
    }

    public void setText(String value) {
        String v = value.length() > maxLength ? value.substring(0, maxLength) : value;
        if (!v.equals(text)) {
            text = v;
            onChange.accept(text);
        }
    }

    public void onChange(Consumer<String> listener) {
        this.onChange = listener;
    }

    public void onEnter(Runnable listener) {
        this.onEnter = listener;
    }

    public boolean focused() {
        return GuiInput.hasKeyFocus(this);
    }

    public void focus() {
        GuiInput.focusKeys(this);
    }

    public void blur() {
        GuiInput.releaseKeys(this);
    }

    @Override
    public boolean key(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!text.isEmpty()) {
                if (event.hasControlDown()) {
                    String trimmed = text.stripTrailing();
                    int cut = trimmed.lastIndexOf(' ');
                    setText(cut < 0 ? "" : trimmed.substring(0, cut + 1));
                } else {
                    setText(text.substring(0, text.length() - 1));
                }
            }
            return true;
        }
        if (event.isPaste()) {
            String clip = StringUtil.filterText(Minecraft.getInstance().keyboardHandler.getClipboard());
            setText(text + clip);
            return true;
        }
        if (event.isConfirmation()) {
            onEnter.run();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            setText("");
            return true;
        }
        return false;
    }

    @Override
    public boolean chr(int codepoint) {
        if (!StringUtil.isAllowedChatCharacter(codepoint)) {
            return false;
        }
        setText(text + Character.toString(codepoint));
        return true;
    }

    /** Draws the text (or placeholder) and a blinking caret when focused. */
    public void render(FontRole role, float x, float cy, float maxW, String placeholder, int textColor, int placeholderColor) {
        float h = GuiRenderUtil.capHeight(role) + 8f;
        GuiRenderUtil.pushScissor(x - 1f, cy - h / 2f, maxW + 2f, h);
        float tw = GuiRenderUtil.textWidth(role, text);
        float offset = Math.max(0, tw + 3f - maxW);
        if (text.isEmpty()) {
            GuiRenderUtil.text(role, placeholder, x, cy, placeholderColor);
        } else {
            GuiRenderUtil.text(role, text, x - offset, cy, textColor);
        }
        if (focused() && (Util.getMillis() / 530) % 2 == 0) {
            float cx = x - offset + tw + 0.75f;
            float ch = GuiRenderUtil.capHeight(role) + 3f;
            GuiRenderUtil.rect(cx, cy - ch / 2f, 1f / GuiRenderUtil.scale() * Math.max(1, GuiRenderUtil.scale() / 2),
                    ch, ThemeManager.accentHover());
        }
        GuiRenderUtil.popScissor();
    }
}
