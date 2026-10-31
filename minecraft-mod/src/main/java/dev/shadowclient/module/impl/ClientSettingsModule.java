package dev.shadowclient.module.impl;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.setting.BooleanSetting;
import dev.shadowclient.module.setting.NumberSetting;

/** Darstellung des Menüs: Akzentfarbe, eigene GUI-Skalierung, Animationen. */
public class ClientSettingsModule extends Module {
    /** Gleiche Vorschläge wie im Launcher. */
    private static final int[] ACCENTS = {0xFF22D3EE, 0xFF3B82F6, 0xFFA855F7, 0xFF22C55E, 0xFFF97316, 0xFFEF4444};
    private static final String[] ACCENT_NAMES = {"Cyan", "Blau", "Lila", "Grün", "Orange", "Rot"};

    public final NumberSetting accent = add(new NumberSetting("accent", "Akzentfarbe", 0, 0, ACCENTS.length - 1, 1));
    public final NumberSetting guiScale = add(new NumberSetting("gui_scale", "Menü-Skalierung", 1.0, 0.5, 1.5, 0.05));
    public final BooleanSetting animations = add(new BooleanSetting("animations", "Animationen", true));
    public final BooleanSetting hideHudInDebug = add(new BooleanSetting("hide_in_debug", "HUD bei F3 ausblenden", true));

    public ClientSettingsModule() {
        super("client", "Client-Einstellungen", "Farbe, Skalierung und Animationen des Menüs", Category.CLIENT, true);
    }

    @Override public boolean isToggleable() { return false; }

    public int accentColor() {
        int i = (int) Math.round(accent.get());
        return ACCENTS[Math.max(0, Math.min(ACCENTS.length - 1, i))];
    }

    public String accentName() {
        int i = (int) Math.round(accent.get());
        return ACCENT_NAMES[Math.max(0, Math.min(ACCENT_NAMES.length - 1, i))];
    }
}
