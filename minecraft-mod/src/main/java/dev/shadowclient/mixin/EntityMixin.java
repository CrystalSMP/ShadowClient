package dev.shadowclient.mixin;

import dev.shadowclient.ShadowClient;
import dev.shadowclient.module.impl.visual.FreelookModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: Mausbewegung dreht nur die Kamera, nicht den Spieler. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void shadowclient$freelook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (ShadowClient.modules() == null) return;
        FreelookModule freelook = ShadowClient.modules().freelook();
        if (freelook.isActive() && (Object) this == MinecraftClient.getInstance().player) {
            freelook.addRotation(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}
