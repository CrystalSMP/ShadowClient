package dev.shadowclient.gui;

import dev.shadowclient.ShadowClient;
import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.impl.ClientSettingsModule;
import dev.shadowclient.module.impl.utility.BadgeModule;
import dev.shadowclient.module.impl.visual.CrosshairModule;
import dev.shadowclient.online.BadgeService;
import dev.shadowclient.module.setting.BooleanSetting;
import dev.shadowclient.module.setting.NumberSetting;
import dev.shadowclient.module.setting.Setting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ingame-Menü: ein verschiebbares Panel pro Kategorie.
 * Linksklick schaltet Module, Rechtsklick klappt Einstellungen aus.
 * Änderungen wirken sofort (Live-Vorschau) und werden beim Schließen gespeichert.
 */
public class ClickGuiScreen extends Screen {
    private static final int PANEL_WIDTH = 140;
    private static final int HEADER_HEIGHT = 18;
    private static final int ROW_HEIGHT = 15;

    private final List<Panel> panels = new ArrayList<>();
    private final Set<Module> expanded = new HashSet<>();
    private final long openedAt = System.currentTimeMillis();

    private Panel draggingPanel;
    private int dragOffsetX, dragOffsetY;
    private NumberSetting draggingSlider;
    private int sliderX;
    private String hoverDescription;

    public ClickGuiScreen() {
        super(Text.literal("ShadowClient"));
    }

    private ClientSettingsModule settings() { return ShadowClient.modules().clientSettings(); }

    private double scale() { return settings().guiScale.get(); }

    private static final int EDITOR_BTN_W = 90, EDITOR_BTN_H = 14;

    @Override
    protected void init() {
        panels.clear();
        int available = (int) (width / scale());
        int x = 20, y = 40, rowHeight = 0;
        for (Category c : Category.values()) {
            int[] saved = ShadowClient.config().panelPositions().get(c.name());
            if (saved != null) {
                panels.add(new Panel(c, saved[0], saved[1]));
                continue;
            }
            // Standard-Anordnung: nebeneinander, bei Platzmangel nächste Reihe
            if (x + PANEL_WIDTH > available - 10 && x > 20) {
                x = 20;
                y += rowHeight + 12;
                rowHeight = 0;
            }
            Panel p = new Panel(c, x, y);
            panels.add(p);
            rowHeight = Math.max(rowHeight, p.height());
            x += PANEL_WIDTH + 12;
        }
    }

    private int editorBtnX() { return 20 + textRenderer.getWidth("ShadowClient") + 14; }

    // ------------------------------------------------------------------ Rendering

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // Hintergrund (Abdunkeln/Blur)

        float progress = 1f;
        if (settings().animations.get()) {
            progress = Math.min(1f, (System.currentTimeMillis() - openedAt) / 160f);
            progress = 1f - (1f - progress) * (1f - progress); // ease-out
        }

        float s = (float) scale();
        double mx = mouseX / s, my = mouseY / s;
        hoverDescription = null;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(s, s);
        ctx.getMatrices().translate(0f, (1f - progress) * 12f);

        int alpha = (int) (progress * 255);
        drawBranding(ctx, alpha);
        drawEditorButton(ctx, mx, my, alpha);
        for (Panel p : panels) p.render(ctx, mx, my, alpha);

        ctx.getMatrices().popMatrix();

        // Fußzeile in normaler Skalierung
        String hint = hoverDescription != null ? hoverDescription
                : "Linksklick: an/aus  ·  Rechtsklick: Einstellungen  ·  Kopf ziehen: verschieben  ·  Mausrad: scrollen";
        int w = textRenderer.getWidth(hint);
        ctx.drawTextWithShadow(textRenderer, hint, (width - w) / 2, height - 14, withAlpha(0xFFB9C6DC, alpha));
    }

    private void drawBranding(DrawContext ctx, int alpha) {
        int accent = settings().accentColor();
        ctx.drawTextWithShadow(textRenderer, "Shadow", 20, 18, withAlpha(0xFFFFFFFF, alpha));
        ctx.drawTextWithShadow(textRenderer, "Client", 20 + textRenderer.getWidth("Shadow"), 18, withAlpha(accent, alpha));
    }

    private void drawEditorButton(DrawContext ctx, double mx, double my, int alpha) {
        int accent = settings().accentColor();
        int bx = editorBtnX(), by = 15;
        boolean hover = inside(mx, my, bx, by, EDITOR_BTN_W, EDITOR_BTN_H);
        ctx.fill(bx, by, bx + EDITOR_BTN_W, by + EDITOR_BTN_H, withAlpha(hover ? accent : 0xE00E1628, alpha));
        String label = "✥ HUD-Editor";
        ctx.drawTextWithShadow(textRenderer, label, bx + (EDITOR_BTN_W - textRenderer.getWidth(label)) / 2, by + 3,
                withAlpha(hover ? 0xFF000000 : 0xFFFFFFFF, alpha));
        if (hover) hoverDescription = "HUD-Elemente verschieben und skalieren";
    }

    // ------------------------------------------------------------------ Eingabe

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int button = click.button();
        double mx = click.x() / scale(), my = click.y() / scale();
        if (button == 0 && inside(mx, my, editorBtnX(), 15, EDITOR_BTN_W, EDITOR_BTN_H)) {
            ShadowClient.config().save();
            client.setScreen(new HudEditorScreen(this));
            return true;
        }
        // Oberstes Panel zuerst (zuletzt gezeichnet = oben)
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (p.mouseClicked(mx, my, button)) {
                panels.remove(i);
                panels.add(p); // nach vorne holen
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double mx = click.x() / scale(), my = click.y() / scale();
        if (draggingPanel != null) {
            draggingPanel.x = (int) mx - dragOffsetX;
            draggingPanel.y = (int) my - dragOffsetY;
            return true;
        }
        if (draggingSlider != null) {
            updateSlider(mx);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingPanel != null) {
            ShadowClient.config().panelPositions().put(draggingPanel.category.name(), new int[]{draggingPanel.x, draggingPanel.y});
        }
        draggingPanel = null;
        draggingSlider = null;
        return super.mouseReleased(click);
    }

    /** Mausrad verschiebt alle Panels, falls lange Listen nicht auf den Bildschirm passen. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int delta = (int) Math.round(vertical * 15);
        for (Panel p : panels) p.y += delta;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (ShadowClient.menuKey().matchesKey(input)) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    private void updateSlider(double mx) {
        double f = (mx - sliderX) / (double) (PANEL_WIDTH - 12);
        draggingSlider.setFromFraction(Math.max(0, Math.min(1, f)));
    }

    @Override
    public void close() {
        ShadowClient.config().save();
        super.close();
    }

    @Override
    public boolean shouldPause() { return false; }

    // ------------------------------------------------------------------ Panel

    private final class Panel {
        final Category category;
        int x, y;

        Panel(Category category, int x, int y) {
            this.category = category;
            this.x = x;
            this.y = y;
        }

        List<Module> modules() { return ShadowClient.modules().byCategory(category); }

        int height() {
            int h = HEADER_HEIGHT;
            for (Module m : modules()) {
                h += ROW_HEIGHT;
                if (expanded.contains(m)) h += m.getSettings().size() * ROW_HEIGHT;
            }
            return h + 3;
        }

        void render(DrawContext ctx, double mx, double my, int alpha) {
            int accent = settings().accentColor();
            int h = height();
            // Körper + Kopf
            ctx.fill(x, y, x + PANEL_WIDTH, y + h, withAlpha(0xE00A1020, alpha));
            ctx.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, withAlpha(0xFF0E1628, alpha));
            ctx.fill(x, y + HEADER_HEIGHT - 1, x + PANEL_WIDTH, y + HEADER_HEIGHT, withAlpha(accent, alpha));
            ctx.drawTextWithShadow(textRenderer, category.displayName, x + 6, y + 5, withAlpha(0xFFFFFFFF, alpha));

            int ry = y + HEADER_HEIGHT;
            for (Module m : modules()) {
                boolean hover = inside(mx, my, x, ry, PANEL_WIDTH, ROW_HEIGHT);
                if (hover) {
                    ctx.fill(x, ry, x + PANEL_WIDTH, ry + ROW_HEIGHT, withAlpha(0x22FFFFFF, alpha));
                    hoverDescription = m instanceof BadgeModule
                            ? m.getDescription() + "  [" + BadgeService.status() + "]"
                            : m instanceof dev.shadowclient.module.impl.utility.FriendsModule
                            ? m.getDescription() + "  [" + dev.shadowclient.online.OnlineService.status() + "]"
                            : m.getDescription();
                }
                if (m.isEnabled() && m.isToggleable()) {
                    ctx.fill(x, ry, x + 2, ry + ROW_HEIGHT, withAlpha(accent, alpha));
                }
                int textColor = !m.isToggleable() || m.isEnabled() ? 0xFFFFFFFF : 0xFF8A99B5;
                ctx.drawTextWithShadow(textRenderer, m.getName(), x + 7, ry + 4, withAlpha(textColor, alpha));
                if (!m.getSettings().isEmpty()) {
                    String arrow = expanded.contains(m) ? "▾" : "▸";
                    ctx.drawTextWithShadow(textRenderer, arrow, x + PANEL_WIDTH - 10, ry + 4, withAlpha(0xFF8A99B5, alpha));
                }
                ry += ROW_HEIGHT;

                if (expanded.contains(m)) {
                    for (Setting<?> setting : m.getSettings()) {
                        renderSetting(ctx, m, setting, ry, mx, my, alpha, accent);
                        ry += ROW_HEIGHT;
                    }
                }
            }
        }

        void renderSetting(DrawContext ctx, Module m, Setting<?> setting, int ry, double mx, double my, int alpha, int accent) {
            int sx = x + 6;
            ctx.fill(x, ry, x + PANEL_WIDTH, ry + ROW_HEIGHT, withAlpha(0x40000000, alpha));
            if (setting instanceof BooleanSetting b) {
                ctx.drawTextWithShadow(textRenderer, b.getName(), sx + 4, ry + 4, withAlpha(0xFFB9C6DC, alpha));
                int bx = x + PANEL_WIDTH - 22;
                ctx.fill(bx, ry + 4, bx + 16, ry + 11, withAlpha(b.get() ? accent : 0xFF2A3550, alpha));
                int knob = b.get() ? bx + 10 : bx + 1;
                ctx.fill(knob, ry + 5, knob + 5, ry + 10, withAlpha(0xFFFFFFFF, alpha));
            } else if (setting instanceof NumberSetting n) {
                String value = m instanceof ClientSettingsModule cs && n == cs.accent ? cs.accentName()
                        : m instanceof CrosshairModule ch && n == ch.color ? ch.colorName()
                        : n.format();
                ctx.drawTextWithShadow(textRenderer, n.getName(), sx + 4, ry + 2, withAlpha(0xFFB9C6DC, alpha));
                ctx.drawTextWithShadow(textRenderer, value, x + PANEL_WIDTH - 6 - textRenderer.getWidth(value), ry + 2, withAlpha(0xFFFFFFFF, alpha));
                int barW = PANEL_WIDTH - 12;
                ctx.fill(sx, ry + 12, sx + barW, ry + 14, withAlpha(0xFF2A3550, alpha));
                ctx.fill(sx, ry + 12, sx + (int) (barW * n.getFraction()), ry + 14, withAlpha(accent, alpha));
            }
        }

        boolean mouseClicked(double mx, double my, int button) {
            if (!inside(mx, my, x, y, PANEL_WIDTH, height())) return false;

            if (my < y + HEADER_HEIGHT) {
                if (button == 0) {
                    draggingPanel = this;
                    dragOffsetX = (int) mx - x;
                    dragOffsetY = (int) my - y;
                }
                return true;
            }

            int ry = y + HEADER_HEIGHT;
            for (Module m : modules()) {
                if (inside(mx, my, x, ry, PANEL_WIDTH, ROW_HEIGHT)) {
                    if (button == 0 && m.isToggleable()) m.toggle();
                    else if (button == 1 || (button == 0 && !m.isToggleable())) {
                        if (!expanded.remove(m)) expanded.add(m);
                    }
                    return true;
                }
                ry += ROW_HEIGHT;
                if (expanded.contains(m)) {
                    for (Setting<?> setting : m.getSettings()) {
                        if (inside(mx, my, x, ry, PANEL_WIDTH, ROW_HEIGHT) && button == 0) {
                            if (setting instanceof BooleanSetting b) b.toggle();
                            else if (setting instanceof NumberSetting n) {
                                draggingSlider = n;
                                sliderX = x + 6;
                                updateSlider(mx);
                            }
                            return true;
                        }
                        ry += ROW_HEIGHT;
                    }
                }
            }
            return true;
        }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int withAlpha(int argb, int alpha) {
        int a = ((argb >>> 24) * alpha) / 255;
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
