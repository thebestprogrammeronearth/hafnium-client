/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
package dev.hafnium.client.modules.world;

import dev.hafnium.client.anubis.chat.ModuleChat;
import dev.hafnium.client.anubis.esp.BlockLayer;
import dev.hafnium.client.anubis.esp.ChunkScanner;
import dev.hafnium.client.anubis.esp.EspStyle;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.ColorSetting;
import dev.hafnium.client.event.WorldRenderEvent;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.LongPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Scans loaded chunks for mob spawners, announces each one once and highlights them. */
public final class SpawnerNotifierModule extends Module implements ChunkScanner.Consumer {
    public static final SoundEvent NOTIFY_SOUND =
            SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("hafnium", "spawner_notify"));
    private static final String SPAWNER = "Spawner";
    private final BoolSetting sNotifications = setting(new BoolSetting("Notifications", true));
    private final BoolSetting sSound = setting(new BoolSetting("Sound", true));
    private final BoolSetting sRender = setting(new BoolSetting("Render", true));
    private final ColorSetting sColor = setting(new ColorSetting("Color", 0xDCFF5555));
    private final EspStyle style = new EspStyle(this::setting, EspStyle.OUTLINE, 0.1F, 256.0F);
    private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
        @Override
        public int color(Object kind) {
            return sColor.argb();
        }

        @Override
        public int tracerColor(Object kind) {
            return 0;
        }
    }, () -> Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension());
    private final LongOpenHashSet notified = new LongOpenHashSet();
    private volatile String lastNotice = "";

    public SpawnerNotifierModule() {
        super("Spawner Notifier", Category.WORLD, "Scans chunks for mob spawners and highlights them.");
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        ChunkScanner.register(this);
        ctx.subscribe(WorldRenderEvent.class, this::onRender3D);
    }

    @Override
    protected void onDisable() {
        ChunkScanner.unregister(this);
        layer.clear();
        notified.clear();
    }

    /** Spawners announced so far (for tests). */
    public int notifiedCount() {
        return notified.size();
    }

    /** Text of the last announcement (for tests). */
    public String lastNotice() {
        return lastNotice;
    }

    public int trackedBlocks() {
        return layer.size();
    }

    @Override
    public boolean wants(BlockState state) {
        return state.is(Blocks.SPAWNER);
    }

    @Override
    public void found(long pos, BlockState state) {
        layer.put(pos, SPAWNER);
        if (notified.add(pos)) {
            notify(BlockPos.of(pos));
        }
    }

    @Override
    public void removed(long pos) {
        layer.remove(pos);
    }

    @Override
    public void reset() {
        layer.clear();
        notified.clear();
    }

    @Override
    public void chunkScanned(long chunkKey, LongPredicate stillThere) {
        layer.retainChunk(chunkKey, stillThere);
    }

    private void notify(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        ModuleChat.Body body = foundMessage(mc, pos);
        lastNotice = body.build().getString();
        if (sNotifications.get()) {
            ModuleChat.send(name(), body);
        }
        if (sSound.get() && mc.player != null && mc.level != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(NOTIFY_SOUND, 1.0F, 1.0F));
        }
    }

    private static ModuleChat.Body foundMessage(Minecraft mc, BlockPos pos) {
        String mob = entityName(mc, pos);
        ModuleChat.Body body = mob == null ? ModuleChat.body().warn("Unknown") : ModuleChat.body().name(mob);
        body.text(" spawner ").good("found").text(" at ").coords(pos.getX(), pos.getY(), pos.getZ());
        if (mc.player != null) {
            long distance = Math.round(mc.player.position().distanceTo(Vec3.atCenterOf(pos)));
            body.text(" (").value(distance + "m").text(")");
        }
        return body;
    }

    private static String entityName(Minecraft mc, BlockPos pos) {
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            Entity entity = spawner.getSpawner().getOrCreateDisplayEntity(mc.level, pos);
            return entity == null ? null : entity.getType().getDescription().getString();
        }
        return null;
    }

    private void onRender3D(WorldRenderEvent event) {
        if (sRender.get()) {
            layer.render(event, style);
        }
    }
}
