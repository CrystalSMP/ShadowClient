package dev.shadowclient.mixin;

import dev.shadowclient.ShadowClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blendet Vanilla-Fadenkreuz bzw. Scoreboard aus, wenn die passenden Module aktiv sind. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void shadowclient$hideVanillaCrosshair(CallbackInfo ci) {
        if (ShadowClient.modules() != null && ShadowClient.modules().crosshair().isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void shadowclient$hideScoreboard(CallbackInfo ci) {
        if (ShadowClient.modules() != null && ShadowClient.modules().hideScoreboard().isEnabled()) {
            ci.cancel();
        }
    }
}
