package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import dev.shadowclient.module.setting.BooleanSetting;
import dev.shadowclient.util.ClickTracker;
import net.minecraft.client.MinecraftClient;

public class CpsModule extends TextHudModule {
    private final BooleanSetting showRight = add(new BooleanSetting("right", "Rechtsklick zeigen", true));

    public CpsModule() {
        super("cps", "CPS", "Klicks pro Sekunde (links | rechts)", false, 4, 52);
    }

    @Override
    protected String getText(MinecraftClient client) {
        return showRight.get()
                ? ClickTracker.leftCps() + " | " + ClickTracker.rightCps() + " CPS"
                : ClickTracker.leftCps() + " CPS";
    }
}
