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
            baseEsp(sp);
            farmFps(sp);
            baseFinder(sp);
            worldScanner(sp);
            hudEditorAndMaps(sp);
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
        // Clusters are kept per chunk (as in Anubis), so keep the fixture inside one chunk: x 4..10, z 2..6 of it.
        int[] p = ctx.computeOnClient(mc -> new int[]{((mc.player.getBlockX() >> 4) << 4) + 4, mc.player.getBlockY(),
                ((mc.player.getBlockZ() >> 4) << 4) + 2});

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
        // Touching blocks are merged as the cluster cache rebuilds; allow a few frames for that to settle.
        int[] frame = ctx.computeOnClient(mc -> esp.lastFrame());
        for (int i = 0; i < 20 && frame[0] != 2; i++) {
            ctx.waitTick();
            frame = ctx.computeOnClient(mc -> esp.lastFrame());
        }
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

    /** Storage ESP, Spawner Notifier and Spawner Nametags on real blocks. */
    private void baseEsp(net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext sp) {
        var core = ctx.computeOnClient(mc -> HafniumCore.get());
        var storage = ctx.computeOnClient(mc -> core.modules().get(dev.hafnium.client.modules.render.StorageEspModule.class));
        var notifier = ctx.computeOnClient(mc -> core.modules().get(dev.hafnium.client.modules.world.SpawnerNotifierModule.class));
        var tags = ctx.computeOnClient(mc -> core.modules().get(dev.hafnium.client.modules.render.SpawnerNametagsModule.class));
        int[] p = ctx.computeOnClient(mc -> new int[]{mc.player.getBlockX() + 4, mc.player.getBlockY(), mc.player.getBlockZ() + 1});
        sp.getServer().runCommand("setblock " + p[0] + " " + p[1] + " " + p[2] + " minecraft:chest");
        sp.getServer().runCommand("setblock " + (p[0] + 2) + " " + p[1] + " " + p[2] + " minecraft:ender_chest");
        sp.getServer().runCommand("setblock " + (p[0] + 4) + " " + p[1] + " " + p[2]
                + " minecraft:spawner{SpawnData:{entity:{id:\"minecraft:zombie\"}}}");
        sp.getServer().runCommand("setblock " + (p[0] + 6) + " " + p[1] + " " + p[2] + " minecraft:furnace");
        sp.getServer().runCommand("tp @a " + (p[0] + 2) + " " + (p[1] + 2) + " " + (p[2] - 6) + " facing " + (p[0] + 2) + " " + p[1] + " " + p[2]);
        ctx.waitTicks(20);

        ctx.runOnClient(mc -> core.modules().setEnabled(storage, true));
        ctx.waitTicks(10);
        // chest, ender chest and spawner are on by default; furnaces are off by default
        checks.check(ctx.computeOnClient(mc -> storage.trackedBlocks()) == 3, "Storage ESP finds chest, ender chest and spawner (furnace off by default)",
                ctx.computeOnClient(mc -> storage.trackedBlocks()));
        ctx.runOnClient(mc -> ((dev.hafnium.client.core.setting.BoolSetting) storage.setting("furnace")).set(true));
        ctx.waitTicks(2);
        checks.check(ctx.computeOnClient(mc -> storage.trackedBlocks()) == 4, "turning a type on shows it immediately",
                ctx.computeOnClient(mc -> storage.trackedBlocks()));
        sp.getServer().runCommand("setblock " + (p[0] + 8) + " " + p[1] + " " + p[2] + " minecraft:barrel");
        ctx.waitTicks(10);
        checks.check(ctx.computeOnClient(mc -> storage.trackedBlocks()) == 5, "a newly placed barrel appears (block update path)",
                ctx.computeOnClient(mc -> storage.trackedBlocks()));
        sp.getServer().runCommand("setblock " + p[0] + " " + p[1] + " " + p[2] + " minecraft:air");
        ctx.waitTicks(10);
        checks.check(ctx.computeOnClient(mc -> storage.trackedBlocks()) == 4, "a broken chest disappears",
                ctx.computeOnClient(mc -> storage.trackedBlocks()));
        ctx.takeScreenshot("hafnium-storage-esp");

        ctx.runOnClient(mc -> {
            core.modules().setEnabled(storage, false);
            core.modules().setEnabled(notifier, true);
            core.modules().setEnabled(tags, true);
        });
        boolean announced = false;
        for (int i = 0; i < 200 && !announced; i++) {
            ctx.waitTick();
            announced = ctx.computeOnClient(mc -> notifier.notifiedCount()) == 1;
        }
        checks.check(announced, "Spawner Notifier announces the spawner once", ctx.computeOnClient(mc -> notifier.notifiedCount()));
        String notice = ctx.computeOnClient(mc -> notifier.lastNotice());
        checks.check(notice.startsWith("Zombie spawner found at " + (p[0] + 4) + ", " + p[1] + ", " + p[2]),
                "the announcement names the mob and coordinates", notice);
        checks.check(ctx.computeOnClient(mc -> notifier.trackedBlocks()) == 1, "Spawner Notifier highlights it",
                ctx.computeOnClient(mc -> notifier.trackedBlocks()));
        ctx.waitTicks(5);
        int[] counts = ctx.computeOnClient(mc -> tags.counts());
        checks.check(counts[0] == 1 && counts[1] == 1, "Spawner Nametags labels the spawner in view", counts[0] + " known, " + counts[1] + " drawn");
        ctx.takeScreenshot("hafnium-spawner-nametags");

        sp.getServer().runCommand("setblock " + (p[0] + 4) + " " + p[1] + " " + p[2] + " minecraft:air");
        ctx.waitTicks(10);
        checks.check(ctx.computeOnClient(mc -> tags.counts()[0]) == 0 && ctx.computeOnClient(mc -> notifier.trackedBlocks()) == 0,
                "a broken spawner disappears from both", ctx.computeOnClient(mc -> tags.counts()[0]));
        ctx.runOnClient(mc -> {
            core.modules().setEnabled(notifier, false);
            core.modules().setEnabled(tags, false);
        });
        ctx.waitTicks(2);
        checks.check(ctx.computeOnClient(mc -> notifier.notifiedCount() + storage.trackedBlocks()) == 0, "disabling clears state",
                ctx.computeOnClient(mc -> notifier.notifiedCount()));
    }

    /** Average entities the Farm FPS Optimizer skipped per rendered frame, over a few ticks. */
    private double culledPerFrame() {
        var core = ctx.computeOnClient(mc -> HafniumCore.get());
        long[] frames = {0};
        Object owner = new Object();
        var sub = ctx.computeOnClient(mc -> core.events().subscribe(WorldRenderEvent.class, owner, e -> frames[0]++));
        ctx.waitTicks(1);
        long c0 = ctx.computeOnClient(mc -> dev.hafnium.client.modules.render.FarmFpsModule.culledTotal());
        long f0 = ctx.computeOnClient(mc -> frames[0]);
        ctx.waitTicks(4);
        long c1 = ctx.computeOnClient(mc -> dev.hafnium.client.modules.render.FarmFpsModule.culledTotal());
        long f1 = ctx.computeOnClient(mc -> frames[0]);
        ctx.runOnClient(mc -> sub.close());
        return f1 == f0 ? -1 : (double) (c1 - c0) / (f1 - f0);
    }

    /** Farm FPS Optimizer: No Render switches plus farm culling, measured with the renderer's own entity count. */
    private void farmFps(net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext sp) {
        var core = ctx.computeOnClient(mc -> HafniumCore.get());
        var farm = ctx.computeOnClient(mc -> core.modules().get(dev.hafnium.client.modules.render.FarmFpsModule.class));
        int[] p = ctx.computeOnClient(mc -> new int[]{mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
        sp.getServer().runCommand("tp @a " + p[0] + " " + p[1] + " " + p[2] + " facing " + (p[0] + 10) + " " + p[1] + " " + p[2]);
        sp.getServer().runCommand("kill @e[type=!player]");
        for (int i = 0; i < 10; i++) {
            sp.getServer().runCommand("summon item " + (p[0] + 4) + " " + p[1] + " " + (p[2] - 2 + i * 0.4)
                    + " {Item:{id:\"minecraft:wheat\",count:1},PickupDelay:32767,Age:-32768}");
        }
        for (int i = 0; i < 5; i++) {
            sp.getServer().runCommand("summon experience_orb " + (p[0] + 5) + " " + p[1] + " " + (p[2] + i * 0.5) + " {Value:1,Age:-32768}");
        }
        for (int i = 0; i < 3; i++) {
            sp.getServer().runCommand("summon pig " + (p[0] + 40) + " " + p[1] + " " + (p[2] - 2 + i * 2) + " {NoAI:1b}");
        }
        ctx.waitTicks(10);
        int total = ctx.computeOnClient(mc -> mc.level.getEntityCount());
        checks.check(!ctx.computeOnClient(mc -> dev.hafnium.client.modules.render.FarmFpsModule.hideFireOverlay()),
                "No Render switches are inactive while the module is off", "");
        checks.check(culledPerFrame() == 0, "nothing is skipped while the module is off", total + " entities");
        ctx.runOnClient(mc -> {
            core.modules().setEnabled(farm, true);
            ((dev.hafnium.client.core.setting.BoolSetting) farm.setting("dropped-items")).set(true);
            ((dev.hafnium.client.core.setting.BoolSetting) farm.setting("xp-orbs")).set(true);
        });
        ctx.waitTicks(2);
        double clutter = culledPerFrame();
        checks.check(Math.abs(clutter - 15) < 0.01, "the renderer skips all 10 dropped items and 5 XP orbs every frame", clutter);
        ctx.takeScreenshot("hafnium-farm-fps-on");
        ctx.runOnClient(mc -> ((dev.hafnium.client.core.setting.NumberSetting) farm.setting("mob-render-distance")).set(16.0));
        ctx.waitTicks(2);
        double withMobs = culledPerFrame();
        checks.check(Math.abs(withMobs - 18) < 0.01, "and the 3 pigs past Mob Render Distance (16 b)", withMobs);
        checks.check(ctx.computeOnClient(mc -> dev.hafnium.client.modules.render.FarmFpsModule.hideFireOverlay()
                        && dev.hafnium.client.modules.render.FarmFpsModule.hideTotemAnimation()
                        && !dev.hafnium.client.modules.render.FarmFpsModule.hideFallingBlocks()),
                "No Render defaults match Anubis (fire/totem hidden, falling blocks shown)", "");
        ctx.runOnClient(mc -> core.modules().setEnabled(farm, false));
        ctx.waitTicks(2);
        checks.check(culledPerFrame() == 0, "disabling renders everything again", "");
        ctx.takeScreenshot("hafnium-farm-fps-off");
        sp.getServer().runCommand("kill @e[type=!player]");
    }

    /** Base Finder flags a chunk full of fully grown bee nests (honey 5 only happens where a player stayed). */
    private void baseFinder(net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext sp) {
        var core = ctx.computeOnClient(mc -> HafniumCore.get());
        var finder = ctx.computeOnClient(mc -> core.modules().get(dev.hafnium.client.modules.world.BaseFinderModule.class));
        int[] c = ctx.computeOnClient(mc -> new int[]{mc.player.chunkPosition().x + 2, mc.player.chunkPosition().z, mc.player.getBlockY()});
        ctx.runOnClient(mc -> core.modules().setEnabled(finder, true));
        ctx.waitTicks(100);
        checks.check(ctx.computeOnClient(mc -> finder.flaggedChunks().size()) == 0, "a fresh world has no flagged chunks",
                ctx.computeOnClient(mc -> finder.flaggedChunks().size()));
        ctx.runOnClient(mc -> core.modules().setEnabled(finder, false));
        for (int i = 0; i < 8; i++) {
            sp.getServer().runCommand("setblock " + ((c[0] << 4) + 2 + i) + " " + c[2] + " " + ((c[1] << 4) + 8) + " minecraft:bee_nest[honey_level=5]");
        }
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> core.modules().setEnabled(finder, true));
        boolean flagged = false;
        for (int i = 0; i < 300 && !flagged; i += 10) {
            ctx.waitTicks(10);
            flagged = ctx.computeOnClient(mc -> finder.flaggedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(c[0], c[1])));
        }
        checks.check(flagged, "Base Finder flags the chunk with 8 full bee nests",
                ctx.computeOnClient(mc -> finder.flaggedChunks().size()) + " flagged");
        ctx.takeScreenshot("hafnium-base-finder");
        ctx.runOnClient(mc -> core.modules().setEnabled(finder, false));
        checks.check(ctx.computeOnClient(mc -> finder.flaggedChunks().size()) == 0, "disabling clears Base Finder", "");
    }

    /** WorldScanner finds a stone-patched shaft through a diorite blob deep underground. */
    private void worldScanner(net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext sp) {
        var core = ctx.computeOnClient(mc -> HafniumCore.get());
        var scanner = ctx.computeOnClient(mc -> core.modules().get(dev.hafnium.client.modules.world.WorldScannerModule.class));
        int[] c = ctx.computeOnClient(mc -> new int[]{mc.player.chunkPosition().x - 2, mc.player.chunkPosition().z});
        int x0 = (c[0] << 4) + 3, z0 = (c[1] << 4) + 3;
        ctx.runOnClient(mc -> core.modules().setEnabled(scanner, true));
        ctx.waitTicks(60);
        checks.check(ctx.computeOnClient(mc -> scanner.flaggedCount()) == 0, "nothing flagged before any shaft exists",
                ctx.computeOnClient(mc -> scanner.flaggedCount()));
        // natural stone with a diorite blob in it; a shaft dug through the blob and refilled with stone
        sp.getServer().runCommand("fill " + x0 + " 2 " + z0 + " " + (x0 + 8) + " 16 " + (z0 + 8) + " minecraft:stone");
        sp.getServer().runCommand("fill " + (x0 + 1) + " 6 " + (z0 + 1) + " " + (x0 + 7) + " 12 " + (z0 + 7) + " minecraft:diorite");
        sp.getServer().runCommand("fill " + (x0 + 4) + " 6 " + (z0 + 4) + " " + (x0 + 4) + " 12 " + (z0 + 4) + " minecraft:stone");
        boolean flagged = false;
        for (int i = 0; i < 300 && !flagged; i += 10) {
            ctx.waitTicks(10);
            flagged = ctx.computeOnClient(mc -> scanner.flaggedCount()) > 0;
        }
        checks.check(flagged, "WorldScanner flags a patched shaft through a diorite blob", ctx.computeOnClient(mc -> scanner.flaggedCount()));
        ctx.runOnClient(mc -> core.modules().setEnabled(scanner, false));
        checks.check(ctx.computeOnClient(mc -> scanner.flaggedCount()) == 0, "disabling clears WorldScanner", "");
    }

    /** Region Map, Waypoints and the HUD Editor (dragged with the real mouse). */
    private void hudEditorAndMaps(net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext sp) {
        var core = ctx.computeOnClient(mc -> HafniumCore.get());
        var map = ctx.computeOnClient(mc -> core.modules().get(dev.hafnium.client.modules.client.RegionMapModule.class));
        var waypoints = ctx.computeOnClient(mc -> core.modules().get(dev.hafnium.client.modules.world.WaypointsModule.class));
        var editor = ctx.computeOnClient(mc -> core.modules().get(dev.hafnium.client.modules.client.HudEditorModule.class));
        var watermarkModule = ctx.computeOnClient(mc -> core.modules().get(dev.hafnium.client.modules.client.WatermarkModule.class));
        int[] p = ctx.computeOnClient(mc -> new int[]{mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
        sp.getServer().runCommand("tp @a " + p[0] + " " + p[1] + " " + p[2] + " facing " + (p[0] + 30) + " " + (p[1] + 1) + " " + p[2]);

        // Region Map
        ctx.runOnClient(mc -> {
            core.modules().setEnabled(map, true);
            core.modules().setEnabled(watermarkModule, true);
        });
        ctx.waitTicks(5);
        var mapHud = ctx.computeOnClient(mc -> dev.hafnium.client.hud.HudLayout.element("region-map"));
        checks.check(ctx.computeOnClient(mc -> mapHud.visible() && mapHud.width() > 100 && mapHud.height() > 100),
                "Region Map draws its panel", ctx.computeOnClient(mc -> mapHud.width() + "x" + mapHud.height()));
        String region = dev.hafnium.client.modules.client.RegionMapModule.regionAt(p[0], p[2]);
        checks.check(!region.equals("—"), "the region under the player is known", region);
        checks.check(dev.hafnium.client.modules.client.RegionMapModule.regionAt(300000, 0).equals("—"), "outside the map there is no region", "");

        // Waypoints
        ctx.runOnClient(mc -> {
            core.modules().setEnabled(waypoints, true);
            ((dev.hafnium.client.core.setting.ActionSetting) waypoints.setting("add-here")).run();
            ((dev.hafnium.client.anubis.setting.StringListSetting) waypoints.setting("waypoints"))
                    .addValue("Base " + (p[0] + 30) + " " + (p[1] + 1) + " " + p[2]);
        });
        ctx.waitTicks(5);
        var entries = ctx.computeOnClient(mc -> waypoints.entries());
        checks.check(entries.size() == 2 && entries.get(0).equals("Waypoint 1 " + p[0] + " " + p[1] + " " + p[2]),
                "Add Here saves the player's position", entries);
        checks.check(ctx.computeOnClient(mc -> waypoints.drawnLastFrame()) == 2, "both waypoints are placed on screen",
                ctx.computeOnClient(mc -> waypoints.drawnLastFrame()));
        ctx.takeScreenshot("hafnium-region-map-waypoints");

        // HUD Editor: drag the watermark to the middle of the screen
        ctx.runOnClient(mc -> editor.openEditor());
        ctx.waitTicks(5);
        var wm = ctx.computeOnClient(mc -> dev.hafnium.client.hud.HudLayout.element("watermark"));
        float[] box = ctx.computeOnClient(mc -> {
            float sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
            return new float[]{wm.x(sw, sh), wm.y(sw, sh), wm.width(), wm.height(), sw, sh};
        });
        double unit = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale() * mc.getWindow().getScreenWidth() / (double) mc.getWindow().getWidth());
        float grabX = box[0] + box[2] / 2f, grabY = box[1] + box[3] / 2f;
        in.setCursorPos(grabX * unit, grabY * unit);
        ctx.waitTick();
        in.holdMouse(0);
        ctx.waitTick();
        for (int step = 1; step <= 8; step++) {
            float t = step / 8f;
            in.setCursorPos((grabX + (box[4] / 2f + 1.5f - grabX) * t) * unit, (grabY + (box[5] / 3f - grabY) * t) * unit);
            ctx.waitTick();
        }
        in.releaseMouse(0);
        ctx.waitTick();
        float[] moved = ctx.computeOnClient(mc -> new float[]{wm.x(box[4], box[5]), wm.y(box[4], box[5])});
        checks.check(Math.abs(moved[0] - (box[4] - box[2]) / 2f) < 0.01f, "dragging snaps the overlay to the centre line",
                moved[0] + " vs " + (box[4] - box[2]) / 2f);
        checks.check(moved[1] > box[1] + 20, "dragging moves the overlay down", box[1] + " -> " + moved[1]);
        ctx.takeScreenshot("hafnium-hud-editor");
        in.setCursorPos((moved[0] + box[2] / 2f) * unit, (moved[1] + box[3] / 2f) * unit);
        ctx.waitTick();
        in.scroll(1);
        ctx.waitTick();
        checks.check(Math.abs(ctx.computeOnClient(mc -> wm.scale()) - 1.05f) < 0.001f, "scrolling resizes the overlay",
                ctx.computeOnClient(mc -> wm.scale()));
        in.pressMouse(1);
        ctx.waitTick();
        float[] reset = ctx.computeOnClient(mc -> new float[]{wm.x(box[4], box[5]), wm.y(box[4], box[5]), wm.scale()});
        checks.check(reset[0] == box[0] && reset[1] == box[1] && reset[2] == 1f, "right-click resets position and size",
                reset[0] + "," + reset[1] + " @" + reset[2]);
        in.pressKey(GLFW.GLFW_KEY_ESCAPE);
        ctx.waitTicks(3);
        checks.check(ctx.computeOnClient(mc -> mc.screen == null), "Esc closes the HUD Editor", "");
        ctx.runOnClient(mc -> {
            core.modules().setEnabled(map, false);
            core.modules().setEnabled(waypoints, false);
            ((dev.hafnium.client.anubis.setting.StringListSetting) waypoints.setting("waypoints")).clear();
        });
    }
}
