package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import net.minecraft.client.MinecraftClient;

/** Anzahl Spieler in der Tab-Liste. */
public class PlayerCountModule extends TextHudModule {
    public PlayerCountModule() {
        super("player_count", "Spieleranzahl", "Wie viele Spieler gerade online sind (Tab-Liste)", false, 4, 228);
    }

    @Override
    protected String getText(MinecraftClient c) {
        if (c.getNetworkHandler() == null || c.isInSingleplayer()) return null;
        return "Spieler " + c.getNetworkHandler().getPlayerList().size();
    }
}
