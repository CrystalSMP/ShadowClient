package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import net.minecraft.client.MinecraftClient;

public class BiomeModule extends TextHudModule {
    public BiomeModule() {
        super("biome", "Biom", "Aktuelles Biom", false, 4, 372);
    }

    @Override
    protected String getText(MinecraftClient client) {
        if (client.world == null || client.player == null) return null;
        String path = client.world.getBiome(client.player.getBlockPos()).getKey()
                .map(k -> k.getValue().getPath()).orElse("unbekannt");
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
