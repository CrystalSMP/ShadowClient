package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import net.minecraft.client.MinecraftClient;

public class FpsModule extends TextHudModule {
    public FpsModule() {
        super("fps", "FPS", "Zeigt die aktuellen Bilder pro Sekunde", true, 4, 4);
    }

    @Override
    protected String getText(MinecraftClient client) {
        return client.getCurrentFps() + " FPS";
    }
}
