package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Kompassband wie in Survival-Spielen: Himmelsrichtungen laufen mit der Blickrichtung mit. */
public class CompassModule extends HudModule {
    private static final String[] LABELS = {"S", "SW", "W", "NW", "N", "NO", "O", "SO"};
    private static final int W = 160, H = 18;

    public CompassModule() {
        super("compass", "Kompass", "Kompassband mit Himmelsrichtungen und Grad", false, 240, 4);
    }

    @Override public int getWidth(MinecraftClient c) { return c.player == null ? 0 : W; }
    @Override public int getHeight(MinecraftClient c) { return H; }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient c, int accent) {
        float yaw = MathHelper.wrapDegrees(c.player.getYaw());   // 0 = Süden
        ctx.fill(0, 0, W, H, 0x90000000);
        float pxPerDeg = 1.4f;
        for (int deg = -180; deg <= 540; deg += 15) {
            float rel = MathHelper.wrapDegrees(deg - yaw);
            int x = Math.round(W / 2f + rel * pxPerDeg);
            if (x < 0 || x > W) continue;
            int d = Math.floorMod(deg, 360);
            if (d % 45 == 0) {
                String label = LABELS[d / 45];
                int color = label.equals("N") ? 0xFFFF5555 : 0xFFFFFFFF;
                ctx.drawText(c.textRenderer, label, x - c.textRenderer.getWidth(label) / 2, 5, color, true);
            } else {
                ctx.fill(x, 7, x + 1, 11, 0x80FFFFFF);
            }
        }
        ctx.fill(W / 2, 0, W / 2 + 1, 3, accent);
        ctx.fill(W / 2, H - 3, W / 2 + 1, H, accent);
        String degText = Math.round(Math.floorMod((int) (yaw + 180), 360)) + "°";
        ctx.drawText(c.textRenderer, degText, W - c.textRenderer.getWidth(degText) - 3, 5, 0xFFAAAAAA, false);
    }
}
