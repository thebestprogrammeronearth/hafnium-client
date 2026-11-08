package dev.hafnium.client.core.setting;

/** Free text (for example a world seed). {@link #privateValue()} hides the text in the GUI until the field is focused. */
public final class StringSetting extends Setting<String> {
    private final int maxLength;
    private boolean privateValue;

    public StringSetting(String name, String defaultValue) {
        this(name, defaultValue, 256);
    }

    public StringSetting(String name, String defaultValue, int maxLength) {
        super(name, defaultValue);
        this.maxLength = maxLength;
    }

    public int maxLength() {
        return maxLength;
    }

    public StringSetting privateValue() {
        privateValue = true;
        return this;
    }

    public boolean isPrivate() {
        return privateValue;
    }
}
