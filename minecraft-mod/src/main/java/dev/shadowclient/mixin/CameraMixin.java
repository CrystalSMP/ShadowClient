package dev.shadowclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.shadowclient.ShadowClient;
import dev.shadowclient.module.impl.visual.FreelookModule;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Freelook: Kamera nutzt die freie Rotation statt der Blickrichtung des Spielers.
 * WrapOperation (MixinExtras) statt Redirect, damit andere Mods (Iris, Flashback …)
 * dieselbe Stelle ebenfalls verändern können.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @WrapOperation(method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0),
            require = 0)
    private void shadowclient$freelookRotation(Camera camera, float yaw, float pitch, Operation<Void> original) {
        FreelookModule freelook = ShadowClient.modules() == null ? null : ShadowClient.modules().freelook();
        if (freelook != null && freelook.isActive()) {
            original.call(camera, freelook.yaw(), freelook.pitch());
        } else {
            original.call(camera, yaw, pitch);
        }
    }
}
