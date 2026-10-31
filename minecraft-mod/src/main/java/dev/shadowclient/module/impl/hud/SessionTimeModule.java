package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import net.minecraft.client.MinecraftClient;

/** Wie lange du schon auf dem aktuellen Server/in der Welt bist. */
public class SessionTimeModule extends TextHudModule {
    private long joinedAt;
    private boolean inWorld;

    public SessionTimeModule() {
        super("session_time", "Sitzungszeit", "Zeit seit Betreten des Servers", false, 4, 404);
    }

    @Override
    public void onTick(MinecraftClient client) {
        boolean now = client.world != null;
        if (now && !inWorld) joinedAt = System.currentTimeMillis();
        inWorld = now;
    }

    @Override
    protected String getText(MinecraftClient client) {
        if (!inWorld) return null;
        long s = (System.currentTimeMillis() - joinedAt) / 1000;
        return s >= 3600
                ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }
}
