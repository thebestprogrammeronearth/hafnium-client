package dev.hafnium.client.navigation;

import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.navigation.*;
import net.minecraft.client.Minecraft;

/** Normal player input only; no position or movement packets are synthesized. */
public final class MinecraftNavigationDriver implements NavigationTask.Driver {
    private final HafniumCore core;
    private final CollisionSnapshot.Bounds bounds;
    private final Object level;
    private final String dimension;
    private double borderMinX,borderMaxX,borderMinZ,borderMaxZ;
    public MinecraftNavigationDriver(HafniumCore core,CollisionSnapshot.Bounds bounds) {
        this.core=core; this.bounds=bounds;
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || !mc.isSameThread()) throw new IllegalStateException("Client world required");
        level=mc.level; dimension=mc.level.dimension().identifier().toString();
    }
    private Minecraft client() {
        var mc=Minecraft.getInstance();
        if(!mc.isSameThread() || mc.level!=level || mc.player==null
                || !mc.level.dimension().identifier().toString().equals(dimension)) throw new IllegalStateException("Navigation world changed");
        return mc;
    }
    public CollisionSnapshot capture() {
        var border=client().level.getWorldBorder();
        borderMinX=border.getMinX(); borderMaxX=border.getMaxX();
        borderMinZ=border.getMinZ(); borderMaxZ=border.getMaxZ();
        return new CollisionCapture().capture(bounds,core.navigation().revision());
    }
    public PathPlanner.Point position() {
        var p=client().player;
        return new PathPlanner.Point((int)Math.floor(p.getX()),Math.rint(p.getY()*16)/16,(int)Math.floor(p.getZ()));
    }
    public boolean valid(CollisionSnapshot s) {
        var mc=client();
        if(s.revision()!=core.navigation().revision()) return false;
        var border=mc.level.getWorldBorder();
        if(borderMinX!=border.getMinX() || borderMaxX!=border.getMaxX()
                || borderMinZ!=border.getMinZ() || borderMaxZ!=border.getMaxZ()) return false;
        for(int x=bounds.minX()>>4;x<=((bounds.maxX()-1)>>4);x++)
            for(int z=bounds.minZ()>>4;z<=((bounds.maxZ()-1)>>4);z++) if(!mc.level.hasChunk(x,z)) return false;
        return true;
    }
    public boolean reached(PathPlanner.Point point) {
        var p=client().player;
        return Math.hypot(p.getX()-point.x()-.5,p.getZ()-point.z()-.5)<.18 && Math.abs(p.getY()-point.y())<.12;
    }
    public void move(PathPlanner.Point point) {
        var mc=client();
        if(mc.screen!=null || !mc.player.isAlive() || !mc.player.onGround()) { release(); return; }
        double dx=point.x()+.5-mc.player.getX(),dz=point.z()+.5-mc.player.getZ();
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-dx,dz)));
        mc.options.keyUp.setDown(true);
    }
    public void release() { Minecraft.getInstance().options.keyUp.setDown(false); }
}
