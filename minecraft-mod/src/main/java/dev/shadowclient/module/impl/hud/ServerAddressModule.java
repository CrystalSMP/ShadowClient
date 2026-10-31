package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

public class ServerAddressModule extends TextHudModule {
    public ServerAddressModule() {
        super("server_address", "Server-IP", "Zeigt die Adresse des aktuellen Servers", false, 4, 84);
    }

    @Override
    protected String getText(MinecraftClient client) {
        if (client.isInSingleplayer()) return "Einzelspieler";
        ServerInfo info = client.getCurrentServerEntry();
        return info == null ? null : info.address;
    }
}
