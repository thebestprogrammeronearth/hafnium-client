package dev.hafnium.client.modules.misc;
import com.google.gson.Gson;
import dev.hafnium.client.config.DeferredJsonFile;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.event.GameKeyEvent;
import dev.hafnium.client.core.module.*;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.setting.KeybindSetting;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.gui.utility.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
public final class DonutWheelModule extends Module {
    public record Action(String label, String icon, String command, boolean confirm, boolean favorite, String dimension,
            Integer x, Integer y, Integer z) {}
    public final KeybindSetting openKey = setting(new KeybindSetting("Open wheel"));
    private List<Action> actions = new ArrayList<>();
    private DeferredJsonFile file;
    private Action waypoint;
    public DonutWheelModule() {
        super("Donut Wheel", Category.MISC,
                "Custom radial commands and waypoints. Bind Open wheel; right-click a slot to edit.");
    }
    public List<Action> actions() {
        return List.copyOf(actions);
    }
    protected void onEnable(ModuleContext ctx) {
        if (file == null) {
            file = new DeferredJsonFile(FabricLoader.getInstance().getConfigDir().resolve("hafnium/wheel.json"));
            try {
                String json = file.read();
                if (json != null)
                    replace(json);
            } catch (Exception e) {
                Minecraft.getInstance().gui.getChat().addMessage(
                        Component.literal("[Hafnium] Wheel config: " + e.getMessage()));
            }
        }
        ctx.subscribe(GameKeyEvent.class, e -> {
            if (e.key() == openKey.get())
                Minecraft.getInstance().setScreen(new WheelScreen(this));
        });
        ctx.subscribe(HudRenderEvent.class, e -> {
            var mc = Minecraft.getInstance();
            if (waypoint == null || mc.player == null || mc.level == null || mc.options.hideGui)
                return;
            if (!mc.level.dimension().identifier().toString().equals(waypoint.dimension))
                return;
            double d = mc.player.position().distanceTo(
                    new net.minecraft.world.phys.Vec3(waypoint.x + .5, waypoint.y, waypoint.z + .5));
            e.graphics().drawString(mc.font,
                    "★ " + waypoint.label + " · " + Math.round(d) + "m · " + waypoint.x + ", " + waypoint.y + ", "
                            + waypoint.z,
                    8, 150, 0xFFB77CFF, false);
        });
    }
    public String json() {
        return new Gson().toJson(actions);
    }
    public void replace(String json) {
        Action[] parsed = new Gson().fromJson(json, Action[].class);
        if (parsed == null || parsed.length > 64)
            throw new IllegalArgumentException("Use a JSON array with at most 64 actions");
        for (var a : parsed) validate(a);
        actions = new ArrayList<>(Arrays.asList(parsed));
        save();
    }
    public void put(int index, Action action) {
        validate(action);
        if (index == actions.size())
            actions.add(action);
        else
            actions.set(index, action);
        save();
    }
    public void swap(int a, int b) {
        Collections.swap(actions, a, b);
        save();
    }
    public void remove(int index) {
        actions.remove(index);
        save();
    }
    private void save() {
        if (file != null)
            file.changed(json(), HafniumCore.get().scheduler().currentTick());
    }
    private static void validate(Action a) {
        if (a == null || a.label == null || a.label.isBlank() || a.label.length() > 40 || a.icon == null
                || a.icon.length() > 8)
            throw new IllegalArgumentException("Action needs label (1–40 chars) and icon (up to 8 chars)");
        if (a.command == null || a.command.length() > 256 || a.command.contains("\n") || a.command.contains("\r"))
            throw new IllegalArgumentException("Invalid command");
        if (a.command.isBlank() && (a.x == null || a.y == null || a.z == null || a.dimension == null))
            throw new IllegalArgumentException("Supply command or dimension/x/y/z waypoint");
    }
    public void select(Action a) {
        var mc = Minecraft.getInstance();
        if (a.command.isBlank()) {
            waypoint = a;
            mc.setScreen(null);
            return;
        }
        if (a.command.contains("{args}")) {
            mc.setScreen(new TextPromptScreen(null, "Arguments for " + a.label, "", args -> {
                if (args.contains("\n") || args.contains("\r"))
                    throw new IllegalArgumentException("Single-line arguments only");
                send(a, a.command.replace("{args}", args));
            }));
            return;
        }
        send(a, a.command);
    }
    private void send(Action a, String command) {
        var mc = Minecraft.getInstance();
        if (command.length() > 256)
            throw new IllegalArgumentException("Command exceeds 256 characters");
        Runnable execute = () -> {
            if (mc.getConnection() != null)
                mc.getConnection().sendCommand(command.startsWith("/") ? command.substring(1) : command);
            mc.setScreen(null);
        };
        if (a.confirm)
            mc.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
                if (yes)
                    execute.run();
                else
                    mc.setScreen(null);
            }, Component.literal("Run " + a.label + "?"), Component.literal(command)));
        else
            execute.run();
    }
}
