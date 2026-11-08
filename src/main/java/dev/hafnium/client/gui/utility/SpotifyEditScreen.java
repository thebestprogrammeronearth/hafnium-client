package dev.hafnium.client.gui.utility;
import dev.hafnium.client.modules.client.SpotifyHudModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
public final class SpotifyEditScreen extends Screen {
    private final SpotifyHudModule module;
    private boolean dragging;
    private double dx, dy;
    public SpotifyEditScreen(SpotifyHudModule module) {
        super(Component.literal("Drag SpotifyHUD • Esc to save"));
        this.module = module;
    }
    public boolean isPauseScreen() {
        return false;
    }
    public void render(GuiGraphics g, int x, int y, float dt) {
        g.fill(0, 0, width, height, 0x8815131C);
        module.draw(g, true);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFB77CFF);
    }
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        double x = Math.min(module.x.get(), Math.max(0, width - 240 * module.scale.get())),
               y = Math.min(module.y.get(), Math.max(0, height - 70 * module.scale.get()));
        if (e.x() >= x && e.x() < x + 240 * module.scale.get() && e.y() >= y && e.y() < y + 70 * module.scale.get()) {
            dragging = true;
            dx = e.x() - x;
            dy = e.y() - y;
            return true;
        }
        return false;
    }
    public boolean mouseDragged(MouseButtonEvent e, double x, double y) {
        if (dragging) {
            module.x.set(e.x() - dx);
            module.y.set(e.y() - dy);
        }
        return dragging;
    }
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = false;
        return true;
    }
}
