package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.HudModule;
import dev.shadowclient.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Rüstung und Item in der Hand mit verbleibender Haltbarkeit. */
public class ArmorStatusModule extends HudModule {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
    private final BooleanSetting percent = add(new BooleanSetting("percent", "Prozent statt Zahl", false));

    public ArmorStatusModule() {
        super("armor_status", "Rüstung", "Rüstung und Haltbarkeit", false, 4, 220);
    }

    private List<ItemStack> stacks(MinecraftClient client) {
        List<ItemStack> list = new ArrayList<>();
        if (client.player == null) return list;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack s = client.player.getEquippedStack(slot);
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    @Override
    public int getWidth(MinecraftClient client) { return stacks(client).isEmpty() ? 0 : 60; }

    @Override
    public int getHeight(MinecraftClient client) { return Math.max(16, stacks(client).size() * 17); }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient client, int accent) {
        int y = 0;
        for (ItemStack s : stacks(client)) {
            ctx.drawItem(s, 0, y);
            if (s.isDamageable()) {
                int left = s.getMaxDamage() - s.getDamage();
                float frac = left / (float) s.getMaxDamage();
                int color = frac > 0.5f ? 0xFF22C55E : frac > 0.2f ? 0xFFFBBF24 : 0xFFEF4444;
                String text = percent.get() ? Math.round(frac * 100) + "%" : String.valueOf(left);
                ctx.drawTextWithShadow(client.textRenderer, text, 20, y + 4, color);
            } else if (s.getCount() > 1) {
                ctx.drawTextWithShadow(client.textRenderer, "x" + s.getCount(), 20, y + 4, 0xFFFFFFFF);
            }
            y += 17;
        }
    }
}
