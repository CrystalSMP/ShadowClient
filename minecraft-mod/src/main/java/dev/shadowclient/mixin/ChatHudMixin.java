package dev.shadowclient.mixin;

import dev.shadowclient.ShadowClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat-Zeitstempel: Uhrzeit vor neue Nachrichten setzen. */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private Text shadowclient$timestamp(Text message) {
        if (ShadowClient.modules() != null && ShadowClient.modules().chatTimestamps().isEnabled()) {
            return ShadowClient.modules().chatTimestamps().decorate(message);
        }
        return message;
    }
}
