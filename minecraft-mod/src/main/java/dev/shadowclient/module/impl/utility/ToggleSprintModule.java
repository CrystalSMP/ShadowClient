package dev.shadowclient.module.impl.utility;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import net.minecraft.client.MinecraftClient;

/** Hält die Sprint-Taste dauerhaft gedrückt, solange das Modul aktiv ist. */
public class ToggleSprintModule extends Module {
    public ToggleSprintModule() {
        super("toggle_sprint", "Toggle Sprint", "Immer sprinten, ohne die Taste zu halten", Category.UTILITY, false);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player != null && client.currentScreen == null) {
            client.options.sprintKey.setPressed(true);
        }
    }

    @Override
    protected void onDisable() {
        MinecraftClient.getInstance().options.sprintKey.setPressed(false);
    }
}
