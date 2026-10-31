package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import net.minecraft.client.MinecraftClient;

public class MemoryModule extends TextHudModule {
    public MemoryModule() {
        super("memory", "Arbeitsspeicher", "Genutzter RAM von Minecraft", false, 4, 132);
    }

    @Override
    protected String getText(MinecraftClient client) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long max = rt.maxMemory() / 1024 / 1024;
        return "RAM " + used + " / " + max + " MB";
    }
}
