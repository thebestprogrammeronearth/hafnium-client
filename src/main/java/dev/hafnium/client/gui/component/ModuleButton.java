package dev.hafnium.client.gui.component;

import dev.hafnium.client.gui.anim.Animation;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleState;
import net.minecraft.client.input.KeyEvent;

/**
 * One module row: left-click toggles the module through the {@link dev.hafnium.client.core.module.ModuleManager}
 * (settings-only modules open their settings instead), right-click opens the settings dropdown.
 */
public final class ModuleButton {
    public static final float ROW_H = 21f;
    private static final float TEXT_X = 12f;
    private static final float DOT_INSET = 14f;
    private static final float DOT_R = 3f;

    private final Module module;
    private final SettingsPanel settings;
    private final Animation hover = new Animation(150, false);
    private final Animation toggle;
    private final Animation expand = new Animation(220, false);
    private final Animation visible = new Animation(170, true);

    private float x;
    private float y;
    private float w;
    private boolean matches = true;
    private String pre = "";
    private String mid = "";
    private String post = "";

    public ModuleButton(Module module) {
        this.module = module;
        this.settings = new SettingsPanel(module);
        this.toggle = new Animation(180, module.isEnabled());
    }

    public float x() {
        return x;
    }

    /** Top of the row in layout units. */
    public float rowY() {
        return y;
    }

    public SettingsPanel settingsPanel() {
        return settings;
    }

    public Module module() {
        return module;
    }

    /** Applies a lower-case query; an empty query shows every row. */
    public void setQuery(String query) {
        if (query.isEmpty()) {
            matches = true;
            pre = mid = post = "";
        } else {
            int i = module.searchKey().indexOf(query);
            matches = i >= 0;
            if (matches) {
                String n = module.name();
                pre = n.substring(0, i);
                mid = n.substring(i, i + query.length());
                post = n.substring(i + query.length());
            }
        }
        visible.setTarget(matches);
    }

    public boolean matches() {
        return matches;
    }

    public void setExpanded(boolean open) {
        expand.setTarget(open);
    }

    public boolean isExpanded() {
        return expand.target();
    }

    public float height() {
        return (ROW_H + settings.contentHeight() * expand.smooth()) * visible.smooth();
    }

    public boolean isAnimating() {
        return visible.isAnimating() || expand.isAnimating() || (expand.linear() > 0 && settings.isAnimating());
    }

    public boolean isRowHovered(double mx, double my) {
        return visible.target() && GuiRenderUtil.inside(mx, my, x, y, w, ROW_H);
    }

    public void layout(float x, float y, float w) {
        this.x = x;
        this.y = y;
        this.w = w;
        if (expand.linear() > 0) {
            settings.layout(x, y + ROW_H, w);
        }
    }

    public void update(float dtMs, double mx, double my, boolean interactive) {
        hover.setTarget(interactive && isRowHovered(mx, my));
        hover.update(dtMs);
        toggle.setTarget(module.isEnabled());
        toggle.update(dtMs);
        expand.update(dtMs);
        visible.update(dtMs);
        if (expand.linear() > 0) {
            settings.update(dtMs, mx, my, interactive && expand.target());
        }
    }

    public void render(double mx, double my, boolean separator) {
        float vis = visible.smooth();
        if (vis <= 0.001f) {
            return;
        }
        float h = height();
        boolean clip = vis < 1f || expand.isAnimating();
        if (clip) {
            GuiRenderUtil.pushScissor(x, y, w, h);
        }
        float hv = hover.eased();
        float on = toggle.eased();

        if (hv > 0 || on > 0) {
            int bg = ThemeManager.withAlpha(0xFFFFFFFF, 0.045f * hv);
            GuiRenderUtil.roundRect(x + 4f, y + 1.5f, w - 8f, ROW_H - 3f, 3f, bg);
            if (on > 0) {
                GuiRenderUtil.roundRect(x + 4f, y + 1.5f, w - 8f, ROW_H - 3f, 3f,
                        ThemeManager.withAlpha(ThemeManager.accent(), 0.08f * on));
            }
        }

        float cy = y + ROW_H / 2f;
        int textColor = ThemeManager.lerp(ThemeManager.lerp(0xFFE4E0EA, ThemeManager.TEXT, hv), ThemeManager.accentHover(), 0.55f * on);
        // Long names (e.g. "Ancient Debris Finder") step down to the small font so they clear the status dot.
        FontRole role = GuiRenderUtil.textWidth(FontRole.TEXT, module.name()) > w - TEXT_X - DOT_INSET - 6f ? FontRole.SMALL : FontRole.TEXT;
        if (mid.isEmpty()) {
            GuiRenderUtil.text(role, module.name(), x + TEXT_X, cy, textColor);
        } else {
            float tx = x + TEXT_X;
            tx += GuiRenderUtil.text(role, pre, tx, cy, textColor);
            tx += GuiRenderUtil.text(role, mid, tx, cy, ThemeManager.accent());
            GuiRenderUtil.text(role, post, tx, cy, textColor);
        }

        float dx = x + w - DOT_INSET;
        if (!module.isToggleable()) {
            // Settings-only module: a small chevron instead of the on/off dot.
            float iconSize = 9f;
            GuiRenderUtil.iconRotated(dev.hafnium.client.gui.render.Icon.CHEVRON_DOWN, dx, cy, iconSize,
                    (float) (-Math.PI / 2) * (1f - expand.smooth()), ThemeManager.lerp(ThemeManager.DOT_OFF, ThemeManager.TEXT, hv));
        } else if (module.state() == ModuleState.FAILED) {
            GuiRenderUtil.circle(dx, cy, DOT_R + 2f, ThemeManager.withAlpha(ThemeManager.DANGER, 0.25f));
            GuiRenderUtil.circle(dx, cy, DOT_R, ThemeManager.DANGER);
        } else {
            if (on > 0) {
                GuiRenderUtil.circle(dx, cy, DOT_R + 2.5f * on, ThemeManager.withAlpha(ThemeManager.accent(), 0.22f * on));
            }
            GuiRenderUtil.circle(dx, cy, DOT_R, ThemeManager.lerp(
                    ThemeManager.lerp(ThemeManager.DOT_OFF, 0xFF8C859A, hv), ThemeManager.accent(), on));
        }

        float e = expand.smooth();
        if (e > 0.001f) {
            settings.render(mx, my, e);
        }
        if (clip) {
            GuiRenderUtil.popScissor();
        }
        if (separator) {
            GuiRenderUtil.hLine(x + 8f, x + w - 8f, y + h - 1f / GuiRenderUtil.scale(),
                    ThemeManager.withAlpha(ThemeManager.SEPARATOR, vis));
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible.target()) {
            return false;
        }
        if (isRowHovered(mx, my)) {
            if (button == 0 && module.isToggleable()) {
                HafniumCore.get().modules().toggle(module);
            } else if (button == 0 || button == 1) {
                expand.setTarget(!expand.target());
            }
            return true;
        }
        if (expand.target() && GuiRenderUtil.inside(mx, my, x, y + ROW_H, w, settings.contentHeight())) {
            settings.mouseClicked(mx, my, button);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return expand.target() && settings.mouseScrolled(mx, my, amount);
    }

    public boolean keyPressed(KeyEvent event) {
        return expand.target() && settings.keyPressed(event);
    }
}
