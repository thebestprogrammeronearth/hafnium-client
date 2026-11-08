package dev.hafnium.client.modules;

import dev.hafnium.client.core.module.ModuleManager;
import dev.hafnium.client.modules.automation.OperatingModeModule;
import dev.hafnium.client.modules.client.CapabilitiesModule;
import dev.hafnium.client.modules.client.ClickGuiModule;
import dev.hafnium.client.modules.client.FontsModule;
import dev.hafnium.client.modules.client.PerformanceModule;
import dev.hafnium.client.modules.client.ServerProfileModule;
import dev.hafnium.client.modules.client.SpotifyHudModule;
import dev.hafnium.client.modules.client.WatermarkModule;
import dev.hafnium.client.modules.misc.AutoSellModule;
import dev.hafnium.client.modules.misc.DonutWheelModule;
import dev.hafnium.client.modules.render.BlockEspModule;
import dev.hafnium.client.modules.world.AncientDebrisModule;
import dev.hafnium.client.modules.world.NavigationModule;
import dev.hafnium.client.modules.world.ObservationModule;

/**
 * Registers the built-in modules. Only modules that work are registered: Hafnium ships its own feature set
 * (see the roadmap in the README), not a list of generic client modules.
 */
public final class BuiltinModules {
    private BuiltinModules() {
    }

    public static void registerAll(ModuleManager manager) {
        // Render
        manager.register(new BlockEspModule());
        manager.register(new dev.hafnium.client.modules.render.StorageEspModule());
        manager.register(new dev.hafnium.client.modules.render.SpawnerNametagsModule());
        manager.register(new dev.hafnium.client.modules.render.FarmFpsModule());
        manager.register(new dev.hafnium.client.modules.render.SchematicPreviewModule());

        // Misc
        manager.register(new DonutWheelModule());
        manager.register(new AutoSellModule());
        manager.register(new dev.hafnium.client.modules.misc.EconomyAssistantModule());
        manager.register(new dev.hafnium.client.modules.misc.InventoryCoreModule());

        // World
        manager.register(new ObservationModule());
        manager.register(new AncientDebrisModule());
        manager.register(new NavigationModule());
        manager.register(new dev.hafnium.client.modules.world.SpawnerNotifierModule());
        manager.register(new dev.hafnium.client.modules.world.BaseFinderModule());
        manager.register(new dev.hafnium.client.modules.world.WorldScannerModule());
        manager.register(new dev.hafnium.client.modules.world.WaypointsModule());
        manager.register(new dev.hafnium.client.modules.world.ElytraFinderModule());

        // Automation
        manager.register(new OperatingModeModule());
        manager.register(new dev.hafnium.client.modules.automation.AutoBaseHuntModule());

        // Client
        manager.register(new SpotifyHudModule());
        manager.register(new ClickGuiModule());
        manager.register(new FontsModule());
        manager.register(new WatermarkModule());
        manager.register(new CapabilitiesModule());
        manager.register(new PerformanceModule());
        manager.register(new dev.hafnium.client.modules.client.HudEditorModule());
        manager.register(new dev.hafnium.client.modules.client.RegionMapModule());
        manager.register(new ServerProfileModule());
    }
}
