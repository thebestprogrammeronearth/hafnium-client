package dev.hafnium.client.core.module;

import java.util.Locale;

/** Module categories, in their default left-to-right order. */
public enum Category {
    RENDER("Render"),
    MISC("Misc"),
    WORLD("World"),
    AUTOMATION("Automation"),
    CLIENT("Client");

    private final String displayName;
    private final String headerLabel;

    Category(String displayName) {
        this.displayName = displayName;
        this.headerLabel = displayName.toUpperCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public String headerLabel() {
        return headerLabel;
    }

    public String idPrefix() {
        return name().toLowerCase(Locale.ROOT);
    }
}
