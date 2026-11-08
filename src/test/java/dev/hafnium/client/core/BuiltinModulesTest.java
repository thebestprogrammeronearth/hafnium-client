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
        assertNotNull(manager.find("render.hud"));
        assertNotNull(manager.find("client.hud"));
        assertEquals(Implementation.IMPLEMENTED, manager.get(ClickGuiModule.class).implementation());
        assertEquals(Implementation.IMPLEMENTED, manager.get(FontsModule.class).implementation());
        assertEquals(Implementation.IMPLEMENTED, manager.get(WatermarkModule.class).implementation());
        long implemented = manager.all().stream().filter(m -> m.implementation() == Implementation.IMPLEMENTED).count();
        assertEquals(14, implemented,
                "only ClickGUI, Fonts, Watermark, Capabilities, Server Profile, Performance, SpotifyHUD, Donut Wheel, Auto Sell, Observation, Ancient Debris Finder, Navigation, Block ESP and Operating Mode are implemented");
        assertNotNull(manager.find("render.block-esp"), "Block ESP replaced the BlockESP placeholder");
        assertEquals(3, manager.byCategory(Category.WORLD).size());
        assertEquals(1, manager.byCategory(Category.AUTOMATION).size());
    }
}
