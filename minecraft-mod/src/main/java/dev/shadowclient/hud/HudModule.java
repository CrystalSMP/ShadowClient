package dev.shadowclient.hud;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Ein frei positionierbares HUD-Element. Position und Skalierung werden im HUD-Editor
 * gesetzt und gespeichert. Unterklassen zeichnen in lokalen Koordinaten ab (0,0).
 */
public abstract class HudModule extends Module {
    private int x;
    private int y;
    private float scale = 1f;

    protected HudModule(String id, String name, String description, boolean enabledByDefault, int defaultX, int defaultY) {
        super(id, name, description, Category.HUD, enabledByDefault);
        this.x = defaultX;
        this.y = defaultY;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public float getScale() { return scale; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void setScale(float scale) { this.scale = Math.max(0.5f, Math.min(2.5f, scale)); }

    /** Breite in lokalen Pixeln (unskaliert); 0 = gerade nichts anzuzeigen. */
    public abstract int getWidth(MinecraftClient client);

    public abstract int getHeight(MinecraftClient client);

    /** Zeichnet den Inhalt ab (0,0). */
    protected abstract void draw(DrawContext ctx, MinecraftClient client, int accent);

    /** Beispielinhalt für den HUD-Editor, wenn das Modul gerade leer wäre. */
    public boolean hasContent(MinecraftClient client) { return getWidth(client) > 0; }

    public int scaledWidth(MinecraftClient c) { return Math.round(getWidth(c) * scale); }
    public int scaledHeight(MinecraftClient c) { return Math.round(getHeight(c) * scale); }

    /** Bildschirmposition, im sichtbaren Bereich gehalten. */
    public int screenX(DrawContext ctx, MinecraftClient c) {
        return Math.max(0, Math.min(x, ctx.getScaledWindowWidth() - scaledWidth(c)));
    }

    public int screenY(DrawContext ctx, MinecraftClient c) {
        return Math.max(0, Math.min(y, ctx.getScaledWindowHeight() - scaledHeight(c)));
    }

    public void render(DrawContext ctx, MinecraftClient client, int accent) {
        if (!hasContent(client)) return;
        int sx = screenX(ctx, client), sy = screenY(ctx, client);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(sx, sy);
        ctx.getMatrices().scale(scale, scale);
        draw(ctx, client, accent);
        ctx.getMatrices().popMatrix();
    }
}
