package dev.shadowclient.hud;

import dev.shadowclient.ShadowClient;
import dev.shadowclient.gui.HudEditorScreen;
import dev.shadowclient.module.Module;
import dev.shadowclient.util.ClickTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Zeichnet Fadenkreuz und alle aktiven HUD-Module. Wird über HudRenderCallback aufgerufen. */
public final class HudRenderer {
    private HudRenderer() {}

    public static void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClickTracker.update(client);
        if (client.player == null || client.options.hudHidden) return;
        var settings = ShadowClient.modules().clientSettings();
        int accent = settings.accentColor();

        if (ShadowClient.modules().lowHealth().isEnabled()) {
            ShadowClient.modules().lowHealth().render(ctx, client);
        }
        if (ShadowClient.modules().crosshair().isEnabled() && client.currentScreen == null) {
            ShadowClient.modules().crosshair().render(ctx, client, accent);
        }
        if (client.currentScreen instanceof HudEditorScreen) return; // Editor zeichnet selbst
        if (settings.hideHudInDebug.get() && client.getDebugHud().shouldShowDebugHud()) return;

        for (Module m : ShadowClient.modules().all()) {
            if (m instanceof HudModule hud && hud.isEnabled()) {
                hud.render(ctx, client, accent);
            }
        }
    }
}
