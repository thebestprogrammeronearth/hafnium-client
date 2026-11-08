package dev.hafnium.gametest;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.modules.world.AncientDebrisModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
public final class DebrisGameTest implements FabricClientGameTest {
    public void runTest(ClientGameTestContext ctx) {
        Checks checks = new Checks();
        try (var sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.waitTicks(20);
            int[] p = ctx.computeOnClient(mc
                    -> new int[] {(int) Math.floor(mc.player.getX()) + 2, (int) Math.floor(mc.player.getY()),
                            (int) Math.floor(mc.player.getZ())});
            var module = ctx.computeOnClient(mc -> HafniumCore.get().modules().get(AncientDebrisModule.class));
            ctx.runOnClient(mc -> HafniumCore.get().modules().setEnabled(module, true));
            sp.getServer().runCommand("setblock " + p[0] + " " + p[1] + " " + p[2] + " minecraft:ancient_debris");
            ctx.waitTicks(30);
            checks.check(ctx.computeOnClient(mc -> module.visible().size() == 1),
                    "received ancient debris becomes a discovery", module.state());
            ctx.runOnClient(mc -> module.tracers.set(true));
            ctx.waitTicks(3);
            ctx.takeScreenshot("hafnium-debris-overlay");
            sp.getServer().runCommand("setblock " + p[0] + " " + p[1] + " " + p[2] + " minecraft:air");
            ctx.waitTicks(20);
            checks.check(
                    ctx.computeOnClient(mc -> module.visible().isEmpty()), "mined debris is removed", module.state());
            checks.check(module.isEnabled(), "debris rendering and tracers do not fail module", module.state());
            ctx.runOnClient(mc -> HafniumCore.get().modules().setEnabled(module, false));
        }
        checks.finish();
    }
}
