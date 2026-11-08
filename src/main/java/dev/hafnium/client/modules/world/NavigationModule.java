package dev.hafnium.client.modules.world;

import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.event.GameKeyEvent;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.*;
import dev.hafnium.client.core.navigation.*;
import dev.hafnium.client.core.permission.Autonomy;
import dev.hafnium.client.core.setting.*;
import dev.hafnium.client.core.task.*;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.navigation.MinecraftNavigationDriver;
import net.minecraft.client.Minecraft;

/** Explicit preview / start actions; enabling never starts movement. */
public final class NavigationModule extends Module {
    public final NumberSetting offsetX=setting(new NumberSetting("Target X offset",5,-12,12,1));
    public final NumberSetting offsetY=setting(new NumberSetting("Target Y offset",0,-2,2,.5));
    public final NumberSetting offsetZ=setting(new NumberSetting("Target Z offset",0,-12,12,1));
    public final NumberSetting riseCost=setting(new NumberSetting("Rise cost",2,0,20,.5));
    public final KeybindSetting previewKey=setting(new KeybindSetting("Preview key"));
    public final KeybindSetting startKey=setting(new KeybindSetting("Start key"));
    public final BoolSetting debug=setting(new BoolSetting("Route debug",true));
    private TaskHandle handle;
    private NavigationTask task;
    private PathPlanner.Route preview;
    private String status="Bind preview/start keys in settings. End cancels.";
    private final dev.hafnium.client.hud.LayoutElement hud = new dev.hafnium.client.hud.LayoutElement(
            "navigation-debug", "Route Debug", () -> isEnabled() && debug.get(), 0.5f, 0f, 0f, 30f);
    public NavigationModule() { super("Navigation",Category.WORLD,"Bounded walking navigation. Set offsets, then explicitly preview or start."); }
    @Override protected void onEnable(ModuleContext ctx) {
        ctx.subscribe(GameKeyEvent.class,e->{
            if(e.key()!=previewKey.get() && e.key()!=startKey.get()) return;
            var mc=Minecraft.getInstance(); if(mc.player==null) return;
            if(handle!=null && !handle.isDone()) handle.cancel("New navigation request");
            try {
                int x=(int)Math.floor(mc.player.getX()), y=(int)Math.floor(mc.player.getY()), z=(int)Math.floor(mc.player.getZ());
                var bounds=new CollisionSnapshot.Bounds(x-15,y-3,z-15,x+16,y+5,z+16);
                var core=HafniumCore.get(); var driver=new MinecraftNavigationDriver(core,bounds);
                var from=driver.position();
                var goal=new PathPlanner.Point(x+offsetX.get().intValue(),from.y()+offsetY.get(),z+offsetZ.get().intValue());
                var costs=new PathPlanner.Costs(1,riseCost.get(),20000);
                preview=null; task=null;
                if(e.key()==previewKey.get()) handle=core.navigation().plan(driver.capture(),from,goal,costs,r->preview=r);
                else { task=new NavigationTask(driver,goal,costs,Autonomy.ASSISTED); handle=ctx.submitTask(task,TaskOptions.builder().timeoutTicks(1200).build()); }
                status="Requested";
            } catch(RuntimeException failure) { status=failure.getMessage(); }
        });
        ctx.subscribe(HudRenderEvent.class,e->{
            if(!debug.get() || Minecraft.getInstance().options.hideGui) return;
            var g=e.graphics(); var font=Minecraft.getInstance().font;
            hud.beginVanilla(g);
            try {
                g.fill(0,0,214,100,0xCC15131C);
                g.drawString(font,"Navigation: "+(handle==null?status:handle.status()),4,4,0xFFB77CFF,false);
                var route=task==null?preview:task.route();
                if(route==null || route.revision()!=HafniumCore.get().navigation().revision()) return;
                var start=route.points().getFirst();
                for(var p:route.points()) { int px=104+(p.x()-start.x())*3,pz=55+(p.z()-start.z())*3; if(px>=0&&px<212&&pz>=0&&pz<98) g.fill(px,pz,px+2,pz+2,0xFFB77CFF); }
                g.drawString(font,"Top view • "+route.points().size()+" waypoints",4,86,0xFFF3F0F8,false);
            } finally {
                hud.endVanilla(g);
                hud.size(214,100);
            }
        });
        ctx.onClose(()->{ if(handle!=null) handle.cancel("Navigation module disabled"); preview=null; task=null; });
    }
}
