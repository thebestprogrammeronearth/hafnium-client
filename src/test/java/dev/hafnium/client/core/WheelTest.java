package dev.hafnium.client.core;
import static org.junit.jupiter.api.Assertions.*;

import dev.hafnium.client.modules.misc.DonutWheelModule;
import org.junit.jupiter.api.Test;
class WheelTest {
    @Test
    void importExportReorderAndFavorites() {
        var w = new DonutWheelModule();
        w.replace(
                "[{\"label\":\"Home\",\"icon\":\"H\",\"command\":\"home {args}\",\"confirm\":true,\"favorite\":true}]");
        w.put(1, new DonutWheelModule.Action("Waypoint", "W", "", false, false, "minecraft:the_nether", 10, 20, 30));
        w.swap(0, 1);
        var copy = new DonutWheelModule();
        copy.replace(w.json());
        assertEquals(w.actions(), copy.actions());
        assertTrue(copy.actions().get(1).favorite());
        copy.remove(0);
        assertEquals(1, copy.actions().size());
    }
    @Test
    void malformedActionsRejectedWithoutChangingExistingConfiguration() {
        var w = new DonutWheelModule();
        assertThrows(IllegalArgumentException.class, () -> w.replace("[{}]"));
        assertTrue(w.actions().isEmpty());
    }
}
