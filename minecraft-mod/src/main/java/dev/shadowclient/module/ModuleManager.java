package dev.shadowclient.module;

import dev.shadowclient.module.impl.ClientSettingsModule;
import dev.shadowclient.module.impl.hud.*;
import dev.shadowclient.module.impl.utility.BadgeModule;
import dev.shadowclient.module.impl.utility.ChatTimestampsModule;
import dev.shadowclient.module.impl.utility.FriendsModule;
import dev.shadowclient.module.impl.utility.ToggleSprintModule;
import dev.shadowclient.module.impl.visual.*;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final ClientSettingsModule clientSettings;
    private final CrosshairModule crosshair;
    private final NoHurtCamModule noHurtCam;
    private final FullbrightModule fullbright;
    private final ZoomModule zoom;
    private final FreelookModule freelook;
    private final LowFireModule lowFire;
    private final ChatTimestampsModule chatTimestamps;
    private final LowHealthWarningModule lowHealth;
    private final HideScoreboardModule hideScoreboard;
    private final BadgeModule badge;
    private dev.shadowclient.module.impl.comfort.WaypointsModule waypoints;
    private final FriendsModule friends;

    public ModuleManager() {
        clientSettings = register(new ClientSettingsModule());
        // HUD
        register(new FpsModule());
        register(new PingModule());
        register(new CoordinatesModule());
        register(new CpsModule());
        register(new ClockModule());
        register(new ServerAddressModule());
        register(new SpeedModule());
        register(new DayCounterModule());
        register(new MemoryModule());
        register(new KeystrokesModule());
        register(new PotionEffectsModule());
        register(new TargetHudModule());
        register(new ComboModule());
        register(new ReachModule());
        register(new ItemCounterModule());
        register(new BiomeModule());
        register(new SaturationModule());
        register(new SessionTimeModule());
        // Optik
        crosshair = register(new CrosshairModule());
        fullbright = register(new FullbrightModule());
        zoom = register(new ZoomModule());
        freelook = register(new FreelookModule());
        noHurtCam = register(new NoHurtCamModule());
        lowFire = register(new LowFireModule());
        register(new ClearWeatherModule());
        lowHealth = register(new LowHealthWarningModule());
        hideScoreboard = register(new HideScoreboardModule());
        // Komfort & Online
        register(new ToggleSprintModule());
        chatTimestamps = register(new ChatTimestampsModule());
        badge = register(new BadgeModule());
        friends = register(new FriendsModule());
        register(new dev.shadowclient.module.impl.visual.CapesModule());
        // v1.0: neue Module
        register(new dev.shadowclient.module.impl.hud.CompassModule());
        register(new dev.shadowclient.module.impl.hud.LightLevelModule());
        register(new dev.shadowclient.module.impl.hud.TpsModule());
        register(new dev.shadowclient.module.impl.hud.PlayerCountModule());
        register(new dev.shadowclient.module.impl.hud.PacksModule());
        register(new dev.shadowclient.module.impl.hud.BowChargeModule());
        register(new dev.shadowclient.module.impl.hud.KillCounterModule());
        register(new dev.shadowclient.module.impl.visual.DamageIndicatorModule());
        register(new dev.shadowclient.module.impl.visual.TimeChangerModule());
        waypoints = register(new dev.shadowclient.module.impl.comfort.WaypointsModule());
        register(new dev.shadowclient.module.impl.comfort.DeathCoordsModule());
        register(new dev.shadowclient.module.impl.comfort.AutoGgModule());
        register(new dev.shadowclient.module.impl.comfort.ChatFilterModule());
    }

    private <M extends Module> M register(M module) {
        modules.add(module);
        return module;
    }

    public void tick(MinecraftClient client) {
        dev.shadowclient.util.CombatTracker.tick(client);
        // Online-Verbindung nur, wenn Abzeichen oder Freunde aktiv sind
        boolean share = !friends.isEnabled() || friends.shareServer.get();
        dev.shadowclient.online.OnlineService.tick(client, badge.isEnabled() || friends.isEnabled(),
                badge.isEnabled() && badge.shareMe.get(), share);
        dev.shadowclient.online.FriendsService.tick(friends.isEnabled(), friends.notifications.get());
        dev.shadowclient.online.FriendsService.joinTick(client);
        if (friends.isEnabled()) dev.shadowclient.online.SocialService.tick(client, friends.partyFollow.get());
        for (Module m : modules) {
            if (m.isEnabled()) m.onTick(client);
        }
    }

    /** Setzt veränderte Vanilla-Optionen zurück (vor dem Speichern/Beenden). */
    public void restoreVanillaOptions() {
        fullbright.restore();
        zoom.restore();
    }

    public List<Module> all() { return Collections.unmodifiableList(modules); }

    public List<Module> byCategory(Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).toList();
    }

    public Optional<Module> byId(String id) {
        return modules.stream().filter(m -> m.getId().equals(id)).findFirst();
    }

    public ClientSettingsModule clientSettings() { return clientSettings; }
    public CrosshairModule crosshair() { return crosshair; }
    public NoHurtCamModule noHurtCam() { return noHurtCam; }
    public FreelookModule freelook() { return freelook; }
    public LowFireModule lowFire() { return lowFire; }
    public ChatTimestampsModule chatTimestamps() { return chatTimestamps; }
    public LowHealthWarningModule lowHealth() { return lowHealth; }
    public HideScoreboardModule hideScoreboard() { return hideScoreboard; }
    public BadgeModule badge() { return badge; }
    public dev.shadowclient.module.impl.comfort.WaypointsModule waypoints() { return waypoints; }
    public FriendsModule friends() { return friends; }
}
