package dev.shadowclient.mixin;

import dev.shadowclient.online.CapeService;
import dev.shadowclient.online.SkinSwap;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ShadowClient-Capes: ersetzt das Cape in den Skin-Daten, wenn der Spieler eins gewählt hat. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = {"getSkin", "getSkinTextures"}, at = @At("RETURN"), cancellable = true, require = 0)
    private void shadowclient$cape(CallbackInfoReturnable<Object> cir) {
        Identifier cape = CapeService.capeFor(((Entity) (Object) this).getUuid());
        if (cape == null) return;
        Object swapped = SkinSwap.withCape(cir.getReturnValue(), cape);
        if (swapped != null) cir.setReturnValue(swapped);
    }
}
