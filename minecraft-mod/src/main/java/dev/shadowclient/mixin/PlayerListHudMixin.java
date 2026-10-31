package dev.shadowclient.mixin;

import dev.shadowclient.online.BadgeService;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** ✦-Abzeichen auch in der Tab-Liste (dort nutzt der Server eigene Namen mit Rang/Team). */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void shadowclient$badgeInTab(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        UUID id = BadgeService.profileId(entry.getProfile());
        if (id == null) return;
        Text decorated = BadgeService.decorate(id, cir.getReturnValue());
        if (decorated != null) cir.setReturnValue(decorated);
    }
}
