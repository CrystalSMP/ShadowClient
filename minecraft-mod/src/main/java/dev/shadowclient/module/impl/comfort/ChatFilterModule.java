package dev.shadowclient.module.impl.comfort;

import dev.shadowclient.module.Category;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.setting.BooleanSetting;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Blendet Chatnachrichten mit bestimmten Wörtern aus.
 * Wörter stehen in config/shadowclient-chatfilter.txt (eine Zeile pro Wort).
 */
public class ChatFilterModule extends Module {
    public static ChatFilterModule INSTANCE;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("shadowclient-chatfilter.txt");
    private final BooleanSetting ads = add(new BooleanSetting("ads", "Server-Werbung ausblenden", true));
    private List<String> words = List.of();
    private long loaded;

    public ChatFilterModule() {
        super("chat_filter", "Chat-Filter", "Versteckt Nachrichten mit Wörtern aus shadowclient-chatfilter.txt", Category.UTILITY, false);
        INSTANCE = this;
    }

    private List<String> words() {
        if (System.currentTimeMillis() - loaded > 10_000) {
            loaded = System.currentTimeMillis();
            try {
                if (!Files.exists(FILE)) Files.writeString(FILE, "# Ein Wort oder eine Phrase pro Zeile – Groß-/Kleinschreibung egal\n");
                List<String> out = new ArrayList<>();
                for (String l : Files.readAllLines(FILE)) { l = l.trim().toLowerCase(Locale.ROOT); if (!l.isEmpty() && !l.startsWith("#")) out.add(l); }
                words = out;
            } catch (Exception ignored) {}
        }
        return words;
    }

    /** true = Nachricht anzeigen. */
    public boolean allow(String text) {
        if (!isEnabled()) return true;
        String t = text.toLowerCase(Locale.ROOT);
        if (ads.get() && (t.matches(".*\\b[a-z0-9-]+\\.(net|de|eu|com|gg|me)\\b.*") && (t.contains("join") || t.contains("komm") || t.contains("ip:")))) return false;
        for (String w : words()) if (t.contains(w)) return false;
        return true;
    }
}
