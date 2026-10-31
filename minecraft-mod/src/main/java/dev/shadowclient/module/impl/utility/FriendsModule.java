package dev.shadowclient.module.impl.utility;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.setting.BooleanSetting;

/**
 * Freundesliste über das ShadowClient-Backend. Standardmäßig AUS.
 * Fenster: Taste O. Sendet deine UUID, deinen Namen und (optional) den aktuellen Server.
 */
public class FriendsModule extends Module {
    public final BooleanSetting shareServer = add(new BooleanSetting("share_server", "Meinen Server zeigen", true));
    public final BooleanSetting notifications = add(new BooleanSetting("notifications", "Benachrichtigungen", true));
    public final BooleanSetting partyFollow = add(new BooleanSetting("party_follow", "Party-Leader automatisch folgen", true));
    public final BooleanSetting dmSound = add(new BooleanSetting("dm_sound", "Ton bei Nachrichten", true));

    public FriendsModule() {
        super("friends", "Freunde", "Freundesliste mit Online-Status (Fenster: Taste O)", Category.ONLINE, false);
    }
}
