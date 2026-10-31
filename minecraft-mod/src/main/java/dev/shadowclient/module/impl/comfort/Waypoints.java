package dev.shadowclient.module.impl.comfort;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Speichert Wegpunkte pro Server/Welt und Dimension in config/shadowclient-waypoints.json. */
public final class Waypoints {
    public record Point(String name, int x, int y, int z, int color) {}
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("shadowclient-waypoints.json");
    private static Map<String, List<Point>> all;

    private Waypoints() {}

    private static Map<String, List<Point>> data() {
        if (all == null) {
            try {
                all = Files.exists(FILE) ? GSON.fromJson(Files.readString(FILE), new TypeToken<Map<String, List<Point>>>() {}.getType()) : null;
            } catch (Exception ignored) {}
            if (all == null) all = new HashMap<>();
        }
        return all;
    }

    private static void save() {
        try { Files.writeString(FILE, GSON.toJson(data())); } catch (Exception ignored) {}
    }

    public static String key(MinecraftClient c) {
        if (c.world == null) return null;
        String place;
        if (c.isInSingleplayer() && c.getServer() != null) place = "sp:" + c.getServer().getSaveProperties().getLevelName();
        else { ServerInfo s = c.getCurrentServerEntry(); place = s != null ? s.address.toLowerCase() : "unbekannt"; }
        return place + "|" + c.world.getRegistryKey().getValue();
    }

    public static List<Point> current(MinecraftClient c) {
        String k = key(c);
        return k == null ? List.of() : data().getOrDefault(k, List.of());
    }

    public static Point add(MinecraftClient c, String name, int color) {
        String k = key(c);
        if (k == null || c.player == null) return null;
        Point p = new Point(name, c.player.getBlockX(), c.player.getBlockY(), c.player.getBlockZ(), color);
        List<Point> list = new ArrayList<>(data().getOrDefault(k, List.of()));
        list.removeIf(o -> o.name().equals(name));
        list.add(p);
        data().put(k, list);
        save();
        return p;
    }

    public static void removeNearest(MinecraftClient c) {
        String k = key(c);
        if (k == null || c.player == null) return;
        List<Point> list = new ArrayList<>(data().getOrDefault(k, List.of()));
        list.stream().min((a, b) -> Double.compare(dist(c, a), dist(c, b))).ifPresent(list::remove);
        data().put(k, list);
        save();
    }

    public static double dist(MinecraftClient c, Point p) {
        double dx = p.x() + 0.5 - c.player.getX(), dz = p.z() + 0.5 - c.player.getZ(), dy = p.y() - c.player.getY();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
