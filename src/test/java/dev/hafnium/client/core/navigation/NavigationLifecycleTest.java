package dev.hafnium.client.core.navigation;

import dev.hafnium.client.core.*;
import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.permission.Autonomy;
import dev.hafnium.client.core.task.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NavigationLifecycleTest {
    private CollisionSnapshot snapshot(long revision) {
        Map<CollisionSnapshot.Cell,CollisionSnapshot.Terrain> cells=new HashMap<>();
        for(int x=0;x<4;x++) for(int y=0;y<4;y++) for(int z=0;z<4;z++)
            cells.put(new CollisionSnapshot.Cell(x,y,z),new CollisionSnapshot.Terrain(y==0?
                    List.of(new CollisionSnapshot.Box(x,0,z,x+1,1,z+1)):List.of(),false,false));
        return new CollisionSnapshot("test",revision,new CollisionSnapshot.Bounds(0,0,0,4,4,4),cells);
    }
    @Test void invalidationCancelsQueuedPlansAndRejectsStaleSnapshots() {
        var scheduler=new TaskScheduler(new EventBus(),ClientThread.DIRECT,Runnable::run);
        var core=new NavigationCore(ClientThread.DIRECT,scheduler);
        var s=snapshot(core.revision());
        var h=core.plan(s,new PathPlanner.Point(1,1,1),new PathPlanner.Point(2,1,1),PathPlanner.Costs.DEFAULT,r->fail("Stale result delivered"));
        core.invalidate(); scheduler.tick(); assertEquals(TaskState.CANCELLED,h.state());
        assertThrows(IllegalArgumentException.class,()->core.plan(s,new PathPlanner.Point(1,1,1),new PathPlanner.Point(2,1,1),PathPlanner.Costs.DEFAULT,r->{}));
    }
    @Test void cancellationReleasesInputAndLocks() {
        var scheduler=new TaskScheduler(new EventBus(),ClientThread.DIRECT,Runnable::run);
        boolean[] moving={false};
        var driver=new NavigationTask.Driver() {
            public CollisionSnapshot capture() { return snapshot(0); }
            public PathPlanner.Point position() { return new PathPlanner.Point(1,1,1); }
            public boolean valid(CollisionSnapshot s) { return true; }
            public boolean reached(PathPlanner.Point p) { return p.equals(position()); }
            public void move(PathPlanner.Point p) { moving[0]=true; }
            public void release() { moving[0]=false; }
        };
        var h=scheduler.submit(new NavigationTask(driver,new PathPlanner.Point(2,1,1),PathPlanner.Costs.DEFAULT,Autonomy.ASSISTED));
        scheduler.tick(); assertTrue(moving[0]); assertSame(h,scheduler.holderOf(TaskResource.MOVEMENT));
        h.cancel("stop"); assertFalse(moving[0]); assertNull(scheduler.holderOf(TaskResource.MOVEMENT));
    }
}
