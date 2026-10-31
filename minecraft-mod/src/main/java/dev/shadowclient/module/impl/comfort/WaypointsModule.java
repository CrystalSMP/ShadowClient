package dev.shadowclient.module.impl.comfort;

import dev.shadowclient.hud.HudModule;
import dev.shadowclient.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.Comparator;
import java.util.List;

/**
 * Wegpunkte: Taste B setzt einen Punkt an deiner Position, Shift+B löscht den nächsten.
 * Das HUD zeigt die nächsten Punkte mit Entfernung und Richtungspfeil.
 */
public class WaypointsModule extends HudModule {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int[] COLORS = {0x22D3EE, 0xF87171, 0x4ADE80, 0xFACC15, 0xC084FC, 0xFB923C};
    private final NumberSetting max = add(new NumberSetting("max", "Anzahl im HUD", 4, 1, 10, 1));

    public WaypointsModule() {
        super("waypoints", "Wegpunkte", "Taste B: Punkt setzen · Shift+B: nächsten löschen. Anzeige mit Entfernung und Richtung.", true, 4, 280);
    }

    public void addHere(MinecraftClient c) {
        int n = Waypoints.current(c).size() + 1;
        Waypoints.Point p = Waypoints.add(c, "Punkt " + n, COLORS[(n - 1) % COLORS.length]);
        if (p != null && c.player != null)
            c.inGameHud.getChatHud().addMessage(Text.literal("§b[ShadowClient] §fWegpunkt §b" + p.name() + "§f gesetzt: " + p.x() + " " + p.y() + " " + p.z()));
    }

    private List<Waypoints.Point> shown(MinecraftClient c) {
        if (c.player == null) return List.of();
        return Waypoints.current(c).stream().sorted(Comparator.comparingDouble(p -> Waypoints.dist(c, p)))
                .limit(max.get().longValue()).toList();
    }

    private String line(MinecraftClient c, Waypoints.Point p) {
        double dx = p.x() + 0.5 - c.player.getX(), dz = p.z() + 0.5 - c.player.getZ();
        float target = (float) Math.toDegrees(Math.atan2(-dx, dz));        // Minecraft-Yaw zum Ziel
        float rel = MathHelper.wrapDegrees(target - c.player.getYaw());
        String arrow = ARROWS[Math.floorMod(Math.round(rel / 45f), 8)];
        return arrow + " " + p.name() + "  " + Math.round(Waypoints.dist(c, p)) + "m";
    }

    @Override public int getWidth(MinecraftClient c) {
        int w = 0;
        for (Waypoints.Point p : shown(c)) w = Math.max(w, c.textRenderer.getWidth(line(c, p)));
        return w == 0 ? 0 : w + 10;
    }
    @Override public int getHeight(MinecraftClient c) { return Math.max(1, shown(c).size()) * 11 + 5; }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient c, int accent) {
        int w = getWidth(c), h = getHeight(c), y = 4;
        ctx.fill(0, 0, w, h, 0x90000000);
        for (Waypoints.Point p : shown(c)) {
            ctx.fill(3, y + 1, 5, y + 8, 0xFF000000 | p.color());
            ctx.drawText(c.textRenderer, line(c, p), 8, y, 0xFFFFFFFF, true);
            y += 11;
        }
    }
}
