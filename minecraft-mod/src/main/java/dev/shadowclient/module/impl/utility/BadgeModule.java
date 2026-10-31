package dev.shadowclient.module.impl.utility;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.setting.BooleanSetting;
import dev.shadowclient.online.BadgeService;
import net.minecraft.client.MinecraftClient;

/**
 * ShadowClient-Abzeichen (✦) vor dem Namen anderer ShadowClient-Nutzer.
 * Standardmäßig AUS. Erst nach Einschalten werden Daten an den ShadowClient-Server gesendet.
 */
public class BadgeModule extends Module {
    public final BooleanSetting showOthers = add(new BooleanSetting("show_others", "Abzeichen anderer anzeigen", true));
    public final BooleanSetting shareMe = add(new BooleanSetting("share_me", "Mich als Nutzer zeigen", true));

    public BadgeModule() {
        super("badge", "ShadowClient-Abzeichen",
                "Zeigt ✦ bei anderen Nutzern. Sendet deine UUID und deinen Namen an den ShadowClient-Server.",
                Category.ONLINE, false);
    }

    @Override protected void onEnable() { BadgeService.start(); }
    @Override protected void onDisable() { BadgeService.stop(); }

    @Override
    public void onTick(MinecraftClient client) {
        BadgeService.tick(client, showOthers.get(), shareMe.get());
    }
}
