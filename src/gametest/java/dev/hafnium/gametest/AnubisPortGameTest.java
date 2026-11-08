package dev.hafnium.gametest;

import dev.hafnium.client.anubis.esp.ChunkScanner;
import dev.hafnium.client.anubis.setting.BlockColorListSetting;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.event.WorldRenderEvent;
import dev.hafnium.client.gui.BlockListScreen;
import dev.hafnium.client.gui.ClickGuiScreen;
import dev.hafnium.client.modules.client.PerformanceModule;
import dev.hafnium.client.modules.render.BlockEspModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

/** Modules ported from Anubis, exercised in a real world with real input. */
public final class AnubisPortGameTest implements FabricClientGameTest {
    private final Checks checks = new Checks();
    private ClientGameTestContext ctx;
    private TestInput in;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        in = context.getInput();
        try (var sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.waitTicks(20);
            gizmoProbe(sp);
            blockEsp(sp);
        }
        checks.finish();
    }

    /** Do Gizmos emitted from WorldRenderEvent reach the screen? Counts magenta pixels in a screenshot. */
    private void gizmoProbe(net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext sp) {
        var core = ctx.computeOnClient(mc -> HafniumCore.get());
        int[] p = ctx.computeOnClient(mc -> new int[]{mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
        sp.getServer().runCommand("tp @a " + p[0] + " " + (p[1] + 1) + " " + p[2] + " facing " + (p[0] + 4) + " " + p[1] + " " + p[2]);
        ctx.waitTicks(10);
        for (boolean onTop : new boolean[]{false, true}) {
            Object owner = new Object();
            var sub = ctx.computeOnClient(mc -> core.events().subscribe(WorldRenderEvent.class, owner, e -> {
                var props = net.minecraft.gizmos.Gizmos.cuboid(new net.minecraft.world.phys.AABB(p[0] + 4, p[1], p[2] - 1, p[0] + 5, p[1] + 2, p[2] + 2),
                        net.minecraft.gizmos.GizmoStyle.strokeAndFill(0xFFFF00FF, 6f, 0x80FF00FF));
                if (onTop) {
                    props.setAlwaysOnTop();
                }
            }));
            ctx.waitTicks(3);
            var shot = ctx.takeScreenshot("hafnium-gizmo-probe-" + (onTop ? "ontop" : "normal"));
            ctx.runOnClient(mc -> sub.close());
            int magenta = 0;
            try {
                var img = javax.imageio.ImageIO.read(shot.toFile());
                // world area only: skip the HUD panel (top-left) and the chat/hotbar (bottom)
                for (int y = img.getHeight() / 5; y < img.getHeight() * 3 / 4; y += 2) {
                    for (int x = 0; x < img.getWidth(); x += 2) {
                        int c = img.getRGB(x, y);
                        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                        if (r > 160 && b > 160 && g < 110) {
                            magenta++;
                        }
                    }
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            checks.check(magenta > 50, "Gizmos from WorldRenderEvent are visible (" + (onTop ? "always on top" : "depth tested") + ")",
                    magenta + " magenta samples");
        }
    }

    private void blockEsp(net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext sp) {
        var core = ctx.computeOnClient(mc -> HafniumCore.get());
        BlockEspModule esp = ctx.computeOnClient(mc -> core.modules().get(BlockEspModule.class));
        PerformanceModule perf = ctx.computeOnClient(mc -> core.modules().get(PerformanceModule.class));
        int[] p = ctx.computeOnClient(mc -> new int[]{mc.player.getBlockX() + 3, mc.player.getBlockY(), mc.player.getBlockZ() + 2});

        ctx.runOnClient(mc -> {
            core.modules().setEnabled(perf, true);
            core.modules().setEnabled(esp, true);
            var gold = esp.blockColors.entry("minecraft:gold_block");
            esp.blockColors.select(gold);
            float[] purple = {0xB7 / 255f, 0x7C / 255f, 1f, 1f};
            System.arraycopy(purple, 0, gold.color(), 0, 4);
            System.arraycopy(purple, 0, gold.tracerColor(), 0, 4);
            esp.blockColors.invalidateLookup();
            esp.blockColors.notifyChanged();
        });
        // three touching gold blocks (one cluster) and one on its own
        for (int i = 0; i < 3; i++) {
            sp.getServer().runCommand("setblock " + (p[0] + i) + " " + p[1] + " " + p[2] + " minecraft:gold_block");
        }
        sp.getServer().runCommand("setblock " + (p[0] + 6) + " " + (p[1] + 1) + " " + p[2] + " minecraft:gold_block");
        sp.getServer().runCommand("setblock " + (p[0] + 1) + " " + p[1] + " " + (p[2] + 4) + " minecraft:diamond_block");
        // look at the blocks from a few blocks away so the screenshot shows the outlines
        sp.getServer().runCommand("tp @a " + (p[0] - 3) + " " + (p[1] + 1) + " " + (p[2] - 4) + " facing " + (p[0] + 2) + " " + p[1] + " " + p[2]);
        ctx.waitTicks(30);
        checks.check(ctx.computeOnClient(mc -> esp.trackedBlocks()) == 4, "Block ESP tracks the selected block type only",
                ctx.computeOnClient(mc -> esp.trackedBlocks()));
        long frameBefore = WorldRenderEvent.frame();
        ctx.waitTicks(5);
        checks.check(WorldRenderEvent.frame() > frameBefore && WorldRenderEvent.lastDurationNanos() > 0,
                "world render event runs ESP handlers every frame", WorldRenderEvent.lastDurationNanos());
        int[] frame = ctx.computeOnClient(mc -> esp.lastFrame());
        checks.check(frame[0] == 2, "both gold clusters in view are drawn (not culled)", "drawn " + frame[0] + ", culled " + frame[1]);
        ctx.takeScreenshot("hafnium-block-esp-outline");

        sp.getServer().runCommand("setblock " + (p[0] + 6) + " " + (p[1] + 1) + " " + p[2] + " minecraft:air");
        ctx.waitTicks(10);
        checks.check(ctx.computeOnClient(mc -> esp.trackedBlocks()) == 3, "a broken block leaves the ESP (block update path)",
                ctx.computeOnClient(mc -> esp.trackedBlocks()));

        ctx.runOnClient(mc -> {
            esp.setting("shape").getClass();
            ((dev.hafnium.client.core.setting.ModeSetting) esp.setting("shape")).set("Boxes");
            ((dev.hafnium.client.core.setting.BoolSetting) esp.setting("tracers")).set(true);
            var e = esp.blockColors.entry("minecraft:gold_block");
            e.tracerEnabled().set(true);
            esp.blockColors.invalidateLookup();
            esp.blockColors.notifyChanged();
        });
        ctx.waitTicks(5);
        ctx.takeScreenshot("hafnium-block-esp-boxes-tracers");
        checks.check(esp.isEnabled(), "boxes and tracers render without failing the module", esp.state());

        // settings round trip: the selection survives save + load
        boolean roundTrip = ctx.computeOnClient(mc -> {
            var json = esp.blockColors.compact(esp.blockColors.serialize());
            BlockColorListSetting fresh = BlockColorListSetting.createDefault();
            fresh.captureDefault();
            fresh.deserialize(json);
            return fresh.isEnabled(Blocks.GOLD_BLOCK) && !fresh.isEnabled(Blocks.DIAMOND_BLOCK);
        });
        checks.check(roundTrip, "block selection persists through save and load", "compact JSON");

        // the block list editor, with real typing and clicking
        ctx.runOnClient(mc -> {
            ClickGuiScreen gui = new ClickGuiScreen(null);
            mc.setScreen(gui);
            mc.setScreen(new BlockListScreen(gui, esp.blockColors));
        });
        ctx.waitTicks(8);
        in.typeChars("diamond block");
        ctx.waitTicks(3);
        ctx.waitTicks(3);
        float[] row = ctx.computeOnClient(mc -> ((BlockListScreen) mc.screen).rowCenter("minecraft:diamond_block"));
        checks.check(row != null, "search finds Diamond Block in the editor", ctx.computeOnClient(mc -> ((BlockListScreen) mc.screen).query()));
        if (row != null) {
            double unit = ctx.computeOnClient(mc -> ((BlockListScreen) mc.screen).pixelScale()
                    * (mc.getWindow().getScreenWidth() / (double) mc.getWindow().getWidth()));
            in.setCursorPos(row[0] * unit - 60 * unit, row[1] * unit);
            ctx.waitTick();
            in.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            ctx.waitTicks(3);
        }
        ctx.takeScreenshot("hafnium-block-list-editor");
        checks.check(ctx.computeOnClient(mc -> esp.blockColors.isEnabled(Blocks.DIAMOND_BLOCK)), "clicking a row selects the block", "diamond_block");
        in.pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitTicks(10);
        in.pressKey(GLFW.GLFW_KEY_ESCAPE);
        // The rescan is budgeted (Performance module), so it completes over several ticks, nearest chunks first.
        int ticks = 0;
        while (ticks < 600 && ctx.computeOnClient(mc -> esp.trackedBlocks()) < 4) {
            ctx.waitTick();
            ticks++;
        }
        checks.check(ctx.computeOnClient(mc -> esp.trackedBlocks()) == 4, "newly selected block is found by a budgeted rescan",
                ticks + " ticks, queue " + ChunkScanner.pending());
        checks.check(ChunkScanner.pending() >= 0 && PerformanceModule.optimizationsEnabled(),
                "Performance module enables budgeted scanning", ChunkScanner.pending());
        ctx.runOnClient(mc -> {
            core.modules().setEnabled(esp, false);
            core.modules().setEnabled(perf, false);
        });
        ctx.waitTicks(2);
        checks.check(ctx.computeOnClient(mc -> esp.trackedBlocks()) == 0, "disabling Block ESP clears it", "cleared");
    }
}
