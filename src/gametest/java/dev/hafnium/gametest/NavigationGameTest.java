package dev.hafnium.gametest;

import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.control.OperatingMode;
import dev.hafnium.client.core.navigation.*;
import dev.hafnium.client.core.permission.Autonomy;
import dev.hafnium.client.core.task.*;
import dev.hafnium.client.navigation.MinecraftNavigationDriver;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class NavigationGameTest implements FabricClientGameTest {
    public void runTest(ClientGameTestContext ctx) {
        Checks checks=new Checks();
        try(var sp=ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender(); ctx.waitTicks(20);
            var driver=ctx.computeOnClient(mc->{
                int x=(int)Math.floor(mc.player.getX()),y=(int)Math.floor(mc.player.getY()),z=(int)Math.floor(mc.player.getZ());
                return new MinecraftNavigationDriver(HafniumCore.get(),new CollisionSnapshot.Bounds(x-5,y-2,z-5,x+6,y+4,z+6));
            });
            var start=ctx.computeOnClient(mc->driver.position());
            var goal=new PathPlanner.Point(start.x()+3,start.y(),start.z());
            var snapshot=ctx.computeOnClient(mc->driver.capture());
            var route=new PathPlanner().plan(snapshot,start,goal,PathPlanner.Costs.DEFAULT,()->false);
            checks.check(route.points().size()==4,"captured flat world yields three-block route",route);
            var denied=ctx.computeOnClient(mc->HafniumCore.get().scheduler().submit(new NavigationTask(driver,goal,PathPlanner.Costs.DEFAULT,Autonomy.ASSISTED)));
            ctx.waitTicks(3);
            checks.check(denied.state()==TaskState.FAILED,"Manual mode refuses navigation",denied.error());
            var handle=ctx.computeOnClient(mc->{
                HafniumCore.get().control().setMode(OperatingMode.ASSIST);
                return HafniumCore.get().scheduler().submit(new NavigationTask(driver,goal,PathPlanner.Costs.DEFAULT,Autonomy.ASSISTED),TaskOptions.builder().timeoutTicks(200).build());
            });
            for(int i=0;i<220 && !handle.isDone();i++) ctx.waitTicks(1);
            checks.check(handle.state()==TaskState.SUCCEEDED,"authorized navigation reaches destination",handle.state()+" "+handle.error());
            checks.check(ctx.computeOnClient(mc->driver.reached(goal)),"player actually reached goal",ctx.computeOnClient(mc->driver.position()));
            checks.check(ctx.computeOnClient(mc->!mc.options.keyUp.isDown()),"completion releases forward input",handle.state());
            checks.check(ctx.computeOnClient(mc->HafniumCore.get().scheduler().holderOf(TaskResource.MOVEMENT)==null),"completion releases movement lock",handle.state());
            ctx.runOnClient(mc->HafniumCore.get().control().emergencyStop("Navigation test cleanup"));
            int x=start.x(), y=(int)start.y(), z=start.z()+3;
            String[] blocks={"stone_slab[type=bottom]","stone_stairs[facing=east]","oak_fence","water"};
            for(int i=0;i<blocks.length;i++) sp.getServer().runCommand("setblock "+(x+i)+" "+y+" "+z+" minecraft:"+blocks[i]);
            ctx.waitTicks(10);
            var changed=ctx.computeOnClient(mc->driver.capture());
            checks.check(!ctx.computeOnClient(mc->driver.valid(snapshot)),"ordinary block updates invalidate old capture",changed.revision());
            var slab=changed.cells().get(new CollisionSnapshot.Cell(x,y,z));
            checks.check(slab.shapes().stream().anyMatch(b->b.maxY()==y+.5),"slab captures half-height shape",slab);
            var stairs=changed.cells().get(new CollisionSnapshot.Cell(x+1,y,z));
            checks.check(stairs.shapes().size()>1,"stairs preserve constituent collision boxes",stairs);
            var fence=changed.cells().get(new CollisionSnapshot.Cell(x+2,y,z));
            checks.check(fence.shapes().stream().anyMatch(b->b.maxY()>y+1),"fence captures above-block collision",fence);
            checks.check(changed.cells().get(new CollisionSnapshot.Cell(x+3,y,z)).fluid(),"water captured as fluid",changed.revision());
        }
        checks.finish();
    }
}
