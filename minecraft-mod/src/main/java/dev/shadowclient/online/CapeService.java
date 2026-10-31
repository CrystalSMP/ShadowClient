package dev.shadowclient.online;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ShadowClient-Capes: lädt alle 60 s, welche aktiven Nutzer welches Cape tragen.
 * Eingebaute Capes liegen in der Mod, eigene (freigegebene) werden einmal heruntergeladen und als Textur registriert.
 */
public final class CapeService {
    private static final long FETCH_INTERVAL_MS = 15_000;
    private static final String[] BUILTIN = {"shadow", "ember", "frost", "nova", "midnight", "aurora"};
    private static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<UUID, String> refs = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> textures = new ConcurrentHashMap<>();
    private static final Set<String> loading = ConcurrentHashMap.newKeySet();
    private static final Set<String> failed = ConcurrentHashMap.newKeySet();
    private static volatile boolean active;
    private static long lastFetch;

    private CapeService() {}

    public static void start() { active = true; lastFetch = 0; }
    public static void stop() { active = false; refs.clear(); }
    public static int count() { return refs.size(); }

    public static void tick() {
        if (!active) start();   // Modul ist standardmäßig an → onEnable wird beim Start nicht aufgerufen
        if (!OnlineService.configured()) return;
        long now = System.currentTimeMillis();
        if (now - lastFetch > FETCH_INTERVAL_MS) {
            lastFetch = now;
            OnlineService.submit(CapeService::fetch);
        }
    }

    /** Wird beim Rendern oft aufgerufen – nur Map-Lookups. */
    public static Identifier capeFor(UUID uuid) {
        if (!active || uuid == null) return null;
        String ref = refs.get(uuid);
        if (ref == null) return null;
        if (ref.startsWith("b:")) {
            String id = ref.substring(2);
            for (String b : BUILTIN) if (b.equals(id)) return Identifier.of("shadowclient", "textures/capes/" + id + ".png");
            return null;
        }
        Identifier tex = textures.get(ref);
        if (tex == null && !failed.contains(ref) && loading.add(ref)) OnlineService.submit(() -> download(ref));
        return tex;
    }

    private static void fetch() {
        HttpResponse<String> res = OnlineService.get("capes", false);
        if (res == null || res.statusCode() != 200) return;
        try {
            JsonObject capes = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonObject("capes");
            Map<UUID, String> fresh = new ConcurrentHashMap<>();
            for (Map.Entry<String, JsonElement> e : capes.entrySet()) {
                UUID u = BadgeService.parseUuid(e.getKey());
                if (u != null) fresh.put(u, e.getValue().getAsString());
            }
            refs.keySet().retainAll(fresh.keySet());
            refs.putAll(fresh);
        } catch (Exception ignored) {}
    }

    private static void download(String url) {
        try {
            if (!url.startsWith("https://")) return;
            HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200 || res.body().length > 128 * 1024) { failed.add(url); return; }
            byte[] bytes = res.body();
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                try {
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                    Identifier id = Identifier.of("shadowclient", "capes/custom/" + Integer.toHexString(url.hashCode()));
                    client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "shadowclient-cape", img));
                    textures.put(url, id);
                } catch (Exception e) { failed.add(url); }
            });
        } catch (Exception e) {
            failed.add(url);
        } finally {
            loading.remove(url);
        }
    }
}
