package dev.shadowclient.hud;

import dev.shadowclient.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** HUD-Element mit einer Textzeile in einer Box. */
public abstract class TextHudModule extends HudModule {
    public static final int PADDING = 4;
    protected final BooleanSetting background = add(new BooleanSetting("background", "Hintergrund", true));
    protected final BooleanSetting shadow = add(new BooleanSetting("shadow", "Textschatten", true));

    protected TextHudModule(String id, String name, String description, boolean enabledByDefault, int x, int y) {
        super(id, name, description, enabledByDefault, x, y);
    }

    /** Anzuzeigender Text oder null. */
    protected abstract String getText(MinecraftClient client);

    @Override
    public int getWidth(MinecraftClient client) {
        String t = getText(client);
        return t == null ? 0 : client.textRenderer.getWidth(t) + PADDING * 2;
    }

    @Override
    public int getHeight(MinecraftClient client) {
        return client.textRenderer.fontHeight + PADDING * 2 - 1;
    }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient client, int accent) {
        String t = getText(client);
        if (t == null) return;
        int w = getWidth(client), h = getHeight(client);
        if (background.get()) {
            ctx.fill(0, 0, w, h, 0x90000000);
            ctx.fill(0, 0, 1, h, accent);
        }
        ctx.drawText(client.textRenderer, t, PADDING, PADDING, 0xFFFFFFFF, shadow.get());
    }
}
