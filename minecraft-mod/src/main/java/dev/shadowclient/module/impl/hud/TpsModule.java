package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import net.minecraft.client.MinecraftClient;

/** Schätzt die Server-TPS aus dem Fortschritt der Weltzeit (20 = flüssig). */
public class TpsModule extends TextHudModule {
    private long lastWorldTime = -1, lastReal;
    private double tps = 20;

    public TpsModule() {
        super("tps", "TPS", "Geschätzte Server-Leistung (Ticks pro Sekunde)", false, 4, 212);
    }

    @Override
    public void onTick(MinecraftClient c) {
        if (c.world == null) { lastWorldTime = -1; return; }
        long now = System.currentTimeMillis(), wt = c.world.getTime();
        if (lastWorldTime < 0) { lastWorldTime = wt; lastReal = now; return; }
        if (now - lastReal >= 2000) {
            double measured = (wt - lastWorldTime) / ((now - lastReal) / 1000.0);
            tps = Math.max(0, Math.min(20, tps * 0.5 + measured * 0.5));
            lastWorldTime = wt; lastReal = now;
        }
    }

    @Override
    protected String getText(MinecraftClient c) {
        if (c.world == null || c.isInSingleplayer() && c.isPaused()) return null;
        String color = tps >= 18 ? "§a" : tps >= 14 ? "§e" : "§c";
        return "TPS " + color + String.format("%.1f", tps);
    }
}
