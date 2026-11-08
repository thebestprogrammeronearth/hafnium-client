package dev.hafnium.client.modules.misc;
import com.google.gson.*;
import dev.hafnium.client.config.DeferredJsonFile;
import dev.hafnium.client.core.HafniumCore;
import dev.hafnium.client.core.economy.SaleProfile;
import dev.hafnium.client.core.event.GameKeyEvent;
import dev.hafnium.client.core.module.*;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.permission.*;
import dev.hafnium.client.core.setting.*;
import dev.hafnium.client.core.task.*;
import dev.hafnium.client.gui.utility.TextPromptScreen;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
public final class AutoSellModule extends Module {
    public final KeybindSetting configure = setting(new KeybindSetting("Configure server"));
    public final KeybindSetting sell = setting(new KeybindSetting("Preview manual sale"));
    public final KeybindSetting automate = setting(new KeybindSetting("Start or stop automation"));
    public final KeybindSetting historyKey = setting(new KeybindSetting("Transaction history"));
    public final NumberSetting interval = setting(new NumberSetting("Interval", 30, 2, 3600, 1, " s"));
    private final Map<String, SaleProfile> profiles = new HashMap<>();
    private final List<String> history = new ArrayList<>();
    private DeferredJsonFile file;
    private TaskHandle active;
    private record Candidate(String item, int count, boolean protectedItem) {}
    public AutoSellModule() {
        super("Auto Sell", Category.MISC,
                "Item-and-quantity selling. Configure each server, preview manually or explicitly start permitted "
                + "automation.");
    }
    private String server() {
        return HafniumCore.get().permissions().environment().serverId();
    }
    protected void onEnable(ModuleContext ctx) {
        if (file == null) {
            file = new DeferredJsonFile(FabricLoader.getInstance().getConfigDir().resolve("hafnium/sales.json"));
            try {
                String s = file.read();
                if (s != null) {
                    var root = JsonParser.parseString(s).getAsJsonObject();
                    for (var e : root.getAsJsonObject("profiles").entrySet())
                        profiles.put(e.getKey(), new Gson().fromJson(e.getValue(), SaleProfile.class));
                    if (root.has("history"))
                        for (var h : root.getAsJsonArray("history")) history.add(h.getAsString());
                }
            } catch (Exception e) {
                notice("Could not load sales: " + e.getMessage());
            }
        }
        ctx.subscribe(GameKeyEvent.class, e -> {
            try {
                if (e.key() == configure.get())
                    edit();
                else if (e.key() == historyKey.get())
                    Minecraft.getInstance().setScreen(new TextPromptScreen(null,
                            "Sale submissions / observed changes (not receipts)", new Gson().toJson(history), s -> {}));
                else if (e.key() == sell.get())
                    manual();
                else if (e.key() == automate.get()) {
                    if (active != null && !active.isDone()) {
                        active.cancel("Stopped by player");
                        notice("Auto Sell stopped");
                    } else {
                        String server = server();
                        SaleProfile p = profile();
                        active = ctx.submitTask(new Task() {
                            long due;
                            Candidate pending;
                            int before;
                            public String name() {
                                return "Auto Sell";
                            }
                            public Set<TaskResource> resources() {
                                return Set.of(TaskResource.INVENTORY, TaskResource.CHAT);
                            }
                            public Set<Capability> capabilities() {
                                return Set.of(Capability.INVENTORY);
                            }
                            public boolean tick(TaskContext c) {
                                var mc = Minecraft.getInstance();
                                if (mc.player == null || mc.getConnection() == null || !server.equals(server()))
                                    throw new TaskException("Disconnected or server changed; restart manually");
                                if (c.currentTick() < due)
                                    return false;
                                if (pending != null) {
                                    int remaining = count(pending.item);
                                    if (remaining >= before) {
                                        record
                                        ("No inventory decrease after selling " + pending.item
                                                + "; automation stopped");
                                        throw new TaskException(
                                                "Sale not observed; check server command and permissions");
                                    }
                                    record
                                    ("Observed inventory decrease: " + pending.item + " ×" + (before - remaining)
                                            + " (not a confirmed payment)");
                                    pending = null;
                                    due = c.currentTick() + (long) (interval.get() * 20);
                                    return false;
                                }
                                if (mc.screen != null)
                                    return false;
                                Candidate candidate = candidate(p, false);
                                if (candidate == null) {
                                    c.status("Waiting for eligible unprotected items");
                                    due = c.currentTick() + 20;
                                    return false;
                                }
                                before = count(candidate.item);
                                submit(server, p, candidate, false);
                                pending = candidate;
                                due = c.currentTick() + 40;
                                c.status("Waiting for inventory synchronization");
                                return false;
                            }
                            public void stop(TaskContext c, StopReason reason) {
                                notice("Auto Sell stopped: " + reason);
                            }
                        }, TaskOptions.DEFAULT);
                        notice("Auto Sell requested; authorization is checked by the scheduler");
                    }
                }
            } catch (RuntimeException failure) {
                notice(failure.getMessage());
            }
        });
    }
    public void configureForServer(String server, SaleProfile profile) {
        java.util.Objects.requireNonNull(profile);
        if (active != null && !active.isDone())
            active.cancel("Sell profile changed");
        profiles.put(server, profile);
        save();
    }
    public java.util.List<String> transactions() {
        return java.util.List.copyOf(history);
    }
    private SaleProfile profile() {
        var p = profiles.get(server());
        if (p == null)
            throw new IllegalStateException("Configure a sell profile for this server first");
        return p;
    }
    private void edit() {
        String server = server();
        SaleProfile p = profiles.get(server);
        String json = p == null ? "{\"command\":\"\",\"whitelist\":[],\"blacklist\":[],\"prices\":{},\"quantity\":64}"
                                : new Gson().toJson(p);
        Minecraft.getInstance().setScreen(new TextPromptScreen(null, "Server sell profile: " + server, json, s -> {
            var parsed = new Gson().fromJson(s, SaleProfile.class);
            if (parsed == null)
                throw new IllegalArgumentException("Profile required");
            configureForServer(server, parsed);
        }));
    }
    private Candidate candidate(SaleProfile p, boolean includeProtected) {
        var mc = Minecraft.getInstance();
        if (mc.player == null)
            return null;
        for (String item : new TreeSet<>(p.whitelist())) {
            if (!p.permits(item))
                continue;
            int count = 0;
            boolean protectedItem = false;
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                var stack = inv.getItem(i);
                if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item)) {
                    count += stack.getCount();
                    protectedItem |= stack.isDamageableItem() || stack.has(DataComponents.CUSTOM_NAME)
                            || !stack.getEnchantments().isEmpty() || stack.has(DataComponents.CONTAINER)
                            || stack.has(DataComponents.BUNDLE_CONTENTS) || stack.has(DataComponents.CUSTOM_DATA);
                }
            }
            if (count > 0 && (!protectedItem || includeProtected))
                return new Candidate(item, Math.min(count, p.quantity()), protectedItem);
        }
        return null;
    }
    private int count(String item) {
        var mc = Minecraft.getInstance();
        if (mc.player == null)
            return 0;
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(item))
                n += s.getCount();
        }
        return n;
    }
    private void manual() {
        var p = profile();
        var c = candidate(p, true);
        if (c == null) {
            notice("No whitelisted items available");
            return;
        }
        String server = server();
        Double price = p.prices().get(c.item);
        Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                yes
                -> {
                    Minecraft.getInstance().setScreen(null);
                    if (yes)
                        try {
                            submit(server, p, c, true);
                        } catch (RuntimeException e) {
                            notice(e.getMessage());
                        }
                },
                Component.literal(c.protectedItem ? "Confirm sale of PROTECTED items" : "Confirm sale"),
                Component.literal(c.item + " ×" + c.count
                        + (price == null ? " · price unknown" : " · estimated " + price * c.count) + " · "
                        + p.render(c.item, c.count))));
    }
    private boolean protectedNow(String item) {
        var inv = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(item)
                    && (s.isDamageableItem() || s.has(DataComponents.CUSTOM_NAME) || !s.getEnchantments().isEmpty()
                            || s.has(DataComponents.CONTAINER) || s.has(DataComponents.BUNDLE_CONTENTS)
                            || s.has(DataComponents.CUSTOM_DATA)))
                return true;
        }
        return false;
    }
    private void submit(String server, SaleProfile p, Candidate c, boolean confirmed) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || !server.equals(server()))
            throw new IllegalStateException("Connection changed; sale cancelled");
        if (count(c.item) < c.count)
            throw new IllegalStateException("Inventory changed; preview again");
        if (protectedNow(c.item) && !c.protectedItem)
            throw new IllegalStateException("Protected items appeared; preview again");
        if (c.protectedItem && !confirmed)
            throw new IllegalStateException("Protected item requires confirmation");
        mc.getConnection().sendCommand(p.render(c.item, c.count));
        record
        ("Submitted " + server + ": " + c.item + " ×" + c.count + "; payment unconfirmed");
    }
    private void record(String text) {
        history.add(java.time.Instant.now() + " " + text);
        while (history.size() > 200) history.removeFirst();
        save();
        notice(text);
    }
    private void save() {
        JsonObject root = new JsonObject();
        root.add("profiles", new Gson().toJsonTree(profiles));
        root.add("history", new Gson().toJsonTree(history));
        file.changed(root.toString(), HafniumCore.get().scheduler().currentTick());
    }
    private static void notice(String text) {
        var mc = Minecraft.getInstance();
        if (mc.gui != null)
            mc.gui.getChat().addMessage(Component.literal("[Hafnium] " + text));
    }
}
