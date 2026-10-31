package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.List;

/** Zeigt die aktiven Resourcepacks (praktisch für PvP-Videos). */
public class PacksModule extends HudModule {
    private List<String> cache = List.of();
    private long lastUpdate;

    public PacksModule() {
        super("packs", "Pack-Anzeige", "Aktive Resourcepacks im HUD", false, 4, 244);
    }

    private List<String> packs(MinecraftClient c) {
        if (System.currentTimeMillis() - lastUpdate > 3000) {
            lastUpdate = System.currentTimeMillis();
            List<String> out = new ArrayList<>();
            for (ResourcePackProfile p : c.getResourcePackManager().getEnabledProfiles()) {
                String id = p.getId();
                if (id.startsWith("file/") || id.startsWith("server/") || id.startsWith("world")) out.add(p.getDisplayName().getString());
            }
            cache = out;
        }
        return cache;
    }

    @Override public int getWidth(MinecraftClient c) {
        int w = c.textRenderer.getWidth("Packs");
        for (String s : packs(c)) w = Math.max(w, c.textRenderer.getWidth(s));
        return packs(c).isEmpty() ? 0 : w + 8;
    }
    @Override public int getHeight(MinecraftClient c) { return 13 + packs(c).size() * 10; }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient c, int accent) {
        int w = getWidth(c), h = getHeight(c);
        ctx.fill(0, 0, w, h, 0x90000000);
        ctx.fill(0, 0, 1, h, accent);
        ctx.drawText(c.textRenderer, "Packs", 4, 3, accent | 0xFF000000, true);
        int y = 14;
        for (String s : packs(c)) { ctx.drawText(c.textRenderer, s, 4, y, 0xFFFFFFFF, true); y += 10; }
    }
}
