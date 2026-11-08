package dev.hafnium.client.core.navigation;

import dev.hafnium.client.core.task.*;
import dev.hafnium.client.core.permission.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authorized movement execution; planning itself remains independent of gameplay permissions. */
public final class NavigationTask implements Task {
    public interface Driver {
        CollisionSnapshot capture();
        PathPlanner.Point position();
        boolean valid(CollisionSnapshot snapshot);
        boolean reached(PathPlanner.Point point);
        void move(PathPlanner.Point point);
        void release();
    }
    private final Driver driver;
    private final PathPlanner.Point goal;
    private final PathPlanner.Costs costs;
    private final Autonomy autonomy;
    private CollisionSnapshot snapshot;
    private AsyncResult<PathPlanner.Route> pending;
    private AtomicBoolean cancelled;
    private PathPlanner.Route route;
    private int index,stuck,replans;
    private PathPlanner.Point last;
    public NavigationTask(Driver driver,PathPlanner.Point goal,PathPlanner.Costs costs,Autonomy autonomy) {
        this.driver=java.util.Objects.requireNonNull(driver); this.goal=goal; this.costs=costs; this.autonomy=autonomy;
    }
    public String name() { return "Navigate to "+goal; }
    public Set<TaskResource> resources() { return Set.of(TaskResource.MOVEMENT,TaskResource.LOOK); }
    public Set<Capability> capabilities() { return Set.of(Capability.MOVEMENT,Capability.LOOK); }
    public Autonomy autonomy() { return autonomy; }
    public PathPlanner.Route route() { return route; }
    private void plan(TaskContext ctx) {
        driver.release(); if(cancelled!=null) cancelled.set(true);
        if(++replans>8) throw new TaskException("Navigation recalculation limit reached");
        snapshot=driver.capture(); PathPlanner.Point start=driver.position();
        cancelled=new AtomicBoolean(); AtomicBoolean token=cancelled;
        pending=ctx.async(snapshot,s->new PathPlanner().plan(s,start,goal,costs,token::get));
        route=null; index=0; stuck=0; last=start; ctx.status("Planning walking route");
    }
    public void start(TaskContext ctx) { plan(ctx); }
    public boolean tick(TaskContext ctx) {
        if(!driver.valid(snapshot)) { plan(ctx); return false; }
        if(route==null) {
            if(!pending.isDone()) return false;
            route=pending.get(); ctx.status("Following route");
        }
        while(index<route.points().size() && driver.reached(route.points().get(index))) index++;
        if(index==route.points().size()) return true;
        PathPlanner.Point position=driver.position();
        if(position.equals(last)) stuck++; else { last=position; stuck=0; }
        if(stuck>=60) { plan(ctx); return false; }
        driver.move(route.points().get(index));
        ctx.progress(index/(double)route.points().size(),"Waypoint "+index+" / "+route.points().size());
        return false;
    }
    public void suspend(TaskContext ctx) { driver.release(); }
    public void resume(TaskContext ctx) { plan(ctx); }
    public void stop(TaskContext ctx,StopReason reason) {
        driver.release(); if(cancelled!=null) cancelled.set(true);
    }
}
