package dev.shadowclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Wertet eigene Treffer aus (über Fabric API AttackEntityCallback):
 * Combo-Zähler, Reichweite des letzten Treffers und letztes Ziel für das Target-HUD.
 * Rein lesend – es werden keine Pakete verändert.
 */
public final class CombatTracker {
    private static int combo;
    private static long lastHit;
    private static double lastReach;
    private static LivingEntity lastTarget;
    private static long lastTargetTime;
    private static int lastHurtTime;

    private CombatTracker() {}

    public static void onAttack(PlayerEntity player, Entity target, EntityHitResult hit) {
        if (!(player instanceof ClientPlayerEntity)) return; // nur die eigene Client-Seite zählen
        long now = System.currentTimeMillis();
        combo = now - lastHit > 3000 ? 1 : combo + 1;
        lastHit = now;
        lastReach = hit != null ? player.getEyePos().distanceTo(hit.getPos()) : player.distanceTo(target);
        if (target instanceof LivingEntity living) {
            lastTarget = living;
            lastTargetTime = now;
        }
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null) { combo = 0; lastTarget = null; return; }
        int hurt = client.player.hurtTime;
        if (hurt > 0 && lastHurtTime == 0) combo = 0; // selbst getroffen → Combo vorbei
        lastHurtTime = hurt;
        if (System.currentTimeMillis() - lastHit > 3000) combo = 0;
        if (client.targetedEntity instanceof LivingEntity living) {
            lastTarget = living;
            lastTargetTime = System.currentTimeMillis();
        }
        if (lastTarget != null && (lastTarget.isRemoved() || System.currentTimeMillis() - lastTargetTime > 2500)) {
            lastTarget = null;
        }
    }

    public static int combo() { return combo; }
    public static double lastReach() { return lastReach; }
    public static long lastHitTime() { return lastHit; }
    public static LivingEntity target() { return lastTarget; }
}
