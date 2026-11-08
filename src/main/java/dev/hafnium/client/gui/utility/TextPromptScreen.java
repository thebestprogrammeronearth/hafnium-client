package dev.hafnium.client.gui.utility;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Hafnium-styled text/JSON editor with clipboard import/export and validation feedback. */
public final class TextPromptScreen extends Screen {
    private final Screen parent;
    private final String initial;
    private final Consumer<String> accept;
    private EditBox input;
    private String error = "";
    public TextPromptScreen(Screen parent, String title, String initial, Consumer<String> accept) {
        super(Component.literal(title));
        this.parent = parent;
        this.initial = initial;
        this.accept = accept;
    }
    protected void init() {
        input = new EditBox(font, width / 2 - 180, height / 2 - 10, 360, 20, Component.literal("Value"));
        input.setMaxLength(32768);
        input.setValue(initial);
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(
                Button.builder(Component.literal("Apply"), b -> {
                          try {
                              accept.accept(input.getValue());
                              if (minecraft.screen == this)
                                  minecraft.setScreen(parent);
                          } catch (RuntimeException e) {
                              error = e.getMessage();
                          }
                      }).bounds(width / 2 - 180, height / 2 + 22, 85, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                        .bounds(width / 2 + 95, height / 2 + 22, 85, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Import"),
                              b -> input.setValue(minecraft.keyboardHandler.getClipboard()))
                        .bounds(width / 2 - 90, height / 2 + 22, 85, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Export"),
                              b -> minecraft.keyboardHandler.setClipboard(input.getValue()))
                        .bounds(width / 2, height / 2 + 22, 85, 20)
                        .build());
    }
    public void onClose() {
        minecraft.setScreen(parent);
    }
    public boolean isPauseScreen() {
        return false;
    }
    public void render(GuiGraphics g, int x, int y, float dt) {
        g.fill(0, 0, width, height, 0xDD15131C);
        g.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xFFB77CFF);
        super.render(g, x, y, dt);
        g.drawCenteredString(font, error, width / 2, height / 2 + 55, 0xFFFF6B81);
    }
}
