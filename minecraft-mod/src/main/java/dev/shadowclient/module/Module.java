package dev.shadowclient.module;

import dev.shadowclient.module.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ein ein-/ausschaltbares Feature mit Einstellungen. */
public abstract class Module {
    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected Module(String id, String name, String description, Category category, boolean enabledByDefault) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabledByDefault;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public List<Setting<?>> getSettings() { return Collections.unmodifiableList(settings); }
    public boolean isEnabled() { return enabled; }

    /** Module, die immer aktiv sind (z. B. Client-Einstellungen), können nicht ausgeschaltet werden. */
    public boolean isToggleable() { return true; }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
    }

    public void toggle() { setEnabled(!enabled); }

    protected void onEnable() {}
    protected void onDisable() {}

    /** Wird jeden Client-Tick (20x/s) aufgerufen, solange das Modul aktiv ist. */
    public void onTick(net.minecraft.client.MinecraftClient client) {}
}
