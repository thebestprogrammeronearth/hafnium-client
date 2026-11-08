package dev.hafnium.client.core.navigation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NavigationTest {
    private Map<CollisionSnapshot.Cell,CollisionSnapshot.Terrain> flat() {
        Map<CollisionSnapshot.Cell,CollisionSnapshot.Terrain> cells=new HashMap<>();
        for(int x=0;x<5;x++) for(int z=0;z<5;z++) for(int y=0;y<4;y++)
            cells.put(new CollisionSnapshot.Cell(x,y,z),new CollisionSnapshot.Terrain(y==0?
                    List.of(new CollisionSnapshot.Box(x,0,z,x+1,1,z+1)):List.of(),false,false));
        return cells;
    }
    private CollisionSnapshot snapshot(Map<CollisionSnapshot.Cell,CollisionSnapshot.Terrain> cells) {
        return new CollisionSnapshot("overworld",0,new CollisionSnapshot.Bounds(0,0,0,5,4,5),cells);
    }
    private PathPlanner.Route plan(CollisionSnapshot s) {
        return new PathPlanner().plan(s,new PathPlanner.Point(1,1,2),new PathPlanner.Point(3,1,2),PathPlanner.Costs.DEFAULT,()->false);
    }
    @Test void shortestFlatPath() { var r=plan(snapshot(flat())); assertEquals(3,r.points().size()); assertEquals(2,r.cost()); }
    @Test void detachedAndUnmodifiable() {
        var cells=flat(); var s=snapshot(cells); cells.clear(); assertEquals(3,plan(s).points().size());
        assertThrows(UnsupportedOperationException.class,()->s.cells().clear());
    }
    @Test void fenceIsRoutedAround() {
        var cells=flat(); cells.put(new CollisionSnapshot.Cell(2,1,2),new CollisionSnapshot.Terrain(
                List.of(new CollisionSnapshot.Box(2.375,1,2.375,2.625,2.5,2.625)),false,false));
        assertTrue(plan(snapshot(cells)).points().size()>3);
    }
    @Test void fluidIsNotWalkedThrough() {
        var cells=flat(); cells.put(new CollisionSnapshot.Cell(2,1,2),new CollisionSnapshot.Terrain(List.of(),true,false));
        assertTrue(plan(snapshot(cells)).points().size()>3);
    }
    @Test void missingCellsAreNotAir() {
        var cells=flat(); cells.remove(new CollisionSnapshot.Cell(3,1,2));
        assertThrows(IllegalArgumentException.class,()->plan(snapshot(cells)));
    }
    @Test void halfSlabStep() {
        var cells=flat(); cells.put(new CollisionSnapshot.Cell(2,1,2),new CollisionSnapshot.Terrain(
                List.of(new CollisionSnapshot.Box(2,1,2,3,1.5,3)),false,false));
        var route=new PathPlanner().plan(snapshot(cells),new PathPlanner.Point(1,1,2),new PathPlanner.Point(2,1.5,2),PathPlanner.Costs.DEFAULT,()->false);
        assertEquals(2,route.points().size());
    }
    @Test void cancellationAndExpansionLimit() {
        var s=snapshot(flat()); var planner=new PathPlanner(); var a=new PathPlanner.Point(1,1,2); var b=new PathPlanner.Point(3,1,2);
        assertThrows(java.util.concurrent.CancellationException.class,()->planner.plan(s,a,b,PathPlanner.Costs.DEFAULT,()->true));
        assertThrows(IllegalStateException.class,()->planner.plan(s,a,b,new PathPlanner.Costs(1,1,1),()->false));
    }
    @Test void boundsAndCostsValidated() {
        assertThrows(IllegalArgumentException.class,()->new CollisionSnapshot.Bounds(0,0,0,100,100,100));
        assertThrows(IllegalArgumentException.class,()->new PathPlanner.Costs(Double.NaN,1,100));
        assertFalse(snapshot(flat()).clear(new CollisionSnapshot.Box(-.1,1,1,.5,2.8,1.6)));
    }
}
