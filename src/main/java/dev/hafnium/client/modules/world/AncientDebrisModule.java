package dev.hafnium.client.modules.world;

import com.google.gson.Gson;
import dev.hafnium.client.config.DeferredJsonFile;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.discovery.DebrisDiscoveries;
import dev.hafnium.client.core.event.*;
import dev.hafnium.client.core.module.*;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.setting.*;
import dev.hafnium.client.core.world.*;
import dev.hafnium.client.event.HudRenderEvent;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

public final class AncientDebrisModule extends Module {
    public final NumberSetting distance = setting(new NumberSetting("Distance", 128, 16, 512, 16));
    public final NumberSetting width = setting(new NumberSetting("Outline width", 2, 1, 5, .5));
    public final ColorSetting color = setting(new ColorSetting("Outline color", 0xFFB77CFF));
    public final BoolSetting tracers = setting(new BoolSetting("Tracers", false));
    public final BoolSetting labels = setting(new BoolSetting("Distances", true));
    public final BoolSetting stale = setting(new BoolSetting("Show stale", false));
    public final BoolSetting notifications = setting(new BoolSetting("Notifications", true));
    public final KeybindSetting waypoint = setting(new KeybindSetting("Toggle nearest waypoint"));
    private final DebrisDiscoveries discoveries = new DebrisDiscoveries();
    private DeferredJsonFile file;
    private String world = "", dimension = "";
    private long nextScan;
    private List<DebrisDiscoveries.Discovery> visible = List.of();
    private String saved = "";
    public AncientDebrisModule() {
        super("Ancient Debris Finder", Category.WORLD,
                "Observed ancient debris only; saved unloaded locations are stale, never predictions.");
    }
    public List<DebrisDiscoveries.Discovery> visible() {
        return visible;
    }
    @Override
    protected void onEnable(ModuleContext ctx) {
        if (file == null) {
            file = new DeferredJsonFile(FabricLoader.getInstance().getConfigDir().resolve("hafnium/debris.json"));
            try {
                String text = file.read();
                if (text != null)
                    discoveries.load(Arrays.asList(new Gson().fromJson(text, DebrisDiscoveries.Discovery[].class)));
            } catch (Exception e) {
                notice("Could not load discoveries: " + e.getMessage());
            }
        }
        nextScan = 0;
        ctx.subscribe(ClientTickEvent.class, e -> {
            if (e == ClientTickEvent.END)
                tick();
        });
        ctx.subscribe(GameKeyEvent.class, e -> {
            if (e.key() == waypoint.get() && !visible.isEmpty()) {
                discoveries.waypoint(visible.getFirst().key());
                nextScan = 0;
                save();
            }
        });
        ctx.subscribe(HudRenderEvent.class, e -> {
            if (!labels.get() || Minecraft.getInstance().options.hideGui || Minecraft.getInstance().player == null)
                return;
            var mc = Minecraft.getInstance();
            // Stack under the Capabilities panel instead of drawing over it.
            int y = Math.max(8, (int) Math.ceil(HafniumCore.get().modules()
                    .get(dev.hafnium.client.modules.client.CapabilitiesModule.class).bottom()) + 8);
            for (var d : visible.stream().limit(6).toList()) {
                double meters =
                        Math.sqrt(Pos.distSq(d.key().pos(), mc.player.getX(), mc.player.getY(), mc.player.getZ()));
                String text = (d.waypoint() ? "★ " : "") + "Debris " + Math.round(meters) + "m · "
                        + Pos.toString(d.key().pos()) + (d.stale() ? " [stale]" : " [observed]");
                e.graphics().fill(5, y - 2, 10 + mc.font.width(text), y + 10, 0xBB15131C);
                e.graphics().drawString(mc.font, text, 8, y, d.stale() ? 0xFF888888 : color.get(), false);
                y += 14;
            }
        });
    }
    private void tick() {
        var core = HafniumCore.get();
        file.tick(core.scheduler());
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            discoveries.stale(world, dimension);
            visible = List.of();
            world = "";
            return;
        }
        long tick = core.scheduler().currentTick();
        String current = mc.getSingleplayerServer() != null
                ? mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString()
                : core.permissions().environment().serverId();
        String dim = mc.level.dimension().identifier().toString();
        if (!current.equals(world) || !dim.equals(dimension)) {
            discoveries.stale(world, dimension);
            visible = List.of();
            nextScan = 0;
        }
        world = current;
        dimension = dim;
        if (tick < nextScan) return;
        nextScan = tick + 10;
        var model = core.world().current();
        if (model == null)
            return;
        discoveries.stale(world, dimension);
        for (var d : discoveries.all())
            if (d.key().world().equals(world) && d.key().dimension().equals(dim)) {
                long p = d.key().pos();
                int x = Pos.x(p), y = Pos.y(p), z = Pos.z(p);
                if (model.isChunkLoaded(x >> 4, z >> 4)) {
                    var b = model.blockAt(x, y, z);
                    if (b != null && b.blockId().equals("minecraft:ancient_debris"))
                        discoveries.observe(d.key(), System.currentTimeMillis());
                    else if (model.isChunkComplete(x >> 4, z >> 4))
                        discoveries.remove(d.key());
                }
            }
        int added = 0;
        for (var b : model.findBlocks(BlockKind.ORE, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                     distance.get(), 100000, false))
            if (b.blockId().equals("minecraft:ancient_debris")
                    && discoveries.observe(new DebrisDiscoveries.Key(world, dim, b.pos()), System.currentTimeMillis()))
                added++;
        if (added > 0 && notifications.get())
            notice("Observed " + added + " new ancient debris block(s).");
        visible = discoveries.nearby(
                world, dim, mc.player.getX(), mc.player.getY(), mc.player.getZ(), distance.get(), stale.get(), 256);
        save();
    }
    private void save() {
        String json = new Gson().toJson(discoveries.all());
        if (!json.equals(saved)) {
            saved = json;
            file.changed(json, HafniumCore.get().scheduler().currentTick());
        }
    }
    private static void notice(String text) {
        var mc = Minecraft.getInstance();
        if (mc.gui != null)
            mc.gui.getChat().addMessage(Component.literal("[Hafnium] " + text));
    }
    @Override
    protected void onDisable() {
        visible = List.of();
        discoveries.stale(world, dimension);
        save();
    }
}
