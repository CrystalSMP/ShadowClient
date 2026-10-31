package dev.shadowclient.module.impl.visual;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;

/** Verhindert das Kamera-Wackeln bei Schaden (siehe GameRendererMixin). */
public class NoHurtCamModule extends Module {
    public NoHurtCamModule() {
        super("no_hurt_cam", "Kein Schadens-Wackeln", "Kamera neigt sich bei Schaden nicht", Category.VISUAL, false);
    }
}
