package dev.shadowclient.mixin;

import dev.shadowclient.ShadowClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Kleines Feuer: verschiebt das Feuer-Overlay nach unten. */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @ModifyArg(method = "renderFireOverlay",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"),
            index = 1, require = 0)
    private static float shadowclient$lowerFire(float y) {
        if (ShadowClient.modules() != null && ShadowClient.modules().lowFire().isEnabled()) {
            return y - ShadowClient.modules().lowFire().offset.get().floatValue();
        }
        return y;
    }
}
