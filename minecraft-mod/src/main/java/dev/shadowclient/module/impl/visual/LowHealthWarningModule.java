package dev.shadowclient.module.impl.visual;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Roter, pulsierender Bildschirmrand bei wenig Leben. */
public class LowHealthWarningModule extends Module {
    public final NumberSetting threshold = add(new NumberSetting("threshold", "Ab Herzen", 4, 1, 10, 0.5));

    public LowHealthWarningModule() {
        super("low_health", "Leben-Warnung", "Roter Rand, wenn du wenig Leben hast", Category.VISUAL, false);
    }

    public void render(DrawContext ctx, MinecraftClient client) {
        if (client.player == null || client.player.isCreative() || client.player.isSpectator()) return;
        float hearts = client.player.getHealth() / 2f;
        if (hearts > threshold.get() || hearts <= 0) return;
        double pulse = 0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 180.0);
        float strength = (float) (1.0 - hearts / threshold.get()) * 0.5f + 0.5f;
        int alpha = (int) (Math.min(1.0, strength * pulse) * 140);
        int red = (alpha << 24) | 0xD01010;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int edge = Math.max(20, h / 6);
        ctx.fillGradient(0, 0, w, edge, red, 0x00D01010);
        ctx.fillGradient(0, h - edge, w, h, 0x00D01010, red);
        int side = Math.max(8, w / 40);
        ctx.fill(0, 0, side, h, (alpha / 2 << 24) | 0xD01010);
        ctx.fill(w - side, 0, w, h, (alpha / 2 << 24) | 0xD01010);
    }
}
