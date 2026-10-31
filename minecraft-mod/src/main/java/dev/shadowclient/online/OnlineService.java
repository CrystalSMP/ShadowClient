package dev.shadowclient.online;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.shadowclient.ShadowClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.session.Session;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gemeinsame Verbindung zum ShadowClient-Backend.
 * Anmeldung einmalig mit Mojang-Prüfung (joinServer → hasJoined), danach Sitzungs-Token.
 * Alle Netzwerkaufrufe laufen in einem eigenen Hintergrund-Thread.
 */
public final class OnlineService {
    private static final long PRESENCE_INTERVAL_MS = 20_000;
    private static final long AUTH_RETRY_MS = 15_000;

    private static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ShadowClient-Online");
        t.setDaemon(true);
        return t;
    });

    private static volatile String token;
    private static volatile String status = "Aus";
    private static long lastPresence, lastAuthAttempt;
    private static volatile boolean showBadge, shareServer;

    private OnlineService() {}

    public static boolean configured() { return !OnlineConfig.backendUrl().contains("YOUR-PROJECT"); }
    public static boolean loggedIn() { return token != null; }
    public static String status() { return status; }

    public static void submit(Runnable task) { executor.execute(task); }

    /** Jeden Tick aufrufen. {@code needed}: irgendein Online-Modul ist aktiv. */
    public static void tick(MinecraftClient client, boolean needed, boolean badge, boolean share) {
        showBadge = badge;
        shareServer = share;
        if (!configured()) { status = "Server nicht eingerichtet"; return; }
        if (!needed) {
            if (token != null) {
                String old = token;
                token = null;
                submit(() -> post("logout", new JsonObject(), old));
            }
            status = "Aus";
            return;
        }
        long now = System.currentTimeMillis();
        String server = currentServer(client);
        if (token == null) {
            if (now - lastAuthAttempt > AUTH_RETRY_MS) {
                lastAuthAttempt = now;
                lastPresence = now;
                status = "Melde an…";
                submit(() -> authenticate(server));
            }
        } else if (now - lastPresence > PRESENCE_INTERVAL_MS || !stateKey(server).equals(lastState)) {
            lastPresence = now;
            lastState = stateKey(server);
            JsonObject body = flags(server);
            submit(() -> post("presence", body, token));
        }
    }

    private static String lastState = "";
    private static String stateKey(String server) { return showBadge + "|" + shareServer + "|" + server; }

    private static JsonObject flags(String server) {
        JsonObject o = new JsonObject();
        o.addProperty("showBadge", showBadge);
        o.addProperty("shareServer", shareServer);
        if (server != null) o.addProperty("server", server);
        return o;
    }

    /** Server, auf dem man gerade spielt (für Freunde sichtbar, wenn erlaubt). */
    public static String currentServer(MinecraftClient client) {
        if (client.world == null) return "Im Menü";
        if (client.isInSingleplayer()) return "Einzelspieler";
        ServerInfo info = client.getCurrentServerEntry();
        return info == null ? "Online" : info.address;
    }

    private static void authenticate(String server) {
        MinecraftClient client = MinecraftClient.getInstance();
        Session session = client.getSession();
        if (session == null || session.getUuidOrNull() == null) { status = "Kein Konto"; return; }
        try {
            byte[] random = new byte[16];
            new SecureRandom().nextBytes(random);
            String serverId = HexFormat.of().formatHex(random);
            // Offizielle Mojang-Anmeldung – derselbe Aufruf wie beim Betreten eines Online-Servers
            client.getApiServices().sessionService().joinServer(session.getUuidOrNull(), session.getAccessToken(), serverId);

            JsonObject body = flags(server);
            body.addProperty("username", session.getUsername());
            body.addProperty("serverId", serverId);
            HttpResponse<String> res = post("heartbeat", body, null);
            if (res != null && res.statusCode() == 200) {
                token = JsonParser.parseString(res.body()).getAsJsonObject().get("token").getAsString();
                status = "Verbunden";
            } else {
                status = "Anmeldung abgelehnt" + (res == null ? "" : " (" + res.statusCode() + ")");
            }
        } catch (Exception e) {
            status = "Anmeldung fehlgeschlagen";
            ShadowClient.LOGGER.warn("Online-Anmeldung fehlgeschlagen: {}", e.getMessage());
        }
    }

    private static HttpRequest.Builder request(String path, String tok) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(OnlineConfig.backendUrl() + "/" + path))
                .timeout(Duration.ofSeconds(15))
                .header("apikey", OnlineConfig.anonKey());
        if (tok != null) b.header("x-shadow-token", tok);
        return b;
    }

    /** POST (Hintergrund-Thread). Gibt null bei Netzwerkfehler zurück. */
    public static HttpResponse<String> post(String path, JsonObject body, String tok) {
        try {
            HttpResponse<String> res = http.send(request(path, tok)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
            handleAuth(res, tok);
            return res;
        } catch (Exception e) {
            status = "Offline";
            return null;
        }
    }

    /** GET (Hintergrund-Thread). */
    public static HttpResponse<String> get(String path, boolean authenticated) {
        String tok = authenticated ? token : null;
        if (authenticated && tok == null) return null;
        try {
            HttpResponse<String> res = http.send(request(path, tok).GET().build(), HttpResponse.BodyHandlers.ofString());
            handleAuth(res, tok);
            return res;
        } catch (Exception e) {
            status = "Offline";
            return null;
        }
    }

    public static HttpResponse<String> postAuthenticated(String path, JsonObject body) {
        String tok = token;
        return tok == null ? null : post(path, body, tok);
    }

    private static void handleAuth(HttpResponse<String> res, String usedToken) {
        if (res.statusCode() == 401 && usedToken != null && usedToken.equals(token)) {
            token = null; // Sitzung abgelaufen → beim nächsten Tick neu anmelden
            lastAuthAttempt = 0;
        }
    }
}
