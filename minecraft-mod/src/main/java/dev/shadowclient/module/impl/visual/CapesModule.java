package dev.shadowclient.module.impl.visual;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.online.CapeService;
import net.minecraft.client.MinecraftClient;

/** Zeigt ShadowClient-Capes anderer Nutzer (Auswahl im Launcher unter „Cosmetics“). Lädt nur – sendet nichts. */
public class CapesModule extends Module {
    public CapesModule() {
        super("capes", "ShadowClient-Capes", "Zeigt die Capes anderer ShadowClient-Nutzer. Dein Cape wählst du im Launcher.",
                Category.VISUAL, true);
    }

    @Override protected void onEnable() { CapeService.start(); }
    @Override protected void onDisable() { CapeService.stop(); }
    @Override public void onTick(MinecraftClient client) { CapeService.tick(); }
}
