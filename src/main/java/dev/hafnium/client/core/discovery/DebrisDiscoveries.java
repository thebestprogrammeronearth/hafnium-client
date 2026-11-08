package dev.hafnium.client.core.discovery;

import dev.hafnium.client.core.world.*;
import java.util.*;

/** Bounded observations, partitioned by world/server and dimension. Never generates predictions. */
public final class DebrisDiscoveries {
    public record Key(String world, String dimension, long pos) {}
    public record Discovery(Key key, long observedAt, boolean stale, boolean waypoint) {}
    private final LinkedHashMap<Key, Discovery> entries = new LinkedHashMap<>();
    public static final int LIMIT = 10000;
    public List<Discovery> all() {
        return List.copyOf(entries.values());
    }
    public boolean observe(Key key, long now) {
        Discovery old = entries.get(key);
        entries.put(key, new Discovery(key, old == null ? now : old.observedAt, false, old != null && old.waypoint));
        while (entries.size() > LIMIT) entries.remove(entries.keySet().iterator().next());
        return old == null;
    }
    public void remove(Key key) {
        entries.remove(key);
    }
    public void stale(String world, String dimension) {
        entries.replaceAll((k, v)
                                   -> k.world.equals(world) && k.dimension.equals(dimension)
                        ? new Discovery(k, v.observedAt, true, v.waypoint)
                        : v);
    }
    public void waypoint(Key key) {
        entries.computeIfPresent(key, (k, v) -> new Discovery(k, v.observedAt, v.stale, !v.waypoint));
    }
    public void load(Collection<Discovery> saved) {
        entries.clear();
        for (Discovery d : saved) {
            if (d == null || d.key == null || d.key.world == null || d.key.dimension == null)
                continue;
            entries.put(d.key, new Discovery(d.key, d.observedAt, true, d.waypoint));
            if (entries.size() >= LIMIT)
                break;
        }
    }
    public List<Discovery> nearby(
            String world, String dimension, double x, double y, double z, double radius, boolean stale, int limit) {
        return entries.values()
                .stream()
                .filter(d
                        -> d.key.world.equals(world) && d.key.dimension.equals(dimension) && (stale || !d.stale)
                                && Pos.distSq(d.key.pos, x, y, z) <= radius * radius)
                .sorted(Comparator.comparingDouble(d -> Pos.distSq(d.key.pos, x, y, z)))
                .limit(limit)
                .toList();
    }
}
