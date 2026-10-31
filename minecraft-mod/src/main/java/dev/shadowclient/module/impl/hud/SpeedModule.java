package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import net.minecraft.client.MinecraftClient;

/** Horizontale Geschwindigkeit in Blöcken pro Sekunde, aus der Positionsänderung pro Tick. */
public class SpeedModule extends TextHudModule {
    private double lastX, lastZ, speed;
    private boolean hasLast;

    public SpeedModule() {
        super("speed", "Geschwindigkeit", "Blöcke pro Sekunde", false, 4, 100);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) { hasLast = false; return; }
        double x = client.player.getX(), z = client.player.getZ();
        if (hasLast) {
            double dx = x - lastX, dz = z - lastZ;
            double now = Math.sqrt(dx * dx + dz * dz) * 20.0;
            speed = speed * 0.6 + now * 0.4; // Glätten
        }
        lastX = x; lastZ = z; hasLast = true;
    }

    @Override
    protected String getText(MinecraftClient client) {
        return client.player == null ? null : String.format("%.2f b/s", speed);
    }
}
