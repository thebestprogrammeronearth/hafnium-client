/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.modules.render;

import dev.hafnium.client.anubis.esp.ChunkScanner;
import dev.hafnium.client.anubis.gui.WorldLabel;
import dev.hafnium.client.anubis.gui.WorldText;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.event.WorldRenderEvent;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.mixin.anubis.AccessorBaseSpawner;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Shows each spawner's mob and activation range over the block, plus a ring at the activation radius. Anubis draws the
 * labels with ImGui; Hafnium draws the same labels with its own text renderer.
 */
public final class SpawnerNametagsModule extends Module implements ChunkScanner.Consumer {
    private static final int MAX_LABELS = 48;
    private static final int RING_SEGMENTS = 64;
    private static final float RING_WIDTH = 1.5F;
    private static final float RING_ALPHA_INSIDE = 0.8F;
    private static final float RING_ALPHA_OUTSIDE = 0.4F;
    private static final double LABEL_HEIGHT = 1.2;
    private static final double FADE_SHARE = 0.125;
    private static final String SEPARATOR = "  ·  ";
    private final NumberSetting sRange = setting(new NumberSetting("Range", 64.0, 8.0, 256.0, 1.0));
    private final BoolSetting sRadius = setting(new BoolSetting("Activation Radius", true));
    private final LongOpenHashSet spawners = new LongOpenHashSet();
    private int lastLabels;

    public SpawnerNametagsModule() {
        super("Spawner Nametags", Category.RENDER, "Shows each spawner's mob and activation range over the block.");
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        ChunkScanner.register(this);
        ctx.subscribe(WorldRenderEvent.class, this::onRender3D);
        ctx.subscribe(HudRenderEvent.class, this::renderHud);
    }

    @Override
    protected void onDisable() {
        ChunkScanner.unregister(this);
        spawners.clear();
    }

    /** Spawners known / labels drawn last frame (for tests). */
    public int[] counts() {
        return new int[]{spawners.size(), lastLabels};
    }

    public static String mobName(SpawnerBlockEntity spawner) {
        SpawnData data = ((AccessorBaseSpawner) spawner.getSpawner()).hafnium$nextSpawnData();
        if (data == null) {
            return null;
        }
        String id = data.entityToSpawn().getStringOr("id", "");
        return id.isEmpty() ? null : EntityType.byString(id).map(type -> type.getDescription().getString()).orElse(null);
    }

    public static int activationRange(SpawnerBlockEntity spawner) {
        return ((AccessorBaseSpawner) spawner.getSpawner()).hafnium$requiredPlayerRange();
    }

    @Override
    public boolean wants(BlockState state) {
        return state.is(Blocks.SPAWNER);
    }

    @Override
    public void found(long pos, BlockState state) {
        spawners.add(pos);
    }

    @Override
    public void removed(long pos) {
        spawners.remove(pos);
    }

    @Override
    public void reset() {
        spawners.clear();
    }

    @Override
    public void chunkScanned(long chunkKey, LongPredicate stillThere) {
        LongIterator it = spawners.iterator();
        while (it.hasNext()) {
            long pos = it.nextLong();
            if (ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4) == chunkKey && !stillThere.test(pos)) {
                it.remove();
            }
        }
    }

    private List<Shown> inRange(Minecraft mc) {
        List<Shown> shown = new ArrayList<>();
        if (spawners.isEmpty() || mc.level == null || mc.player == null) {
            return shown;
        }
        Level level = mc.level;
        Vec3 player = mc.player.position();
        double range = sRange.get();
        LongIterator it = spawners.iterator();
        while (it.hasNext()) {
            BlockPos pos = BlockPos.of(it.nextLong());
            double distanceSq = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distanceSq <= range * range && level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
                int activation = activationRange(spawner);
                boolean inside = activation < 0 || distanceSq < (double) activation * activation;
                shown.add(new Shown(pos, Math.sqrt(distanceSq), mobName(spawner), activation, inside));
            }
        }
        shown.sort(Comparator.comparingDouble(Shown::distance));
        return shown.size() > MAX_LABELS ? shown.subList(0, MAX_LABELS) : shown;
    }

    private float fade(double distance) {
        double range = sRange.get();
        return (float) Math.max(0.0, Math.min(1.0, (range - distance) / (range * FADE_SHARE)));
    }

    private void renderHud(HudRenderEvent e) {
        Minecraft mc = Minecraft.getInstance();
        lastLabels = 0;
        if (mc.options.hideGui || spawners.isEmpty()) {
            return;
        }
        List<Shown> shown = inRange(mc);
        if (shown.isEmpty()) {
            return;
        }
        float screenW = e.graphics().guiWidth();
        float screenH = e.graphics().guiHeight();
        int accent = WorldText.hudAccent();
        int scale = Math.max(1, mc.getWindow().getGuiScale());
        GuiRenderUtil.begin(e.graphics(), scale, scale);
        try {
            for (int i = shown.size() - 1; i >= 0; i--) {
                Shown spawner = shown.get(i);
                float[] screen = labelAnchor(mc, spawner.pos(), screenW, screenH);
                if (screen == null) {
                    continue;
                }
                String name = spawner.mob() == null ? "Spawner" : spawner.mob() + " Spawner";
                String distance = Math.round(spawner.distance()) + "m";
                String[] runs;
                int[] colors;
                if (sRadius.get() && spawner.range() > 0) {
                    runs = new String[]{distance, SEPARATOR, spawner.range() + "m range"};
                    colors = new int[]{WorldText.LABEL, WorldText.LABEL, spawner.inside() ? accent : WorldText.LABEL};
                } else {
                    runs = new String[]{distance};
                    colors = new int[]{WorldText.LABEL};
                }
                WorldText.draw(screen[0], screen[1], name, runs, colors, fade(spawner.distance()));
                lastLabels++;
            }
        } finally {
            GuiRenderUtil.end();
        }
    }

    public static float[] labelAnchor(Minecraft mc, BlockPos pos, float screenW, float screenH) {
        float[] anchor = WorldLabel.project(mc, new Vec3(pos.getX() + 0.5, pos.getY() + LABEL_HEIGHT, pos.getZ() + 0.5), screenW, screenH);
        if (anchor == null) {
            return null;
        }
        for (int corner = 0; corner < 4; corner++) {
            float[] top = WorldLabel.project(mc, new Vec3(pos.getX() + (corner & 1), pos.getY() + 1.0, pos.getZ() + (corner >> 1)), screenW, screenH);
            if (top != null) {
                anchor[1] = Math.min(anchor[1], top[1]);
            }
        }
        return anchor;
    }

    private void onRender3D(WorldRenderEvent event) {
        if (!sRadius.get()) {
            return;
        }
        List<Shown> shown = inRange(Minecraft.getInstance());
        int accent = WorldText.hudAccent() & 0xFFFFFF;
        for (Shown spawner : shown) {
            if (spawner.range() > 0) {
                float alpha = (spawner.inside() ? RING_ALPHA_INSIDE : RING_ALPHA_OUTSIDE) * fade(spawner.distance());
                int color = Math.round(alpha * 255.0F) << 24 | accent;
                if (color >>> 24 >= 4) {
                    drawRing(Vec3.atCenterOf(spawner.pos()), spawner.range(), color);
                }
            }
        }
    }

    private static void drawRing(Vec3 centre, double radius, int color) {
        Vec3 previous = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = (Math.PI * 2) * i / RING_SEGMENTS;
            Vec3 point = centre.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            if (previous != null) {
                Gizmos.line(previous, point, color, RING_WIDTH).setAlwaysOnTop();
            }
            previous = point;
        }
    }

    private record Shown(BlockPos pos, double distance, String mob, int range, boolean inside) {
    }
}
