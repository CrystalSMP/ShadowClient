package dev.shadowclient.module.impl.visual;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.util.OptionHelper;
import net.minecraft.client.MinecraftClient;

/** Maximale Helligkeit über einen erhöhten Gamma-Wert (nur Darstellung). */
public class FullbrightModule extends Module {
    private static final double BRIGHT = 16.0;
    private Double previous;

    public FullbrightModule() {
        super("fullbright", "Fullbright", "Alles hell, auch in Höhlen und nachts", Category.VISUAL, false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        double current = client.options.getGamma().getValue();
        if (current < BRIGHT) {
            if (previous == null) previous = current;
            OptionHelper.setRaw(client.options.getGamma(), BRIGHT);
        }
    }

    @Override
    protected void onDisable() { restore(); }

    /** Setzt den ursprünglichen Wert zurück (auch beim Beenden, damit options.txt gültig bleibt). */
    public void restore() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (previous != null && client != null && client.options != null) {
            OptionHelper.setRaw(client.options.getGamma(), previous);
            previous = null;
        }
    }
}
