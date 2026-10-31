package dev.shadowclient.module.impl.visual;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.setting.NumberSetting;

/** Schiebt das Feuer-Overlay nach unten, damit man beim Brennen mehr sieht (siehe InGameOverlayRendererMixin). */
public class LowFireModule extends Module {
    public final NumberSetting offset = add(new NumberSetting("offset", "Absenken", 0.3, 0.05, 0.6, 0.05));

    public LowFireModule() {
        super("low_fire", "Kleines Feuer", "Feuer-Overlay tiefer, mehr Sicht beim Brennen", Category.VISUAL, false);
    }
}
