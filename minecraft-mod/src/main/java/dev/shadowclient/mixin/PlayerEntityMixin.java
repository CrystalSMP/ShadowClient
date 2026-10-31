package dev.shadowclient.mixin;

import dev.shadowclient.online.BadgeService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hängt das ShadowClient-Abzeichen vor den Namen von Spielern, die ShadowClient nutzen (nur lokal sichtbar). */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void shadowclient$badge(CallbackInfoReturnable<Text> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        Text decorated = BadgeService.decorate(self.getUuid(), cir.getReturnValue());
        if (decorated != null) cir.setReturnValue(decorated);
    }
}
