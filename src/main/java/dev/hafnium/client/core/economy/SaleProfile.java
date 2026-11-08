package dev.hafnium.client.core.economy;
import java.util.*;
public record SaleProfile(
        String command, Set<String> whitelist, Set<String> blacklist, Map<String, Double> prices, int quantity) {
    public SaleProfile {
        Objects.requireNonNull(command);
        whitelist = Set.copyOf(whitelist);
        blacklist = Set.copyOf(blacklist);
        prices = Map.copyOf(prices);
        if (!command.contains("{item}") || !command.contains("{quantity}") || command.contains("\n")
                || command.contains("\r") || command.length() > 200 || quantity < 1 || quantity > 2304)
            throw new IllegalArgumentException("Command requires {item} and {quantity}; quantity 1–2304");
        for (String item : whitelist)
            if (!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Use full item IDs, e.g. minecraft:cobblestone");
        for (double p : prices.values())
            if (!Double.isFinite(p) || p < 0)
                throw new IllegalArgumentException("Prices must be finite and nonnegative");
    }
    public boolean permits(String item) {
        return whitelist.contains(item) && !blacklist.contains(item);
    }
    public String render(String item, int count) {
        if (!permits(item) || count < 1 || count > quantity)
            throw new IllegalArgumentException("Sale outside configured limits");
        String s = command.replace("{item}", item).replace("{quantity}", Integer.toString(count));
        if (s.startsWith("/"))
            s = s.substring(1);
        if (s.length() > 256)
            throw new IllegalArgumentException("Command too long");
        return s;
    }
}
