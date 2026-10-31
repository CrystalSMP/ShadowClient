package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BowItem;
import net.minecraft.item.Items;

/** Balken für die Bogenspannung – voll = maximaler Schaden. */
public class BowChargeModule extends HudModule {
    public BowChargeModule() {
        super("bow_charge", "Bogenspannung", "Zeigt beim Spannen, wie weit der Bogen gezogen ist", false, 300, 300);
    }

    private float charge(MinecraftClient c) {
        if (c.player == null || !c.player.isUsingItem() || !c.player.getActiveItem().isOf(Items.BOW)) return -1;
        return BowItem.getPullProgress(c.player.getItemUseTime());
    }

    @Override public int getWidth(MinecraftClient c) { return charge(c) < 0 ? 0 : 60; }
    @Override public int getHeight(MinecraftClient c) { return 6; }
    @Override public boolean hasContent(MinecraftClient c) { return charge(c) >= 0; }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient c, int accent) {
        float f = charge(c);
        ctx.fill(0, 0, 60, 6, 0xA0000000);
        int color = f >= 1f ? 0xFF4ADE80 : accent | 0xFF000000;
        ctx.fill(1, 1, 1 + Math.round(58 * f), 5, color);
    }
}
