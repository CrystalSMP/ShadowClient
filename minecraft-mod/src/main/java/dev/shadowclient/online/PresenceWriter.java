package dev.shadowclient.online;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Schreibt den aktuellen Server nach shadowclient/presence.json – der Launcher zeigt ihn in Discord an. */
public final class PresenceWriter {
    private PresenceWriter() {}

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> write(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> write(null, false));
        write(null, false);
    }

    private static void write(MinecraftClient client) {
        ServerInfo info = client.getCurrentServerEntry();
        boolean sp = client.isInSingleplayer();
        String server = (!sp && info != null) ? info.address.replaceAll(":25565$", "").toLowerCase() : null;
        write(server, sp);
    }

    private static void write(String server, boolean singleplayer) {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("shadowclient");
            Files.createDirectories(dir);
            String json = "{\"server\":" + (server == null ? "null" : "\"" + server.replace("\"", "") + "\"")
                    + ",\"singleplayer\":" + singleplayer + "}";
            Files.writeString(dir.resolve("presence.json"), json, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }
}
