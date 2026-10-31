package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.HudModule;
import dev.shadowclient.module.setting.BooleanSetting;
import dev.shadowclient.util.ClickTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;

/** W/A/S/D, Maustasten mit CPS und Leertaste. Gedrückte Tasten leuchten in der Akzentfarbe. */
public class KeystrokesModule extends HudModule {
    private static final int KEY = 22, GAP = 2;
    private final BooleanSetting mouse = add(new BooleanSetting("mouse", "Maustasten", true));
    private final BooleanSetting space = add(new BooleanSetting("space", "Leertaste", true));

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", "Zeigt gedrückte Bewegungstasten", false, 4, 150);
    }

    @Override
    public int getWidth(MinecraftClient client) { return KEY * 3 + GAP * 2; }

    @Override
    public int getHeight(MinecraftClient client) {
        int h = KEY * 2 + GAP;
        if (mouse.get()) h += KEY + GAP;
        if (space.get()) h += 12 + GAP;
        return h;
    }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient client, int accent) {
        GameOptions o = client.options;
        key(ctx, client, KEY + GAP, 0, KEY, KEY, label(o.forwardKey, "W"), o.forwardKey.isPressed(), accent);
        int y = KEY + GAP;
        key(ctx, client, 0, y, KEY, KEY, label(o.leftKey, "A"), o.leftKey.isPressed(), accent);
        key(ctx, client, KEY + GAP, y, KEY, KEY, label(o.backKey, "S"), o.backKey.isPressed(), accent);
        key(ctx, client, (KEY + GAP) * 2, y, KEY, KEY, label(o.rightKey, "D"), o.rightKey.isPressed(), accent);
        y += KEY + GAP;
        int half = (getWidth(client) - GAP) / 2;
        if (mouse.get()) {
            key(ctx, client, 0, y, half, KEY, ClickTracker.leftCps() + " CPS", o.attackKey.isPressed(), accent);
            key(ctx, client, half + GAP, y, half, KEY, ClickTracker.rightCps() + " CPS", o.useKey.isPressed(), accent);
            y += KEY + GAP;
        }
        if (space.get()) {
            boolean down = o.jumpKey.isPressed();
            ctx.fill(0, y, getWidth(client), y + 12, down ? accent : 0x90000000);
            int barW = getWidth(client) / 3;
            int bx = (getWidth(client) - barW) / 2;
            ctx.fill(bx, y + 5, bx + barW, y + 7, down ? 0xFF000000 : 0xFFFFFFFF);
        }
    }

    private static String label(KeyBinding key, String fallback) {
        String s = key.getBoundKeyLocalizedText().getString();
        return s.isEmpty() || s.length() > 3 ? fallback : s.toUpperCase();
    }

    private static void key(DrawContext ctx, MinecraftClient client, int x, int y, int w, int h, String text, boolean down, int accent) {
        ctx.fill(x, y, x + w, y + h, down ? accent : 0x90000000);
        int tw = client.textRenderer.getWidth(text);
        ctx.drawText(client.textRenderer, text, x + (w - tw) / 2, y + (h - 8) / 2, down ? 0xFF000000 : 0xFFFFFFFF, !down);
    }
}
