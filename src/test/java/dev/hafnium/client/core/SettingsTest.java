package dev.hafnium.client.core;

import dev.hafnium.client.core.setting.ColorSetting;
import dev.hafnium.client.core.setting.KeybindSetting;
import dev.hafnium.client.core.setting.ListSetting;
import dev.hafnium.client.core.setting.ModeSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.core.setting.Setting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @Test
    void idsAreDerivedFromNames() {
        assertEquals("anti-kick", Setting.toId("Anti Kick"));
        assertEquals("fov", Setting.toId(" FOV "));
        assertThrows(IllegalArgumentException.class, () -> Setting.toId("  -- "));
    }

    @Test
    void numbersAreClampedAndSnapped() {
        NumberSetting n = new NumberSetting("Range", 4.5, 1, 6, 0.1);
        n.set(9.0);
        assertEquals(6.0, n.get());
        n.set(-3.0);
        assertEquals(1.0, n.get());
        n.set(2.34);
        assertEquals(2.3, n.get(), 1e-12);
        n.setFraction(0.5);
        assertEquals(3.5, n.get(), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> n.set(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new NumberSetting("Bad", 10, 1, 6, 0.1));
    }

    @Test
    void listenersFireOnlyOnRealChanges() {
        NumberSetting n = new NumberSetting("Delay", 2, 0, 20, 1);
        List<Double> seen = new ArrayList<>();
        Runnable remove = n.addListener(seen::add);
        n.set(2.0);
        n.set(5.0);
        n.set(5.2); // snaps to 5, no change
        remove.run();
        n.set(7.0);
        assertEquals(List.of(5.0), seen);
    }

    @Test
    void modesAndListsRejectUnknownValues() {
        ModeSetting mode = new ModeSetting("Mode", "A", "A", "B", "C");
        mode.cycle(-1);
        assertEquals("C", mode.get());
        assertThrows(IllegalArgumentException.class, () -> mode.set("Z"));
        assertThrows(IllegalArgumentException.class, () -> new ModeSetting("Mode", "Z", "A"));
        ListSetting list = new ListSetting("Sort", "x", "x", "y");
        assertThrows(IllegalArgumentException.class, () -> list.set("q"));
    }

    @Test
    void colourNotifiesAndRoundTrips() {
        ColorSetting c = new ColorSetting("Accent", 0xFFB77CFF);
        assertEquals("#B77CFF", c.hex());
        List<Integer> seen = new ArrayList<>();
        c.addListener(seen::add);
        c.setHsv(0f, 1f, 1f);
        assertEquals(0xFFFF0000, c.get());
        assertEquals(1, seen.size());
    }

    @Test
    void keybindStoresNameAndClears() {
        KeybindSetting k = new KeybindSetting("Bind");
        List<Integer> seen = new ArrayList<>();
        k.addListener(v -> seen.add(v));
        k.bind(75, "K");
        assertEquals("K", k.keyName());
        k.clear();
        assertEquals(KeybindSetting.NONE, k.get());
        assertEquals("None", k.keyName());
        assertEquals(List.of(75, -1), seen);
    }
}
