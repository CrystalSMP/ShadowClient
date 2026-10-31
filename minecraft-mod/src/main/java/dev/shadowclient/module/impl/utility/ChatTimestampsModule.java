package dev.shadowclient.module.impl.utility;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.setting.BooleanSetting;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Uhrzeit vor jeder Chat-Nachricht (siehe ChatHudMixin). */
public class ChatTimestampsModule extends Module {
    private static final DateTimeFormatter WITH_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter NO_SECONDS = DateTimeFormatter.ofPattern("HH:mm");
    public final BooleanSetting seconds = add(new BooleanSetting("seconds", "Sekunden", false));

    public ChatTimestampsModule() {
        super("chat_timestamps", "Chat-Zeitstempel", "Uhrzeit vor jeder Chat-Nachricht", Category.UTILITY, false);
    }

    public Text decorate(Text message) {
        String time = LocalTime.now().format(seconds.get() ? WITH_SECONDS : NO_SECONDS);
        return Text.literal("[" + time + "] ").formatted(Formatting.GRAY).append(message);
    }
}
