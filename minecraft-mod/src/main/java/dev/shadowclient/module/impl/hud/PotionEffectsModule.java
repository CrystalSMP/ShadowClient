package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Aktive Effekte mit Stufe und Restzeit. */
public class PotionEffectsModule extends HudModule {
    public PotionEffectsModule() {
        super("potion_effects", "Effekte", "Aktive Trank-Effekte mit Restzeit", false, 4, 310);
    }

    private List<String> lines(MinecraftClient client) {
        List<String> out = new ArrayList<>();
        if (client.player == null) return out;
        for (StatusEffectInstance e : client.player.getStatusEffects()) {
            Text name = e.getEffectType().value().getName();
            String level = e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "";
            out.add(name.getString() + level + "  " + formatDuration(e));
        }
        return out;
    }

    private static String formatDuration(StatusEffectInstance e) {
        if (e.isInfinite()) return "∞";
        int seconds = e.getDuration() / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public int getWidth(MinecraftClient client) {
        int w = 0;
        for (String l : lines(client)) w = Math.max(w, client.textRenderer.getWidth(l));
        return w == 0 ? 0 : w + 8;
    }

    @Override
    public int getHeight(MinecraftClient client) { return Math.max(12, lines(client).size() * 11 + 4); }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient client, int accent) {
        List<String> lines = lines(client);
        ctx.fill(0, 0, getWidth(client), getHeight(client), 0x90000000);
        ctx.fill(0, 0, 1, getHeight(client), accent);
        int y = 3;
        for (String l : lines) {
            ctx.drawTextWithShadow(client.textRenderer, l, 4, y, 0xFFFFFFFF);
            y += 11;
        }
    }
}
