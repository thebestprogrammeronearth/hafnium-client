package dev.hafnium.client.gui;

import dev.hafnium.client.config.MockConfig;
import dev.hafnium.client.config.MockConfigManager;
import dev.hafnium.client.gui.anim.Animation;
import dev.hafnium.client.gui.anim.AnimationUtil;
import dev.hafnium.client.gui.component.TextInput;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.render.Icon;
import dev.hafnium.client.gui.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Config manager drawn over the ClickGUI. Create, load, save, rename and delete act on in-memory configs only
 * (a config is a snapshot of which placeholder modules are toggled on); nothing touches the disk.
 */
public final class ConfigScreen extends Screen {
    private static final float W = 290f;
    private static final float ROW_H = 21f;
    private static final int VISIBLE_ROWS = 6;
    private static final float PAD = 12f;
    private static final float BTN_H = 21f;
    private static final long CONFIRM_MS = 3000;
    private static final long TOAST_MS = 2200;

    private enum Action {
        CREATE("Create", Icon.PLUS), LOAD("Load", Icon.DOWNLOAD), SAVE("Save", Icon.SAVE),
        RENAME("Rename", Icon.PENCIL), DELETE("Delete", Icon.TRASH);

        final String label;
        final Icon icon;

        Action(String label, Icon icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private enum InputMode { NONE, CREATE, RENAME }

    private final ClickGuiScreen parent;
    private final MockConfigManager manager = MockConfigManager.get();
    private final Animation open = new Animation(180, false);
    private final Animation inputAnim = new Animation(180, false);
    private final Animation[] buttonHover = new Animation[Action.values().length];
    private final Animation closeHover = new Animation(150, false);
    private final TextInput input = new TextInput(24);

    private boolean closing;
    private long lastFrame;
    private MockConfig selected;
    private int scroll;
    private int hoveredRow = -1;
    private InputMode inputMode = InputMode.NONE;
    private String error = "";
    private long deleteArmedAt;
    private String toast = "";
    private long toastAt;

    private int guiScale = 2;
    private int pixelScale = 2;
    private float uw;
    private float uh;

    public ConfigScreen(ClickGuiScreen parent) {
        super(Component.translatable("hafnium.configs.title"));
        this.parent = parent;
        for (int i = 0; i < buttonHover.length; i++) {
            buttonHover[i] = new Animation(150, false);
        }
        this.selected = manager.active();
        input.onEnter(this::confirmInput);
        input.onChange(s -> error = "");
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
            closing = true;
            GuiInput.reset();
            open.setTarget(false);
        }
    }

    /** Uses the ClickGUI's pixel scale so the modal matches the panels behind it. */
    private void computeScale() {
        guiScale = parent.guiScale();
        pixelScale = parent.pixelScale();
        uw = width * guiScale / (float) pixelScale;
        uh = height * guiScale / (float) pixelScale;
    }

    private double ux(double gx) {
        return gx * guiScale / pixelScale;
    }

    private double uy(double gy) {
        return gy * guiScale / pixelScale;
    }

    // ------------------------------------------------------------------ geometry

    private float listH() {
        return VISIBLE_ROWS * ROW_H + 4f;
    }

    private float inputH() {
        return 30f * inputAnim.smooth();
    }

    private float H() {
        return 46f + listH() + 8f + inputH() + BTN_H + PAD + 4f;
    }

    public float x0() {
        return uw / 2f - W / 2f;
    }

    private float y0() {
        return uh / 2f - H() / 2f;
    }

    public float listY() {
        return y0() + 46f;
    }

    private float inputY() {
        return listY() + listH() + 8f;
    }

    public float buttonsY() {
        return inputY() + inputH();
    }

    public float buttonW() {
        return (W - PAD * 2 - 4 * 5f) / 5f;
    }

    public float buttonX(int i) {
        return x0() + PAD + i * (buttonW() + 5f);
    }

    private float closeCx() {
        return x0() + W - 17f;
    }

    private float closeCy() {
        return y0() + 19f;
    }

    // ------------------------------------------------------------------ read-only accessors (also used by the client gametests)

    /** Index of an action button: Create, Load, Save, Rename, Delete. */
    public static int actionIndex(String label) {
        for (Action a : Action.values()) {
            if (a.label.equalsIgnoreCase(label)) {
                return a.ordinal();
            }
        }
        throw new IllegalArgumentException("No config action " + label);
    }

    public float rowCenterY(int visibleIndex) {
        return listY() + 2f + visibleIndex * ROW_H + ROW_H / 2f;
    }

    public float buttonHeight() {
        return BTN_H;
    }

    public MockConfig selected() {
        return selected;
    }

    public boolean isEnteringName() {
        return inputMode != InputMode.NONE;
    }

    public String nameInput() {
        return input.text();
    }

    public String error() {
        return error;
    }

    public boolean isDeleteArmed() {
        return deleteArmedAt != 0;
    }

    // ------------------------------------------------------------------ rendering

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
        inputAnim.setTarget(inputMode != InputMode.NONE);
        inputAnim.update(dt);
        computeScale();
        if (deleteArmedAt != 0 && now - deleteArmedAt > CONFIRM_MS) {
            deleteArmedAt = 0;
        }

        double mx = closing ? -1000 : ux(mouseX);
        double my = closing ? -1000 : uy(mouseY);
        List<MockConfig> configs = manager.configs();
        hoveredRow = -1;
        if (GuiRenderUtil.inside(mx, my, x0() + PAD, listY() + 2f, W - PAD * 2, listH() - 4f)) {
            int r = (int) ((my - listY() - 2f) / ROW_H) + scroll;
            if (r >= 0 && r < configs.size()) {
                hoveredRow = r;
            }
        }
        for (Action a : Action.values()) {
            Animation h = buttonHover[a.ordinal()];
            h.setTarget(enabled(a) && GuiRenderUtil.inside(mx, my, buttonX(a.ordinal()), buttonsY(), buttonW(), BTN_H));
            h.update(dt);
        }
        closeHover.setTarget(Math.abs(mx - closeCx()) < 10 && Math.abs(my - closeCy()) < 10);
        closeHover.update(dt);

        GuiRenderUtil.begin(g, guiScale, pixelScale);
        float a = open.eased();
        // dim the ClickGUI behind the modal
        GuiRenderUtil.rect(0, 0, uw, uh, ThemeManager.withAlpha(0xFF0C0A12, 0.55f * a));
        GuiRenderUtil.setAlpha(a);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        float s = AnimationUtil.lerp(0.95f, 1f, a);
        float cx = uw * pixelScale / 2f;
        float cy = uh * pixelScale / 2f;
        pose.translate(cx, cy);
        pose.scale(s, s);
        pose.translate(-cx, -cy);

        renderModal(configs, now);

        pose.popMatrix();
        GuiRenderUtil.end();
    }

    private void renderModal(List<MockConfig> configs, long now) {
        float x = x0();
        float y = y0();
        float h = H();
        GuiRenderUtil.roundRect(x, y, W, h, 6f, ThemeManager.withAlpha(ThemeManager.PANEL, 0.97f));
        GuiRenderUtil.roundOutline(x, y, W, h, 6f, ThemeManager.withAlpha(ThemeManager.accent(), 0.75f));

        // header
        GuiRenderUtil.icon(Icon.FOLDER, x + PAD, y + 12f, 14f, ThemeManager.accent());
        GuiRenderUtil.text(FontRole.HEADER, "CONFIGS", x + PAD + 20f, y + 19f, ThemeManager.TEXT);
        GuiRenderUtil.text(FontRole.SMALL, "Preview only · nothing is written to disk", x + PAD, y + 35f, ThemeManager.TEXT_MUTED);
        float ch = closeHover.eased();
        if (ch > 0.01f) {
            GuiRenderUtil.circle(closeCx(), closeCy(), 9f, ThemeManager.withAlpha(0xFFFFFFFF, 0.07f * ch));
        }
        GuiRenderUtil.icon(Icon.CLOSE, closeCx() - 9f, closeCy() - 9f, 18f, ThemeManager.lerp(ThemeManager.TEXT_SECONDARY, ThemeManager.TEXT, ch));

        // list
        float lx = x + PAD;
        float ly = listY();
        float lw = W - PAD * 2;
        GuiRenderUtil.roundRect(lx, ly, lw, listH(), 4f, ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.85f));
        GuiRenderUtil.roundOutline(lx, ly, lw, listH(), 4f, ThemeManager.withAlpha(ThemeManager.BORDER, 0.8f));
        if (configs.isEmpty()) {
            GuiRenderUtil.textCentered(FontRole.SMALL, "No configs yet — press Create", lx + lw / 2f, ly + listH() / 2f, ThemeManager.TEXT_SECONDARY);
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, configs.size() - VISIBLE_ROWS)));
        for (int i = 0; i < VISIBLE_ROWS && i + scroll < configs.size(); i++) {
            MockConfig c = configs.get(i + scroll);
            float ry = ly + 2f + i * ROW_H;
            boolean sel = c == selected;
            boolean hov = hoveredRow == i + scroll;
            if (sel) {
                GuiRenderUtil.roundRect(lx + 2f, ry, lw - 4f, ROW_H, 3f, ThemeManager.withAlpha(ThemeManager.accent(), 0.14f));
                GuiRenderUtil.roundOutline(lx + 2f, ry, lw - 4f, ROW_H, 3f, ThemeManager.withAlpha(ThemeManager.accent(), 0.55f));
            } else if (hov) {
                GuiRenderUtil.roundRect(lx + 2f, ry, lw - 4f, ROW_H, 3f, 0x0EFFFFFF);
            }
            float rcy = ry + ROW_H / 2f;
            boolean active = c == manager.active();
            GuiRenderUtil.circle(lx + 11f, rcy, 2.5f, active ? ThemeManager.accent() : ThemeManager.DOT_OFF);
            float nw = GuiRenderUtil.text(FontRole.TEXT, c.name(), lx + 20f, rcy, sel ? ThemeManager.TEXT : 0xFFE4E0EA);
            if (active) {
                float tx = lx + 26f + nw;
                float tw = GuiRenderUtil.textWidth(FontRole.TINY, "ACTIVE") + 8f;
                GuiRenderUtil.roundRect(tx, rcy - 5f, tw, 10f, 3f, ThemeManager.withAlpha(ThemeManager.accent(), 0.2f));
                GuiRenderUtil.text(FontRole.TINY, "ACTIVE", tx + 4f, rcy, ThemeManager.accentHover());
            }
            GuiRenderUtil.textRight(FontRole.SMALL, MockConfigManager.ago(c.savedAt()), lx + lw - 10f, rcy, ThemeManager.TEXT_SECONDARY);
            if (i + scroll < configs.size() - 1 && i < VISIBLE_ROWS - 1) {
                GuiRenderUtil.hLine(lx + 8f, lx + lw - 8f, ry + ROW_H - 1f / GuiRenderUtil.scale(), ThemeManager.SEPARATOR);
            }
        }

        // name input
        float ia = inputAnim.smooth();
        if (ia > 0.01f) {
            float iy = inputY();
            GuiRenderUtil.pushScissor(lx - 1f, iy, lw + 2f, inputH());
            float fh = 22f;
            GuiRenderUtil.roundRect(lx, iy, lw, fh, 4f, ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.9f));
            GuiRenderUtil.roundOutline(lx, iy, lw, fh, 4f, error.isEmpty() ? ThemeManager.accent() : ThemeManager.DANGER);
            String label = inputMode == InputMode.RENAME ? "Rename:" : "New:";
            float lw2 = GuiRenderUtil.text(FontRole.SMALL, label, lx + 8f, iy + fh / 2f, ThemeManager.TEXT_SECONDARY);
            float fx = lx + 12f + lw2;
            String hint = error.isEmpty() ? "Enter ↵ to confirm · Esc to cancel" : error;
            float hw = GuiRenderUtil.textRight(FontRole.SMALL, hint, lx + lw - 8f, iy + fh / 2f,
                    error.isEmpty() ? ThemeManager.TEXT_MUTED : ThemeManager.DANGER);
            input.render(FontRole.TEXT, fx, iy + fh / 2f, lx + lw - 14f - hw - fx, "config name", ThemeManager.TEXT, ThemeManager.TEXT_MUTED);
            GuiRenderUtil.popScissor();
        }

        // buttons
        for (Action act : Action.values()) {
            renderButton(act, now);
        }

        // toast
        if (!toast.isEmpty() && now - toastAt < TOAST_MS) {
            float t = 1f - AnimationUtil.clamp01((now - toastAt - (TOAST_MS - 400)) / 400f);
            float tw = GuiRenderUtil.textWidth(FontRole.SMALL, toast) + 26f;
            float tx = uw / 2f - tw / 2f;
            float ty = y + h + 8f;
            float prev = GuiRenderUtil.alpha();
            GuiRenderUtil.setAlpha(prev * t);
            GuiRenderUtil.roundRect(tx, ty, tw, 18f, 9f, ThemeManager.withAlpha(ThemeManager.HEADER, 0.95f));
            GuiRenderUtil.roundOutline(tx, ty, tw, 18f, 9f, ThemeManager.withAlpha(ThemeManager.accent(), 0.7f));
            GuiRenderUtil.icon(Icon.CHECK, tx + 7f, ty + 5f, 8f, ThemeManager.accent());
            GuiRenderUtil.text(FontRole.SMALL, toast, tx + 18f, ty + 9f, ThemeManager.TEXT);
            GuiRenderUtil.setAlpha(prev);
        }
    }

    private boolean enabled(Action a) {
        return a == Action.CREATE || selected != null;
    }

    private void renderButton(Action act, long now) {
        float bx = buttonX(act.ordinal());
        float by = buttonsY();
        float bw = buttonW();
        boolean en = enabled(act);
        float hv = buttonHover[act.ordinal()].eased();
        boolean armed = act == Action.DELETE && deleteArmedAt != 0;
        int accent = act == Action.DELETE ? ThemeManager.DANGER : ThemeManager.accent();
        float prev = GuiRenderUtil.alpha();
        if (!en) {
            GuiRenderUtil.setAlpha(prev * 0.4f);
        }
        int fill = armed ? ThemeManager.withAlpha(ThemeManager.DANGER, 0.25f)
                : ThemeManager.lerp(ThemeManager.withAlpha(ThemeManager.HEADER, 0.95f), ThemeManager.withAlpha(accent, 0.22f), hv);
        GuiRenderUtil.roundRect(bx, by, bw, BTN_H, 4f, fill);
        GuiRenderUtil.roundOutline(bx, by, bw, BTN_H, 4f,
                ThemeManager.lerp(ThemeManager.withAlpha(ThemeManager.BORDER, 1f), accent, armed ? 1f : hv));
        String label = armed ? "Confirm" : act.label;
        float icon = 8f;
        float tw = GuiRenderUtil.textWidth(FontRole.SMALL, label);
        float total = icon + 4f + tw;
        float sx = bx + bw / 2f - total / 2f;
        float cy = by + BTN_H / 2f;
        int fg = armed ? ThemeManager.DANGER : ThemeManager.lerp(ThemeManager.TEXT, act == Action.DELETE ? ThemeManager.DANGER : ThemeManager.accentHover(), hv);
        GuiRenderUtil.icon(armed ? Icon.CHECK : act.icon, sx, cy - icon / 2f, icon, fg);
        GuiRenderUtil.text(FontRole.SMALL, label, sx + icon + 4f, cy, fg);
        GuiRenderUtil.setAlpha(prev);
    }

    // ------------------------------------------------------------------ actions

    private void run(Action act) {
        if (!enabled(act)) {
            return;
        }
        if (act != Action.DELETE) {
            deleteArmedAt = 0;
        }
        switch (act) {
            case CREATE -> beginInput(InputMode.CREATE, "");
            case RENAME -> beginInput(InputMode.RENAME, selected.name());
            case LOAD -> {
                manager.load(selected);
                toast("Loaded “" + selected.name() + "”");
            }
            case SAVE -> {
                manager.save(selected);
                toast("Saved “" + selected.name() + "”");
            }
            case DELETE -> {
                if (deleteArmedAt == 0) {
                    deleteArmedAt = Util.getMillis();
                } else {
                    String name = selected.name();
                    manager.delete(selected);
                    selected = manager.active();
                    deleteArmedAt = 0;
                    toast("Deleted “" + name + "”");
                }
            }
        }
    }

    private void beginInput(InputMode mode, String initial) {
        inputMode = mode;
        input.setText(initial);
        error = "";
        input.focus();
    }

    private void cancelInput() {
        inputMode = InputMode.NONE;
        input.blur();
        error = "";
    }

    private void confirmInput() {
        String name = input.text().trim();
        if (name.isEmpty()) {
            error = "Name can't be empty";
            return;
        }
        boolean sameAsSelected = inputMode == InputMode.RENAME && selected != null && selected.name().equalsIgnoreCase(name);
        if (manager.exists(name) && !sameAsSelected) {
            error = "“" + name + "” already exists";
            return;
        }
        if (inputMode == InputMode.CREATE) {
            selected = manager.create(name);
            scroll = Integer.MAX_VALUE;
            toast("Created “" + name + "”");
        } else if (selected != null) {
            selected.rename(name);
            toast("Renamed to “" + name + "”");
        }
        cancelInput();
    }

    private void toast(String msg) {
        toast = msg;
        toastAt = Util.getMillis();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) {
            return true;
        }
        double mx = ux(event.x());
        double my = uy(event.y());
        if (!GuiRenderUtil.inside(mx, my, x0(), y0(), W, H())) {
            onClose();
            return true;
        }
        if (Math.abs(mx - closeCx()) < 10 && Math.abs(my - closeCy()) < 10) {
            onClose();
            return true;
        }
        for (Action act : Action.values()) {
            if (GuiRenderUtil.inside(mx, my, buttonX(act.ordinal()), buttonsY(), buttonW(), BTN_H)) {
                run(act);
                return true;
            }
        }
        if (hoveredRow >= 0) {
            MockConfig c = manager.configs().get(hoveredRow);
            if (c != selected) {
                deleteArmedAt = 0;
            }
            selected = c;
            if (doubleClick) {
                run(Action.LOAD);
            }
            return true;
        }
        if (inputMode != InputMode.NONE && GuiRenderUtil.inside(mx, my, x0() + PAD, inputY(), W - PAD * 2, 22f)) {
            input.focus();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll -= (int) Math.signum(vertical);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing) {
            return true;
        }
        if (event.isEscape()) {
            if (inputMode != InputMode.NONE) {
                cancelInput();
            } else {
                onClose();
            }
            return true;
        }
        if (inputMode != InputMode.NONE && input.key(event)) {
            return true;
        }
        List<MockConfig> configs = manager.configs();
        if (!configs.isEmpty() && (event.key() == GLFW.GLFW_KEY_DOWN || event.key() == GLFW.GLFW_KEY_UP)) {
            int i = selected == null ? -1 : configs.indexOf(selected);
            i = Math.floorMod(i + (event.key() == GLFW.GLFW_KEY_DOWN ? 1 : -1), configs.size());
            selected = configs.get(i);
            if (i < scroll) {
                scroll = i;
            } else if (i >= scroll + VISIBLE_ROWS) {
                scroll = i - VISIBLE_ROWS + 1;
            }
            deleteArmedAt = 0;
            return true;
        }
        if (event.isConfirmation() && selected != null) {
            run(Action.LOAD);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return inputMode != InputMode.NONE && input.chr(event.codepoint());
    }
}
