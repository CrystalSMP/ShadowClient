package dev.shadowclient.module.impl.visual;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.setting.NumberSetting;

/** Eigene Tageszeit nur auf deinem Bildschirm (Server bleibt unverändert). */
public class TimeChangerModule extends Module {
    public static TimeChangerModule INSTANCE;
    public final NumberSetting hour = add(new NumberSetting("hour", "Uhrzeit (Stunde)", 12, 0, 23, 1));

    public TimeChangerModule() {
        super("time_changer", "Zeit lokal", "Feste Tageszeit nur für dich – z. B. immer Tag", Category.VISUAL, false);
        INSTANCE = this;
    }

    /** Minecraft-Zeit: 0 = 6 Uhr morgens, 6000 = Mittag, 18000 = Mitternacht. */
    public long timeOfDay() { return Math.floorMod((long) ((hour.get() - 6) * 1000), 24000L); }
}
