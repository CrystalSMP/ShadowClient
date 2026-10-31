package dev.shadowclient.gui;

import dev.shadowclient.ShadowClient;
import dev.shadowclient.hud.HudModule;
import dev.shadowclient.module.Module;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-Editor: aktive HUD-Elemente per Drag-and-drop verschieben,
 * mit dem Mausrad skalieren, Rechtsklick setzt die Größe zurück.
 * Elemente rasten an Bildschirmrändern und der Mitte ein.
 */
public class HudEditorScreen extends Screen {
    private static final int SNAP = 4;
    private final Screen parent;
    private HudModule dragging;
    private int offX, offY;
    private boolean snappedX, snappedY;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("HUD-Editor"));
        this.parent = parent;
    }

    private List<HudModule> huds() {
        List<HudModule> list = new ArrayList<>();
        for (Module m : ShadowClient.modules().all()) {
            if (m instanceof HudModule h && h.isEnabled() && h.hasContent(client)) list.add(h);
        }
        return list;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x60000000);
        int accent = ShadowClient.modules().clientSettings().accentColor();

        // Hilfslinien
        ctx.fill(width / 2, 0, width / 2 + 1, height, snappedX ? accent : 0x30FFFFFF);
        ctx.fill(0, height / 2, width, height / 2 + 1, snappedY ? accent : 0x30FFFFFF);

        for (HudModule h : huds()) {
            h.render(ctx, client, accent);
            int x = h.screenX(ctx, client), y = h.screenY(ctx, client);
            int w = h.scaledWidth(client), hh = h.scaledHeight(client);
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + hh;
            int border = h == dragging || hover ? accent : 0x80FFFFFF;
            ctx.fill(x - 1, y - 1, x + w + 1, y, border);
            ctx.fill(x - 1, y + hh, x + w + 1, y + hh + 1, border);
            ctx.fill(x - 1, y, x, y + hh, border);
            ctx.fill(x + w, y, x + w + 1, y + hh, border);
            if (hover && dragging == null) {
                String label = h.getName() + "  " + Math.round(h.getScale() * 100) + "%";
                ctx.drawTextWithShadow(textRenderer, label, x, Math.max(0, y - 11), 0xFFFFFFFF);
            }
        }

        String hint = "Ziehen: verschieben  ·  Mausrad: Größe  ·  Rechtsklick: Größe zurücksetzen  ·  ESC: fertig";
        ctx.drawTextWithShadow(textRenderer, hint, (width - textRenderer.getWidth(hint)) / 2, height - 14, 0xFFB9C6DC);
        String title = "HUD-Editor";
        ctx.drawTextWithShadow(textRenderer, title, (width - textRenderer.getWidth(title)) / 2, 8, accent);
    }

    private HudModule at(double mx, double my) {
        List<HudModule> list = huds();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudModule h = list.get(i);
            int x = clampX(h), y = clampY(h);
            if (mx >= x && mx < x + h.scaledWidth(client) && my >= y && my < y + h.scaledHeight(client)) return h;
        }
        return null;
    }

    private int clampX(HudModule h) { return Math.max(0, Math.min(h.getX(), width - h.scaledWidth(client))); }
    private int clampY(HudModule h) { return Math.max(0, Math.min(h.getY(), height - h.scaledHeight(client))); }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();
        HudModule h = at(mx, my);
        if (h == null) return super.mouseClicked(click, doubled);
        if (button == 1) {
            h.setScale(1f);
            return true;
        }
        dragging = h;
        h.setPosition(clampX(h), clampY(h));
        offX = (int) mx - h.getX();
        offY = (int) my - h.getY();
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging == null) return super.mouseDragged(click, offsetX, offsetY);
        double mx = click.x(), my = click.y();
        int w = dragging.scaledWidth(client), h = dragging.scaledHeight(client);
        int x = (int) mx - offX, y = (int) my - offY;
        snappedX = false;
        snappedY = false;
        // Einrasten: Ränder und Mitte
        if (Math.abs(x) < SNAP) x = 0;
        if (Math.abs(x + w - width) < SNAP) x = width - w;
        if (Math.abs(x + w / 2 - width / 2) < SNAP) { x = width / 2 - w / 2; snappedX = true; }
        if (Math.abs(y) < SNAP) y = 0;
        if (Math.abs(y + h - height) < SNAP) y = height - h;
        if (Math.abs(y + h / 2 - height / 2) < SNAP) { y = height / 2 - h / 2; snappedY = true; }
        dragging.setPosition(Math.max(0, Math.min(x, width - w)), Math.max(0, Math.min(y, height - h)));
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = null;
        snappedX = false;
        snappedY = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        HudModule h = at(mx, my);
        if (h == null) return false;
        h.setScale(h.getScale() + (float) vertical * 0.1f);
        return true;
    }

    @Override
    public void close() {
        ShadowClient.config().save();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
