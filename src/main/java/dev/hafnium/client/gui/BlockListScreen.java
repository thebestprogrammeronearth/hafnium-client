package dev.hafnium.client.gui;

import dev.hafnium.client.anubis.setting.BlockColorListSetting;
import dev.hafnium.client.anubis.setting.BlockColorListSetting.BlockEntry;
import dev.hafnium.client.gui.anim.Animation;
import dev.hafnium.client.gui.component.TextInput;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.render.Icon;
import dev.hafnium.client.gui.theme.ThemeManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Editor for a {@link BlockColorListSetting} (Block ESP and friends), drawn over the ClickGUI in Hafnium's style. Click a
 * block to select it, click its swatch to change its colour (right-click goes back), and "T" toggles its tracer.
 */
public final class BlockListScreen extends Screen {
    private static final float W = 340f;
    private static final float ROW = 18f;
    private static final float PAD = 12f;
    private static final float BTN_H = 20f;
    /** Swatch colours (ARGB), cycled with clicks. */
    private static final int[] PALETTE = {0xFFFFFFFF, 0xFFB77CFF, 0xFF7BE0A0, 0xFFFFC66B, 0xFFFF6B81, 0xFF5BC8FF,
            0xFFFF9F43, 0xFFFFEA6B, 0xFF4DE0D0, 0xFFFF7BD5, 0xFF8C8CFF, 0xFFB0B0B0};
    private static final String[] BUTTONS = {"Clear all", "Reset colours", "Done"};

    private final ClickGuiScreen parent;
    private final BlockColorListSetting setting;
    private final TextInput search = new TextInput(48);
    private final Animation open = new Animation(180, false);
    private boolean closing;
    private long lastFrame;
    private int scroll;
    private int guiScale = 2;
    private int pixelScale = 2;
    private float uw;
    private float uh;
    private List<BlockEntry> rows = List.of();

    public BlockListScreen(ClickGuiScreen parent, BlockColorListSetting setting) {
        super(Component.literal(setting.name()));
        this.parent = parent;
        this.setting = setting;
        search.onChange(s -> scroll = 0);
    }

    @Override
    protected void init() {
        parent.init(width, height);
        lastFrame = Util.getMillis();
        search.focus();
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

    // ------------------------------------------------------------------ geometry (also used by the gametests)

    private float H() {
        return Math.min(uh - 30f, 380f);
    }

    public float x0() {
        return uw / 2f - W / 2f;
    }

    public float y0() {
        return uh / 2f - H() / 2f;
    }

    private float listY() {
        return y0() + 72f;
    }

    private int visibleRows() {
        return (int) ((H() - 72f - BTN_H - PAD - 8f) / ROW);
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

    public int pixelScale() {
        return pixelScale;
    }

    /** Centre of the visible row showing {@code blockId}, in GUI units, or null. */
    public float[] rowCenter(String blockId) {
        for (int i = 0; i < visibleRows() && i + scroll < rows.size(); i++) {
            if (rows.get(i + scroll).id().equals(blockId)) {
                return new float[]{x0() + W / 2f, listY() + i * ROW + ROW / 2f};
            }
        }
        return null;
    }

    public String query() {
        return search.text();
    }

    // ------------------------------------------------------------------ data

    private List<BlockEntry> filter() {
        String q = search.text().trim().toLowerCase(Locale.ROOT);
        List<BlockEntry> out = new ArrayList<>();
        if (q.isEmpty()) {
            out.addAll(setting.selected());
            for (BlockEntry e : setting.entries()) {
                if (!e.enabled().get()) {
                    out.add(e);
                }
            }
        } else {
            for (BlockEntry e : setting.entries()) {
                if (e.displayName().toLowerCase(Locale.ROOT).contains(q) || e.id().contains(q)) {
                    out.add(e);
                }
            }
            out.sort((a, b) -> Boolean.compare(b.enabled().get(), a.enabled().get()));
        }
        return out;
    }

    private static int argb(float[] rgba) {
        return BlockColorListSetting.rgbaToArgb(rgba);
    }

    private void setColor(BlockEntry e, int argb) {
        float[] c = {((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f};
        System.arraycopy(c, 0, e.color(), 0, 4);
        System.arraycopy(c, 0, e.tracerColor(), 0, 4);
        setting.invalidateLookup();
        setting.notifyChanged();
    }

    private void cycleColor(BlockEntry e, int dir) {
        int current = argb(e.color());
        int idx = 0;
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == current) {
                idx = i;
                break;
            }
        }
        setColor(e, PALETTE[Math.floorMod(idx + dir, PALETTE.length)]);
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
        guiScale = parent.guiScale();
        pixelScale = parent.pixelScale();
        uw = width * guiScale / (float) pixelScale;
        uh = height * guiScale / (float) pixelScale;
        rows = filter();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
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
            GuiRenderUtil.icon(Icon.GRID, x + PAD, y + 12f, 14f, ThemeManager.accent());
            GuiRenderUtil.text(FontRole.HEADER, setting.name().toUpperCase(Locale.ROOT), x + PAD + 20f, y + 19f, ThemeManager.TEXT);
            int n = setting.selected().size();
            GuiRenderUtil.textRight(FontRole.SMALL, n + " selected", x + W - PAD, y + 19f, ThemeManager.TEXT_SECONDARY);

            float fy = y + 38f;
            GuiRenderUtil.roundRect(x + PAD, fy, W - PAD * 2, 22f, 4f, ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.9f));
            GuiRenderUtil.roundOutline(x + PAD, fy, W - PAD * 2, 22f, 4f, ThemeManager.accent());
            GuiRenderUtil.icon(Icon.SEARCH, x + PAD + 6f, fy + 6f, 10f, ThemeManager.TEXT_SECONDARY);
            search.render(FontRole.TEXT, x + PAD + 22f, fy + 11f, W - PAD * 2 - 30f, "Search " + setting.entries().size() + " blocks…",
                    ThemeManager.TEXT, ThemeManager.TEXT_MUTED);

            float ly = listY();
            List<Runnable> icons = new ArrayList<>();
            for (int i = 0; i < visibleRows() && i + scroll < rows.size(); i++) {
                BlockEntry e = rows.get(i + scroll);
                float ry = ly + i * ROW;
                float cy = ry + ROW / 2f;
                boolean on = e.enabled().get();
                boolean hov = GuiRenderUtil.inside(mx, my, x + PAD, ry, W - PAD * 2, ROW);
                if (on) {
                    GuiRenderUtil.roundRect(x + PAD, ry + 1f, W - PAD * 2, ROW - 2f, 3f, ThemeManager.withAlpha(ThemeManager.accent(), 0.12f));
                } else if (hov) {
                    GuiRenderUtil.roundRect(x + PAD, ry + 1f, W - PAD * 2, ROW - 2f, 3f, 0x0EFFFFFF);
                }
                GuiRenderUtil.circle(x + PAD + 7f, cy, 2.5f, on ? ThemeManager.accent() : ThemeManager.DOT_OFF);
                var item = BuiltInRegistries.BLOCK.getValue(Identifier.parse(e.id())).asItem();
                if (item != Items.AIR) {
                    float ix = x + PAD + 14f;
                    float iy = ry + 2f;
                    icons.add(() -> drawItem(g, new ItemStack(item), ix, iy, 14f));
                }
                GuiRenderUtil.text(FontRole.SMALL, e.displayName(), x + PAD + 33f, cy, on ? ThemeManager.TEXT : 0xFFD8D3E0);
                if (on) {
                    float sx = x + W - PAD - 46f;
                    GuiRenderUtil.roundRect(sx, cy - 5f, 18f, 10f, 3f, argb(e.color()));
                    GuiRenderUtil.roundOutline(sx, cy - 5f, 18f, 10f, 3f, ThemeManager.withAlpha(0xFFFFFFFF, 0.25f));
                    boolean t = e.tracerEnabled().get();
                    float tx = x + W - PAD - 20f;
                    GuiRenderUtil.roundRect(tx, cy - 5f, 14f, 10f, 3f, t ? ThemeManager.withAlpha(ThemeManager.accent(), 0.35f) : 0x10FFFFFF);
                    GuiRenderUtil.textCentered(FontRole.TINY, "T", tx + 7f, cy, t ? ThemeManager.TEXT : ThemeManager.TEXT_MUTED);
                }
            }
            if (rows.isEmpty()) {
                GuiRenderUtil.textCentered(FontRole.SMALL, "No blocks match “" + search.text().trim() + "”", x + W / 2f, ly + 30f, ThemeManager.TEXT_SECONDARY);
            }
            if (rows.size() > visibleRows()) {
                float trackH = visibleRows() * ROW;
                float thumb = Math.max(14f, trackH * visibleRows() / rows.size());
                float ty = ly + (trackH - thumb) * scroll / Math.max(1, rows.size() - visibleRows());
                GuiRenderUtil.roundRect(x + W - 6f, ty, 2f, thumb, 1f, ThemeManager.withAlpha(ThemeManager.accent(), 0.6f));
            }
            for (int i = 0; i < BUTTONS.length; i++) {
                float bx = buttonX(i);
                boolean hov = GuiRenderUtil.inside(mx, my, bx, buttonsY(), buttonW(), BTN_H);
                GuiRenderUtil.roundRect(bx, buttonsY(), buttonW(), BTN_H, 4f, hov ? ThemeManager.withAlpha(ThemeManager.accent(), 0.22f)
                        : ThemeManager.withAlpha(ThemeManager.HEADER, 0.95f));
                GuiRenderUtil.roundOutline(bx, buttonsY(), buttonW(), BTN_H, 4f, hov ? ThemeManager.accent() : ThemeManager.BORDER);
                GuiRenderUtil.textCentered(FontRole.SMALL, BUTTONS[i], bx + buttonW() / 2f, buttonsY() + BTN_H / 2f, ThemeManager.TEXT);
            }
            GuiRenderUtil.setAlpha(1f);
            icons.forEach(Runnable::run);
        } finally {
            GuiRenderUtil.end();
        }
    }

    private void drawItem(GuiGraphics g, ItemStack stack, float ux, float uy, float size) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(GuiRenderUtil.px(ux), GuiRenderUtil.px(uy));
        float s = GuiRenderUtil.px(size) / 16f;
        pose.scale(s, s);
        g.renderItem(stack, 0, 0);
        pose.popMatrix();
    }

    // ------------------------------------------------------------------ input

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
                    case 0 -> setting.deselectAll();
                    case 1 -> setting.resetColors();
                    default -> onClose();
                }
                return true;
            }
        }
        float ly = listY();
        for (int i = 0; i < visibleRows() && i + scroll < rows.size(); i++) {
            float ry = ly + i * ROW;
            if (!GuiRenderUtil.inside(mx, my, x + PAD, ry, W - PAD * 2, ROW)) {
                continue;
            }
            BlockEntry e = rows.get(i + scroll);
            boolean on = e.enabled().get();
            if (on && GuiRenderUtil.inside(mx, my, x + W - PAD - 46f, ry, 18f, ROW)) {
                cycleColor(e, event.button() == 1 ? -1 : 1);
            } else if (on && GuiRenderUtil.inside(mx, my, x + W - PAD - 20f, ry, 14f, ROW)) {
                e.tracerEnabled().set(!e.tracerEnabled().get());
                setting.invalidateLookup();
                setting.notifyChanged();
            } else if (event.button() == 0) {
                if (on) {
                    setting.deselect(e);
                } else {
                    setting.select(e);
                }
            }
            return true;
        }
        search.focus();
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
            onClose();
            return true;
        }
        return search.key(event) || true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return search.chr(event.codepoint());
    }
}
