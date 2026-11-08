package dev.hafnium.client.core.world;

/**
 * Block positions packed into a long (26 bits x, 12 bits y, 26 bits z — same ranges as Minecraft's BlockPos) and
 * chunk keys, without depending on Minecraft classes.
 */
public final class Pos {
    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;
    private static final int Z_SHIFT = Y_BITS;
    private static final int X_SHIFT = Y_BITS + XZ_BITS;

    private Pos() {
    }

    public static long pack(int x, int y, int z) {
        return ((x & XZ_MASK) << X_SHIFT) | ((z & XZ_MASK) << Z_SHIFT) | (y & Y_MASK);
    }

    public static int x(long p) {
        return (int) (p << (64 - X_SHIFT - XZ_BITS) >> (64 - XZ_BITS));
    }

    public static int y(long p) {
        return (int) (p << (64 - Y_BITS) >> (64 - Y_BITS));
    }

    public static int z(long p) {
        return (int) (p << (64 - Z_SHIFT - XZ_BITS) >> (64 - XZ_BITS));
    }

    public static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }

    public static int chunkX(long key) {
        return (int) key;
    }

    public static int chunkZ(long key) {
        return (int) (key >>> 32);
    }

    public static long chunkOf(long pos) {
        return chunkKey(x(pos) >> 4, z(pos) >> 4);
    }

    public static double distSq(long pos, double x, double y, double z) {
        double dx = x(pos) + 0.5 - x;
        double dy = y(pos) + 0.5 - y;
        double dz = z(pos) + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static String toString(long p) {
        return x(p) + "," + y(p) + "," + z(p);
    }
}
