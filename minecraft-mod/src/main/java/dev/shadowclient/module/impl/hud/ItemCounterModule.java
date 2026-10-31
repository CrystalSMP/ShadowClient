package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.HudModule;
import dev.shadowclient.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Zählt wichtige Items im Inventar (Totems, Perlen, Goldäpfel, Pfeile). */
public class ItemCounterModule extends HudModule {
    private final BooleanSetting totems = add(new BooleanSetting("totems", "Totems", true));
    private final BooleanSetting pearls = add(new BooleanSetting("pearls", "Enderperlen", true));
    private final BooleanSetting apples = add(new BooleanSetting("apples", "Goldäpfel", true));
    private final BooleanSetting arrows = add(new BooleanSetting("arrows", "Pfeile", true));
    private final BooleanSetting hideZero = add(new BooleanSetting("hide_zero", "Leere ausblenden", true));

    public ItemCounterModule() {
        super("item_counter", "Item-Zähler", "Totems, Perlen, Goldäpfel und Pfeile im Inventar", false, 4, 420);
    }

    private record Entry(ItemStack icon, int count) {}

    private List<Entry> entries(MinecraftClient client) {
        List<Entry> out = new ArrayList<>();
        if (client.player == null) return out;
        PlayerInventory inv = client.player.getInventory();
        if (totems.get()) addEntry(out, inv, Items.TOTEM_OF_UNDYING);
        if (pearls.get()) addEntry(out, inv, Items.ENDER_PEARL);
        if (apples.get()) {
            int golden = count(inv, Items.GOLDEN_APPLE) + count(inv, Items.ENCHANTED_GOLDEN_APPLE);
            if (golden > 0 || !hideZero.get()) out.add(new Entry(new ItemStack(Items.GOLDEN_APPLE), golden));
        }
        if (arrows.get()) {
            int a = count(inv, Items.ARROW) + count(inv, Items.SPECTRAL_ARROW) + count(inv, Items.TIPPED_ARROW);
            if (a > 0 || !hideZero.get()) out.add(new Entry(new ItemStack(Items.ARROW), a));
        }
        return out;
    }

    private void addEntry(List<Entry> out, PlayerInventory inv, Item item) {
        int c = count(inv, item);
        if (c > 0 || !hideZero.get()) out.add(new Entry(new ItemStack(item), c));
    }

    private static int count(PlayerInventory inv, Item item) {
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) total += s.getCount();
        }
        return total;
    }

    @Override
    public int getWidth(MinecraftClient client) {
        return entries(client).isEmpty() ? 0 : 44;
    }

    @Override
    public int getHeight(MinecraftClient client) {
        return Math.max(16, entries(client).size() * 17);
    }

    @Override
    protected void draw(DrawContext ctx, MinecraftClient client, int accent) {
        int y = 0;
        for (Entry e : entries(client)) {
            ctx.drawItem(e.icon(), 0, y);
            ctx.drawTextWithShadow(client.textRenderer, String.valueOf(e.count()), 20, y + 4,
                    e.count() == 0 ? 0xFFEF4444 : 0xFFFFFFFF);
            y += 17;
        }
    }
}
