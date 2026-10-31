package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import dev.shadowclient.util.CombatTracker;
import net.minecraft.client.MinecraftClient;

public class ComboModule extends TextHudModule {
    public ComboModule() {
        super("combo", "Combo", "Treffer in Folge, ohne selbst getroffen zu werden", false, 4, 340);
    }

    @Override
    protected String getText(MinecraftClient client) {
        int c = CombatTracker.combo();
        return c == 0 ? "Keine Combo" : c + " Combo";
    }
}
