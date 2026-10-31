package dev.shadowclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.shadowclient.ShadowClient;
import dev.shadowclient.hud.HudModule;
import dev.shadowclient.module.Module;
import dev.shadowclient.module.ModuleManager;
import dev.shadowclient.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Speichert Modul-Zustände dauerhaft in config/shadowclient.json (im Spielordner des Profils).
 * Schreiben passiert atomar über eine Temp-Datei.
 */
public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("shadowclient.json");
    private final ModuleManager modules;
    /** Menü-Panel-Positionen, Schlüssel = Kategorie-Name */
    private final Map<String, int[]> panelPositions = new HashMap<>();

    public ConfigManager(ModuleManager modules) {
        this.modules = modules;
    }

    public Map<String, int[]> panelPositions() { return panelPositions; }

    public void load() {
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject mods = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
            for (Module m : modules.all()) {
                if (!mods.has(m.getId())) continue;
                JsonObject o = mods.getAsJsonObject(m.getId());
                if (o.has("enabled") && m.isToggleable()) m.setEnabled(o.get("enabled").getAsBoolean());
                if (o.has("settings")) {
                    JsonObject s = o.getAsJsonObject("settings");
                    for (Setting<?> setting : m.getSettings()) setting.fromJson(s.get(setting.getId()));
                }
                if (m instanceof HudModule hud && o.has("x") && o.has("y")) {
                    hud.setPosition(o.get("x").getAsInt(), o.get("y").getAsInt());
                }
                if (m instanceof HudModule hud && o.has("scale")) hud.setScale(o.get("scale").getAsFloat());
            }
            if (root.has("panels")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("panels").entrySet()) {
                    JsonObject p = e.getValue().getAsJsonObject();
                    panelPositions.put(e.getKey(), new int[]{p.get("x").getAsInt(), p.get("y").getAsInt()});
                }
            }
        } catch (Exception e) {
            ShadowClient.LOGGER.error("Konfiguration {} ist beschädigt, verwende Standardwerte", file, e);
            try {
                Files.move(file, file.resolveSibling("shadowclient.json.broken"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject mods = new JsonObject();
        for (Module m : modules.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            JsonObject s = new JsonObject();
            for (Setting<?> setting : m.getSettings()) s.add(setting.getId(), setting.toJson());
            o.add("settings", s);
            if (m instanceof HudModule hud) {
                o.addProperty("x", hud.getX());
                o.addProperty("y", hud.getY());
                o.addProperty("scale", hud.getScale());
            }
            mods.add(m.getId(), o);
        }
        root.add("modules", mods);
        JsonObject panels = new JsonObject();
        panelPositions.forEach((k, v) -> {
            JsonObject p = new JsonObject();
            p.addProperty("x", v[0]);
            p.addProperty("y", v[1]);
            panels.add(k, p);
        });
        root.add("panels", panels);

        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("shadowclient.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            ShadowClient.LOGGER.error("Konnte Konfiguration nicht speichern", e);
        }
    }
}
