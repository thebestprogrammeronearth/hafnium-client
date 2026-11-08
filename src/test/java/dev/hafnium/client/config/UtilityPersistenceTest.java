package dev.hafnium.client.config;
import static org.junit.jupiter.api.Assertions.*;

import dev.hafnium.client.core.*;
import dev.hafnium.client.core.event.EventBus;
import dev.hafnium.client.core.task.TaskScheduler;
import dev.hafnium.client.modules.BuiltinModules;
import dev.hafnium.client.modules.client.SpotifyHudModule;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class UtilityPersistenceTest {
    /** Module settings include block lists, which read Minecraft's block registry. */
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void oldBackgroundWriteCannotOverwriteShutdownFlush(@TempDir Path dir) throws Exception {
        List<Runnable> workers = new ArrayList<>();
        var scheduler = new TaskScheduler(new EventBus(), ClientThread.DIRECT, workers::add);
        var file = new DeferredJsonFile(dir.resolve("data.json"));
        file.changed("old", 0);
        for (int i = 0; i < 21; i++) scheduler.tick();
        file.tick(scheduler);
        scheduler.tick();
        assertEquals(1, workers.size());
        file.changed("new", scheduler.currentTick());
        DeferredJsonFile.flushAll();
        workers.getFirst().run();
        scheduler.tick();
        assertEquals("new", Files.readString(dir.resolve("data.json")));
    }
    @Test
    void moduleValuesAndNamedKeybindsRoundTripWithoutAutoEnable(@TempDir Path dir) {
        var a = new HafniumCore(ClientThread.DIRECT, dev.hafnium.client.core.world.WorldModelLimits.DEFAULT);
        BuiltinModules.registerAll(a.modules());
        new ModuleSettingsStore(a, dir.resolve("settings.json")).attach();
        var module = a.modules().get(SpotifyHudModule.class);
        module.scale.set(1.7);
        module.edit.bind(75, "K");
        module.compact.set(true);
        DeferredJsonFile.flushAll();
        a.shutdown();
        var b = new HafniumCore(ClientThread.DIRECT, dev.hafnium.client.core.world.WorldModelLimits.DEFAULT);
        BuiltinModules.registerAll(b.modules());
        new ModuleSettingsStore(b, dir.resolve("settings.json")).attach();
        var restored = b.modules().get(SpotifyHudModule.class);
        assertEquals(1.7, restored.scale.get(), .001);
        assertEquals(75, restored.edit.get());
        assertEquals("K", restored.edit.keyName());
        assertTrue(restored.compact.get());
        assertFalse(restored.isEnabled());
        b.shutdown();
    }
}
