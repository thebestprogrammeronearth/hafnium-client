package dev.hafnium.client.modules.misc;

import dev.hafnium.client.anubis.setting.StringListSetting;
import dev.hafnium.client.core.event.ClientTickEvent;
import dev.hafnium.client.core.event.WorldEvent;
import dev.hafnium.client.core.module.Category;
import dev.hafnium.client.core.module.Module;
import dev.hafnium.client.core.module.ModuleContext;
import dev.hafnium.client.core.setting.ActionSetting;
import dev.hafnium.client.core.setting.BoolSetting;
import dev.hafnium.client.core.setting.StringSetting;
import dev.hafnium.client.event.HudRenderEvent;
import dev.hafnium.client.gui.render.FontRole;
import dev.hafnium.client.gui.render.GuiRenderUtil;
import dev.hafnium.client.gui.theme.ThemeManager;
import dev.hafnium.client.hud.LayoutElement;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Economy Assistant: what your inventory is worth (your own price list, shulker contents included), your balance as
 * the server shows it on the sidebar, and what you made this session: profit, profit per hour, and the income and
 * spending the server announces in chat. Prices also appear in item tooltips. Read-only: it never buys or sells.
 */
public final class EconomyAssistantModule extends Module {
    private static final Pattern NUMBER = Pattern.compile("(-?[\\d][\\d,]*(?:\\.\\d+)?)\\s*([kKmMbBtT])?");

    private final StringListSetting prices = setting(new StringListSetting("Prices", "diamond 100", "netherite_ingot 2500",
            "elytra 25000", "totem_of_undying 5000", "spawner 150000").hint("One per line: item price, e.g. \"diamond 120\" or \"minecraft:beacon 1.5k\"."));
    private final BoolSetting tooltips = setting(new BoolSetting("Tooltip Prices", true));
    private final BoolSetting countShulkers = setting(new BoolSetting("Count Shulker Contents", true));
    private final StringSetting balanceKeyword = setting(new StringSetting("Balance Line", "Money"));
    private final StringSetting incomePattern = setting(new StringSetting("Income Pattern",
            "(?i)(?:\\+\\s*\\$|received\\s+\\$|earned\\s+\\$|sold\\b.*?\\$)\\s*([\\d,.]+\\s*[kmbt]?)"));
    private final StringSetting spendPattern = setting(new StringSetting("Spend Pattern",
            "(?i)(?:-\\s*\\$|paid\\s+\\$|spent\\s+\\$|bought\\b.*?\\$)\\s*([\\d,.]+\\s*[kmbt]?)"));
    private final ActionSetting resetSession = setting(new ActionSetting("Reset Session", this::resetSession));
    private final LayoutElement hud = new LayoutElement("economy", "Economy Assistant", this::isEnabled, 0f, 0.5f, 4f, 0f);

    private static boolean hooksInstalled;
    private static volatile EconomyAssistantModule active;

    private Map<Item, Double> priceTable;
    private Pattern income, spend;
    private String incomeSource = "", spendSource = "";
    private double inventoryValue;
    private Double balance, startBalance;
    private double earned, spent;
    private long sessionStart;
    private int ticks;

    public EconomyAssistantModule() {
        super("Economy Assistant", Category.MISC,
                "Inventory value from your price list, balance and session profit from the sidebar, income and spending from chat.");
        prices.onChange(() -> priceTable = null);
    }

    @Override
    protected void onEnable(ModuleContext ctx) {
        active = this;
        installHooks();
        resetSession();
        ctx.subscribe(ClientTickEvent.class, e -> {
            if (e == ClientTickEvent.END && ++ticks % 10 == 0) {
                refresh();
            }
        });
        ctx.subscribe(WorldEvent.class, e -> {
            if (e == WorldEvent.JOIN) {
                resetSession();
            }
        });
        ctx.subscribe(HudRenderEvent.class, this::renderHud);
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    private static void installHooks() {
        if (hooksInstalled) {
            return;
        }
        hooksInstalled = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            EconomyAssistantModule m = active;
            if (m != null && m.isEnabled()) {
                m.onMessage(message.getString());
            }
        });
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            EconomyAssistantModule m = active;
            if (m != null && m.isEnabled() && m.tooltips.get()) {
                double each = m.worth(stack) / Math.max(1, stack.getCount());
                if (each > 0) {
                    lines.add(Component.literal("Worth " + money(each) + (stack.getCount() > 1 ? " · stack " + money(each * stack.getCount()) : ""))
                            .withStyle(ChatFormatting.GOLD));
                }
            }
        });
    }

    private void resetSession() {
        earned = 0;
        spent = 0;
        startBalance = null;
        balance = null;
        sessionStart = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ parsing

    /** "1,234.5" -> 1234.5, "1.5k" -> 1500, "2M" -> 2000000. Returns NaN when there is no number. */
    public static double parseAmount(String text) {
        Matcher m = NUMBER.matcher(text);
        if (!m.find()) {
            return Double.NaN;
        }
        double v;
        try {
            v = Double.parseDouble(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
        String unit = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
        return switch (unit) {
            case "k" -> v * 1e3;
            case "m" -> v * 1e6;
            case "b" -> v * 1e9;
            case "t" -> v * 1e12;
            default -> v;
        };
    }

    public static String money(double v) {
        double a = Math.abs(v);
        String s = a >= 1e9 ? String.format(Locale.ROOT, "%.2fB", a / 1e9) : a >= 1e6 ? String.format(Locale.ROOT, "%.2fM", a / 1e6)
                : a >= 1e4 ? String.format(Locale.ROOT, "%.1fk", a / 1e3) : String.format(Locale.ROOT, "%,.0f", a);
        return (v < 0 ? "-$" : "$") + s;
    }

    private Map<Item, Double> prices() {
        if (priceTable == null) {
            Map<Item, Double> table = new HashMap<>();
            for (String line : prices.values()) {
                String[] parts = line.trim().split("\\s+", 2);
                if (parts.length < 2) {
                    continue;
                }
                Identifier id = Identifier.tryParse(parts[0].contains(":") ? parts[0] : "minecraft:" + parts[0]);
                double price = parseAmount(parts[1]);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id) && !Double.isNaN(price)) {
                    table.put(BuiltInRegistries.ITEM.getValue(id), price);
                }
            }
            priceTable = table;
        }
        return priceTable;
    }

    /** Worth of a stack, including the contents of a shulker box (or any container item) when enabled. */
    public double worth(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        double value = prices().getOrDefault(stack.getItem(), 0.0) * stack.getCount();
        if (countShulkers.get()) {
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents != null) {
                for (ItemStack inner : contents.nonEmptyItems()) {
                    value += worth(inner);
                }
            }
        }
        return value;
    }

    private Pattern compile(String source, boolean incomeKind) {
        try {
            if (incomeKind) {
                if (!source.equals(incomeSource)) {
                    incomeSource = source;
                    income = source.isBlank() ? null : Pattern.compile(source);
                }
                return income;
            }
            if (!source.equals(spendSource)) {
                spendSource = source;
                spend = source.isBlank() ? null : Pattern.compile(source);
            }
            return spend;
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    /** A system/server chat line: counts announced income and spending. */
    public void onMessage(String text) {
        Pattern in = compile(incomePattern.get(), true);
        Pattern out = compile(spendPattern.get(), false);
        Matcher m;
        if (in != null && (m = in.matcher(text)).find()) {
            double v = parseAmount(m.groupCount() >= 1 && m.group(1) != null ? m.group(1) : m.group());
            if (!Double.isNaN(v)) {
                earned += v;
            }
        } else if (out != null && (m = out.matcher(text)).find()) {
            double v = parseAmount(m.groupCount() >= 1 && m.group(1) != null ? m.group(1) : m.group());
            if (!Double.isNaN(v)) {
                spent += v;
            }
        }
    }

    /** The first sidebar line containing the Balance Line keyword, as a number (null when there is none). */
    private Double readBalance(Minecraft mc) {
        if (mc.level == null || balanceKeyword.get().isBlank()) {
            return null;
        }
        Scoreboard board = mc.level.getScoreboard();
        Objective sidebar = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return null;
        }
        String keyword = balanceKeyword.get().toLowerCase(Locale.ROOT);
        for (PlayerScoreEntry entry : board.listPlayerScores(sidebar)) {
            if (entry.isHidden()) {
                continue;
            }
            PlayerTeam team = board.getPlayersTeam(entry.owner());
            String line = PlayerTeam.formatNameForTeam(team, entry.display() != null ? entry.display() : Component.literal(entry.owner())).getString();
            int at = line.toLowerCase(Locale.ROOT).indexOf(keyword);
            if (at >= 0) {
                double v = parseAmount(line.substring(at + keyword.length()));
                if (!Double.isNaN(v)) {
                    return v;
                }
            }
        }
        return null;
    }

    private void refresh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Inventory inv = mc.player.getInventory();
        double total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            total += worth(inv.getItem(i));
        }
        inventoryValue = total;
        Double now = readBalance(mc);
        if (now != null) {
            balance = now;
            if (startBalance == null) {
                startBalance = now;
            }
        }
    }

    /** {inventory value, balance or NaN, session profit (balance change) or NaN, earned, spent} (for tests). */
    public double[] numbers() {
        return new double[]{inventoryValue, balance == null ? Double.NaN : balance,
                balance == null || startBalance == null ? Double.NaN : balance - startBalance, earned, spent};
    }

    private void renderHud(HudRenderEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }
        double hours = Math.max(1.0 / 60.0, (System.currentTimeMillis() - sessionStart) / 3_600_000.0);
        double profit = balance != null && startBalance != null ? balance - startBalance : earned - spent;
        List<String[]> rows = new java.util.ArrayList<>();
        rows.add(new String[]{"Inventory", money(inventoryValue)});
        if (balance != null) {
            rows.add(new String[]{"Balance", money(balance)});
        }
        rows.add(new String[]{"Session", (profit >= 0 ? "+" : "") + money(profit) + "  ·  " + money(profit / hours) + "/h"});
        if (earned > 0 || spent > 0) {
            rows.add(new String[]{"Chat", "+" + money(earned) + "  −" + money(spent).replace("-", "")});
        }
        float labelW = 0, valueW = 0;
        for (String[] r : rows) {
            labelW = Math.max(labelW, GuiRenderUtil.textWidth(FontRole.SMALL, r[0]));
            valueW = Math.max(valueW, GuiRenderUtil.textWidth(FontRole.SMALL, r[1]));
        }
        float w = labelW + valueW + 20f, h = 6f + rows.size() * 10f + 4f;
        ThemeManager.sync();
        hud.begin(e.graphics());
        try {
            GuiRenderUtil.roundRect(0, 0, w, h, 3f, ThemeManager.withAlpha(ThemeManager.BACKGROUND, 0.82f));
            GuiRenderUtil.roundOutline(0, 0, w, h, 3f, ThemeManager.withAlpha(ThemeManager.BORDER, 0.6f));
            float y = 10f;
            for (String[] r : rows) {
                GuiRenderUtil.text(FontRole.SMALL, r[0], 6f, y, ThemeManager.TEXT_SECONDARY);
                int color = r[0].equals("Session") ? (profit >= 0 ? 0xFF7BE0A0 : ThemeManager.DANGER) : ThemeManager.TEXT;
                GuiRenderUtil.text(FontRole.SMALL, r[1], 14f + labelW, y, color);
                y += 10f;
            }
            hud.size(w, h);
        } finally {
            hud.end();
        }
    }
}
