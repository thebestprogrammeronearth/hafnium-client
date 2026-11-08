package dev.hafnium.client.core.navigation;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Conservative walking A*: no swimming, jumping, parkour or unsupported drops. */
public final class PathPlanner {
    public record Point(int x,double y,int z) {}
    public record Costs(double step,double rise,int maxExpanded) {
        public Costs {
            if(!Double.isFinite(step) || !Double.isFinite(rise) || step<=0 || rise<0 || maxExpanded<1)
                throw new IllegalArgumentException("Invalid costs");
        }
        public static final Costs DEFAULT=new Costs(1,2,20000);
    }
    public record Route(String dimension,long revision,List<Point> points,double cost) {
        public Route { points=List.copyOf(points); }
    }
    private record Entry(Point point,double score,double distance) {}
    public static CollisionSnapshot.Box body(Point p) {
        return new CollisionSnapshot.Box(p.x+.2,p.y,p.z+.2,p.x+.8,p.y+1.8,p.z+.8);
    }
    public Route plan(CollisionSnapshot s,Point start,Point goal,Costs costs,BooleanSupplier cancelled) {
        if(!standable(s,start) || !standable(s,goal)) throw new IllegalArgumentException("Endpoint is not a known walkable surface");
        PriorityQueue<Entry> open=new PriorityQueue<>(Comparator.comparingDouble(Entry::score));
        Map<Point,Double> distances=new HashMap<>(); Map<Point,Point> parents=new HashMap<>();
        distances.put(start,0d); open.add(new Entry(start,0,0)); int expanded=0;
        while(!open.isEmpty()) {
            if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            Entry e=open.remove(); Point p=e.point;
            if(e.distance>distances.get(p)) continue;
            if(p.equals(goal)) {
                List<Point> path=new ArrayList<>(); for(Point q=p;q!=null;q=parents.get(q)) path.add(q);
                Collections.reverse(path); return new Route(s.dimension(),s.revision(),path,e.distance);
            }
            if(++expanded>costs.maxExpanded) throw new IllegalStateException("Planning expansion budget exhausted");
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int x=p.x+d[0],z=p.z+d[1];
                for(double y:s.surfaces(x,z)) {
                    Point q=new Point(x,y,z);
                    if(Math.abs(y-p.y)>.600001 || !standable(s,q) || !transition(s,p,q)) continue;
                    double g=e.distance+costs.step+Math.abs(y-p.y)*costs.rise;
                    if(g>=distances.getOrDefault(q,Double.POSITIVE_INFINITY)) continue;
                    distances.put(q,g); parents.put(q,p);
                    double h=(Math.abs(x-goal.x)+Math.abs(z-goal.z))*costs.step;
                    open.add(new Entry(q,g+h,g));
                }
            }
        }
        throw new IllegalStateException("No walking route within observed bounds");
    }
    private static boolean standable(CollisionSnapshot s,Point p) {
        return s.surfaces(p.x,p.z).contains(p.y) && s.clear(body(p));
    }
    private static boolean transition(CollisionSnapshot s,Point a,Point b) {
        double high=Math.max(a.y,b.y);
        // Swept volume, including the vertical step, rather than sampled point collisions.
        return s.clear(new CollisionSnapshot.Box(Math.min(a.x,b.x)+.2,high,Math.min(a.z,b.z)+.2,
                Math.max(a.x,b.x)+.8,high+1.8,Math.max(a.z,b.z)+.8))
                && s.clear(new CollisionSnapshot.Box(a.x+.2,a.y,a.z+.2,a.x+.8,high+1.8,a.z+.8))
                && s.clear(new CollisionSnapshot.Box(b.x+.2,b.y,b.z+.2,b.x+.8,high+1.8,b.z+.8));
    }
}
