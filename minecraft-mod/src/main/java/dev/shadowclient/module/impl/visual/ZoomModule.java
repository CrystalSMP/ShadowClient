package dev.shadowclient.module.impl.visual;

import dev.shadowclient.ShadowClient;
import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.setting.BooleanSetting;
import dev.shadowclient.module.setting.NumberSetting;
import dev.shadowclient.util.OptionHelper;
import net.minecraft.client.MinecraftClient;

/** Zoom solange die Zoom-Taste (Standard: C) gehalten wird. */
public class ZoomModule extends Module {
    public final NumberSetting factor = add(new NumberSetting("factor", "Zoom-Stärke", 4, 2, 10, 0.5));
    public final BooleanSetting cinematic = add(new BooleanSetting("cinematic", "Weiche Kamera", true));

    private Integer savedFov;
    private boolean savedSmooth;

    public ZoomModule() {
        super("zoom", "Zoom", "Taste gedrückt halten zum Heranzoomen (Standard: C)", Category.VISUAL, true);
    }

    @Override
    public void onTick(MinecraftClient client) {
        boolean held = ShadowClient.zoomKey().isPressed() && client.currentScreen == null;
        if (held && savedFov == null) {
            savedFov = client.options.getFov().getValue();
            savedSmooth = client.options.smoothCameraEnabled;
            int zoomed = (int) Math.max(1, Math.round(savedFov / factor.get()));
            OptionHelper.setRaw(client.options.getFov(), zoomed);
            if (cinematic.get()) client.options.smoothCameraEnabled = true;
        } else if (!held && savedFov != null) {
            restore();
        }
    }

    @Override
    protected void onDisable() { restore(); }

    public void restore() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (savedFov != null && client != null && client.options != null) {
            OptionHelper.setRaw(client.options.getFov(), savedFov);
            client.options.smoothCameraEnabled = savedSmooth;
            savedFov = null;
        }
    }
}
