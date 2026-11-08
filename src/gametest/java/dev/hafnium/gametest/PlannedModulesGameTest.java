package dev.hafnium.gametest;

import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.setting.ModeSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.core.setting.StringSetting;
import dev.hafnium.client.modules.automation.AutoBaseHuntModule;
import dev.hafnium.client.modules.misc.EconomyAssistantModule;
import dev.hafnium.client.modules.misc.InventoryCoreModule;
import dev.hafnium.client.modules.render.SchematicPreviewModule;
import dev.hafnium.client.modules.world.ElytraFinderModule;
import dev.hafnium.client.modules.world.WaypointsModule;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;

/** Hafnium's own planned modules (no Anubis equivalent), exercised in a real world. */
public final class PlannedModulesGameTest implements FabricClientGameTest {
    private final Checks checks = new Checks();
    private ClientGameTestContext ctx;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (var sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.waitTicks(20);
            schematic(sp);
            economy(sp);
            inventory(sp);
            baseHunt(sp);
            elytra(sp);
        }
        checks.finish();
    }

    private HafniumCore core() {
        return ctx.computeOnClient(mc -> HafniumCore.get());
    }

    private <T extends dev.hafnium.client.core.module.Module> T module(Class<T> type) {
        return ctx.computeOnClient(mc -> HafniumCore.get().modules().get(type));
    }

    private static CompoundTag state(String name) {
        CompoundTag t = new CompoundTag();
        t.putString("Name", name);
        return t;
    }

    private static ListTag ints(int... v) {
        ListTag l = new ListTag();
        for (int i : v) {
            l.add(IntTag.valueOf(i));
        }
        return l;
    }

    private void schematic(TestSingleplayerContext sp) {
        SchematicPreviewModule m = module(SchematicPreviewModule.class);
        Path dir = SchematicPreviewModule.folder();
        try {
            Files.createDirectories(dir);
            // vanilla structure: 3 stone in a row and an oak plank on top of the first
            CompoundTag nbt = new CompoundTag();
            ListTag palette = new ListTag();
            palette.add(state("minecraft:stone"));
            palette.add(state("minecraft:oak_planks"));
            nbt.put("palette", palette);
            ListTag blocks = new ListTag();
            int[][] pos = {{0, 0, 0}, {1, 0, 0}, {2, 0, 0}, {0, 1, 0}};
            for (int i = 0; i < pos.length; i++) {
                CompoundTag b = new CompoundTag();
                b.put("pos", ints(pos[i]));
                b.putInt("state", i == 3 ? 1 : 0);
                blocks.add(b);
            }
            nbt.put("blocks", blocks);
            nbt.put("size", ints(3, 2, 1));
            NbtIo.writeCompressed(nbt, dir.resolve("hf-test.nbt"));
            // Sponge v2: 2x1x1, both stone
            CompoundTag schem = new CompoundTag();
            schem.putShort("Width", (short) 2);
            schem.putShort("Height", (short) 1);
            schem.putShort("Length", (short) 1);
            CompoundTag sp2 = new CompoundTag();
            sp2.putInt("minecraft:stone", 0);
            sp2.putInt("minecraft:air", 1);
            schem.put("Palette", sp2);
            schem.putByteArray("BlockData", new byte[]{0, 0});
            NbtIo.writeCompressed(schem, dir.resolve("hf-test2.schem"));
            // Litematica: one 3x1x1 region, palette [air, stone, glass], blocks stone, air, glass (2 bits each)
            CompoundTag lit = new CompoundTag();
            CompoundTag regions = new CompoundTag();
            CompoundTag region = new CompoundTag();
            CompoundTag p0 = new CompoundTag();
            p0.putInt("x", 0);
            p0.putInt("y", 0);
            p0.putInt("z", 0);
            CompoundTag size = new CompoundTag();
            size.putInt("x", 3);
            size.putInt("y", 1);
            size.putInt("z", 1);
            region.put("Position", p0);
            region.put("Size", size);
            ListTag lp = new ListTag();
            lp.add(state("minecraft:air"));
            lp.add(state("minecraft:stone"));
            lp.add(state("minecraft:glass"));
            region.put("BlockStatePalette", lp);
            region.putLongArray("BlockStates", new long[]{1L | (0L << 2) | (2L << 4)});
            regions.put("main", region);
            lit.put("Regions", regions);
            NbtIo.writeCompressed(lit, dir.resolve("hf-test3.litematic"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        StringSetting file = (StringSetting) m.setting("file");
        ctx.runOnClient(mc -> {
            HafniumCore.get().modules().setEnabled(m, true);
            file.set("hf-test2");
            m.load();
        });
        checks.check(ctx.computeOnClient(mc -> m.schematic() != null && m.schematic().blocks() == 2), "Sponge .schem loads", "");
        ctx.runOnClient(mc -> {
            file.set("hf-test3");
            m.load();
        });
        checks.check(ctx.computeOnClient(mc -> m.schematic() != null && m.schematic().blocks() == 2
                && m.schematic().states()[1].getBlock() == net.minecraft.world.level.block.Blocks.GLASS), "Litematica .litematic loads (packed palette)", "");
        int[] p = ctx.computeOnClient(mc -> new int[]{mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
        sp.getServer().runCommand("tp @a " + p[0] + " " + p[1] + " " + p[2] + " facing " + (p[0] + 4) + " " + p[1] + " " + (p[2] + 3));
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> {
            file.set("hf-test");
            m.load();
            m.placeHere();
            ((NumberSetting) m.setting("offset-x")).set(3.0);
            ((NumberSetting) m.setting("offset-z")).set(3.0);
        });
        ctx.waitTicks(5);
        int[] c = ctx.computeOnClient(mc -> m.counts());
        checks.check(c[0] == 4 && c[2] == 0, "structure .nbt: all 4 blocks start missing", java.util.Arrays.toString(c));
        int x = p[0] + 3, y = p[1], z = p[2] + 3;
        sp.getServer().runCommand("setblock " + x + " " + y + " " + z + " minecraft:stone");
        sp.getServer().runCommand("setblock " + (x + 1) + " " + y + " " + z + " minecraft:stone");
        sp.getServer().runCommand("setblock " + (x + 2) + " " + y + " " + z + " minecraft:dirt");
        ctx.waitTicks(5);
        c = ctx.computeOnClient(mc -> m.counts());
        checks.check(c[0] == 1 && c[1] == 1 && c[2] == 2, "placing blocks updates missing / wrong / correct", java.util.Arrays.toString(c));
        ctx.takeScreenshot("hafnium-schematic-preview");
        ctx.runOnClient(mc -> ((ModeSetting) m.setting("rotation")).set("90°"));
        ctx.waitTicks(5);
        c = ctx.computeOnClient(mc -> m.counts());
        checks.check(c[2] == 1, "rotating 90° moves the guide (only the corner block still matches)", java.util.Arrays.toString(c));
        ctx.runOnClient(mc -> HafniumCore.get().modules().setEnabled(m, false));
        for (int i = 0; i < 3; i++) {
            sp.getServer().runCommand("setblock " + (x + i) + " " + y + " " + z + " minecraft:air");
        }
    }

    private void economy(TestSingleplayerContext sp) {
        EconomyAssistantModule m = module(EconomyAssistantModule.class);
        sp.getServer().runCommand("clear @a");
        sp.getServer().runCommand("give @a diamond 10");
        sp.getServer().runCommand("give @a shulker_box[container=[{slot:0,item:{id:\"minecraft:diamond\",count:64}}]] 1");
        sp.getServer().runCommand("scoreboard objectives add hfstats dummy \"Stats\"");
        sp.getServer().runCommand("scoreboard objectives setdisplay sidebar hfstats");
        // servers write sidebar lines as team prefixes on hidden holders
        sp.getServer().runCommand("team add hfmoney");
        sp.getServer().runCommand("team modify hfmoney prefix \"Money $1.5M \"");
        sp.getServer().runCommand("team join hfmoney line1");
        sp.getServer().runCommand("scoreboard players set line1 hfstats 3");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> HafniumCore.get().modules().setEnabled(m, true));
        ctx.waitTicks(15);
        double[] n = ctx.computeOnClient(mc -> m.numbers());
        checks.check(n[0] == 7400, "inventory value counts loose diamonds and shulker contents (74 × $100)", n[0]);
        checks.check(n[1] == 1_500_000, "balance is read from the sidebar ($1.5M)", n[1]);
        sp.getServer().runCommand("team modify hfmoney prefix \"Money $1.6M \"");
        sp.getServer().runCommand("tellraw @a \"You sold 64 items for $1,500\"");
        sp.getServer().runCommand("tellraw @a \"You paid $2.5k to Notch\"");
        ctx.waitTicks(15);
        n = ctx.computeOnClient(mc -> m.numbers());
        checks.check(n[2] == 100_000, "session profit follows the balance (+$100k)", n[2]);
        checks.check(n[3] == 1500 && n[4] == 2500, "chat income and spending are counted", n[3] + " / " + n[4]);
        checks.check(EconomyAssistantModule.parseAmount("$2,345.5") == 2345.5 && EconomyAssistantModule.parseAmount("1.2M") == 1_200_000,
                "amount parsing handles commas and suffixes", "");
        String tip = ctx.computeOnClient(mc -> {
            var lines = new ItemStack(net.minecraft.world.item.Items.DIAMOND, 10).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY,
                    mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
            return lines.get(lines.size() - 1).getString();
        });
        checks.check(tip.equals("Worth $100 · stack $1,000"), "tooltips show the price", tip);
        ctx.takeScreenshot("hafnium-economy");
        ctx.runOnClient(mc -> HafniumCore.get().modules().setEnabled(m, false));
        sp.getServer().runCommand("scoreboard objectives remove hfstats");
        sp.getServer().runCommand("team remove hfmoney");
        sp.getServer().runCommand("clear @a");
    }

    private boolean waitDone(java.util.function.Supplier<dev.hafnium.client.core.task.TaskHandle> handle, int ticks) {
        for (int i = 0; i < ticks; i++) {
            ctx.waitTick();
            if (ctx.computeOnClient(mc -> handle.get() != null && handle.get().isDone())) {
                return true;
            }
        }
        return false;
    }

    private void inventory(TestSingleplayerContext sp) {
        InventoryCoreModule m = module(InventoryCoreModule.class);
        ctx.runOnClient(mc -> {
            HafniumCore.get().control().setMode(OperatingMode.ASSIST);
            HafniumCore.get().modules().setEnabled(m, true);
        });
        sp.getServer().runCommand("clear @a");
        String[] items = {"minecraft:oak_log 5", "minecraft:stone 20", "minecraft:apple 3", "minecraft:stone 64", "minecraft:dirt 7", "minecraft:apple 9"};
        int[] slots = {0, 4, 7, 12, 15, 26};
        for (int i = 0; i < items.length; i++) {
            sp.getServer().runCommand("item replace entity @a inventory." + slots[i] + " with " + items[i]);
        }
        sp.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:cobblestone 10");
        sp.getServer().runCommand("item replace entity @a inventory.20 with minecraft:cobblestone 30");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> m.sort());
        boolean done = waitDone(m::running, 200);
        checks.check(done && ctx.computeOnClient(mc -> m.running().isDone()), "Sort Inventory finishes", ctx.computeOnClient(mc -> m.running().status()));
        boolean sorted = ctx.computeOnClient(mc -> {
            var inv = mc.player.getInventory();
            java.util.List<ItemStack> main = new java.util.ArrayList<>();
            for (int i = 9; i < 36; i++) {
                main.add(inv.getItem(i));
            }
            java.util.List<ItemStack> expect = new java.util.ArrayList<>(main);
            expect.sort((a, b) -> {
                int ea = a.isEmpty() ? 1 : 0, eb = b.isEmpty() ? 1 : 0;
                if (ea != eb) return ea - eb;
                int ia = net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(a.getItem());
                int ib = net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(b.getItem());
                return ia != ib ? ia - ib : b.getCount() - a.getCount();
            });
            for (int i = 0; i < main.size(); i++) {
                if (!ItemStack.matches(main.get(i), expect.get(i))) return false;
            }
            return !main.get(0).isEmpty() && main.get(7).isEmpty();
        });
        checks.check(sorted, "the main inventory ends up grouped by item, packed to the front", "");
        ctx.runOnClient(mc -> m.refill());
        waitDone(m::running, 100);
        int hot = ctx.computeOnClient(mc -> mc.player.getInventory().getItem(0).getCount());
        checks.check(hot == 40, "Refill Hotbar tops up the cobblestone stack (10 + 30)", hot);
        ctx.runOnClient(mc -> HafniumCore.get().control().setMode(OperatingMode.MANUAL));
        ctx.runOnClient(mc -> m.sort());
        waitDone(m::running, 40);
        String refused = ctx.computeOnClient(mc -> m.running().status());
        checks.check(refused.contains("Manual"), "in Manual mode the permission layer refuses the action", refused);
        sp.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword[damage=1550]");
        ctx.waitTicks(10);
        ctx.takeScreenshot("hafnium-inventory-core");
        ctx.runOnClient(mc -> HafniumCore.get().modules().setEnabled(m, false));
        sp.getServer().runCommand("clear @a");
    }

    private void baseHunt(TestSingleplayerContext sp) {
        AutoBaseHuntModule m = module(AutoBaseHuntModule.class);
        double[] start = ctx.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getZ()});
        ctx.runOnClient(mc -> {
            ((ModeSetting) m.setting("pattern")).set("Spiral Only");
            ((NumberSetting) m.setting("spiral-step")).set(48.0);
            ((NumberSetting) m.setting("max-distance")).set(200.0);
            HafniumCore.get().modules().setEnabled(m, true);
            m.toggle();
        });
        ctx.runOnClient(mc -> HafniumCore.get().control().setMode(OperatingMode.MANUAL));
        ctx.waitTicks(5);
        String manual = ctx.computeOnClient(mc -> m.running().status());
        checks.check(ctx.computeOnClient(mc -> m.running().isDone()), "Auto Base Hunt does not run in Manual mode", manual);
        ctx.runOnClient(mc -> {
            HafniumCore.get().control().setMode(OperatingMode.AUTONOMOUS);
            m.toggle();
        });
        ctx.waitTicks(200);
        double moved = ctx.computeOnClient(mc -> Math.hypot(mc.player.getX() - start[0], mc.player.getZ() - start[1]));
        checks.check(moved > 10, "in Autonomous mode (own world) it walks the spiral", Math.round(moved) + " blocks, " + ctx.computeOnClient(mc -> m.status()));
        ctx.takeScreenshot("hafnium-auto-base-hunt");
        ctx.runOnClient(mc -> m.toggle());
        ctx.waitTicks(3);
        checks.check(ctx.computeOnClient(mc -> m.running().isDone() && !mc.options.keyUp.isDown()), "Start / Stop stops it and releases the keys", "");
        ctx.runOnClient(mc -> {
            HafniumCore.get().modules().setEnabled(m, false);
            HafniumCore.get().control().setMode(OperatingMode.MANUAL);
        });
    }

    private void elytra(TestSingleplayerContext sp) {
        ElytraFinderModule m = module(ElytraFinderModule.class);
        WaypointsModule wp = module(WaypointsModule.class);
        // Fabric's test harness does not deliver entities after a cross-dimension teleport, so the ship is built in
        // the Overworld with "End Only" off (End detection of the bow block itself is covered by the same scanner).
        int[] p = ctx.computeOnClient(mc -> new int[]{mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
        int x = p[0] + 6, y = p[1], z = p[2];
        sp.getServer().runCommand("fill " + x + " " + y + " " + z + " " + x + " " + (y + 2) + " " + z + " minecraft:purpur_block");
        sp.getServer().runCommand("setblock " + x + " " + (y + 3) + " " + z + " minecraft:dragon_head");
        sp.getServer().runCommand("summon item_frame " + (x - 1) + " " + (y + 1) + " " + z + " {Facing:4b,Fixed:1b,Item:{id:\"minecraft:elytra\",count:1}}");
        sp.getServer().runCommand("tp @a " + p[0] + " " + p[1] + " " + p[2] + " facing " + x + " " + (y + 1) + " " + z);
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> {
            ((dev.hafnium.client.core.setting.BoolSetting) m.setting("end-only")).set(false);
            HafniumCore.get().modules().setEnabled(wp, true);
            HafniumCore.get().modules().setEnabled(m, true);
        });
        boolean found = false;
        for (int i = 0; i < 200 && !found; i += 10) {
            ctx.waitTicks(10);
            found = ctx.computeOnClient(mc -> m.ships().containsValue(ElytraFinderModule.Status.ELYTRA));
        }
        checks.check(found, "Elytra Finder finds the ship and its elytra", ctx.computeOnClient(mc -> m.ships().toString()));
        checks.check(ctx.computeOnClient(mc -> m.route().size()) == 1, "the unlooted ship is on the route", ctx.computeOnClient(mc -> m.route().size()));
        ctx.takeScreenshot("hafnium-elytra-finder");
        ctx.runOnClient(mc -> ((dev.hafnium.client.core.setting.ActionSetting) m.setting("send-to-waypoints")).run());
        checks.check(ctx.computeOnClient(mc -> wp.entries().stream().anyMatch(e -> e.startsWith("End Ship 1 " + x + " " + (y + 3) + " " + z))),
                "Send to Waypoints adds the ship", ctx.computeOnClient(mc -> wp.entries()));
        sp.getServer().runCommand("data remove entity @e[type=item_frame,limit=1,sort=nearest] Item");
        boolean looted = false;
        for (int i = 0; i < 60 && !looted; i += 5) {
            ctx.waitTicks(5);
            looted = ctx.computeOnClient(mc -> m.ships().containsValue(ElytraFinderModule.Status.LOOTED) && m.route().isEmpty());
        }
        checks.check(looted, "an emptied frame marks the ship looted and drops it from the route", ctx.computeOnClient(mc -> m.ships().toString()));
        ctx.runOnClient(mc -> {
            HafniumCore.get().modules().setEnabled(m, false);
            ((dev.hafnium.client.core.setting.BoolSetting) m.setting("end-only")).set(true);
            ((dev.hafnium.client.anubis.setting.StringListSetting) wp.setting("waypoints")).clear();
            HafniumCore.get().modules().setEnabled(wp, false);
        });
    }
}
