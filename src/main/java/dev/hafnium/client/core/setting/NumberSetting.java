package dev.hafnium.client.core.setting;

import java.util.Locale;

/** A number clamped to [min, max] and snapped to {@code step}. */
public final class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;
    private final String suffix;
    private final String format;
    private String cachedText;
    private double cachedFor = Double.NaN;

    public NumberSetting(String name, double defaultValue, double min, double max, double step, String suffix) {
        super(name, defaultValue);
        if (!(min < max) || step <= 0 || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("Invalid range for setting '" + name + "': default " + defaultValue
                    + ", min " + min + ", max " + max + ", step " + step);
        }
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
        int decimals = step >= 1 ? 0 : step >= 0.1 ? 1 : 2;
        this.format = "%." + decimals + "f";
    }

    public NumberSetting(String name, double defaultValue, double min, double max, double step) {
        this(name, defaultValue, min, max, step, "");
    }

    public float getFloat() {
        return get().floatValue();
    }

    public int getInt() {
        return (int) Math.round(get());
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    @Override
    protected Double sanitize(Double v) {
        if (v.isNaN()) {
            throw new IllegalArgumentException("NaN is not a valid value for '" + name() + "'");
        }
        double snapped = Math.round((v - min) / step) * step + min;
        // Round away floating point noise from the step arithmetic (e.g. 0.30000000000000004).
        snapped = Math.round(snapped * 1e9) / 1e9;
        return Math.max(min, Math.min(max, snapped));
    }

    /** 0..1 position of the current value along the slider. */
    public double fraction() {
        return (value - min) / (max - min);
    }

    public void setFraction(double f) {
        set(min + Math.max(0, Math.min(1, f)) * (max - min));
    }

    /** Formatted value, only re-formatted when the value changes. */
    public String text() {
        if (cachedFor != value) {
            cachedFor = value;
            cachedText = String.format(Locale.ROOT, format, value) + suffix;
        }
        return cachedText;
    }
}
