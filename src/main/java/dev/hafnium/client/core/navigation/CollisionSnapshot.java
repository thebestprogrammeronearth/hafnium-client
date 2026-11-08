package dev.hafnium.client.core.navigation;

import dev.hafnium.client.core.ImmutableSnapshot;
import java.util.*;

/** Detached geometry in world coordinates. Missing cells are solid unknown space. */
public record CollisionSnapshot(String dimension, long revision, Bounds bounds, Map<Cell, Terrain> cells)
        implements ImmutableSnapshot {
    public record Cell(int x, int y, int z) {}
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds {
            long volume = ((long) maxX-minX)*((long) maxY-minY)*((long) maxZ-minZ);
            if (maxX<=minX || maxY<=minY || maxZ<=minZ || maxX-(long)minX>64
                    || maxY-(long)minY>64 || maxZ-(long)minZ>64 || volume>65536)
                throw new IllegalArgumentException("Capture must be positive, <=64 per axis and <=65536 cells");
        }
        public boolean contains(int x,int y,int z) {
            return x>=minX && y>=minY && z>=minZ && x<maxX && y<maxY && z<maxZ;
        }
    }
    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        public Box {
            if (!Double.isFinite(minX+minY+minZ+maxX+maxY+maxZ)
                    || minX>maxX || minY>maxY || minZ>maxZ) throw new IllegalArgumentException("Invalid box");
        }
        public boolean intersects(Box b) {
            return maxX>b.minX+1e-7 && minX<b.maxX-1e-7 && maxY>b.minY+1e-7
                    && minY<b.maxY-1e-7 && maxZ>b.minZ+1e-7 && minZ<b.maxZ-1e-7;
        }
    }
    public record Terrain(List<Box> shapes, boolean fluid, boolean hazard) {
        public Terrain { shapes=List.copyOf(shapes); }
    }
    public CollisionSnapshot {
        Objects.requireNonNull(dimension); Objects.requireNonNull(bounds);
        cells=Map.copyOf(cells);
        for (Cell c:cells.keySet()) if(!bounds.contains(c.x,c.y,c.z)) throw new IllegalArgumentException("Cell outside capture");
    }
    public boolean clear(Box body) {
        if(body.minX<bounds.minX+1 || body.minY<bounds.minY+1 || body.minZ<bounds.minZ+1
                || body.maxX>bounds.maxX-1 || body.maxY>bounds.maxY || body.maxZ>bounds.maxZ-1) return false;
        // Shapes such as fences extend beyond their owning block. Include a one-cell halo.
        for(int x=(int)Math.floor(body.minX)-1;x<=Math.floor(body.maxX);x++)
            for(int y=(int)Math.floor(body.minY)-1;y<=Math.floor(body.maxY);y++)
                for(int z=(int)Math.floor(body.minZ)-1;z<=Math.floor(body.maxZ);z++) {
                    Terrain t=cells.get(new Cell(x,y,z));
                    Box cube=new Box(x,y,z,x+1,y+1,z+1);
                    if(t==null) { if(cube.intersects(body)) return false; }
                    else {
                        if(t.fluid && cube.intersects(body)) return false;
                        for(Box shape:t.shapes) if(shape.intersects(body)) return false;
                    }
                }
        return true;
    }
    public List<Double> surfaces(int x,int z) {
        TreeSet<Double> heights=new TreeSet<>();
        for(int y=bounds.minY;y<bounds.maxY;y++) {
            Terrain t=cells.get(new Cell(x,y,z));
            if(t!=null && !t.fluid && !t.hazard) for(Box b:t.shapes)
                if(b.minX<=x+.2 && b.maxX>=x+.8 && b.minZ<=z+.2 && b.maxZ>=z+.8) heights.add(b.maxY);
        }
        return List.copyOf(heights);
    }
}
