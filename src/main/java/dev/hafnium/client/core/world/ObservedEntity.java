package dev.hafnium.client.core.world;

import java.util.UUID;

/**
 * An entity the client can currently see. Instances are updated in place every tick (no per-tick allocation); copy
 * the fields you need rather than holding the object across ticks.
 */
public final class ObservedEntity {
    private final int id;
    private final UUID uuid;
    private final String typeId;
    private final EntityKind kind;
    private final long firstSeenTick;
    double x;
    double y;
    double z;
    double vx;
    double vy;
    double vz;
    float health = Float.NaN;
    String name;
    long lastSeenTick;

    public ObservedEntity(int id, UUID uuid, String typeId, EntityKind kind, long tick) {
        this.id = id;
        this.uuid = uuid;
        this.typeId = typeId;
        this.kind = kind;
        this.firstSeenTick = tick;
        this.lastSeenTick = tick;
    }

    public void update(double x, double y, double z, double vx, double vy, double vz, float health, long tick) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.health = health;
        this.lastSeenTick = tick;
    }

    public int id() {
        return id;
    }

    public UUID uuid() {
        return uuid;
    }

    public String typeId() {
        return typeId;
    }

    public EntityKind kind() {
        return kind;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public double vx() {
        return vx;
    }

    public double vy() {
        return vy;
    }

    public double vz() {
        return vz;
    }

    /** NaN for entities without health. */
    public float health() {
        return health;
    }

    /** Display name, recorded once when first seen (players) or null. */
    public void setName(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public long firstSeenTick() {
        return firstSeenTick;
    }

    public long lastSeenTick() {
        return lastSeenTick;
    }

    public double distSq(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public String toString() {
        return typeId + "#" + id + "(" + kind + ")";
    }
}
