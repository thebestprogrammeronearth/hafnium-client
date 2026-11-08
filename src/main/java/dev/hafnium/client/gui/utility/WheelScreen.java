package dev.hafnium.client.gui.utility;
import com.google.gson.Gson;
import dev.hafnium.client.modules.misc.DonutWheelModule;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
public final class WheelScreen extends Screen {
    private final DonutWheelModule module;
    private int page, pressed = -1;
    private boolean dragged, favorites;
    private double pressX, pressY;
    private long opened;
    public WheelScreen(DonutWheelModule module) {
        super(Component.literal("Donut Wheel"));
        this.module = module;
    }
    protected void init() {
        opened = System.nanoTime();
        addRenderableWidget(Button.builder(Component.literal("‹ Page"), b -> {
                                      page = Math.max(0, page - 1);
                                  }).bounds(width / 2 - 155, height - 34, 75, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Page ›"), b -> {
                                      page = Math.min(7, page + 1);
                                  }).bounds(width / 2 - 75, height - 34, 75, 20).build());
        addRenderableWidget(
                Button.builder(Component.literal("Favorites"), b -> {
                          favorites = !favorites;
                          page = 0;
                      }).bounds(width / 2 + 5, height - 34, 75, 20).build());
        addRenderableWidget(
                Button.builder(Component.literal("Import/export"),
                              b
                              -> minecraft.setScreen(
                                      new TextPromptScreen(this, "Wheel actions JSON", module.json(), module::replace)))
                        .bounds(width / 2 + 85, height - 34, 100, 20)
                        .build());
    }
    public boolean isPauseScreen() {
        return false;
    }
    private List<Integer> indexes() {
        List<Integer> out = new ArrayList<>();
        var all = module.actions();
        for (int i = 0; i < all.size(); i++)
            if (!favorites || all.get(i).favorite())
                out.add(i);
        return out;
    }
    // Slots sit on an ellipse so neighbouring slots never overlap; the whole wheel scales down to stay clear of the
    // help line at the top and the buttons at the bottom on small windows.
    private static final double RX = 150, RY = 75, HALF_W = 40, HALF_H = 18;

    private double fit() {
        return Math.min(1, Math.min((height / 2d - 38) / (RY + HALF_H), (width / 2d - 8) / (RX + HALF_W)));
    }

    /** Centre of slot {@code i} (0 = top, clockwise) in GUI coordinates, at full open animation. */
    public double[] slotCenter(int i) {
        double angle = i * Math.PI / 4 - Math.PI / 2, k = fit();
        return new double[] {width / 2d + Math.cos(angle) * RX * k, height / 2d + Math.sin(angle) * RY * k};
    }

    private int slot(double x, double y) {
        double k = fit();
        for (int i = 0; i < 8; i++) {
            double[] c = slotCenter(i);
            if (Math.abs(x - c[0]) <= HALF_W * k && Math.abs(y - c[1]) <= HALF_H * k)
                return i;
        }
        return -1;
    }
    private int index(int slot) {
        var ids = indexes();
        int n = page * 8 + slot;
        return slot < 0 || n >= ids.size() ? -1 : ids.get(n);
    }
    public void render(GuiGraphics g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0xBB100D18);
        double anim = Math.min(1, (System.nanoTime() - opened) / 180_000_000d);
        int hovered = slot(mx, my);
        var all = module.actions();
        double k = fit();
        int hw = (int) Math.round(HALF_W * k), hh = (int) Math.round(HALF_H * k);
        for (int i = 0; i < 8; i++) {
            double[] c = slotCenter(i);
            int x = (int) Math.round(width / 2d + (c[0] - width / 2d) * anim),
                y = (int) Math.round(height / 2d + (c[1] - height / 2d) * anim);
            int idx = index(i);
            g.fill(x - hw, y - hh, x + hw, y + hh, i == hovered ? 0xEE49305F : 0xEE211A2B);
            g.renderOutline(x - hw, y - hh, hw * 2, hh * 2, 0xFFB77CFF);
            String label = idx < 0 ? "+ Add" : all.get(idx).label(), icon = idx < 0 ? "+" : all.get(idx).icon();
            g.drawCenteredString(font, icon, x, y - 11, 0xFFC99DFF);
            g.drawCenteredString(font, font.plainSubstrByWidth(label, hw * 2 - 4), x, y + 2, 0xFFF3F0F8);
        }
        g.drawCenteredString(font, "Hf / 72", width / 2, height / 2 - 8, 0xFFB77CFF);
        g.drawCenteredString(font, "Page " + (page + 1), width / 2, height / 2 + 7, 0xFFA6A0B2);
        g.drawCenteredString(font, "Click: run • Right-click: edit • Drag: reorder • Shift-right-click: delete",
                width / 2, 18, 0xFFC99DFF);
        super.render(g, mx, my, dt);
    }
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        int s = slot(e.x(), e.y());
        if (s < 0)
            return super.mouseClicked(e, twice);
        int idx = index(s);
        if (e.button() == 1 || idx < 0) {
            if (idx >= 0 && net.minecraft.client.Minecraft.getInstance().hasShiftDown()) {
                module.remove(idx);
                return true;
            }
            var action = idx < 0 ? new DonutWheelModule.Action("Home", "⌂", "home", true, false, null, null, null, null)
                                 : module.actions().get(idx);
            int target = idx < 0 ? module.actions().size() : idx;
            minecraft.setScreen(new TextPromptScreen(this, "Edit action JSON", new Gson().toJson(action),
                    text -> module.put(target, new Gson().fromJson(text, DonutWheelModule.Action.class))));
            return true;
        }
        if (e.button() == 0) {
            pressed = idx;
            dragged = false;
            pressX = e.x();
            pressY = e.y();
            return true;
        }
        return true;
    }
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (pressed >= 0 && Math.hypot(e.x() - pressX, e.y() - pressY) > 5)
            dragged = true;
        return true;
    }
    public boolean mouseReleased(MouseButtonEvent e) {
        int idx = index(slot(e.x(), e.y()));
        if (pressed >= 0) {
            if (dragged && idx >= 0)
                module.swap(pressed, idx);
            else if (idx == pressed)
                module.select(module.actions().get(idx));
            pressed = -1;
            return true;
        }
        return super.mouseReleased(e);
    }
}
