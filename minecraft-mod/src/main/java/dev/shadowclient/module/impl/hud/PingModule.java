package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

/** Ping aus der Spielerliste (dieselbe Quelle wie die Tab-Liste). */
public class PingModule extends TextHudModule {
    public PingModule() {
        super("ping", "Ping", "Zeigt deine Latenz zum Server", true, 4, 20);
    }

    @Override
    protected String getText(MinecraftClient client) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null || client.player == null) return null;
        if (client.isInSingleplayer()) return "Einzelspieler";
        PlayerListEntry entry = handler.getPlayerListEntry(client.player.getUuid());
        return entry == null ? "? ms" : entry.getLatency() + " ms";
    }
}
