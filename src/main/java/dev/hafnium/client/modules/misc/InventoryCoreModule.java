package dev.hafnium.client.modules.misc;

import dev.hafnium.client.anubis.chat.ModuleChat;
import dev.hafnium.client.core.event.ClientTickEvent;
import dev.hafnium.client.core.event.GameKeyEvent;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.permission.Autonomy;
import dev.hafnium.client.core.permission.Capability;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.KeybindSetting;
import dev.hafnium.client.core.setting.NumberSetting;
import dev.hafnium.client.core.task.StopReason;
import dev.hafnium.client.core.task.Task;
import dev.hafnium.client.core.task.TaskContext;
import dev.hafnium.client.core.task.TaskException;
import dev.hafnium.client.core.task.TaskHandle;
import dev.hafnium.client.core.task.TaskOptions;
import dev.hafnium.client.core.task.TaskResource;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.hud.LayoutElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * InventoryCore: equipment at a glance (armour, held items and elytra with durability, totem count), alerts before
 * gear breaks, and two actions you trigger yourself: sort the inventory and refill partial hotbar stacks. The actions
 * are assisted tasks, so the permission layer decides where they may run (Server Profile, operating mode).
 */
public final class InventoryCoreModule extends Module {
    private static final EquipmentSlot[] GEAR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private final BoolSetting showPanel = setting(new BoolSetting("Equipment Panel", true));
    private final BoolSetting alerts = setting(new BoolSetting("Durability Alerts", true));
    private final NumberSetting alertAt = setting(new NumberSetting("Alert Below", 10, 1, 50, 1, "%"));
    private final BoolSetting sound = setting(new BoolSetting("Alert Sound", true));
    private final KeybindSetting sortKey = setting(new KeybindSetting("Sort Inventory"));
    private final KeybindSetting refillKey = setting(new KeybindSetting("Refill Hotbar"));
    private final LayoutElement hud = new LayoutElement("inventory-core", "Equipment", () -> isEnabled() && this.showPanel.get(), 1f, 1f, -4f, -16f);
    private final Set<String> alerted = new HashSet<>();
    private TaskHandle running;
    private ModuleContext context;

    public InventoryCoreModule() {
        super("InventoryCore", Category.MISC,
                "Equipment durability panel and alerts, plus Sort Inventory and Refill Hotbar on keys (assisted, permission-checked).");
        alertAt.visibleWhen(alerts::get);
        sound.visibleWhen(alerts::get);
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        context = ctx;
        alerted.clear();
        ctx.subscribe(ClientTickEvent.class, e -> {
            if (e == ClientTickEvent.END) {
                checkDurability();
            }
        });
        ctx.subscribe(GameKeyEvent.class, e -> {
            if (e.key() == KeybindSetting.NONE) {
                return;
            }
            if (e.key() == sortKey.get()) {
                sort();
            } else if (e.key() == refillKey.get()) {
                refill();
            }
        });
        ctx.subscribe(HudRenderEvent.class, this::renderHud);
    }

    @Override
    protected void onDisable() {
        if (running != null && !running.isDone()) {
            running.cancel("InventoryCore disabled");
        }
        context = null;
    }

    // ------------------------------------------------------------------ durability

    private static float durability(ItemStack stack) {
        return stack.isDamageableItem() ? 1f - stack.getDamageValue() / (float) stack.getMaxDamage() : -1f;
    }

    private void checkDurability() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !alerts.get()) {
            return;
        }
        float limit = alertAt.getFloat() / 100f;
        for (EquipmentSlot slot : GEAR) {
            ItemStack stack = player.getItemBySlot(slot);
            float d = durability(stack);
            String key = slot.getName() + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (d >= 0 && d <= limit) {
                if (alerted.add(key)) {
                    ModuleChat.send(name(), ModuleChat.body().name(stack.getHoverName().getString()).text(" is at ")
                            .danger(Math.round(d * 100) + "%").text(" (" + (stack.getMaxDamage() - stack.getDamageValue()) + " uses left)"));
                    if (sound.get()) {
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.6F, 0.4F));
                    }
                }
            } else if (d > limit + 0.05f) {
                alerted.remove(key);
            }
        }
    }

    // ------------------------------------------------------------------ actions

    /** Order the main inventory (not the hotbar) should end up in: by item, then fullest stacks first. */
    static Comparator<ItemStack> order() {
        return Comparator.<ItemStack>comparingInt(s -> s.isEmpty() ? 1 : 0)
                .thenComparingInt(s -> BuiltInRegistries.ITEM.getId(s.getItem()))
                .thenComparing(s -> s.getHoverName().getString())
                .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());
    }

    /** Inventory slot index (0-35) to inventory-menu slot number (container 0). */
    private static int menuSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? 36 + inventoryIndex : inventoryIndex;
    }

    private void start(String what, Task task) {
        if (context == null) {
            return;
        }
        if (running != null && !running.isDone()) {
            running.cancel("Replaced by " + what);
        }
        running = context.submitTask(task, TaskOptions.DEFAULT);
    }

    private abstract static class InventoryTask implements Task {
        @Override
        public Set<TaskResource> resources() {
            return Set.of(TaskResource.INVENTORY);
        }

        @Override
        public Set<Capability> capabilities() {
            return Set.of(Capability.INVENTORY);
        }

        @Override
        public Autonomy autonomy() {
            return Autonomy.ASSISTED;
        }

        static void click(LocalPlayer player, int slot) {
            Minecraft.getInstance().gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, 0, ClickType.PICKUP, player);
        }

        static LocalPlayer player() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gameMode == null) {
                throw new TaskException("Not in a world");
            }
            if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) {
                throw new TaskException("Close the open container first");
            }
            if (!mc.player.inventoryMenu.getCarried().isEmpty()) {
                throw new TaskException("Put down the item on your cursor first");
            }
            return mc.player;
        }
    }

    /** Sorts slots 9-35 with one swap every other tick, re-reading the inventory each time. */
    public void sort() {
        start("sort", new InventoryTask() {
            int swaps;

            @Override
            public String name() {
                return "Sort Inventory";
            }

            @Override
            public boolean tick(TaskContext c) {
                if (c.runningTicks() % 2 != 0) {
                    return false;
                }
                LocalPlayer player = player();
                var inv = player.getInventory();
                List<ItemStack> current = new ArrayList<>();
                for (int i = 9; i < 36; i++) {
                    current.add(inv.getItem(i));
                }
                List<ItemStack> wanted = new ArrayList<>(current);
                wanted.sort(order());
                for (int p = 0; p < wanted.size(); p++) {
                    if (ItemStack.matches(current.get(p), wanted.get(p))) {
                        continue;
                    }
                    int from = -1;
                    for (int j = p + 1; j < current.size(); j++) {
                        if (ItemStack.matches(current.get(j), wanted.get(p))) {
                            from = j;
                            break;
                        }
                    }
                    if (from < 0) {
                        throw new TaskException("Inventory changed while sorting");
                    }
                    int a = menuSlot(9 + from), b = menuSlot(9 + p);
                    click(player, a);
                    click(player, b);
                    if (!player.inventoryMenu.getCarried().isEmpty()) {
                        click(player, a);
                    }
                    swaps++;
                    c.status("Sorting (" + swaps + " moves)");
                    if (swaps > 200) {
                        throw new TaskException("Sort did not settle");
                    }
                    return false;
                }
                ModuleChat.send(InventoryCoreModule.this.name(), ModuleChat.body().good("Sorted").text(" in " + swaps + " moves."));
                return true;
            }

            @Override
            public void stop(TaskContext c, StopReason reason) {
                if (reason != StopReason.SUCCEEDED) {
                    ModuleChat.send(InventoryCoreModule.this.name(), ModuleChat.body().warn("Sort stopped: ").text(String.valueOf(reason)));
                }
            }
        });
    }

    /** Tops up partial hotbar stacks from matching stacks in the main inventory. */
    public void refill() {
        start("refill", new InventoryTask() {
            int moves;

            @Override
            public String name() {
                return "Refill Hotbar";
            }

            @Override
            public boolean tick(TaskContext c) {
                if (c.runningTicks() % 2 != 0) {
                    return false;
                }
                LocalPlayer player = player();
                var inv = player.getInventory();
                for (int h = 0; h < 9; h++) {
                    ItemStack hot = inv.getItem(h);
                    if (hot.isEmpty() || hot.getCount() >= hot.getMaxStackSize()) {
                        continue;
                    }
                    for (int i = 9; i < 36; i++) {
                        if (ItemStack.isSameItemSameComponents(inv.getItem(i), hot)) {
                            click(player, menuSlot(i));
                            click(player, menuSlot(h));
                            if (!player.inventoryMenu.getCarried().isEmpty()) {
                                click(player, menuSlot(i));
                            }
                            moves++;
                            return false;
                        }
                    }
                }
                ModuleChat.send(InventoryCoreModule.this.name(), moves == 0 ? ModuleChat.body().text("Nothing to refill.")
                        : ModuleChat.body().good("Refilled").text(" " + moves + " stack" + (moves == 1 ? "" : "s") + "."));
                return true;
            }
        });
    }

    /** The task currently running (for tests). */
    public TaskHandle running() {
        return running;
    }

    // ------------------------------------------------------------------ HUD

    private void renderHud(HudRenderEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!showPanel.get() || mc.player == null || mc.options.hideGui) {
            return;
        }
        List<ItemStack> shown = new ArrayList<>();
        for (EquipmentSlot slot : GEAR) {
            ItemStack s = mc.player.getItemBySlot(slot);
            if (!s.isEmpty()) {
                shown.add(s);
            }
        }
        int totems = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.is(Items.TOTEM_OF_UNDYING)) {
                totems += s.getCount();
            }
        }
        if (shown.isEmpty() && totems == 0) {
            hud.size(1, 1);
            return;
        }
        float cell = 20f;
        float w = Math.max(1, shown.size()) * cell + (totems > 0 ? 28f : 0f) + 4f, h = 26f;
        ThemeManager.sync();
        float limit = alertAt.getFloat() / 100f;
        List<float[]> icons = new ArrayList<>();
        hud.begin(e.graphics());
        try {
            GuiRenderUtil.roundRect(0, 0, w, h, 3f, ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.75f));
            for (int i = 0; i < shown.size(); i++) {
                ItemStack s = shown.get(i);
                float x = 4f + i * cell;
                float d = durability(s);
                if (d >= 0) {
                    int color = d <= limit ? ThemeManager.DANGER : d < 0.35f ? 0xFFFFC66B : 0xFF7BE0A0;
                    GuiRenderUtil.rect(x, 21f, 16f, 1.5f, ThemeManager.TRACK);
                    GuiRenderUtil.rect(x, 21f, 16f * d, 1.5f, color);
                }
                icons.add(new float[]{x, 3f, i});
            }
            if (totems > 0) {
                float x = 4f + shown.size() * cell;
                icons.add(new float[]{x, 3f, -1});
                GuiRenderUtil.text(FontRole.SMALL, "×" + totems, x + 17f, 12f, ThemeManager.TEXT);
            }
            hud.size(w, h);
        } finally {
            hud.end();
        }
        var g = e.graphics();
        g.pose().pushMatrix();
        g.pose().translate(hud.x(g.guiWidth(), g.guiHeight()), hud.y(g.guiWidth(), g.guiHeight()));
        g.pose().scale(hud.scale(), hud.scale());
        for (float[] icon : icons) {
            ItemStack stack = icon[2] < 0 ? new ItemStack(Items.TOTEM_OF_UNDYING) : shown.get((int) icon[2]);
            g.renderItem(stack, Math.round(icon[0]), Math.round(icon[1]));
        }
        g.pose().popMatrix();
    }
}
