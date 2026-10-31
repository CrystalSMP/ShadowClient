package dev.shadowclient.module.setting;

import com.google.gson.JsonElement;

/** Basis für alle Modul-Einstellungen. Werte werden als JSON gespeichert. */
public abstract class Setting<T> {
    private final String id;
    private final String name;
    protected T value;
    private final T defaultValue;

    protected Setting(String id, String name, T defaultValue) {
        this.id = id;
        this.name = name;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public T get() { return value; }
    public void set(T value) { this.value = value; }
    public void reset() { this.value = defaultValue; }

    public abstract JsonElement toJson();
    public abstract void fromJson(JsonElement json);
}
