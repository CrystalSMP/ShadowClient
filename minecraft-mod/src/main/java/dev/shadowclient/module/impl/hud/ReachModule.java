package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import dev.shadowclient.util.CombatTracker;
import net.minecraft.client.MinecraftClient;

public class ReachModule extends TextHudModule {
    public ReachModule() {
        super("reach", "Reichweite", "Entfernung deines letzten Treffers", false, 4, 356);
    }

    @Override
    protected String getText(MinecraftClient client) {
        if (System.currentTimeMillis() - CombatTracker.lastHitTime() > 5000) return "– Blöcke";
        return String.format("%.2f Blöcke", CombatTracker.lastReach());
    }
}
