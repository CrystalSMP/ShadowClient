package dev.shadowclient.module.impl.visual;

import dev.shadowclient.hud.HudModule;
import dev.shadowclient.util.CombatTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/** Schadenszahlen neben dem Fadenkreuz, wenn dein Ziel Leben verliert. */
public class DamageIndicatorModule extends HudModule {
    private record Pop(String text, long time, int dx) {}
    private final List<Pop> pops = new ArrayList<>();
    private LivingEntity tracked;
    private float lastHealth;

    public DamageIndicatorModule() {
        super("damage_indicator", "Schadensanzeige", "Zeigt verursachten Schaden als Zahl neben dem Fadenkreuz", false, 0, 0);
    }

    @Override
    public void onTick(MinecraftClient c) {
        LivingEntity t = CombatTracker.target();
        if (t != tracked) { tracked = t; lastHealth = t == null ? 0 : t.getHealth() + t.getAbsorptionAmount(); return; }
        if (t == null) return;
        float hp = t.getHealth() + t.getAbsorptionAmount();
        if (hp < lastHealth - 0.01f) {
            float dmg = (lastHealth - hp) / 2f;
            pops.add(new Pop(String.format("-%.1f ❤", dmg), System.currentTimeMillis(), (int) (Math.random() * 16) - 8));
        }
        lastHealth = hp;
        pops.removeIf(p -> System.currentTimeMillis() - p.time > 1000);
    }

    // Position folgt dem Fadenkreuz, nicht dem HUD-Editor
    @Override public int getWidth(MinecraftClient c) { return 1; }
    @Override public int getHeight(MinecraftClient c) { return 1; }
    @Override public boolean hasContent(MinecraftClient c) { return !pops.isEmpty(); }

    @Override
    public void render(DrawContext ctx, MinecraftClient c, int accent) {
        if (pops.isEmpty()) return;
        int cx = ctx.getScaledWindowWidth() / 2 + 14, cy = ctx.getScaledWindowHeight() / 2 - 14;
        long now = System.currentTimeMillis();
        for (Pop p : pops) {
            float t = (now - p.time) / 1000f;
            int alpha = (int) (255 * (1 - t));
            if (alpha < 8) continue;
            ctx.drawText(c.textRenderer, p.text, cx + p.dx, cy - Math.round(t * 18), (alpha << 24) | 0xFF5555, true);
        }
    }

    @Override protected void draw(DrawContext ctx, MinecraftClient c, int accent) {}
}
