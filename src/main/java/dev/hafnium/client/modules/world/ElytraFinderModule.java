package dev.hafnium.client.modules.world;

import dev.hafnium.client.anubis.chat.ModuleChat;
import dev.hafnium.client.anubis.esp.BlockLayer;
import dev.hafnium.client.anubis.esp.ChunkScanner;
import dev.hafnium.client.anubis.esp.EspStyle;
import dev.hafnium.client.anubis.setting.StringListSetting;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.event.ClientTickEvent;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.setting.ActionSetting;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.ColorSetting;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.event.WorldRenderEvent;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.hud.LayoutElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Elytra Finder: finds end ships in the End from what your client has loaded. Every end ship carries a dragon head on
 * its bow, and its elytra hangs in an item frame inside; the module checks that frame to tell ships that still have
 * an elytra from looted ones, highlights them, and plans the shortest round past the unlooted ones (nearest
 * neighbour from where you are). "Send to Waypoints" adds them to the Waypoints module.
 */
public final class ElytraFinderModule extends Module implements ChunkScanner.Consumer {
    public enum Status { ELYTRA, LOOTED, UNKNOWN }

    /** A ship is identified by its dragon head; frames within this distance belong to it. */
    private static final double FRAME_RADIUS = 20.0;

    private final BoolSetting endOnly = setting(new BoolSetting("End Only", true));
    private final BoolSetting notifications = setting(new BoolSetting("Notifications", true));
    private final BoolSetting sound = setting(new BoolSetting("Sound", true));
    private final BoolSetting showLooted = setting(new BoolSetting("Show Looted", false));
    private final BoolSetting tracers = setting(new BoolSetting("Tracers", true));
    private final ColorSetting elytraColor = setting(new ColorSetting("Elytra Color", 0xFF7BE0A0));
    private final ColorSetting unknownColor = setting(new ColorSetting("Unchecked Color", 0xFFFFC66B));
    private final ColorSetting lootedColor = setting(new ColorSetting("Looted Color", 0xFF8C8C99));
    private final EspStyle style = new EspStyle(this::setting, EspStyle.BOXES, 0.25F, 1024.0F);
    private final ActionSetting toWaypoints = setting(new ActionSetting("Send to Waypoints", this::sendToWaypoints));
    private final Map<Long, Ship> ships = new LinkedHashMap<>();
    private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
        @Override
        public int color(Object kind) {
            return switch ((Status) kind) {
                case ELYTRA -> elytraColor.argb();
                case UNKNOWN -> unknownColor.argb();
                case LOOTED -> lootedColor.argb();
            };
        }

        @Override
        public int tracerColor(Object kind) {
            return kind == Status.ELYTRA && tracers.get() ? elytraColor.argb() : 0;
        }
    }, () -> Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension());
    private final LayoutElement hud = new LayoutElement("elytra-finder", "Elytra Finder", () -> isEnabled() && this.inEnd(), 1f, 0.5f, -4f, 0f);
    private int ticks;

    private static final class Ship {
        final BlockPos head;
        Status status = Status.UNKNOWN;
        boolean announced;

        Ship(BlockPos head) {
            this.head = head;
        }
    }

    public record Planned(BlockPos pos, Status status, double distance) {
    }

    public ElytraFinderModule() {
        super("Elytra Finder", Category.WORLD,
                "Finds end ships in the End, tells you which still have their elytra, and plans the shortest round past them.");
        showLooted.onChange(this::relayer);
    }

    /** In the End, or anywhere when "End Only" is off (servers with End-like custom dimensions). */
    private boolean inEnd() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && (mc.level.dimension() == Level.END || !endOnly.get());
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        ships.clear();
        layer.clear();
        ChunkScanner.register(this);
        ctx.subscribe(ClientTickEvent.class, e -> {
            if (e == ClientTickEvent.END && ++ticks % 10 == 0) {
                checkFrames();
            }
        });
        ctx.subscribe(WorldRenderEvent.class, e -> {
            layer.render(e, style);
            if (tracers.get()) {
                layer.renderTracers(style);
            }
        });
        ctx.subscribe(HudRenderEvent.class, this::renderHud);
    }

    @Override
    protected void onDisable() {
        ChunkScanner.unregister(this);
        ships.clear();
        layer.clear();
    }

    // ------------------------------------------------------------------ detection

    @Override
    public boolean wants(BlockState state) {
        return state.is(Blocks.DRAGON_WALL_HEAD) || state.is(Blocks.DRAGON_HEAD);
    }

    @Override
    public void found(long pos, BlockState state) {
        if (!inEnd()) {
            return;
        }
        ships.computeIfAbsent(pos, p -> new Ship(BlockPos.of(p)));
        relayer();
    }

    @Override
    public void removed(long pos) {
        // a broken bow does not unmake the ship; keep it until the world changes
    }

    @Override
    public void reset() {
        ships.clear();
        layer.clear();
    }

    /** Matches loaded item frames to ships: a frame holding an elytra means unlooted, an empty one means looted. */
    private void checkFrames() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ships.isEmpty() || !inEnd()) {
            return;
        }
        boolean changed = false;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemFrame frame)) {
                continue;
            }
            Ship ship = nearestShip(frame.position());
            if (ship == null) {
                continue;
            }
            boolean elytra = frame.getItem().is(Items.ELYTRA);
            Status next = elytra ? Status.ELYTRA : frame.getItem().isEmpty() ? Status.LOOTED : ship.status;
            if (next != ship.status) {
                ship.status = next;
                changed = true;
                if (next == Status.ELYTRA && !ship.announced) {
                    ship.announced = true;
                    announce(mc, ship);
                }
            }
        }
        if (changed) {
            relayer();
        }
    }

    private Ship nearestShip(Vec3 at) {
        Ship best = null;
        double bestSq = FRAME_RADIUS * FRAME_RADIUS;
        for (Ship ship : ships.values()) {
            double d = at.distanceToSqr(Vec3.atCenterOf(ship.head));
            if (d < bestSq) {
                bestSq = d;
                best = ship;
            }
        }
        return best;
    }

    private void announce(Minecraft mc, Ship ship) {
        if (notifications.get()) {
            ModuleChat.Body body = ModuleChat.body().good("Elytra").text(" in an end ship at ")
                    .coords(ship.head.getX(), ship.head.getY(), ship.head.getZ());
            if (mc.player != null) {
                body.text(" (").value(Math.round(mc.player.position().distanceTo(Vec3.atCenterOf(ship.head))) + "m").text(")");
            }
            ModuleChat.send(name(), body);
        }
        if (sound.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4F, 0.6F));
        }
    }

    private void relayer() {
        layer.clear();
        if (!isEnabled()) {
            return;
        }
        for (Ship ship : ships.values()) {
            if (ship.status != Status.LOOTED || showLooted.get()) {
                layer.put(ship.head.asLong(), ship.status);
            }
        }
    }

    // ------------------------------------------------------------------ planning

    /** Unlooted ships (elytra seen, or not checked yet) in visiting order: nearest first, then nearest to the last. */
    public List<Planned> route() {
        Minecraft mc = Minecraft.getInstance();
        List<Ship> left = new ArrayList<>();
        for (Ship ship : ships.values()) {
            if (ship.status != Status.LOOTED) {
                left.add(ship);
            }
        }
        List<Planned> out = new ArrayList<>();
        Vec3 from = mc.player == null ? Vec3.ZERO : mc.player.position();
        while (!left.isEmpty()) {
            Vec3 origin = from;
            Ship next = left.stream().min(Comparator.comparingDouble(s -> origin.distanceToSqr(Vec3.atCenterOf(s.head)))).orElseThrow();
            out.add(new Planned(next.head, next.status, from.distanceTo(Vec3.atCenterOf(next.head))));
            from = Vec3.atCenterOf(next.head);
            left.remove(next);
        }
        return out;
    }

    public Map<BlockPos, Status> ships() {
        Map<BlockPos, Status> out = new LinkedHashMap<>();
        ships.values().forEach(s -> out.put(s.head, s.status));
        return out;
    }

    private void sendToWaypoints() {
        WaypointsModule waypoints = HafniumCore.get().modules().get(WaypointsModule.class);
        StringListSetting list = (StringListSetting) waypoints.setting("waypoints");
        int added = 0;
        int n = 1;
        for (Planned p : route()) {
            String entry = "End Ship " + n++ + " " + p.pos().getX() + " " + p.pos().getY() + " " + p.pos().getZ() + " end";
            if (!list.values().contains(entry) && list.addValue(entry)) {
                added++;
            }
        }
        ModuleChat.send(name(), added == 0 ? ModuleChat.body().warn("No new unlooted ships to add.")
                : ModuleChat.body().good("Added ").value(added).text(" end ships to Waypoints."));
    }

    // ------------------------------------------------------------------ HUD

    private static String heading(Vec3 from, BlockPos to) {
        double dx = to.getX() + 0.5 - from.x, dz = to.getZ() + 0.5 - from.z;
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        return names[Math.floorMod((int) Math.round(angle / 45.0), 8)];
    }

    private void renderHud(HudRenderEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!inEnd() || mc.player == null || mc.options.hideGui) {
            return;
        }
        List<Planned> route = route();
        long looted = ships.values().stream().filter(s -> s.status == Status.LOOTED).count();
        List<String> lines = new ArrayList<>();
        double total = 0;
        for (int i = 0; i < route.size(); i++) {
            Planned p = route.get(i);
            total += p.distance();
            if (i < 5) {
                lines.add((i + 1) + ". " + (p.status() == Status.ELYTRA ? "Elytra" : "Unchecked") + "  " + Math.round(p.distance()) + "m  "
                        + heading(i == 0 ? mc.player.position() : Vec3.atCenterOf(route.get(i - 1).pos()), p.pos())
                        + "  " + p.pos().getX() + ", " + p.pos().getZ());
            }
        }
        String head = route.isEmpty() ? (ships.isEmpty() ? "No end ships seen yet" : "All seen ships looted")
                : route.size() + " to visit · round "
                        + (total >= 1000 ? String.format(Locale.ROOT, "%.1fk", total / 1000.0) : String.valueOf(Math.round(total))) + " blocks";
        String foot = ships.size() + " ships seen · " + looted + " looted";
        float w = GuiRenderUtil.textWidth(FontRole.SMALL, head);
        for (String l : lines) {
            w = Math.max(w, GuiRenderUtil.textWidth(FontRole.SMALL, l));
        }
        w = Math.max(w, GuiRenderUtil.textWidth(FontRole.TINY, foot)) + 12f;
        float h = 26f + lines.size() * 10f + 8f;
        ThemeManager.sync();
        hud.begin(e.graphics());
        try {
            GuiRenderUtil.roundRect(0, 0, w, h, 3f, ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.82f));
            GuiRenderUtil.roundOutline(0, 0, w, h, 3f, ThemeManager.withAlpha(ThemeManager.BORDER, 0.6f));
            GuiRenderUtil.text(FontRole.SMALL, "Elytra Finder", 6f, 7f, ThemeManager.accentHover());
            GuiRenderUtil.text(FontRole.SMALL, head, 6f, 17f, ThemeManager.TEXT);
            float y = 28f;
            for (int i = 0; i < lines.size(); i++) {
                int color = route.get(i).status() == Status.ELYTRA ? elytraColor.argb() : unknownColor.argb();
                GuiRenderUtil.text(FontRole.SMALL, lines.get(i), 6f, y, color);
                y += 10f;
            }
            GuiRenderUtil.text(FontRole.TINY, foot, 6f, y + 1f, ThemeManager.TEXT_SECONDARY);
            hud.size(w, h);
        } finally {
            hud.end();
        }
    }
}
