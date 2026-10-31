package dev.shadowclient.gui;

import dev.shadowclient.ShadowClient;
import dev.shadowclient.module.impl.utility.FriendsModule;
import dev.shadowclient.online.FriendsService;
import dev.shadowclient.online.FriendsService.Friend;
import dev.shadowclient.online.OnlineService;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Freunde-Fenster im Social-Stil: links Liste (Tabs Freunde/Anfragen), rechts Details.
 * Öffnen: Taste O ingame oder Button im Hauptmenü.
 */
public class FriendsScreen extends Screen {
    private static final int BG = 0xF0161616, PANEL = 0xFF1F1F1F, HEADER = 0xFF262626, ROW_HOVER = 0xFF2C2C2C,
            ROW_SELECTED = 0xFF333333, TEXT = 0xFFFFFFFF, MUTED = 0xFF9A9A9A, ONLINE = 0xFF22C55E, OFFLINE = 0xFF5B5B5B;
    private static final int ROW_H = 30;

    private enum Tab { FRIENDS, REQUESTS }

    private final Screen parent;
    private Tab tab = Tab.FRIENDS;
    private String selectedUuid;
    private boolean adding;
    private String keptName = "";
    private int scroll;
    private int shownVersion = -1;

    private TextFieldWidget nameField;
    private int listX, listY, listW, listH, detailX, detailW, top, bottom;

    public FriendsScreen(Screen parent) {
        super(Text.literal("Freunde"));
        this.parent = parent;
    }

    public FriendsScreen() { this(null); }

    private FriendsModule module() { return ShadowClient.modules().friends(); }
    private int accent() { return ShadowClient.modules().clientSettings().accentColor(); }

    // ------------------------------------------------------------------ Layout & Widgets

    @Override
    protected void init() {
        shownVersion = FriendsService.version();
        int margin = Math.max(16, width / 20);
        top = Math.max(16, height / 10);
        bottom = height - top;
        listX = margin + 34;
        listW = Math.min(260, (width - listX - margin) / 3 + 60);
        detailX = listX + listW + 6;
        detailW = width - margin - detailX;
        listY = top + 64;
        listH = bottom - listY;
        nameField = null;

        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> close()).dimensions(margin, top, 24, 24).build());

        if (!module().isEnabled()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Freunde aktivieren"), b -> {
                module().setEnabled(true);
                ShadowClient.config().save();
                FriendsService.refreshNow();
                clearAndInit();
            }).dimensions(detailX + detailW / 2 - 70, top + 110, 140, 20).build());
            return;
        }

        // Kopf links: Freund hinzufügen
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> { adding = true; selectedUuid = null; clearAndInit(); })
                .dimensions(listX + listW - 28, top + 6, 22, 20).build());

        Friend sel = selected();
        if (adding) {
            int fx = detailX + 16, fy = top + 70;
            nameField = new TextFieldWidget(textRenderer, fx, fy, Math.min(220, detailW - 130), 20, Text.literal("Name"));
            nameField.setMaxLength(16);
            nameField.setText(keptName);
            addDrawableChild(nameField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Anfrage senden"), b -> submitName())
                    .dimensions(fx + Math.min(220, detailW - 130) + 6, fy, 100, 20).build());
            setInitialFocus(nameField);
        } else if (sel != null) {
            int bx = detailX + 16, by = top + 150;
            boolean incoming = FriendsService.incoming().stream().anyMatch(f -> f.uuid().equals(sel.uuid()));
            boolean outgoing = FriendsService.outgoing().stream().anyMatch(f -> f.uuid().equals(sel.uuid()));
            if (incoming) {
                addDrawableChild(ButtonWidget.builder(Text.literal("Annehmen"), b -> FriendsService.accept(sel.uuid()))
                        .dimensions(bx, by, 100, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Ablehnen"), b -> { FriendsService.remove(sel.uuid()); selectedUuid = null; })
                        .dimensions(bx + 106, by, 100, 20).build());
            } else if (outgoing) {
                addDrawableChild(ButtonWidget.builder(Text.literal("Anfrage zurückziehen"), b -> { FriendsService.remove(sel.uuid()); selectedUuid = null; })
                        .dimensions(bx, by, 150, 20).build());
            } else {
                if (FriendsService.joinable(sel)) {
                    addDrawableChild(ButtonWidget.builder(Text.literal("▶ Beitreten"), b -> { FriendsService.join(sel.server()); close(); })
                            .dimensions(bx, by - 26, 130, 20).build());
                }
                addDrawableChild(ButtonWidget.builder(Text.literal(sel.unread() > 0 ? "✉ Nachricht (" + sel.unread() + ")" : "✉ Nachricht"),
                        b -> client.setScreen(new DmScreen(this, sel.uuid(), sel.name()))).dimensions(bx + 136, by - 26, 130, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Auf meinen Server einladen"),
                        b -> dev.shadowclient.online.SocialService.invite(sel.uuid(), null)).dimensions(bx, by + 26, 166, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("In Party einladen"),
                        b -> dev.shadowclient.online.SocialService.partyInvite(sel.uuid(), null)).dimensions(bx + 172, by + 26, 120, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Freund entfernen"), b -> { FriendsService.remove(sel.uuid()); selectedUuid = null; })
                        .dimensions(bx, by, 130, 20).build());
            }
        }
    }

    private List<Friend> currentList() {
        if (tab == Tab.REQUESTS) {
            List<Friend> all = new java.util.ArrayList<>(FriendsService.incoming());
            all.addAll(FriendsService.outgoing());
            return all;
        }
        return FriendsService.friends();
    }

    private Friend selected() {
        if (selectedUuid == null) return null;
        for (List<Friend> l : List.of(FriendsService.friends(), FriendsService.incoming(), FriendsService.outgoing())) {
            for (Friend f : l) if (f.uuid().equals(selectedUuid)) return f;
        }
        return null;
    }

    private void submitName() {
        if (nameField == null) return;
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        FriendsService.add(name);
        keptName = "";
        nameField.setText("");
    }

    @Override
    public void tick() {
        if (nameField != null) keptName = nameField.getText();
        if (FriendsService.version() != shownVersion) clearAndInit();
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, BG);
        int accent = accent();

        // Linkes Panel
        ctx.fill(listX, top, listX + listW, bottom, PANEL);
        ctx.fill(listX, top, listX + listW, top + 32, HEADER);
        ctx.drawTextWithShadow(textRenderer, "Social", listX + 10, top + 12, TEXT);

        // Rechtes Panel
        ctx.fill(detailX, top, detailX + detailW, bottom, PANEL);
        ctx.fill(detailX, top, detailX + detailW, top + 32, HEADER);

        String status = OnlineService.status();
        ctx.drawTextWithShadow(textRenderer, status, detailX + detailW - 10 - textRenderer.getWidth(status), top + 12, MUTED);

        if (!module().isEnabled()) {
            ctx.drawTextWithShadow(textRenderer, "Freunde", detailX + 12, top + 12, TEXT);
            centered(ctx, "Die Freundesliste ist aus.", detailX, detailW, top + 60, TEXT);
            centered(ctx, "Beim Aktivieren werden UUID, Name und aktueller Server", detailX, detailW, top + 76, MUTED);
            centered(ctx, "an den ShadowClient-Server gesendet.", detailX, detailW, top + 88, MUTED);
            super.render(ctx, mouseX, mouseY, delta);
            return;
        }

        // Tabs
        int incoming = FriendsService.incoming().size();
        drawTab(ctx, "Freunde", listX + 10, top + 42, tab == Tab.FRIENDS, accent);
        String reqLabel = "Anfragen" + (incoming > 0 ? " (" + incoming + ")" : "");
        drawTab(ctx, reqLabel, listX + 20 + textRenderer.getWidth("Freunde"), top + 42, tab == Tab.REQUESTS, incoming > 0 ? 0xFFFBBF24 : accent);
        ctx.fill(listX, listY - 4, listX + listW, listY - 3, 0xFF2E2E2E);

        // Liste
        List<Friend> list = currentList();
        int maxScroll = Math.max(0, list.size() * ROW_H - listH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        ctx.enableScissor(listX, listY, listX + listW, bottom);
        int y = listY - scroll;
        for (Friend f : list) {
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW_H && mouseY >= listY;
            boolean sel = f.uuid().equals(selectedUuid);
            if (sel) ctx.fill(listX, y, listX + listW, y + ROW_H, ROW_SELECTED);
            else if (hover) ctx.fill(listX, y, listX + listW, y + ROW_H, ROW_HOVER);
            avatar(ctx, f, listX + 8, y + 5, 20);
            ctx.drawTextWithShadow(textRenderer, f.name(), listX + 34, y + 6, TEXT);
            ctx.drawTextWithShadow(textRenderer, trim(subtitle(f), listW - 44), listX + 34, y + 17, MUTED);
            y += ROW_H;
        }
        ctx.disableScissor();
        if (list.isEmpty()) {
            String empty = !FriendsService.loaded() ? "Lade…" : tab == Tab.FRIENDS ? "Noch keine Freunde" : "Keine Anfragen";
            centered(ctx, empty, listX, listW, listY + 20, MUTED);
            if (tab == Tab.FRIENDS && FriendsService.loaded()) centered(ctx, "Klicke oben auf +", listX, listW, listY + 34, MUTED);
        }

        // Details
        Friend sel = selected();
        if (adding) {
            ctx.drawTextWithShadow(textRenderer, "Freund hinzufügen", detailX + 12, top + 12, TEXT);
            ctx.drawTextWithShadow(textRenderer, "Minecraft-Name deines Freundes:", detailX + 16, top + 56, MUTED);
            if (nameField != null && nameField.getText().isEmpty() && !nameField.isFocused()) {
                ctx.drawTextWithShadow(textRenderer, "Spielername…", detailX + 20, top + 76, 0xFF6B6B6B);
            }
            String msg = dev.shadowclient.online.SocialService.lastResult != null ? dev.shadowclient.online.SocialService.lastResult : FriendsService.message();
            if (msg != null) ctx.drawTextWithShadow(textRenderer, msg, detailX + 16, top + 100, 0xFFFBBF24);
            ctx.drawTextWithShadow(textRenderer, "Dein Freund muss ShadowClient nutzen und „Freunde“ aktiviert haben.", detailX + 16, top + 120, MUTED);
        } else if (sel != null) {
            ctx.drawTextWithShadow(textRenderer, sel.name(), detailX + 12, top + 12, TEXT);
            avatar(ctx, sel, detailX + 16, top + 48, 64);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(detailX + 92, top + 52);
            ctx.getMatrices().scale(2f, 2f);
            ctx.drawTextWithShadow(textRenderer, sel.name(), 0, 0, TEXT);
            ctx.getMatrices().popMatrix();
            ctx.fill(detailX + 92, top + 80, detailX + 98, top + 86, sel.online() ? ONLINE : OFFLINE);
            ctx.drawTextWithShadow(textRenderer, subtitle(sel), detailX + 104, top + 79, sel.online() ? ONLINE : MUTED);
            if (sel.online() && sel.server() != null) {
                ctx.drawTextWithShadow(textRenderer, "Server: " + sel.server(), detailX + 92, top + 96, TEXT);
            }
            String msg = dev.shadowclient.online.SocialService.lastResult != null ? dev.shadowclient.online.SocialService.lastResult : FriendsService.message();
            if (msg != null) ctx.drawTextWithShadow(textRenderer, msg, detailX + 16, top + 212, 0xFFFBBF24);
        } else {
            ctx.drawTextWithShadow(textRenderer, "Freunde", detailX + 12, top + 12, TEXT);
            long online = FriendsService.friends().stream().filter(Friend::online).count();
            centered(ctx, online + " von " + FriendsService.friends().size() + " Freunden online", detailX, detailW, top + 70, TEXT);
            centered(ctx, "Wähle links einen Freund aus", detailX, detailW, top + 86, MUTED);
            var party = dev.shadowclient.online.SocialService.party();
            int py = top + 120;
            if (party != null) {
                ctx.drawTextWithShadow(textRenderer, "Party (" + party.members().size() + ")", detailX + 16, py, accent());
                py += 14;
                for (var m : party.members()) {
                    ctx.fill(detailX + 16, py + 2, detailX + 21, py + 7, m.online() ? ONLINE : OFFLINE);
                    String line = m.name() + (m.uuid().equals(party.leader()) ? " ★" : "") + (m.server() != null ? "  · " + m.server() : "");
                    ctx.drawTextWithShadow(textRenderer, trim(line, detailW - 40), detailX + 26, py, TEXT);
                    py += 12;
                }
                ctx.drawTextWithShadow(textRenderer, "/sc party leave zum Verlassen", detailX + 16, py + 4, MUTED);
            } else {
                ctx.drawTextWithShadow(textRenderer, "Keine Party – wähle einen Freund → „In Party einladen“", detailX + 16, py, MUTED);
            }
            var inv = dev.shadowclient.online.SocialService.lastInvite();
            if (inv != null) ctx.drawTextWithShadow(textRenderer, "Offene Einladung von " + inv.senderName() + " – /sc join", detailX + 16, bottom - 20, 0xFFFBBF24);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private String subtitle(Friend f) {
        if (FriendsService.incoming().stream().anyMatch(x -> x.uuid().equals(f.uuid()))) return "möchte dein Freund sein";
        if (FriendsService.outgoing().stream().anyMatch(x -> x.uuid().equals(f.uuid()))) return "Anfrage gesendet";
        if (!f.online()) return "Offline";
        String base = f.server() != null ? "Spielt auf " + f.server() : "Online";
        return f.status() != null ? base + " · " + f.status() : base;
    }

    private void drawTab(DrawContext ctx, String label, int x, int y, boolean active, int accent) {
        ctx.drawTextWithShadow(textRenderer, label, x, y, active ? accent : TEXT);
        if (active) ctx.fill(x, y + 11, x + textRenderer.getWidth(label), y + 12, accent);
    }

    /** Avatar: farbiges Feld mit Anfangsbuchstabe (Farbe aus dem Namen) plus Online-Punkt. */
    private void avatar(DrawContext ctx, Friend f, int x, int y, int size) {
        int hue = Math.abs(f.name().hashCode());
        int color = 0xFF000000 | (0x40 + (hue & 0x7F)) << 16 | (0x40 + ((hue >> 7) & 0x7F)) << 8 | (0x40 + ((hue >> 14) & 0x7F));
        ctx.fill(x, y, x + size, y + size, color);
        String letter = f.name().isEmpty() ? "?" : f.name().substring(0, 1).toUpperCase();
        float s = size / 10f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x + size / 2f - textRenderer.getWidth(letter) * s / 2f, y + size / 2f - 4 * s);
        ctx.getMatrices().scale(s, s);
        ctx.drawTextWithShadow(textRenderer, letter, 0, 0, TEXT);
        ctx.getMatrices().popMatrix();
        int d = Math.max(4, size / 6);
        ctx.fill(x + size - d, y + size - d, x + size + 1, y + size + 1, PANEL);
        ctx.fill(x + size - d + 1, y + size - d + 1, x + size, y + size, f.online() ? ONLINE : OFFLINE);
    }

    private String trim(String s, int w) {
        return textRenderer.getWidth(s) <= w ? s : textRenderer.trimToWidth(s, w - 6) + "…";
    }

    private void centered(DrawContext ctx, String text, int x, int w, int y, int color) {
        ctx.drawTextWithShadow(textRenderer, text, x + (w - textRenderer.getWidth(text)) / 2, y, color);
    }

    // ------------------------------------------------------------------ Eingabe

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();
        if (!module().isEnabled() || click.button() != 0) return false;

        int tabY = top + 42;
        int friendsW = textRenderer.getWidth("Freunde");
        if (my >= tabY - 2 && my < tabY + 12) {
            if (mx >= listX + 10 && mx < listX + 10 + friendsW) { tab = Tab.FRIENDS; scroll = 0; return true; }
            if (mx >= listX + 20 + friendsW && mx < listX + listW) { tab = Tab.REQUESTS; scroll = 0; return true; }
        }
        if (mx >= listX && mx < listX + listW && my >= listY && my < bottom) {
            int index = (int) ((my - listY + scroll) / ROW_H);
            List<Friend> list = currentList();
            if (index >= 0 && index < list.size()) {
                selectedUuid = list.get(index).uuid();
                adding = false;
                clearAndInit();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= listX && mouseX < listX + listW) {
            scroll -= (int) Math.round(vertical * ROW_H);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (nameField != null && nameField.isFocused()
                && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            submitName();
            return true;
        }
        if (ShadowClient.friendsKey().matchesKey(input) && (nameField == null || !nameField.isFocused())) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }
}
