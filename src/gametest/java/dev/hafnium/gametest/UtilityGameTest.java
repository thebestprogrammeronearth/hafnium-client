package dev.hafnium.gametest;
import dev.hafnium.client.core.*;
import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.economy.SaleProfile;
import dev.hafnium.client.core.event.GameKeyEvent;
import dev.hafnium.client.gui.utility.WheelScreen;
import dev.hafnium.client.modules.client.SpotifyHudModule;
import dev.hafnium.client.modules.misc.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
public final class UtilityGameTest implements FabricClientGameTest {
    public void runTest(ClientGameTestContext ctx) {
        Checks checks = new Checks();
        try (var sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            sp.getServer().runOnServer(server -> {
                server.getPlayerList().setAllowCommandsForAllPlayers(true);
                for (var player : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(player);
            });
            ctx.waitTicks(20);
            var core = ctx.computeOnClient(mc -> HafniumCore.get());
            var wheel = ctx.computeOnClient(mc -> core.modules().get(DonutWheelModule.class));
            int[] p = ctx.computeOnClient(
                    mc -> new int[] {mc.player.getBlockX() + 2, mc.player.getBlockY(), mc.player.getBlockZ()});
            ctx.runOnClient(mc -> {
                core.modules().setEnabled(wheel, true);
                wheel.replace("[]");
                wheel.put(0,
                        new DonutWheelModule.Action("Fixture command", "T",
                                "setblock " + p[0] + " " + p[1] + " " + p[2] + " minecraft:gold_block", false, true,
                                null, null, null, null));
                mc.setScreen(new WheelScreen(wheel));
            });
            ctx.waitTicks(5);
            ctx.takeScreenshot("hafnium-donut-wheel");
            double[] click = ctx.computeOnClient(mc -> {
                double[] c = ((WheelScreen) mc.screen).slotCenter(0);
                return new double[] {c[0] * mc.getWindow().getGuiScale(), c[1] * mc.getWindow().getGuiScale()};
            });
            ctx.getInput().setCursorPos(click[0], click[1]);
            ctx.getInput().pressMouse(0);
            ctx.waitTicks(10);
            checks.check(ctx.computeOnClient(mc
                                 -> mc.level.getBlockState(new net.minecraft.core.BlockPos(p[0], p[1], p[2]))
                                         .is(Blocks.GOLD_BLOCK)),
                    "wheel selection executes the configured server command", wheel.actions().getFirst().label());
            var sales = ctx.computeOnClient(mc -> core.modules().get(AutoSellModule.class));
            sp.getServer().runCommand("clear @a");
            sp.getServer().runCommand("give @a minecraft:cobblestone 16");
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> {
                core.modules().setEnabled(sales, true);
                sales.interval.set(60d);
                sales.automate.bind(79, "O");
                sales.sell.bind(80, "P");
                sales.configureForServer(core.permissions().environment().serverId(),
                        new SaleProfile(
                                "clear @s {item} {quantity}", Set.of("minecraft:cobblestone"), Set.of(), Map.of(), 8));
                core.control().setMode(OperatingMode.AUTONOMOUS);
                core.events().post(new GameKeyEvent(79, 0));
            });
            ctx.waitTicks(50);
            checks.check(ctx.computeOnClient(mc -> mc.player.getInventory().countItem(Items.COBBLESTONE) == 8),
                    "selling workflow sends specified item and quantity (clear command fixture, no economy)",
                    ctx.computeOnClient(mc -> mc.player.getInventory().countItem(Items.COBBLESTONE)));
            checks.check(
                    ctx.computeOnClient(mc
                            -> sales.transactions().stream().anyMatch(s -> s.contains("Observed inventory decrease"))),
                    "history separates inventory decrease from confirmed payment", sales.transactions().size());
            ctx.runOnClient(mc -> core.events().post(new GameKeyEvent(79, 0)));
            sp.getServer().runCommand("give @a minecraft:diamond_sword");
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> {
                sales.configureForServer(core.permissions().environment().serverId(),
                        new SaleProfile("clear @s {item} {quantity}", Set.of("minecraft:diamond_sword"), Set.of(),
                                Map.of(), 1));
                core.events().post(new GameKeyEvent(79, 0));
            });
            ctx.waitTicks(25);
            checks.check(ctx.computeOnClient(mc -> mc.player.getInventory().countItem(Items.DIAMOND_SWORD) == 1),
                    "automation excludes protected equipment", sales.state());
            ctx.runOnClient(mc -> {
                core.events().post(new GameKeyEvent(79, 0));
                core.events().post(new GameKeyEvent(80, 0));
            });
            ctx.waitTicks(2);
            checks.check(ctx.computeOnClient(mc
                                 -> mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen
                                         && mc.screen.getTitle().getString().contains("PROTECTED")),
                    "manual protected sale requires explicit confirmation", sales.state());
            ctx.runOnClient(mc -> {
                mc.setScreen(null);
                core.modules().setEnabled(sales, false);
                core.modules().setEnabled(wheel, false);
                core.control().emergencyStop("Utility test cleanup");
            });
            var spotify = ctx.computeOnClient(mc -> core.modules().get(SpotifyHudModule.class));
            ctx.runOnClient(mc -> {
                spotify.hide.set(false);
                core.modules().setEnabled(spotify, true);
            });
            ctx.waitTicks(30);
            ctx.takeScreenshot("hafnium-spotify-session-status");
            checks.check(
                    spotify.isEnabled(), "Spotify backend failure is handled without module crash", spotify.state());
            ctx.runOnClient(mc -> core.modules().setEnabled(spotify, false));
        }
        checks.finish();
    }
}
