package dev.shadowclient.module.impl.hud;

import dev.shadowclient.hud.TextHudModule;
import dev.shadowclient.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Zählt Kills: Ziele, die du getroffen hast und die innerhalb von 4 s sterben. */
public class KillCounterModule extends TextHudModule {
    private static final Map<Integer, Long> recent = new HashMap<>();
    private static final Map<Integer, LivingEntity> entities = new HashMap<>();
    private final BooleanSetting playersOnly = add(new BooleanSetting("players_only", "Nur Spieler", true));
    private int kills;

    public KillCounterModule() {
        super("kill_counter", "Kill-Zähler", "Kills in dieser Sitzung (zurückgesetzt beim Serverwechsel)", false, 4, 260);
    }

    public static void onAttack(Entity target) {
        if (target instanceof LivingEntity l) { recent.put(l.getId(), System.currentTimeMillis()); entities.put(l.getId(), l); }
    }

    @Override
    public void onTick(MinecraftClient c) {
        if (c.world == null) { kills = 0; recent.clear(); entities.clear(); return; }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> it = recent.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> e = it.next();
            LivingEntity l = entities.get(e.getKey());
            if (l != null && (l.isDead() || l.getHealth() <= 0)) {
                if (!playersOnly.get() || l instanceof PlayerEntity) kills++;
                it.remove(); entities.remove(e.getKey());
            } else if (now - e.getValue() > 4000) {
                it.remove(); entities.remove(e.getKey());
            }
        }
    }

    @Override
    protected String getText(MinecraftClient c) { return c.world == null ? null : "Kills " + kills; }
}
