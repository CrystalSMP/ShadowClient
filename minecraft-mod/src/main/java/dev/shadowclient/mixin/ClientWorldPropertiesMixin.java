package dev.shadowclient.mixin;

import dev.shadowclient.module.impl.visual.TimeChangerModule;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** „Zeit lokal“: liefert die gewünschte Tageszeit statt der Serverzeit (nur Darstellung). */
@Mixin(ClientWorld.Properties.class)
public abstract class ClientWorldPropertiesMixin {
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true, require = 0)
    private void shadowclient$time(CallbackInfoReturnable<Long> cir) {
        TimeChangerModule m = TimeChangerModule.INSTANCE;
        if (m != null && m.isEnabled()) cir.setReturnValue(m.timeOfDay());
    }
}
