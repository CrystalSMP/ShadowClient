package dev.shadowclient.module.impl.visual;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.setting.BooleanSetting;
import dev.shadowclient.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Eigenes Fadenkreuz. Ersetzt das Vanilla-Fadenkreuz (siehe InGameHudMixin). */
public class CrosshairModule extends Module {
    private static final int[] COLORS = {0xFFFFFFFF, 0, 0xFF22C55E, 0xFFEF4444, 0xFFFBBF24, 0xFF000000};
    private static final String[] COLOR_NAMES = {"Weiß", "Akzent", "Grün", "Rot", "Gelb", "Schwarz"};

    public final NumberSetting size = add(new NumberSetting("size", "Länge", 5, 1, 15, 1));
    public final NumberSetting gap = add(new NumberSetting("gap", "Abstand", 2, 0, 10, 1));
    public final NumberSetting thickness = add(new NumberSetting("thickness", "Dicke", 1, 1, 4, 1));
    public final NumberSetting color = add(new NumberSetting("color", "Farbe", 0, 0, COLORS.length - 1, 1));
    public final BooleanSetting dot = add(new BooleanSetting("dot", "Mittelpunkt", false));
    public final BooleanSetting outline = add(new BooleanSetting("outline", "Umrandung", true));

    public CrosshairModule() {
        super("crosshair", "Fadenkreuz", "Eigenes, anpassbares Fadenkreuz", Category.VISUAL, false);
    }

    public String colorName() { return COLOR_NAMES[index()]; }

    private int index() { return (int) Math.max(0, Math.min(COLORS.length - 1, Math.round(color.get()))); }

    public void render(DrawContext ctx, MinecraftClient client, int accent) {
        if (!client.options.getPerspective().isFirstPerson()) return;
        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;
        int c = index() == 1 ? accent : COLORS[index()];
        int len = (int) Math.round(size.get());
        int g = (int) Math.round(gap.get());
        int t = (int) Math.round(thickness.get());
        int half = t / 2;

        int[][] bars = {
                {cx - half, cy - g - len, cx - half + t, cy - g},          // oben
                {cx - half, cy + g + 1, cx - half + t, cy + g + 1 + len},  // unten
                {cx - g - len, cy - half, cx - g, cy - half + t},          // links
                {cx + g + 1, cy - half, cx + g + 1 + len, cy - half + t},  // rechts
        };
        if (outline.get()) {
            for (int[] b : bars) ctx.fill(b[0] - 1, b[1] - 1, b[2] + 1, b[3] + 1, 0xC0000000);
            if (dot.get()) ctx.fill(cx - half - 1, cy - half - 1, cx - half + t + 1, cy - half + t + 1, 0xC0000000);
        }
        for (int[] b : bars) ctx.fill(b[0], b[1], b[2], b[3], c);
        if (dot.get()) ctx.fill(cx - half, cy - half, cx - half + t, cy - half + t, c);
    }
}
