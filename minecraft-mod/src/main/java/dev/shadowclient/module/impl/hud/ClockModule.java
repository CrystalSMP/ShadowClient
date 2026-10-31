package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import dev.shadowclient.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClockModule extends TextHudModule {
    private static final DateTimeFormatter WITH_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter NO_SECONDS = DateTimeFormatter.ofPattern("HH:mm");
    private final BooleanSetting seconds = add(new BooleanSetting("seconds", "Sekunden", false));

    public ClockModule() {
        super("clock", "Uhrzeit", "Echte Uhrzeit deines PCs", false, 4, 68);
    }

    @Override
    protected String getText(MinecraftClient client) {
        return LocalTime.now().format(seconds.get() ? WITH_SECONDS : NO_SECONDS);
    }
}
