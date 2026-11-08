package dev.hafnium.client.modules;

import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.ModuleManager;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.ColorSetting;
import dev.hafnium.client.core.setting.ListSetting;
import dev.hafnium.client.core.setting.ModeSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.modules.automation.OperatingModeModule;
import dev.hafnium.client.modules.client.CapabilitiesModule;
import dev.hafnium.client.modules.client.ClickGuiModule;
import dev.hafnium.client.modules.client.ServerProfileModule;
import dev.hafnium.client.modules.client.FontsModule;
import dev.hafnium.client.modules.client.WatermarkModule;
import dev.hafnium.client.modules.world.ObservationModule;

/**
 * Registers the built-in modules. Entries created with {@link #p} are {@link PlaceholderModule}s: names and sample
 * settings only, labelled "not implemented" in the GUI until a later stage implements them.
 */
public final class BuiltinModules {
    private final ModuleManager manager;

    private BuiltinModules(ModuleManager manager) {
        this.manager = manager;
    }

    public static void registerAll(ModuleManager manager) {
        new BuiltinModules(manager).register();
    }

    private static PlaceholderModule p(String name, Category category, String description) {
        return new PlaceholderModule(name, category, description);
    }

    private void add(PlaceholderModule module) {
        manager.register(module);
    }

    private void register() {
        // Combat
        add(p("AutoTotem", Category.COMBAT, "Placeholder: totem helper.")
                .with(new NumberSetting("Health", 6, 1, 20, 0.5, " hp"))
                .with(new ModeSetting("Mode", "Smart", "Smart", "Strict", "Always"))
                .with(new BoolSetting("Offhand Only", true)));
        add(p("AutoCrystal", Category.COMBAT, "Placeholder: crystal helper.")
                .with(new NumberSetting("Range", 4.5, 1, 6, 0.1))
                .with(new NumberSetting("Delay", 2, 0, 20, 1, " t"))
                .with(new ListSetting("Priority", "Closest", "Closest", "Health", "Armor", "Angle"))
                .with(new BoolSetting("Rotate", true))
                .with(new ColorSetting("Render", 0xFFB77CFF)));
        add(p("KillAura", Category.COMBAT, "Placeholder: attack helper.")
                .with(new NumberSetting("Range", 3.2, 1, 6, 0.1))
                .with(new ModeSetting("Target", "Players", "Players", "Mobs", "All"))
                .with(new ListSetting("Sort", "Distance", "Distance", "Health", "Angle"))
                .with(new BoolSetting("Through Walls", false)));
        add(p("AimAssist", Category.COMBAT, "Placeholder: aim helper.")
                .with(new NumberSetting("Speed", 5, 1, 10, 0.5))
                .with(new NumberSetting("FOV", 90, 10, 180, 5, "°"))
                .with(new BoolSetting("Click Only", true)));
        add(p("Surround", Category.COMBAT, "Placeholder: surround helper.")
                .with(new ListSetting("Block", "Obsidian", "Obsidian", "Ender Chest", "Crying Obsidian"))
                .with(new BoolSetting("Center", true))
                .with(new BoolSetting("Toggle on Jump", true)));
        add(p("AntiBot", Category.COMBAT, "Placeholder: bot filter.")
                .with(new ModeSetting("Mode", "Tab", "Tab", "Ping", "Name")));
        add(p("Criticals", Category.COMBAT, "Placeholder: criticals.")
                .with(new ModeSetting("Mode", "Packet", "Packet", "Jump", "Mini Jump")));
        add(p("HitBox", Category.COMBAT, "Placeholder: hitbox visuals.")
                .with(new NumberSetting("Expand", 0.2, 0, 1, 0.05)));
        add(p("ShieldBreaker", Category.COMBAT, "Placeholder: shield helper.")
                .with(new BoolSetting("Swap Back", true)));
        add(p("AnchorMacro", Category.COMBAT, "Placeholder: anchor macro.")
                .with(new NumberSetting("Delay", 1, 0, 10, 1, " t")));
        add(p("MaceSwap", Category.COMBAT, "Placeholder: mace swap.")
                .with(new NumberSetting("Min Fall", 3, 1, 20, 0.5, " m"))
                .with(new BoolSetting("Swap Back", true)));

        // Movement
        add(p("ElytraFly", Category.MOVEMENT, "Placeholder: elytra flight.")
                .with(new ModeSetting("Mode", "Control", "Control", "Boost", "Bounce"))
                .with(new NumberSetting("Speed", 1.8, 0.1, 5, 0.1)));
        add(p("Flight", Category.MOVEMENT, "Placeholder: flight.")
                .with(new ModeSetting("Mode", "Vanilla", "Vanilla", "Creative", "Glide"))
                .with(new NumberSetting("Speed", 1, 0.1, 5, 0.1))
                .with(new BoolSetting("Anti Kick", true)));
        add(p("Speed", Category.MOVEMENT, "Placeholder: speed.")
                .with(new ListSetting("Mode", "Strafe", "Strafe", "OnGround", "Vanilla"))
                .with(new NumberSetting("Multiplier", 1.2, 1, 3, 0.05)));
        add(p("Strafe", Category.MOVEMENT, "Placeholder: strafe."));
        add(p("LongJump", Category.MOVEMENT, "Placeholder: long jump.")
                .with(new NumberSetting("Boost", 1.5, 1, 4, 0.1)));
        add(p("NoSlow", Category.MOVEMENT, "Placeholder: no slowdown.")
                .with(new BoolSetting("Items", true))
                .with(new BoolSetting("Webs", false))
                .with(new BoolSetting("Soul Sand", false)));
        add(p("Jesus", Category.MOVEMENT, "Placeholder: water walking.")
                .with(new ModeSetting("Mode", "Solid", "Solid", "Dolphin")));
        add(p("Step", Category.MOVEMENT, "Placeholder: step.")
                .with(new NumberSetting("Height", 1, 0.5, 2.5, 0.5, " b")));
        add(p("Velocity", Category.MOVEMENT, "Placeholder: knockback.")
                .with(new NumberSetting("Horizontal", 0, 0, 100, 1, "%"))
                .with(new NumberSetting("Vertical", 0, 0, 100, 1, "%")));
        add(p("Sprint", Category.MOVEMENT, "Placeholder: sprint.")
                .with(new ModeSetting("Mode", "Legit", "Legit", "Omni")));

        // Render
        add(p("ESP", Category.RENDER, "Placeholder: entity outlines.")
                .with(new ModeSetting("Mode", "Box", "Box", "Outline", "Glow"))
                .with(new BoolSetting("Players", true))
                .with(new BoolSetting("Mobs", false))
                .with(new ColorSetting("Color", 0xFFB77CFF)));
        add(p("Tracers", Category.RENDER, "Placeholder: tracer lines.")
                .with(new ListSetting("Target", "Chest", "Head", "Chest", "Feet"))
                .with(new ColorSetting("Color", 0xFFC99DFF)));
        add(p("NameTags", Category.RENDER, "Placeholder: name tags.")
                .with(new NumberSetting("Scale", 1, 0.5, 3, 0.1, "x"))
                .with(new BoolSetting("Armor", true))
                .with(new BoolSetting("Health", true)));
        add(p("Chams", Category.RENDER, "Placeholder: chams.")
                .with(new ColorSetting("Visible", 0xFF8F5CFF))
                .with(new ColorSetting("Hidden", 0xFF4A2B80)));
        add(p("StorageESP", Category.RENDER, "Placeholder: container outlines.")
                .with(new ColorSetting("Chests", 0xFFFFB84D)));
        manager.register(new dev.hafnium.client.modules.render.BlockEspModule());
        add(p("Fullbright", Category.RENDER, "Placeholder: brightness.")
                .with(new ModeSetting("Mode", "Gamma", "Gamma", "Potion")));
        add(p("HUD", Category.RENDER, "Placeholder: HUD elements.")
                .with(new BoolSetting("Coordinates", true))
                .with(new BoolSetting("Arraylist", true))
                .with(new BoolSetting("FPS", false)));
        add(p("Animations", Category.RENDER, "Placeholder: hand animations.")
                .with(new NumberSetting("Swing Speed", 6, 1, 20, 1)));
        add(p("Crosshair", Category.RENDER, "Placeholder: crosshair.")
                .with(new NumberSetting("Size", 4, 1, 10, 1, " px"))
                .with(new ColorSetting("Color", 0xFFF3F0F8)));

        // Player
        add(p("AutoEat", Category.PLAYER, "Placeholder: auto eat.")
                .with(new NumberSetting("Hunger", 14, 1, 20, 1)));
        add(p("AutoTool", Category.PLAYER, "Placeholder: tool switch.")
                .with(new BoolSetting("Switch Back", true)));
        add(p("InventoryMove", Category.PLAYER, "Placeholder: inventory movement."));
        add(p("NoFall", Category.PLAYER, "Placeholder: fall damage.")
                .with(new ModeSetting("Mode", "Packet", "Packet", "Bucket")));
        add(p("FastPlace", Category.PLAYER, "Placeholder: place delay.")
                .with(new NumberSetting("Delay", 0, 0, 4, 1, " t")));
        add(p("AntiAFK", Category.PLAYER, "Placeholder: anti AFK.")
                .with(new NumberSetting("Interval", 30, 5, 120, 5, " s")));
        add(p("Respawn", Category.PLAYER, "Placeholder: auto respawn."));
        add(p("Scaffold", Category.PLAYER, "Placeholder: scaffold.")
                .with(new BoolSetting("Tower", false))
                .with(new BoolSetting("Safe Walk", true)));
        add(p("Timer", Category.PLAYER, "Placeholder: tick speed.")
                .with(new NumberSetting("Speed", 1, 0.1, 5, 0.1, "x")));

        manager.register(new dev.hafnium.client.modules.misc.DonutWheelModule());

        manager.register(new dev.hafnium.client.modules.misc.AutoSellModule());

        // Misc
        add(p("MiddleClickPearl", Category.MISC, "Placeholder: pearl on middle click."));
        add(p("AutoReconnect", Category.MISC, "Placeholder: reconnect.")
                .with(new NumberSetting("Delay", 5, 1, 30, 1, " s")));
        add(p("Notifications", Category.MISC, "Placeholder: toast notifications.")
                .with(new ListSetting("Position", "Bottom Right", "Top Right", "Bottom Right", "Top Left"))
                .with(new BoolSetting("Sound", true)));
        add(p("DiscordRPC", Category.MISC, "Placeholder: Discord presence.")
                .with(new BoolSetting("Show Server", false)));
        add(p("Hitsounds", Category.MISC, "Placeholder: hit sounds.")
                .with(new ListSetting("Sound", "Bell", "Bell", "Click", "Pop")));
        add(p("PingSpoof", Category.MISC, "Placeholder: ping display.")
                .with(new NumberSetting("Ping", 100, 0, 1000, 10, " ms")));
        add(p("SafeWalk", Category.MISC, "Placeholder: edge safety."));
        add(p("FakePlayer", Category.MISC, "Placeholder: fake player.")
                .with(new ModeSetting("Skin", "Self", "Self", "Steve", "Alex")));

        // World
        manager.register(new ObservationModule());
        manager.register(new dev.hafnium.client.modules.world.AncientDebrisModule());
        manager.register(new dev.hafnium.client.modules.world.NavigationModule());

        // Automation
        manager.register(new OperatingModeModule());

        manager.register(new dev.hafnium.client.modules.client.SpotifyHudModule());

        // Client
        add(p("HUD", Category.CLIENT, "Client HUD layout (visual only).")
                .with(new ModeSetting("Layout", "Compact", "Compact", "Wide")));
        manager.register(new ClickGuiModule());
        add(p("Config", Category.CLIENT, "Config behaviour (visual only).")
                .with(new BoolSetting("Auto Save", true)));
        add(p("Themes", Category.CLIENT, "Theme presets (visual only).")
                .with(new ListSetting("Theme", "Lavender", "Lavender", "Amethyst", "Orchid", "Midnight")));
        manager.register(new FontsModule());
        add(p("Sounds", Category.CLIENT, "GUI click sounds (visual only).")
                .with(new NumberSetting("Volume", 50, 0, 100, 1, "%")));
        manager.register(new WatermarkModule());
        manager.register(new CapabilitiesModule());
        manager.register(new dev.hafnium.client.modules.client.PerformanceModule());
        manager.register(new ServerProfileModule());
    }
}
