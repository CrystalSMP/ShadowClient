package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.HudModule;
import dev.shadowclient.util.CombatTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

/** Name und Leben des Gegners, den du anschaust oder zuletzt getroffen hast. */
public class TargetHudModule extends HudModule {
    private static final int W = 120, H = 30;

    public TargetHudModule() {
        super("target_hud", "Target-HUD", "Leben deines aktuellen Ziels", false, 200, 200);
    }

    @Override
    public int getWidth(MinecraftClient client) { return W; }

    @Override
    public int getHeight(MinecraftClient client) { return H; }

    @Override
    public boolean hasContent(MinecraftClient client) { return CombatTracker.target() != null; }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient client, int accent) {
        LivingEntity t = CombatTracker.target();
        if (t == null) return;
        float hp = t.getHealth(), max = Math.max(1f, t.getMaxHealth()), abs = t.getAbsorptionAmount();
        ctx.fill(0, 0, W, H, 0xB0000000);
        ctx.fill(0, 0, 2, H, accent);
        String name = t.getName().getString();
        if (client.textRenderer.getWidth(name) > W - 10) name = client.textRenderer.trimToWidth(name, W - 14) + "…";
        ctx.drawTextWithShadow(client.textRenderer, name, 6, 4, 0xFFFFFFFF);
        String hpText = String.format("%.1f ❤", hp / 2f) + (abs > 0 ? String.format(" +%.1f", abs / 2f) : "");
        ctx.drawTextWithShadow(client.textRenderer, hpText, W - 6 - client.textRenderer.getWidth(hpText), 4, 0xFFFF5555);
        int barW = W - 12;
        float frac = Math.min(1f, hp / max);
        int color = frac > 0.5f ? 0xFF22C55E : frac > 0.25f ? 0xFFFBBF24 : 0xFFEF4444;
        ctx.fill(6, 18, 6 + barW, 24, 0xFF2A3550);
        ctx.fill(6, 18, 6 + (int) (barW * frac), 24, color);
        if (abs > 0) ctx.fill(6, 24, 6 + (int) (barW * Math.min(1f, abs / max)), 25, 0xFFFBBF24);
    }
}
