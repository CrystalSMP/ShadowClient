package dev.shadowclient.mixin;

import dev.shadowclient.ShadowClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** „Kein Schadens-Wackeln“: Kamera neigt sich bei Schaden nicht mehr. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void shadowclient$noHurtCam(CallbackInfo ci) {
        if (ShadowClient.modules() != null && ShadowClient.modules().noHurtCam().isEnabled()) {
            ci.cancel();
        }
    }
}
