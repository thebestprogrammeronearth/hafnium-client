package dev.hafnium.client.gui.setting;

import dev.hafnium.client.gui.anim.Animation;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.render.Icon;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.core.setting.ListSetting;

import java.util.List;

/** A value picked from a list that expands below the row. */
public final class DropdownComponent extends SettingComponent {
    private static final float OPTION = 13f;
    private static final float PAD = 2f;

    private final ListSetting setting;
    private final Animation open = new Animation(180, false);
    private int hoveredOption = -1;

    public DropdownComponent(ListSetting setting) {
        this.setting = setting;
    }

    private float listHeight() {
        return setting.options().size() * OPTION + PAD * 2;
    }

    @Override
    public float height() {
        return ROW + (listHeight() + 2f) * open.smooth();
    }

    @Override
    public boolean isAnimating() {
        return open.isAnimating();
    }

    @Override
    public void update(float dtMs, double mx, double my, boolean interactive) {
        super.update(dtMs, mx, my, interactive);
        open.update(dtMs);
        hoveredOption = -1;
        if (interactive && open.target() && GuiRenderUtil.inside(mx, my, x, y + ROW + PAD, w, listHeight() - PAD * 2)) {
            hoveredOption = (int) ((my - (y + ROW + PAD)) / OPTION);
        }
    }

    @Override
    public void render(double mx, double my) {
        float cy = rowCenter();
        float h = hover.eased();
        GuiRenderUtil.text(FontRole.SMALL, setting.name(), x, cy,
                ThemeManager.lerp(ThemeManager.TEXT_SECONDARY, ThemeManager.TEXT, h));
        float icon = 8f;
        float o = open.smooth();
        GuiRenderUtil.iconRotated(Icon.CHEVRON_DOWN, x + w - icon / 2f, cy, icon, (float) Math.PI * o,
                ThemeManager.withAlpha(ThemeManager.TEXT_SECONDARY, 0.6f + 0.4f * h));
        GuiRenderUtil.textRight(FontRole.SMALL, setting.get(), x + w - icon - 3f, cy,
                ThemeManager.lerp(ThemeManager.accent(), ThemeManager.accentHover(), h));

        if (o <= 0.001f) {
            return;
        }
        float ly = y + ROW;
        float lh = listHeight();
        GuiRenderUtil.pushScissor(x - 1, ly, w + 2, (lh + 2f) * o);
        GuiRenderUtil.roundRect(x, ly, w, lh, 3f, ThemeManager.withAlpha(ThemeManager.HEADER, 0.95f));
        GuiRenderUtil.roundOutline(x, ly, w, lh, 3f, ThemeManager.withAlpha(ThemeManager.BORDER, 0.8f));
        List<String> options = setting.options();
        for (int i = 0; i < options.size(); i++) {
            String opt = options.get(i);
            float oy = ly + PAD + i * OPTION;
            boolean selected = opt.equals(setting.get());
            if (i == hoveredOption) {
                GuiRenderUtil.roundRect(x + 2, oy, w - 4, OPTION, 2f, 0x14FFFFFF);
            }
            float ocy = oy + OPTION / 2f;
            GuiRenderUtil.text(FontRole.SMALL, opt, x + 6f, ocy,
                    selected ? ThemeManager.accent() : i == hoveredOption ? ThemeManager.TEXT : ThemeManager.TEXT_SECONDARY);
            if (selected) {
                GuiRenderUtil.icon(Icon.CHECK, x + w - 12f, ocy - 3.5f, 7f, ThemeManager.accent());
            }
        }
        GuiRenderUtil.popScissor();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overRow(mx, my) && (button == 0 || button == 1)) {
            open.setTarget(!open.target());
            return true;
        }
        if (open.target() && hoveredOption >= 0 && hoveredOption < setting.options().size() && button == 0) {
            setting.set(setting.options().get(hoveredOption));
            open.setTarget(false);
            return true;
        }
        return false;
    }
}
