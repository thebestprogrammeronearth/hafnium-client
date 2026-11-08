package dev.hafnium.client.gui.setting;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hafnium.client.gui.GuiInput;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.core.setting.KeybindSetting;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/** Click, then press a key to store it. Escape, Backspace or Delete clear the bind. Nothing listens for it in game. */
public final class KeybindComponent extends SettingComponent implements GuiInput.KeyHandler {
    private static final String LISTENING = "Press a key…";

    private final KeybindSetting setting;

    public KeybindComponent(KeybindSetting setting) {
        this.setting = setting;
    }

    private boolean listening() {
        return GuiInput.hasKeyFocus(this);
    }

    @Override
    public float height() {
        return ROW;
    }

    @Override
    public void render(double mx, double my) {
        float cy = rowCenter();
        float h = hover.eased();
        boolean listening = listening();
        String label = listening ? LISTENING : setting.keyName();
        float tw = GuiRenderUtil.textWidth(FontRole.SMALL, label);
        float bw = tw + 10f;
        float bh = 11f;
        float bx = x + w - bw;
        GuiRenderUtil.pushScissor(x, y, Math.max(0f, bx - x - 3f), ROW);
        GuiRenderUtil.text(FontRole.SMALL, setting.name(), x, cy,
                ThemeManager.lerp(ThemeManager.TEXT_SECONDARY, ThemeManager.TEXT, h));
        GuiRenderUtil.popScissor();
        float by = cy - bh / 2f;
        GuiRenderUtil.roundRect(bx, by, bw, bh, 3f, ThemeManager.lerp(ThemeManager.TRACK, 0xFF3F3750, h));
        if (listening) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(Util.getMillis() / 160.0);
            GuiRenderUtil.roundOutline(bx, by, bw, bh, 3f, ThemeManager.withAlpha(ThemeManager.accent(), pulse));
        }
        GuiRenderUtil.text(FontRole.SMALL, label, bx + 5f, cy, listening ? ThemeManager.accentHover() : ThemeManager.TEXT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!overRow(mx, my)) {
            return false;
        }
        if (button == 1 && !listening()) {
            setting.clear();
        } else if (listening()) {
            GuiInput.releaseKeys(this);
        } else {
            GuiInput.focusKeys(this);
        }
        return true;
    }

    @Override
    public boolean key(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            setting.clear();
        } else {
            setting.bind(key, InputConstants.getKey(event).getDisplayName().getString());
        }
        GuiInput.releaseKeys(this);
        return true;
    }
}
