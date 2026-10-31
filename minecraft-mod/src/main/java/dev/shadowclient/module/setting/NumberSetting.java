package dev.shadowclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Zahl mit Bereich und Schrittweite (wird als Schieberegler angezeigt). */
public class NumberSetting extends Setting<Double> {
    private final double min, max, step;

    public NumberSetting(String id, String name, double defaultValue, double min, double max, double step) {
        super(id, name, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }

    @Override public void set(Double v) {
        double clamped = Math.max(min, Math.min(max, v));
        value = Math.round(clamped / step) * step;
    }

    /** 0..1 → Wert */
    public void setFromFraction(double f) { set(min + (max - min) * f); }
    public double getFraction() { return (value - min) / (max - min); }

    public String format() {
        return step >= 1 ? String.valueOf((int) Math.round(value)) : String.format("%.2f", value);
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsDouble());
    }
}
