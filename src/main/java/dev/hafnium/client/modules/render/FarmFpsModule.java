/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.modules.render;

import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Farm FPS Optimizer: Anubis's "No Render" (hide selected overlays, screen effects and entity renders, all ten
 * settings unchanged) plus Hafnium's farm culling: dropped items, XP orbs and mobs past a distance are not rendered.
 * Nothing is removed from the world; only drawing is skipped. Pair with Performance for budgeted chunk scanning.
 */
public final class FarmFpsModule extends Module {
    private static volatile FarmFpsModule instance;
    private static long culledTotal;
    // Anubis No Render
    private final BoolSetting sFireOverlay = setting(new BoolSetting("Fire Overlay", true));
    private final BoolSetting sTotemAnimation = setting(new BoolSetting("Totem Animation", true));
    private final BoolSetting sPumpkinPowderSnow = setting(new BoolSetting("Pumpkin & Powder Snow", true));
    private final BoolSetting sNauseaPortal = setting(new BoolSetting("Nausea & Portal", true));
    private final BoolSetting sBlindnessDarkness = setting(new BoolSetting("Blindness & Darkness", true));
    private final BoolSetting sSpawnerMob = setting(new BoolSetting("Spawner Mob", true));
    private final BoolSetting sFallingBlocks = setting(new BoolSetting("Falling Blocks", false));
    private final BoolSetting sWeather = setting(new BoolSetting("Weather", true));
    private final BoolSetting sLavaFog = setting(new BoolSetting("Lava Fog", true));
    private final BoolSetting sEntityFire = setting(new BoolSetting("Entity Fire", false));
    // Hafnium farm culling
    private final BoolSetting sDroppedItems = setting(new BoolSetting("Dropped Items", false));
    private final BoolSetting sXpOrbs = setting(new BoolSetting("XP Orbs", false));
    private final NumberSetting sMobDistance = setting(new NumberSetting("Mob Render Distance", 0, 0, 128, 4, " b"));

    public FarmFpsModule() {
        super("Farm FPS Optimizer", Category.RENDER,
                "Hides overlays, screen effects and farm clutter (items, XP orbs, distant mobs) to keep FPS up around big farms.");
        instance = this;
    }

    private static boolean on(BoolSetting s) {
        FarmFpsModule m = instance;
        return m != null && m.isEnabled() && s.get();
    }

    public static boolean hideFireOverlay() {
        FarmFpsModule m = instance;
        return m != null && on(m.sFireOverlay);
    }

    public static boolean hideTotemAnimation() {
        FarmFpsModule m = instance;
        return m != null && on(m.sTotemAnimation);
    }

    public static boolean hidePumpkinPowderSnow() {
        FarmFpsModule m = instance;
        return m != null && on(m.sPumpkinPowderSnow);
    }

    public static boolean hideNauseaPortal() {
        FarmFpsModule m = instance;
        return m != null && on(m.sNauseaPortal);
    }

    public static boolean hideBlindnessDarkness() {
        FarmFpsModule m = instance;
        return m != null && on(m.sBlindnessDarkness);
    }

    public static boolean hideSpawnerMob() {
        FarmFpsModule m = instance;
        return m != null && on(m.sSpawnerMob);
    }

    public static boolean hideFallingBlocks() {
        FarmFpsModule m = instance;
        return m != null && on(m.sFallingBlocks);
    }

    public static boolean hideWeather() {
        FarmFpsModule m = instance;
        return m != null && on(m.sWeather);
    }

    public static boolean hideLavaFog() {
        FarmFpsModule m = instance;
        return m != null && on(m.sLavaFog);
    }

    public static boolean hideEntityFire() {
        FarmFpsModule m = instance;
        return m != null && on(m.sEntityFire);
    }

    /** True when this entity should not be drawn (farm culling). Called for every entity every frame: keep it cheap. */
    public static boolean cull(Entity entity, double camX, double camY, double camZ) {
        FarmFpsModule m = instance;
        if (m == null || !m.isEnabled()) {
            return false;
        }
        boolean cull;
        if (entity instanceof ItemEntity) {
            cull = m.sDroppedItems.get() || m.beyond(entity, camX, camY, camZ);
        } else if (entity instanceof ExperienceOrb) {
            cull = m.sXpOrbs.get() || m.beyond(entity, camX, camY, camZ);
        } else {
            cull = entity instanceof Mob && m.beyond(entity, camX, camY, camZ);
        }
        if (cull) {
            culledTotal++;
        }
        return cull;
    }

    /** Entities skipped since startup (render thread only; for tests and stats). */
    public static long culledTotal() {
        return culledTotal;
    }

    private boolean beyond(Entity entity, double camX, double camY, double camZ) {
        double d = sMobDistance.get();
        return d > 0 && entity.distanceToSqr(camX, camY, camZ) > d * d;
    }
}
