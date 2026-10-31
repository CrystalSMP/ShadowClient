package dev.shadowclient.module.impl.comfort;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Nach dem Tod: Koordinaten im Chat und als Wegpunkt „Tod“. */
public class DeathCoordsModule extends Module {
    private boolean wasDead;

    public DeathCoordsModule() {
        super("death_coords", "Todespunkt", "Speichert beim Sterben die Koordinaten als Wegpunkt „Tod“", Category.UTILITY, true);
    }

    @Override
    public void onTick(MinecraftClient c) {
        if (c.player == null) { wasDead = false; return; }
        boolean dead = c.player.isDead() || c.player.getHealth() <= 0;
        if (dead && !wasDead) {
            Waypoints.Point p = Waypoints.add(c, "Tod", 0xF87171);
            if (p != null) c.inGameHud.getChatHud().addMessage(Text.literal("§c[ShadowClient] §fGestorben bei §c" + p.x() + " " + p.y() + " " + p.z() + "§f – als Wegpunkt „Tod“ gespeichert."));
        }
        wasDead = dead;
    }
}
