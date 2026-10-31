package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import net.minecraft.client.MinecraftClient;

/** Versteckter Sättigungswert (bestimmt, wie lange der Hunger stillsteht). */
public class SaturationModule extends TextHudModule {
    public SaturationModule() {
        super("saturation", "Sättigung", "Zeigt den versteckten Sättigungswert", false, 4, 388);
    }

    @Override
    protected String getText(MinecraftClient client) {
        if (client.player == null) return null;
        return String.format("Sättigung %.1f", client.player.getHungerManager().getSaturationLevel());
    }
}
