package dev.hafnium.client.core;

import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Implementation;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleManager;
import dev.hafnium.client.modules.BuiltinModules;
import dev.hafnium.client.modules.client.ClickGuiModule;
import dev.hafnium.client.modules.client.FontsModule;
import dev.hafnium.client.modules.client.WatermarkModule;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinModulesTest {
    @Test
    void builtinsRegisterWithUniqueIdsAndHonestStatus() {
        ModuleManager manager = new ModuleManager(new EventBus(), ClientThread.DIRECT, new dev.hafnium.client.core.task.TaskScheduler(new EventBus(), ClientThread.DIRECT, Runnable::run));
        BuiltinModules.registerAll(manager);
        Set<String> ids = new HashSet<>();
        for (Module m : manager.all()) {
            assertTrue(ids.add(m.id()), "duplicate id " + m.id());
        }
        assertEquals(Implementation.IMPLEMENTED, manager.get(ClickGuiModule.class).implementation());
        assertEquals(Implementation.IMPLEMENTED, manager.get(FontsModule.class).implementation());
        assertEquals(Implementation.IMPLEMENTED, manager.get(WatermarkModule.class).implementation());
        for (Module m : manager.all()) {
            assertEquals(Implementation.IMPLEMENTED, m.implementation(), m.name() + " is a placeholder");
        }
        Set<String> names = new java.util.TreeSet<>();
        manager.all().forEach(m -> names.add(m.name()));
        assertEquals(new java.util.TreeSet<>(Set.of(
                "ClickGUI", "Fonts", "Watermark", "Capabilities", "Server Profile", "Performance", "SpotifyHUD",
                "Donut Wheel", "Auto Sell", "Observation", "Ancient Debris Finder", "Navigation", "Operating Mode",
                "Block ESP", "Storage ESP", "Spawner Nametags", "Spawner Notifier", "Farm FPS Optimizer", "Base Finder", "WorldScanner", "Waypoints", "Region Map", "HUD Editor", "Elytra Finder", "Schematic Preview", "Economy Assistant", "InventoryCore", "Auto Base Hunt")), names);
        assertNotNull(manager.find("render.block-esp"));
        assertEquals(8, manager.byCategory(Category.WORLD).size());
        assertEquals(2, manager.byCategory(Category.AUTOMATION).size());
    }
}
