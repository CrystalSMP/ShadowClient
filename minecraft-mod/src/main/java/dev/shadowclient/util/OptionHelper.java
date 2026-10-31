package dev.shadowclient.util;

import dev.shadowclient.mixin.SimpleOptionAccessor;
import net.minecraft.client.option.SimpleOption;

/**
 * Setzt Werte einer Vanilla-Option direkt, auch außerhalb des Schieberegler-Bereichs
 * (z. B. Gamma für Fullbright oder FOV für Zoom). Wird vor dem Speichern immer zurückgesetzt.
 */
public final class OptionHelper {
    private OptionHelper() {}

    public static void setRaw(SimpleOption<?> option, Object value) {
        ((SimpleOptionAccessor) (Object) option).shadowclient$setValueRaw(value);
    }
}
