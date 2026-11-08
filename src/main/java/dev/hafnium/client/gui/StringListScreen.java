package dev.hafnium.client.gui;

import dev.hafnium.client.anubis.setting.StringListSetting;
import dev.hafnium.client.gui.anim.Animation;
import dev.hafnium.client.gui.component.TextInput;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.render.Icon;
import dev.hafnium.client.gui.theme.ThemeManager;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Editor for a list of text entries (for example waypoints), shown over the ClickGUI. Click an entry to edit it,
 * × removes it, "Add" appends an empty one. Edits apply as you type. Replaces Anubis's ImGui list editor.
 */
public final class StringListScreen extends Screen {
    private static final float W = 340f;
    private static final float ROW = 18f;
    private static final float PAD = 12f;
    private static final float BTN_H = 20f;
    private static final String[] BUTTONS = {"Add", "Clear all", "Done"};

    private final ClickGuiScreen parent;
    private final StringListSetting setting;
    private final String hint;
    private final TextInput input = new TextInput(256);
    private final Animation open = new Animation(180, false);
    private int editing = -1;
    private boolean closing;
    private long lastFrame;
    private int scroll;
    private int guiScale = 2;
    private int pixelScale = 2;
    private float uw;
    private float uh;

    public StringListScreen(ClickGuiScreen parent, StringListSetting setting, String hint) {
        super(Component.literal(setting.name()));
        this.parent = parent;
        this.setting = setting;
        this.hint = hint;
        input.onChange(text -> {
            if (editing >= 0) {
                setting.set(editing, text);
            }
        });
        input.onEnter(this::stopEditing);
    }

    @Override
    protected void init() {
        parent.init(width, height);
        lastFrame = Util.getMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (!closing) {
            stopEditing();
            closing = true;
            GuiInput.reset();
            open.setTarget(false);
        }
    }

    private void stopEditing() {
        editing = -1;
        input.blur();
    }

    private void edit(int index) {
        editing = index;
        input.setText(setting.entry(index).get());
        input.focus();
    }

    private float H() {
        return Math.min(uh - 30f, 360f);
    }

    public float x0() {
        return uw / 2f - W / 2f;
    }

    public float y0() {
        return uh / 2f - H() / 2f;
    }

    private float listY() {
        return y0() + 54f;
    }

    private int visibleRows() {
        return (int) ((H() - 54f - BTN_H - PAD - 8f) / ROW);
    }

    private float buttonsY() {
        return y0() + H() - PAD - BTN_H;
    }

    private float buttonW() {
        return (W - PAD * 2 - 10f) / BUTTONS.length;
    }

    private float buttonX(int i) {
        return x0() + PAD + i * (buttonW() + 5f);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        parent.renderBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        parent.renderGui(g, mouseX, mouseY, false);
        long now = Util.getMillis();
        float dt = Math.min(100f, Math.max(0f, now - lastFrame));
        lastFrame = now;
        if (!closing) {
            open.setTarget(true);
        }
        open.update(dt);
        if (closing && open.linear() <= 0f) {
            minecraft.setScreen(parent);
            return;
        }
        guiScale = parent.guiScale();
        pixelScale = parent.pixelScale();
        uw = width * guiScale / (float) pixelScale;
        uh = height * guiScale / (float) pixelScale;
        int n = setting.size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, n - visibleRows())));
        double mx = mouseX * guiScale / (double) pixelScale;
        double my = mouseY * guiScale / (double) pixelScale;
        GuiRenderUtil.begin(g, guiScale, pixelScale);
        try {
            float a = open.eased();
            GuiRenderUtil.rect(0, 0, uw, uh, ThemeManager.withAlpha(0xFF0C0A12, 0.55f * a));
            GuiRenderUtil.setAlpha(a);
            float x = x0();
            float y = y0();
            GuiRenderUtil.roundRect(x, y, W, H(), 6f, ThemeManager.withAlpha(ThemeManager.PANEL, 0.97f));
            GuiRenderUtil.roundOutline(x, y, W, H(), 6f, ThemeManager.withAlpha(ThemeManager.accent(), 0.75f));
            GuiRenderUtil.icon(Icon.PENCIL, x + PAD, y + 12f, 14f, ThemeManager.accent());
            GuiRenderUtil.text(FontRole.HEADER, setting.name().toUpperCase(Locale.ROOT), x + PAD + 20f, y + 19f, ThemeManager.TEXT);
            GuiRenderUtil.textRight(FontRole.SMALL, n + " / " + StringListSetting.MAX_ENTRIES, x + W - PAD, y + 19f, ThemeManager.TEXT_SECONDARY);
            GuiRenderUtil.text(FontRole.TINY, hint, x + PAD, y + 38f, ThemeManager.TEXT_SECONDARY);
            float ly = listY();
            for (int i = 0; i < visibleRows() && i + scroll < n; i++) {
                int index = i + scroll;
                float ry = ly + i * ROW;
                float cy = ry + ROW / 2f;
                boolean hov = GuiRenderUtil.inside(mx, my, x + PAD, ry, W - PAD * 2, ROW);
                boolean ed = index == editing;
                GuiRenderUtil.roundRect(x + PAD, ry + 1f, W - PAD * 2, ROW - 2f, 3f,
                        ed ? ThemeManager.withAlpha(ThemeManager.accent(), 0.14f) : hov ? 0x0EFFFFFF : 0x06FFFFFF);
                if (ed) {
                    GuiRenderUtil.roundOutline(x + PAD, ry + 1f, W - PAD * 2, ROW - 2f, 3f, ThemeManager.accent());
                    input.render(FontRole.SMALL, x + PAD + 6f, cy, W - PAD * 2 - 30f, "", ThemeManager.TEXT, ThemeManager.TEXT_MUTED);
                } else {
                    String text = setting.entry(index).get();
                    GuiRenderUtil.pushScissor(x + PAD + 4f, ry, W - PAD * 2 - 28f, ROW);
                    GuiRenderUtil.text(FontRole.SMALL, text.isBlank() ? "(empty)" : text, x + PAD + 6f, cy,
                            text.isBlank() ? ThemeManager.TEXT_MUTED : ThemeManager.TEXT);
                    GuiRenderUtil.popScissor();
                }
                boolean delHov = GuiRenderUtil.inside(mx, my, x + W - PAD - 18f, ry, 18f, ROW);
                GuiRenderUtil.icon(Icon.CLOSE, x + W - PAD - 14f, cy - 4.5f, 9f, delHov ? ThemeManager.DANGER : ThemeManager.TEXT_SECONDARY);
            }
            if (n == 0) {
                GuiRenderUtil.textCentered(FontRole.SMALL, "No entries yet. Press Add.", x + W / 2f, ly + 30f, ThemeManager.TEXT_SECONDARY);
            }
            for (int i = 0; i < BUTTONS.length; i++) {
                float bx = buttonX(i);
                boolean hov = GuiRenderUtil.inside(mx, my, bx, buttonsY(), buttonW(), BTN_H);
                boolean disabled = i == 0 && !setting.canAdd();
                GuiRenderUtil.roundRect(bx, buttonsY(), buttonW(), BTN_H, 4f, hov && !disabled ? ThemeManager.withAlpha(ThemeManager.accent(), 0.22f)
                        : ThemeManager.withAlpha(ThemeManager.HEADER, 0.95f));
                GuiRenderUtil.roundOutline(bx, buttonsY(), buttonW(), BTN_H, 4f, hov && !disabled ? ThemeManager.accent() : ThemeManager.BORDER);
                GuiRenderUtil.textCentered(FontRole.SMALL, BUTTONS[i], bx + buttonW() / 2f, buttonsY() + BTN_H / 2f,
                        disabled ? ThemeManager.TEXT_MUTED : ThemeManager.TEXT);
            }
            GuiRenderUtil.setAlpha(1f);
        } finally {
            GuiRenderUtil.end();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) {
            return true;
        }
        double mx = event.x() * guiScale / (double) pixelScale;
        double my = event.y() * guiScale / (double) pixelScale;
        float x = x0();
        if (!GuiRenderUtil.inside(mx, my, x, y0(), W, H())) {
            onClose();
            return true;
        }
        for (int i = 0; i < BUTTONS.length; i++) {
            if (GuiRenderUtil.inside(mx, my, buttonX(i), buttonsY(), buttonW(), BTN_H)) {
                switch (i) {
                    case 0 -> {
                        if (setting.canAdd()) {
                            setting.add();
                            scroll = Math.max(0, setting.size() - visibleRows());
                            edit(setting.size() - 1);
                        }
                    }
                    case 1 -> {
                        stopEditing();
                        setting.clear();
                    }
                    default -> onClose();
                }
                return true;
            }
        }
        float ly = listY();
        for (int i = 0; i < visibleRows() && i + scroll < setting.size(); i++) {
            float ry = ly + i * ROW;
            if (!GuiRenderUtil.inside(mx, my, x + PAD, ry, W - PAD * 2, ROW)) {
                continue;
            }
            int index = i + scroll;
            if (GuiRenderUtil.inside(mx, my, x + W - PAD - 18f, ry, 18f, ROW)) {
                stopEditing();
                setting.remove(index);
            } else {
                edit(index);
            }
            return true;
        }
        stopEditing();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll -= (int) Math.signum(vertical) * 3;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            if (editing >= 0) {
                stopEditing();
            } else {
                onClose();
            }
            return true;
        }
        return editing < 0 || input.key(event) || true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return editing >= 0 && input.chr(event.codepoint());
    }
}
