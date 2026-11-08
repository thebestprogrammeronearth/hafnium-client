package dev.hafnium.gametest;

import dev.hafnium.client.config.MockConfig;
import dev.hafnium.client.config.MockConfigManager;
import dev.hafnium.client.gui.ClickGuiScreen;
import dev.hafnium.client.gui.ConfigScreen;
import dev.hafnium.client.gui.component.CategoryPanel;
import dev.hafnium.client.gui.component.ModuleButton;
import dev.hafnium.client.gui.setting.SettingComponent;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.module.ModuleState;
import dev.hafnium.client.modules.client.WatermarkModule;
import dev.hafnium.client.core.setting.ColorSetting;
import dev.hafnium.client.core.setting.KeybindSetting;
import dev.hafnium.client.core.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Drives the ClickGUI with simulated hardware input (GLFW callback level: cursor moves, button presses, key presses,
 * typed characters, scroll) inside a real singleplayer world, and checks the resulting GUI state.
 */
public final class ClickGuiInputTest implements FabricClientGameTest {
    private static final int WIDTH = 1672;
    private static final int HEIGHT = 941;

    private ClientGameTestContext ctx;
    private TestInput in;
    private final Checks checks = new Checks();
    /** Window pixels per layout unit. */
    private double unit;

    @Override
    public void runTest(ClientGameTestContext context) {
        this.ctx = context;
        this.in = context.getInput();
        in.resizeWindow(WIDTH, HEIGHT);
        setGuiScale(3);

        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            ctx.waitTicks(20);

            openAndClose();
            defaultLayout();
            moduleRows();
            settingsControls();
            panelDragAndFold();
            search();
            topBarIcons();
            configs();
            watermarkLifecycle();
            scales();
        }
        checks.finish();
    }

    // ------------------------------------------------------------------ scenarios

    private void openAndClose() {
        checks.check(screenIs(null), "starts in game with no screen", screenName());
        in.pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ctx.waitTicks(1);
        checks.check(screenIs(ClickGuiScreen.class), "Right Shift opens ClickGUI in world", screenName());
        ctx.waitTicks(6);
        refreshUnit();
        ctx.takeScreenshot("hafnium-ingame-open");

        in.pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitTicks(8);
        checks.check(screenIs(null), "Escape closes ClickGUI (after close animation)", screenName());

        in.pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ctx.waitTicks(8);
        checks.check(screenIs(ClickGuiScreen.class), "Right Shift reopens", screenName());
        in.pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ctx.waitTicks(8);
        checks.check(screenIs(null), "Right Shift closes when search is not focused", screenName());

        in.pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ctx.waitTicks(8);
        refreshUnit();
    }

    private void moduleRows() {
        Module esp = module(Category.RENDER, "Block ESP");
        clickRow(Category.RENDER, "Block ESP", 0);
        checks.check(esp.isEnabled(), "left-click enables module", esp.isEnabled());
        clickRow(Category.RENDER, "Block ESP", 0);
        checks.check(!esp.isEnabled(), "second left-click disables module", esp.isEnabled());

        clickRow(Category.RENDER, "Block ESP", 1);
        ctx.waitTicks(8);
        boolean open = ctx.computeOnClient(mc -> gui(mc).panel(Category.RENDER).find("Block ESP").isExpanded());
        checks.check(open, "right-click opens settings dropdown", open);
        checks.check(!esp.isEnabled(), "right-click does not toggle", esp.isEnabled());
    }

    private void settingsControls() {
        // Block ESP: Shape (mode), Fill Alpha (slider), Glow (bool), Bind (keybind)
        clickSetting(Category.RENDER, "Block ESP", "Shape", 0, 0.5f);
        checks.check("Boxes".equals(value(Category.RENDER, "Block ESP", "Shape")), "mode left-click cycles forward", value(Category.RENDER, "Block ESP", "Shape"));
        clickSetting(Category.RENDER, "Block ESP", "Shape", 1, 0.5f);
        checks.check("Outline".equals(value(Category.RENDER, "Block ESP", "Shape")), "mode right-click cycles back", value(Category.RENDER, "Block ESP", "Shape"));

        clickSetting(Category.RENDER, "Block ESP", "Glow", 0, 0.5f);
        checks.check(Boolean.FALSE.equals(value(Category.RENDER, "Block ESP", "Glow")), "boolean click toggles", value(Category.RENDER, "Block ESP", "Glow"));

        // Slider: press at the left end, drag to the right end, release.
        float[] s = settingRect(Category.RENDER, "Block ESP", "Fill Alpha");
        moveTo(s[0] + 1, s[1] + 18);
        in.holdMouse(0);
        ctx.waitTick();
        moveTo(s[0] + s[2] + 20, s[1] + 18);
        ctx.waitTick();
        in.releaseMouse(0);
        ctx.waitTick();
        checks.check(eq(value(Category.RENDER, "Block ESP", "Fill Alpha"), 1.0), "slider drag past the end clamps to max", value(Category.RENDER, "Block ESP", "Fill Alpha"));
        click(s[0] + s[2] * 0.5f, s[1] + 18, 0);
        double mid = (Double) value(Category.RENDER, "Block ESP", "Fill Alpha");
        checks.check(Math.abs(mid - 0.5) <= 0.06, "slider click at middle sets ~midpoint", mid);
        moveTo(s[0] + s[2] * 0.5f, s[1] + 18);
        in.scroll(1);
        ctx.waitTick();
        double scrolled = (Double) value(Category.RENDER, "Block ESP", "Fill Alpha");
        checks.check(Math.abs(scrolled - (mid + 0.05)) < 1e-6, "scroll over slider steps value", scrolled);

        // Keybind: listen, press K; listen, press Escape (clears and must NOT close the GUI).
        clickSetting(Category.RENDER, "Block ESP", "Bind", 0, 0.9f);
        in.pressKey(GLFW.GLFW_KEY_K);
        ctx.waitTick();
        KeybindSetting bind = module(Category.RENDER, "Block ESP").bind();
        checks.check(bind.get() == GLFW.GLFW_KEY_K, "keybind captures pressed key", bind.keyName());
        clickSetting(Category.RENDER, "Block ESP", "Bind", 0, 0.9f);
        in.pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitTicks(2);
        checks.check(bind.get() == KeybindSetting.NONE, "Escape while listening clears bind", bind.keyName());
        checks.check(screenIs(ClickGuiScreen.class), "Escape while listening keeps GUI open", screenName());

        // Colour picker on World > Ancient Debris Finder
        clickRow(Category.WORLD, "Ancient Debris Finder", 1);
        ctx.waitTicks(8);
        clickSetting(Category.WORLD, "Ancient Debris Finder", "Outline color", 0, 0.3f);
        ctx.waitTicks(8);
        float[] c = settingRect(Category.WORLD, "Ancient Debris Finder", "Outline color");
        click(c[0] + c[2] * 0.25f, c[1] + 16 + 1 + 20, 0);
        ColorSetting color = (ColorSetting) setting(Category.WORLD, "Ancient Debris Finder", "Outline color");
        checks.check(Math.abs(color.saturation() - 0.25f) < 0.04f && Math.abs(color.brightness() - 0.5f) < 0.04f,
                "colour square click sets saturation/value", color.saturation() + "/" + color.brightness());
        float hueBefore = color.hue();
        click(c[0] + c[2] * 0.6f, c[1] + 16 + 1 + 40 + 4 + 3, 0);
        checks.check(Math.abs(color.hue() - 0.6f) < 0.04f, "hue strip click sets hue", hueBefore + " -> " + color.hue());
        ctx.takeScreenshot("hafnium-ingame-settings");
    }

    private void panelDragAndFold() {
        float[] before = panelPos(Category.MISC);
        moveTo(before[0] + 60, before[1] + 15);
        in.holdMouse(0);
        ctx.waitTick();
        moveTo(before[0] + 60 + 30, before[1] + 15 + 150);
        ctx.waitTick();
        in.releaseMouse(0);
        ctx.waitTick();
        float[] after = panelPos(Category.MISC);
        checks.check(Math.abs(after[0] - before[0] - 30) < 1 && Math.abs(after[1] - before[1] - 150) < 1,
                "dragging a header moves the panel", before[0] + "," + before[1] + " -> " + after[0] + "," + after[1]);
        boolean stillOpen = ctx.computeOnClient(mc -> gui(mc).panel(Category.MISC).isExpanded());
        checks.check(stillOpen, "a drag does not fold the panel", stillOpen);

        click(after[0] + 60, after[1] + 15, 0);
        boolean folded = !ctx.computeOnClient(mc -> gui(mc).panel(Category.MISC).isExpanded());
        checks.check(folded, "clicking a header folds the panel", folded);
        ctx.waitTicks(6);
        click(after[0] + 60, after[1] + 15, 0);
        boolean unfolded = ctx.computeOnClient(mc -> gui(mc).panel(Category.MISC).isExpanded());
        checks.check(unfolded, "clicking again unfolds it", unfolded);
        ctx.waitTicks(6);

        // Position survives closing and reopening.
        in.pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitTicks(8);
        in.pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ctx.waitTicks(8);
        float[] reopened = panelPos(Category.MISC);
        checks.check(Math.abs(reopened[0] - after[0]) < 0.01 && Math.abs(reopened[1] - after[1]) < 0.01,
                "panel position is remembered after reopening", reopened[0] + "," + reopened[1]);
    }

    private void search() {
        float[] bar = ctx.computeOnClient(mc -> new float[]{gui(mc).searchBar().x(), gui(mc).searchBar().y()});
        click(bar[0] + 60, bar[1] + 12, 0);
        in.typeChars("ESP");
        ctx.waitTicks(6);
        checks.check(matches(Category.RENDER, "Block ESP"),
                "uppercase query matches case-insensitively", "render");
        checks.check(!matches(Category.WORLD, "Navigation") && !matches(Category.CLIENT, "Watermark") && !matches(Category.MISC, "Auto Sell"),
                "non-matching modules are hidden", "filter");
        ctx.takeScreenshot("hafnium-ingame-search");

        in.pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ctx.waitTicks(2);
        checks.check(screenIs(ClickGuiScreen.class), "Right Shift does not close while typing in search", screenName());

        in.pressKey(GLFW.GLFW_KEY_BACKSPACE);
        in.pressKey(GLFW.GLFW_KEY_BACKSPACE);
        in.pressKey(GLFW.GLFW_KEY_BACKSPACE);
        checks.check(searchText().isEmpty(), "backspace erases query", searchText());

        // Click empty space (unfocus), then type: type-to-search should refocus the bar.
        float h = ctx.computeOnClient(mc -> gui(mc).layoutHeight());
        click(5, h - 5, 0);
        in.typeChars("deb");
        ctx.waitTicks(4);
        checks.check("deb".equals(searchText()) && matches(Category.WORLD, "Ancient Debris Finder") && !matches(Category.WORLD, "Navigation"),
                "typing anywhere searches", searchText());

        click(bar[0] + 172 - 14, bar[1] + 12, 0);
        checks.check(searchText().isEmpty() && matches(Category.WORLD, "Navigation"), "clear button empties search", searchText());

        click(5, h - 5, 0);
        in.holdControl();
        in.pressKey(GLFW.GLFW_KEY_F);
        in.releaseControl();
        in.typeChars("zzz");
        ctx.waitTicks(6);
        checks.check("zzz".equals(searchText()), "Ctrl+F focuses search", searchText());
        int visible = ctx.computeOnClient(mc -> {
            int n = 0;
            for (Category c : Category.values()) {
                for (ModuleButton b : gui(mc).panel(c).buttons()) {
                    n += b.matches() ? 1 : 0;
                }
            }
            return n;
        });
        checks.check(visible == 0, "nonsense query hides everything", visible);
        ctx.takeScreenshot("hafnium-ingame-noresults");
        click(bar[0] + 172 - 14, bar[1] + 12, 0);
        ctx.waitTicks(6);
    }

    private void topBarIcons() {
        float gear = ctx.computeOnClient(mc -> gui(mc).gearCx());
        click(gear, ClickGuiScreen.BAR_CY, 0);
        ctx.waitTicks(8);
        boolean open = ctx.computeOnClient(mc -> gui(mc).panel(Category.CLIENT).find("ClickGUI").isExpanded());
        checks.check(open, "gear icon opens Client > ClickGUI settings", open);
        click(gear, ClickGuiScreen.BAR_CY, 0);
        ctx.waitTicks(8);

        float folder = ctx.computeOnClient(mc -> gui(mc).folderCx());
        click(folder, ClickGuiScreen.BAR_CY, 0);
        ctx.waitTicks(6);
        checks.check(screenIs(ConfigScreen.class), "folder icon opens config manager", screenName());
        in.pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitTicks(8);
        checks.check(screenIs(ClickGuiScreen.class), "Escape in config manager returns to ClickGUI", screenName());
    }

    private void configs() {
        MockConfigManager manager = MockConfigManager.get();
        // Enable Block ESP so a saved snapshot has something in it.
        clickRow(Category.RENDER, "Block ESP", 0);
        Module espModule = module(Category.RENDER, "Block ESP");
        checks.check(espModule.isEnabled(), "Block ESP enabled before saving", espModule.isEnabled());

        float[] btn = ctx.computeOnClient(mc -> new float[]{gui(mc).configsX(), gui(mc).configsY(), gui(mc).configsW(), gui(mc).configsH()});
        click(btn[0] + btn[2] / 2, btn[1] + btn[3] / 2, 0);
        ctx.waitTicks(6);
        checks.check(screenIs(ConfigScreen.class), "CONFIGS button opens config manager", screenName());

        configButton("Create");
        ctx.waitTicks(6);
        checks.check(cfg(ConfigScreen::isEnteringName), "Create opens the name field", "");
        in.typeChars("pvp");
        in.pressKey(GLFW.GLFW_KEY_ENTER);
        checks.check(!cfg(ConfigScreen::error).isEmpty(), "duplicate name is rejected", cfg(ConfigScreen::error));
        for (int i = 0; i < 3; i++) {
            in.pressKey(GLFW.GLFW_KEY_BACKSPACE);
        }
        in.typeChars("test-a");
        in.pressKey(GLFW.GLFW_KEY_ENTER);
        ctx.waitTicks(6);
        checks.check(manager.exists("test-a") && "test-a".equals(cfg(s -> s.selected().name())), "Create adds and selects config", names(manager));

        configButton("Rename");
        ctx.waitTicks(6);
        checks.check("test-a".equals(cfg(ConfigScreen::nameInput)), "Rename prefills current name", cfg(ConfigScreen::nameInput));
        in.pressKey(GLFW.GLFW_KEY_BACKSPACE);
        in.typeChars("b");
        in.pressKey(GLFW.GLFW_KEY_ENTER);
        ctx.waitTicks(6);
        checks.check(manager.exists("test-b") && !manager.exists("test-a"), "Rename renames config", names(manager));

        configButton("Save");
        MockConfig testB = cfg(ConfigScreen::selected);
        checks.check(Boolean.TRUE.equals(testB.moduleStates().get("render.block-esp")), "Save snapshots enabled modules", testB.moduleStates());

        clickConfigRow(0); // "default" (empty snapshot)
        configButton("Load");
        checks.check(!espModule.isEnabled(), "Load restores that config's module states", espModule.isEnabled());
        int idx = manager.configs().indexOf(testB);
        clickConfigRow(idx);
        configButton("Load");
        checks.check(espModule.isEnabled() && manager.active() == testB, "Loading test-b re-enables Block ESP", espModule.isEnabled());
        ctx.takeScreenshot("hafnium-ingame-configs");

        configButton("Delete");
        checks.check(cfg(ConfigScreen::isDeleteArmed) && manager.exists("test-b"), "first Delete click only arms", names(manager));
        configButton("Delete");
        checks.check(!manager.exists("test-b"), "second Delete click deletes", names(manager));

        in.pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitTicks(8);
        checks.check(screenIs(ClickGuiScreen.class), "back to ClickGUI after configs", screenName());
        in.pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitTicks(8);
        checks.check(screenIs(null), "Escape closes ClickGUI to game", screenName());
    }

    /** Opens the GUI at several GUI scales / window sizes and checks the panel row fits the screen. */
    private void scales() {
        int[][] cases = {{1672, 941, 1}, {1672, 941, 2}, {1672, 941, 4}, {1280, 720, 0}, {2560, 1440, 0}, {1024, 600, 2}};
        for (int[] c : cases) {
            in.resizeWindow(c[0], c[1]);
            setGuiScale(c[2]);
            in.pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            ctx.waitTicks(8);
            String label = c[0] + "x" + c[1] + " guiScale " + (c[2] == 0 ? "auto" : c[2]);
            String result = ctx.computeOnClient(mc -> {
                ClickGuiScreen g = gui(mc);
                int gs = mc.getWindow().getGuiScale();
                StringBuilder problems = new StringBuilder();
                if (g.pixelScale() > gs) {
                    problems.append("pixelScale ").append(g.pixelScale()).append(" > guiScale ").append(gs).append("; ");
                }
                for (Category cat : Category.values()) {
                    CategoryPanel p = g.panel(cat);
                    if (p.x() < 0 || p.x() + CategoryPanel.WIDTH > g.layoutWidth() + 0.01f || p.y() < 0
                            || p.y() + CategoryPanel.HEADER_H > g.layoutHeight()) {
                        problems.append(cat).append(" off-screen at ").append(p.x()).append("; ");
                    }
                }
                if (g.closeCx() + 12 > g.layoutWidth()) {
                    problems.append("close icon off-screen; ");
                }
                return problems.length() == 0 ? "ok (pixelScale " + g.pixelScale() + ", gui " + gs + ")" : problems.toString();
            });
            checks.check(result.startsWith("ok"), "layout fits at " + label, result);
            ctx.takeScreenshot("hafnium-scale-" + c[0] + "x" + c[1] + "-gs" + c[2]);
            in.pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(8);
        }
    }

    /** All eight category panels exist and their default slots do not overlap. */
    private void defaultLayout() {
        String result = ctx.computeOnClient(mc -> {
            ClickGuiScreen g = gui(mc);
            StringBuilder problems = new StringBuilder();
            Category[] cats = Category.values();
            for (int i = 0; i < cats.length; i++) {
                CategoryPanel a = g.panel(cats[i]);
                if (a == null) {
                    problems.append("missing panel ").append(cats[i]).append("; ");
                    continue;
                }
                for (int j = i + 1; j < cats.length; j++) {
                    CategoryPanel b = g.panel(cats[j]);
                    boolean overlapX = a.x() < b.x() + CategoryPanel.WIDTH && b.x() < a.x() + CategoryPanel.WIDTH;
                    boolean overlapY = a.y() < b.y() + b.estimatedHeight() && b.y() < a.y() + a.estimatedHeight();
                    if (overlapX && overlapY) {
                        problems.append(cats[i]).append(" overlaps ").append(cats[j]).append("; ");
                    }
                }
            }
            return problems.length() == 0 ? "ok" : problems.toString();
        });
        checks.check("ok".equals(result), "8 panels, default layout without overlap", result);
        ctx.takeScreenshot("hafnium-stage1-default-layout");
    }

    /**
     * Real module end to end: enabling Watermark subscribes a HUD handler that draws on screen; a keybind set in the
     * GUI toggles it in game; disabling removes the subscription.
     */
    private void watermarkLifecycle() {
        in.pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
        ctx.waitTicks(8);
        refreshUnit();
        WatermarkModule wm = ctx.computeOnClient(mc -> HafniumCore.get().modules().get(WatermarkModule.class));
        checks.check(!wm.isEnabled() && countHud(wm) == 0, "Watermark starts disabled with no HUD handler", countHud(wm));
        clickRow(Category.CLIENT, "Watermark", 0);
        checks.check(wm.isEnabled() && countHud(wm) == 1, "enabling Watermark subscribes one HUD handler", wm.state() + "/" + countHud(wm));

        clickRow(Category.CLIENT, "Watermark", 1);
        ctx.waitTicks(8);
        clickSetting(Category.CLIENT, "Watermark", "Bind", 0, 0.9f);
        in.pressKey(GLFW.GLFW_KEY_J);
        ctx.waitTick();
        checks.check(wm.bind().get() == GLFW.GLFW_KEY_J, "Watermark bound to J from the GUI", wm.bind().keyName());

        in.pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitTicks(8);
        java.nio.file.Path shot = ctx.takeScreenshot("hafnium-stage1-watermark-on");
        int accentPixels = countAccentPixels(shot);
        checks.check(accentPixels > 20, "watermark is drawn on the HUD (accent pixels in top-left)", accentPixels);

        in.pressKey(GLFW.GLFW_KEY_J);
        ctx.waitTicks(2);
        checks.check(!wm.isEnabled() && countHud(wm) == 0, "J in game disables Watermark and unsubscribes", wm.state() + "/" + countHud(wm));
        java.nio.file.Path off = ctx.takeScreenshot("hafnium-stage1-watermark-off");
        int accentOff = countAccentPixels(off);
        checks.check(accentOff < 5, "watermark gone from HUD after disabling", accentOff);

        in.pressKey(GLFW.GLFW_KEY_J);
        ctx.waitTicks(2);
        checks.check(wm.state() == ModuleState.ENABLED, "J again re-enables it", wm.state());
        in.pressKey(GLFW.GLFW_KEY_J);
        ctx.waitTicks(2);
    }

    /** HUD handlers owned by {@code owner} (other modules, e.g. the Capabilities indicator, have their own). */
    private int countHud(Object owner) {
        return ctx.computeOnClient(mc -> HafniumCore.get().events().countOwnedBy(owner));
    }

    /** Counts pixels close to the accent colour (#B77CFF) in the top-left 120x60 window pixels. */
    private static int countAccentPixels(java.nio.file.Path png) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png.toFile());
            int n = 0;
            for (int y = 0; y < Math.min(60, img.getHeight()); y++) {
                for (int x = 0; x < Math.min(120, img.getWidth()); x++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    if (Math.abs(r - 0xB7) < 30 && Math.abs(g - 0x7C) < 30 && Math.abs(b - 0xFF) < 30) {
                        n++;
                    }
                }
            }
            return n;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void setGuiScale(int scale) {
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(scale);
            mc.resizeDisplay();
        });
        ctx.waitTick();
    }

    private static ClickGuiScreen gui(Minecraft mc) {
        if (mc.screen instanceof ClickGuiScreen g) {
            return g;
        }
        throw new IllegalStateException("ClickGUI is not open (screen: " + mc.screen + ")");
    }

    private void refreshUnit() {
        unit = ctx.computeOnClient(mc -> {
            ClickGuiScreen g = gui(mc);
            double windowPerFramebuffer = mc.getWindow().getScreenWidth() / (double) mc.getWindow().getWidth();
            return g.pixelScale() * windowPerFramebuffer;
        });
    }

    private void moveTo(float ux, float uy) {
        in.setCursorPos(ux * unit, uy * unit);
    }

    private void click(float ux, float uy, int button) {
        moveTo(ux, uy);
        ctx.waitTick();
        in.pressMouse(button);
        ctx.waitTick();
    }

    private void clickRow(Category c, String name, int button) {
        for (int attempt = 0; attempt < 40; attempt++) {
            float[] r = ctx.computeOnClient(mc -> {
                ModuleButton b = gui(mc).panel(c).find(name);
                CategoryPanel p = gui(mc).panel(c);
                return new float[]{b.x() + 40, b.rowY() + 10.5f, p.x(), p.y() + CategoryPanel.HEADER_H, p.y() + p.totalHeight()};
            });
            if (r[1] >= r[3] && r[1] < r[4] - 2) {
                click(r[0], r[1], button);
                return;
            }
            moveTo(r[2] + 2, (r[3] + r[4]) / 2);
            in.scroll(r[1] < r[3] ? 1 : -1);
            ctx.waitTicks(2);
        }
        throw new AssertionError("Module did not scroll into view: " + name);
    }

    private float[] settingRect(Category c, String module, String setting) {
        return ctx.computeOnClient(mc -> {
            ModuleButton b = gui(mc).panel(c).find(module);
            // The panel lists visible settings only (e.g. Block ESP hides "Box Size" in Outline mode).
            int i = 0;
            for (var s : b.module().settings()) {
                if (s.name().equals(setting)) {
                    break;
                }
                i += s.isVisible() ? 1 : 0;
            }
            SettingComponent sc = b.settingsPanel().components().get(i);
            return new float[]{sc.x(), sc.y(), sc.width()};
        });
    }

    private void clickSetting(Category c, String module, String setting, int button, float xFraction) {
        // Expanded settings can fall below the panel viewport as modules are added.
        for (int attempt = 0; attempt < 30; attempt++) {
            float[] r = settingRect(c, module, setting);
            float[] viewport = ctx.computeOnClient(mc -> {
                var p = gui(mc).panel(c);
                return new float[]{p.x(), p.y() + CategoryPanel.HEADER_H, p.y() + p.totalHeight()};
            });
            if (r[1] + 16 < viewport[2] && r[1] >= viewport[1]) {
                click(r[0] + r[2] * xFraction, r[1] + 8, button);
                return;
            }
            moveTo(viewport[0] + 2, (viewport[1] + viewport[2]) / 2);
            in.scroll(r[1] < viewport[1] ? 1 : -1);
            ctx.waitTicks(2);
        }
        throw new AssertionError("Setting did not scroll into view: " + module + "." + setting);
    }

    private static int indexOf(Module m, String setting) {
        for (int i = 0; i < m.settings().size(); i++) {
            if (m.settings().get(i).name().equals(setting)) {
                return i;
            }
        }
        throw new IllegalArgumentException(m.name() + " has no setting " + setting);
    }

    private static Module module(Category c, String name) {
        for (Module m : HafniumCore.get().modules().byCategory(c)) {
            if (m.name().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("No module " + name + " in " + c);
    }

    private static Setting<?> setting(Category c, String module, String name) {
        Module m = module(c, module);
        return m.settings().get(indexOf(m, name));
    }

    private Object value(Category c, String module, String name) {
        return ctx.computeOnClient(mc -> setting(c, module, name).get());
    }

    private static boolean eq(Object v, double expected) {
        return v instanceof Double d && Math.abs(d - expected) < 1e-6;
    }

    private float[] panelPos(Category c) {
        return ctx.computeOnClient(mc -> new float[]{gui(mc).panel(c).x(), gui(mc).panel(c).y()});
    }

    private boolean matches(Category c, String name) {
        return ctx.computeOnClient(mc -> gui(mc).panel(c).find(name).matches());
    }

    private String searchText() {
        return ctx.computeOnClient(mc -> gui(mc).searchBar().input().text());
    }

    private boolean screenIs(Class<?> type) {
        return ctx.computeOnClient(mc -> type == null ? mc.screen == null : type.isInstance(mc.screen));
    }

    private String screenName() {
        return ctx.computeOnClient(mc -> mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
    }

    private <T> T cfg(java.util.function.Function<ConfigScreen, T> f) {
        return ctx.computeOnClient(mc -> f.apply((ConfigScreen) mc.screen));
    }

    private void configButton(String label) {
        int i = ConfigScreen.actionIndex(label);
        float[] r = cfg(s -> new float[]{s.buttonX(i) + s.buttonW() / 2, s.buttonsY() + s.buttonHeight() / 2});
        click(r[0], r[1], 0);
    }

    private void clickConfigRow(int index) {
        float[] r = cfg(s -> new float[]{s.x0() + 60, s.rowCenterY(index)});
        click(r[0], r[1], 0);
    }

    private static String names(MockConfigManager m) {
        StringBuilder sb = new StringBuilder();
        for (MockConfig c : m.configs()) {
            sb.append(c.name()).append(' ');
        }
        return sb.toString().trim();
    }
}
