package dev.shadowclient.gui;

import dev.shadowclient.ShadowClient;
import dev.shadowclient.online.OnlineService;
import dev.shadowclient.online.SocialService;
import dev.shadowclient.online.SocialService.Msg;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Privater Chat mit einem Freund (über das ShadowClient-Backend, serverübergreifend). */
public class DmScreen extends Screen {
    private static final int BG = 0xF0161616, PANEL = 0xFF1F1F1F, MINE = 0xFF1E3A44, THEIRS = 0xFF2A2A2A, MUTED = 0xFF9A9A9A;
    private final Screen parent;
    private final String uuid, name;
    private volatile List<Msg> messages = new ArrayList<>();
    private volatile String info;
    private TextFieldWidget input;
    private long lastLoad;
    private int boxX, boxY, boxW, boxH;

    public DmScreen(Screen parent, String uuid, String name) {
        super(Text.literal("Nachricht an " + name));
        this.parent = parent; this.uuid = uuid; this.name = name;
    }

    @Override
    protected void init() {
        boxW = Math.min(420, width - 40); boxX = (width - boxW) / 2;
        boxY = Math.max(20, height / 10); boxH = height - boxY * 2;
        input = new TextFieldWidget(textRenderer, boxX + 8, boxY + boxH - 28, boxW - 86, 20, Text.literal("Nachricht"));
        input.setMaxLength(256);
        input.setPlaceholder(Text.literal("Nachricht an " + name + " …"));
        addDrawableChild(input);
        setInitialFocus(input);
        addDrawableChild(ButtonWidget.builder(Text.literal("Senden"), b -> send()).dimensions(boxX + boxW - 74, boxY + boxH - 28, 66, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> close()).dimensions(boxX + 6, boxY + 6, 20, 20).build());
        load();
    }

    private void load() {
        lastLoad = System.currentTimeMillis();
        OnlineService.submit(() -> messages = SocialService.conversation(uuid));
    }

    private void send() {
        String t = input.getText().trim();
        if (t.isEmpty()) return;
        input.setText("");
        String me = SocialService.myUuid();
        List<Msg> next = new ArrayList<>(messages);
        next.add(new Msg(-1, me, "text", t, null, null, null));
        messages = next;
        SocialService.sendDm(uuid, t, r -> { info = r; load(); });
    }

    @Override
    public void tick() {
        if (System.currentTimeMillis() - lastLoad > 4000) load();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);
        ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, BG);
        ctx.fill(boxX, boxY, boxX + boxW, boxY + 32, PANEL);
        ctx.drawTextWithShadow(textRenderer, name, boxX + 32, boxY + 12, 0xFFFFFFFF);
        ctx.fill(boxX, boxY + 32, boxX + boxW, boxY + 33, ShadowClient.modules().clientSettings().accentColor());
        // Nachrichten von unten nach oben
        String me = SocialService.myUuid();
        int y = boxY + boxH - 40, maxW = boxW * 3 / 4;
        List<Msg> list = messages;
        for (int i = list.size() - 1; i >= 0 && y > boxY + 40; i--) {
            Msg m = list.get(i);
            String text = "invite".equals(m.kind()) ? "Einladung nach " + m.server() : "party".equals(m.kind()) ? "Party-Einladung" : m.body();
            if (text == null) continue;
            var lines = textRenderer.wrapLines(Text.literal(text), maxW - 12);
            int h = lines.size() * 10 + 6, w = 0;
            for (var l : lines) w = Math.max(w, textRenderer.getWidth(l));
            boolean mine = me.equals(m.sender());
            int x = mine ? boxX + boxW - 10 - w - 12 : boxX + 10;
            y -= h + 4;
            if (y < boxY + 40) break;
            ctx.fill(x, y, x + w + 12, y + h, mine ? MINE : THEIRS);
            int ly = y + 4;
            for (var l : lines) { ctx.drawText(textRenderer, l, x + 6, ly, 0xFFFFFFFF, false); ly += 10; }
        }
        if (list.isEmpty()) {
            String t = "Noch keine Nachrichten – schreib " + name + " etwas.";
            ctx.drawTextWithShadow(textRenderer, t, boxX + (boxW - textRenderer.getWidth(t)) / 2, boxY + boxH / 2, MUTED);
        }
        if (info != null) ctx.drawTextWithShadow(textRenderer, info, boxX + 8, boxY + boxH - 40, 0xFFFBBF24);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput in) {
        if (in.key() == GLFW.GLFW_KEY_ENTER || in.key() == GLFW.GLFW_KEY_KP_ENTER) { send(); return true; }
        return super.keyPressed(in);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (client != null) client.setScreen(parent); }
}
