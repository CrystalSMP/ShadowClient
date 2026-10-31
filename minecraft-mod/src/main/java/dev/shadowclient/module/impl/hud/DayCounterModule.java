package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import net.minecraft.client.MinecraftClient;

public class DayCounterModule extends TextHudModule {
    public DayCounterModule() {
        super("day_counter", "Spieltag", "Zeigt den aktuellen Minecraft-Tag", false, 4, 116);
    }

    @Override
    protected String getText(MinecraftClient client) {
        if (client.world == null) return null;
        return "Tag " + (client.world.getTimeOfDay() / 24000L + 1);
    }
}
