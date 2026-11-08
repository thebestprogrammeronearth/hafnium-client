package dev.hafnium.client.gui.component;

import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.setting.BoolComponent;
import dev.hafnium.client.gui.setting.ColorComponent;
import dev.hafnium.client.gui.setting.DropdownComponent;
import dev.hafnium.client.gui.setting.KeybindComponent;
import dev.hafnium.client.gui.setting.ModeComponent;
import dev.hafnium.client.gui.setting.SettingComponent;
import dev.hafnium.client.gui.setting.SliderComponent;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.ColorSetting;
import dev.hafnium.client.core.setting.KeybindSetting;
import dev.hafnium.client.core.setting.ListSetting;
import dev.hafnium.client.core.setting.ModeSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.core.setting.Setting;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** The inset area under a module row that lists its placeholder setting controls. */
public final class SettingsPanel {
    static final float MARGIN_X = 5f;
    static final float PAD_X = 6f;
    static final float PAD_Y = 3f;
    static final float SPACING = 1f;

    private final List<SettingComponent> components = new ArrayList<>();
    private float x;
    private float y;
    private float w;

    public SettingsPanel(Module module) {
        for (Setting<?> s : module.settings()) {
            SettingComponent c = create(s);
            if (c != null) {
                components.add(c);
            }
        }
    }

    private static SettingComponent create(Setting<?> s) {
        if (s instanceof BoolSetting b) {
            return new BoolComponent(b);
        }
        if (s instanceof NumberSetting n) {
            return new SliderComponent(n);
        }
        if (s instanceof ModeSetting m) {
            return new ModeComponent(m);
        }
        if (s instanceof ListSetting l) {
            return new DropdownComponent(l);
        }
        if (s instanceof KeybindSetting k) {
            return new KeybindComponent(k);
        }
        if (s instanceof ColorSetting c) {
            return new ColorComponent(c);
        }
        return null;
    }

    public List<SettingComponent> components() {
        return components;
    }

    /** Full height when open, including the gap below. */
    public float contentHeight() {
        float h = PAD_Y * 2 + 3f;
        for (SettingComponent c : components) {
            h += c.height() + SPACING;
        }
        return h;
    }

    public boolean isAnimating() {
        for (SettingComponent c : components) {
            if (c.isAnimating()) {
                return true;
            }
        }
        return false;
    }

    public void layout(float x, float y, float w) {
        this.x = x;
        this.y = y;
        this.w = w;
        float cy = y + PAD_Y;
        float cx = x + MARGIN_X + PAD_X;
        float cw = w - (MARGIN_X + PAD_X) * 2;
        for (SettingComponent c : components) {
            c.layout(cx, cy, cw);
            cy += c.height() + SPACING;
        }
    }

    public void update(float dtMs, double mx, double my, boolean interactive) {
        for (SettingComponent c : components) {
            c.update(dtMs, mx, my, interactive);
        }
    }

    public void render(double mx, double my, float openness) {
        float h = contentHeight() - 3f;
        GuiRenderUtil.roundRect(x + MARGIN_X, y, w - MARGIN_X * 2, h, 3f,
                ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.75f));
        // Thin accent rule on the left marks which module these settings belong to.
        GuiRenderUtil.roundRect(x + MARGIN_X, y + 3f, 1.5f, Math.max(0, h - 6f), 0.75f,
                ThemeManager.withAlpha(ThemeManager.accent(), 0.55f * openness));
        for (SettingComponent c : components) {
            c.render(mx, my);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        for (SettingComponent c : components) {
            if (c.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        for (SettingComponent c : components) {
            if (c.mouseScrolled(mx, my, amount)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        for (SettingComponent c : components) {
            if (c.keyPressed(event)) {
                return true;
            }
        }
        return false;
    }
}
