package dev.hafnium.client.gui.component;

import dev.hafnium.client.gui.anim.Animation;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.render.Icon;
import dev.hafnium.client.gui.theme.ThemeManager;

import java.util.Locale;
import java.util.function.Consumer;

/** The pill-shaped search field at the top centre. Filtering is case-insensitive and live. */
public final class SearchBar {
    public static final float WIDTH = 172f;
    public static final float HEIGHT = 25f;

    private final TextInput input = new TextInput(32);
    private final Animation hover = new Animation(150, false);
    private final Animation focus = new Animation(150, false);
    private final Animation clearHover = new Animation(120, false);
    private final Animation hasText = new Animation(150, false);
    private float x;
    private float y;

    public SearchBar(Consumer<String> onQuery) {
        input.onChange(text -> onQuery.accept(text.trim().toLowerCase(Locale.ROOT)));
        input.onEnter(input::blur);
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public TextInput input() {
        return input;
    }

    public void layout(float x, float y) {
        this.x = x;
        this.y = y;
    }

    private boolean overClear(double mx, double my) {
        return !input.text().isEmpty() && GuiRenderUtil.inside(mx, my, x + WIDTH - 22f, y, 22f, HEIGHT);
    }

    public void update(float dtMs, double mx, double my, boolean interactive) {
        hover.setTarget(interactive && GuiRenderUtil.inside(mx, my, x, y, WIDTH, HEIGHT));
        hover.update(dtMs);
        focus.setTarget(input.focused());
        focus.update(dtMs);
        clearHover.setTarget(interactive && overClear(mx, my));
        clearHover.update(dtMs);
        hasText.setTarget(!input.text().isEmpty());
        hasText.update(dtMs);
    }

    public void render() {
        float r = HEIGHT / 2f;
        float f = focus.eased();
        float h = hover.eased();
        GuiRenderUtil.roundRect(x, y, WIDTH, HEIGHT, r,
                ThemeManager.lerp(ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.72f), ThemeManager.withAlpha(ThemeManager.HEADER, 0.9f), Math.max(f, h * 0.6f)));
        GuiRenderUtil.roundOutline(x, y, WIDTH, HEIGHT, r,
                ThemeManager.withAlpha(ThemeManager.lerp(ThemeManager.accent(), ThemeManager.accentHover(), f), 0.7f + 0.3f * Math.max(f, h)));
        float cy = y + HEIGHT / 2f;
        float icon = 14f;
        GuiRenderUtil.icon(Icon.SEARCH, x + 11f, cy - icon / 2f, icon, ThemeManager.lerp(ThemeManager.TEXT_SECONDARY, ThemeManager.TEXT, f));
        float textX = x + 33f;
        float clearSpace = 22f * hasText.eased();
        input.render(FontRole.TEXT, textX, cy, WIDTH - (textX - x) - 10f - clearSpace, "Search...",
                ThemeManager.TEXT, ThemeManager.TEXT_SECONDARY);
        float ht = hasText.eased();
        if (ht > 0.01f) {
            float c = 9f;
            GuiRenderUtil.icon(Icon.CLOSE, x + WIDTH - 11f - c / 2f - 3f, cy - c / 2f, c,
                    ThemeManager.withAlpha(ThemeManager.lerp(ThemeManager.TEXT_SECONDARY, ThemeManager.TEXT, clearHover.eased()), ht));
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!GuiRenderUtil.inside(mx, my, x, y, WIDTH, HEIGHT)) {
            return false;
        }
        if (overClear(mx, my) || button == 1) {
            input.setText("");
        }
        input.focus();
        return true;
    }

    public void clear() {
        input.setText("");
    }
}
