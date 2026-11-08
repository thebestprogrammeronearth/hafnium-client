package dev.hafnium.client.core.economy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
class SaleProfileTest {
    @Test
    void explicitItemQuantityAndBlacklist() {
        var p = new SaleProfile("/market sell {item} {quantity}", Set.of("minecraft:stone", "minecraft:diamond"),
                Set.of("minecraft:diamond"), Map.of("minecraft:stone", 2d), 64);
        assertEquals("market sell minecraft:stone 32", p.render("minecraft:stone", 32));
        assertThrows(IllegalArgumentException.class, () -> p.render("minecraft:diamond", 1));
        assertThrows(IllegalArgumentException.class, () -> p.render("minecraft:stone", 65));
        assertThrows(IllegalArgumentException.class, () -> p.render("minecraft:stone", 0));
    }
    @Test
    void rejectsInventoryWideOrMalformedTemplates() {
        assertThrows(
                IllegalArgumentException.class, () -> new SaleProfile("sell all", Set.of(), Set.of(), Map.of(), 64));
        assertThrows(IllegalArgumentException.class,
                () -> new SaleProfile("sell {item} {quantity}\nother", Set.of(), Set.of(), Map.of(), 64));
        assertThrows(IllegalArgumentException.class,
                () -> new SaleProfile("sell {item} {quantity}", Set.of(), Set.of(), Map.of("a", Double.NaN), 64));
    }
}
