package dev.shadowclient.module.impl.visual;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import net.minecraft.client.MinecraftClient;

/** Blendet Regen und Gewitter nur auf deinem Bildschirm aus. */
public class ClearWeatherModule extends Module {
    public ClearWeatherModule() {
        super("clear_weather", "Klares Wetter", "Kein Regen/Gewitter (nur Darstellung)", Category.VISUAL, false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.world != null) {
            client.world.setRainGradient(0f);
            client.world.setThunderGradient(0f);
        }
    }
}
