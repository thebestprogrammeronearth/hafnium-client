package dev.hafnium.client.core.discovery;
import static org.junit.jupiter.api.Assertions.*;

import dev.hafnium.client.core.world.Pos;
import java.util.List;
import org.junit.jupiter.api.Test;
class DebrisDiscoveriesTest {
    @Test
    void scopedObservedStaleRemovedAndWaypoints() {
        var d = new DebrisDiscoveries();
        var a = new DebrisDiscoveries.Key("server-a", "nether", Pos.pack(1, 15, 2));
        assertTrue(d.observe(a, 123));
        assertFalse(d.observe(a, 456));
        assertTrue(d.nearby("server-b", "nether", 0, 15, 0, 100, true, 10).isEmpty());
        assertTrue(d.nearby("server-a", "overworld", 0, 15, 0, 100, true, 10).isEmpty());
        d.waypoint(a);
        assertTrue(d.all().getFirst().waypoint());
        d.stale("server-a", "nether");
        assertTrue(d.nearby("server-a", "nether", 0, 15, 0, 100, false, 10).isEmpty());
        assertEquals(1, d.nearby("server-a", "nether", 0, 15, 0, 100, true, 10).size());
        d.observe(a, 789);
        assertFalse(d.all().getFirst().stale());
        assertTrue(d.all().getFirst().waypoint());
        d.remove(a);
        assertTrue(d.all().isEmpty());
    }
    @Test
    void loadedDiscoveriesAreNeverAssumedCurrent() {
        var d = new DebrisDiscoveries();
        var key = new DebrisDiscoveries.Key("a", "nether", 0);
        d.load(List.of(new DebrisDiscoveries.Discovery(key, 100, false, true)));
        assertTrue(d.all().getFirst().stale());
        assertTrue(d.all().getFirst().waypoint());
    }
}
