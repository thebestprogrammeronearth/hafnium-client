package dev.hafnium.client.gui.setting;

import dev.hafnium.client.core.setting.StringSetting;
import dev.hafnium.client.gui.component.TextInput;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;

/** Name on the left, an editable text box on the right. Edits apply as you type; Enter or a click elsewhere ends editing. */
public final class TextComponent extends SettingComponent {
    private final StringSetting setting;
    private final TextInput input;

    public TextComponent(StringSetting setting) {
        this.setting = setting;
        this.input = new TextInput(setting.maxLength());
        this.input.setText(setting.get());
        this.input.onChange(setting::set);
        this.input.onEnter(this.input::blur);
    }

    private boolean editing() {
        return input.focused();
    }

    @Override
    public float height() {
        return ROW;
    }

    private float boxX() {
        return x + Math.min(w * 0.45f, GuiRenderUtil.textWidth(FontRole.SMALL, setting.name()) + 8f);
    }

    @Override
    public void render(double mx, double my) {
        float cy = rowCenter();
        float h = hover.eased();
        GuiRenderUtil.text(FontRole.SMALL, setting.name(), x, cy, ThemeManager.lerp(ThemeManager.TEXT_SECONDARY, ThemeManager.TEXT, h));
        float bx = boxX();
        float bw = x + w - bx;
        float bh = 11f;
        GuiRenderUtil.roundRect(bx, cy - bh / 2f, bw, bh, 3f, ThemeManager.lerp(ThemeManager.TRACK, 0xFF3F3750, h));
        if (editing()) {
            GuiRenderUtil.roundOutline(bx, cy - bh / 2f, bw, bh, 3f, ThemeManager.accent());
            input.render(FontRole.SMALL, bx + 4f, cy, bw - 8f, "", ThemeManager.TEXT, ThemeManager.TEXT_SECONDARY);
        } else {
            String shown = setting.get().isEmpty() ? "none" : setting.isPrivate() ? "•".repeat(Math.min(12, setting.get().length())) : setting.get();
            GuiRenderUtil.pushScissor(bx + 3f, cy - bh / 2f, bw - 6f, bh);
            GuiRenderUtil.text(FontRole.SMALL, shown, bx + 4f, cy, setting.get().isEmpty() ? ThemeManager.TEXT_SECONDARY : ThemeManager.TEXT);
            GuiRenderUtil.popScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!overRow(mx, my)) {
            if (editing()) {
                input.blur();
            }
            return false;
        }
        if (!editing()) {
            input.setText(setting.get());
            input.focus();
        }
        return true;
    }
}
