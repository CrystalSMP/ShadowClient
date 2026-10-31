package dev.shadowclient.module.impl.comfort;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;

/** Schreibt am Rundenende automatisch „gg“ (erkennt typische Sieg-Nachrichten). */
public class AutoGgModule extends Module {
    public static AutoGgModule INSTANCE;
    private static final String[] TRIGGERS = {
            "hat das spiel gewonnen", "hat gewonnen", "gewinnt das spiel", "sieger:", "winner:", "won the game", "1st killer",
            "victory!", "#1 victory", "game over", "das team", "hat die runde gewonnen", "you won", "du hast gewonnen",
    };
    private final NumberSetting delay = add(new NumberSetting("delay", "Verzögerung (s)", 1, 0, 5, 0.5));
    private long lastSent, sendAt;

    public AutoGgModule() {
        super("auto_gg", "Auto-GG", "Schreibt am Rundenende automatisch „gg“", Category.UTILITY, false);
        INSTANCE = this;
    }

    public void onMessage(String text) {
        if (!isEnabled()) return;
        String t = text.toLowerCase(Locale.ROOT);
        for (String trig : TRIGGERS) {
            if (t.contains(trig) && System.currentTimeMillis() - lastSent > 15_000) {
                if (trig.equals("das team") && !t.contains("gewonnen")) continue;
                sendAt = System.currentTimeMillis() + (long) (delay.get() * 1000);
                lastSent = System.currentTimeMillis();
                return;
            }
        }
    }

    @Override
    public void onTick(MinecraftClient c) {
        if (sendAt > 0 && System.currentTimeMillis() >= sendAt) {
            sendAt = 0;
            if (c.getNetworkHandler() != null && !c.isInSingleplayer()) c.getNetworkHandler().sendChatMessage("gg");
        }
    }
}
