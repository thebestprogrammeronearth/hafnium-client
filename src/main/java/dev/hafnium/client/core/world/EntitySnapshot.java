package dev.hafnium.client.core.world;

import java.util.UUID;

/** Immutable copy of an {@link ObservedEntity} for snapshots. */
public record EntitySnapshot(int id, UUID uuid, String typeId, EntityKind kind, double x, double y, double z,
                             double vx, double vy, double vz, float health, String name, long lastSeenTick) {
    static EntitySnapshot of(ObservedEntity e) {
        return new EntitySnapshot(e.id(), e.uuid(), e.typeId(), e.kind(), e.x(), e.y(), e.z(), e.vx(), e.vy(), e.vz(),
                e.health(), e.name(), e.lastSeenTick());
    }

    public double distSq(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
