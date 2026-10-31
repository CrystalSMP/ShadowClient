package dev.shadowclient.online;

import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Freundesliste: laden, Anfragen verwalten, Benachrichtigungen im Chat. */
public final class FriendsService {
    public record Friend(String uuid, String name, boolean online, String server, String status, int unread) {}

    private static final long FETCH_INTERVAL_MS = 10_000;
    private static volatile List<Friend> friends = List.of();
    private static volatile List<Friend> incoming = List.of();
    private static volatile List<Friend> outgoing = List.of();
    private static volatile int version;
    private static volatile String message;
    private static volatile boolean loaded;
    private static long lastFetch;
    private static boolean notifications = true;

    private FriendsService() {}

    public static List<Friend> friends() { return friends; }
    public static List<Friend> incoming() { return incoming; }
    public static List<Friend> outgoing() { return outgoing; }
    /** Erhöht sich bei jeder Änderung – das Fenster baut sich dann neu auf. */
    public static int version() { return version; }
    public static boolean loaded() { return loaded; }
    public static String message() { return message; }

    public static void tick(boolean enabled, boolean notify) {
        notifications = notify;
        if (!enabled) {
            if (loaded) { friends = List.of(); incoming = List.of(); outgoing = List.of(); loaded = false; version++; }
            return;
        }
        long now = System.currentTimeMillis();
        if (OnlineService.loggedIn() && now - lastFetch > FETCH_INTERVAL_MS) {
            lastFetch = now;
            OnlineService.submit(FriendsService::fetch);
        }
    }

    public static void refreshNow() { lastFetch = 0; }

    public static Friend byName(String name) {
        for (Friend f : friends) if (f.name().equalsIgnoreCase(name)) return f;
        return null;
    }

    // ---------- Freunden beitreten ----------
    private static volatile String pendingJoin;
    private static int joinWait;

    /** true, wenn der angezeigte Server eine echte Adresse ist (nicht „Im Menü“, „Einzelspieler“ …). */
    public static boolean joinable(Friend f) {
        return f != null && f.online() && f.server() != null && f.server().matches("[A-Za-z0-9._:-]+") && f.server().contains(".");
    }

    public static void join(String address) {
        MinecraftClient client = MinecraftClient.getInstance();
        ServerInfo cur = client.getCurrentServerEntry();
        if (cur != null && cur.address.equalsIgnoreCase(address)) return;  // schon dort
        pendingJoin = address;
        joinWait = 0;
        if (client.world != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().getConnection().disconnect(Text.literal("Wechsle zu " + address + " …"));
        }
    }

    /** Jeden Tick: sobald keine Welt mehr geladen ist, zum Server des Freundes verbinden. */
    public static void joinTick(MinecraftClient client) {
        String addr = pendingJoin;
        if (addr == null) return;
        if (client.world != null) { if (++joinWait > 200) pendingJoin = null; return; }
        pendingJoin = null;
        ServerInfo info = new ServerInfo(addr, addr, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(addr), info, false, null);
    }

    public static void add(String name) { action("friends/add", "name", name); }
    public static void accept(String uuid) { action("friends/accept", "uuid", uuid); }
    public static void remove(String uuid) { action("friends/remove", "uuid", uuid); }

    private static void action(String path, String key, String value) {
        JsonObject body = new JsonObject();
        body.addProperty(key, value);
        OnlineService.submit(() -> {
            HttpResponse<String> res = OnlineService.postAuthenticated(path, body);
            if (res == null) {
                message = OnlineService.loggedIn() ? "Server nicht erreichbar" : "Nicht angemeldet – kurz warten";
            } else {
                try {
                    JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                    if (o.has("error")) message = o.get("error").getAsString();
                    else if (o.has("message")) message = o.get("message").getAsString();
                    else message = null;
                } catch (RuntimeException e) {
                    message = "Fehler " + res.statusCode();
                }
            }
            fetch();
        });
    }

    private static void fetch() {
        HttpResponse<String> res = OnlineService.get("friends", true);
        if (res == null || res.statusCode() != 200) return;
        try {
            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            List<Friend> newFriends = parse(o.getAsJsonArray("friends"));
            List<Friend> newIncoming = parse(o.getAsJsonArray("incoming"));
            List<Friend> newOutgoing = parse(o.getAsJsonArray("outgoing"));
            if (loaded && notifications) notifyChanges(newFriends, newIncoming);
            friends = newFriends;
            incoming = newIncoming;
            outgoing = newOutgoing;
            SocialService.update(o, loaded && notifications);
            loaded = true;
            version++;
        } catch (RuntimeException ignored) {
        }
    }

    private static List<Friend> parse(JsonArray arr) {
        List<Friend> out = new ArrayList<>();
        if (arr == null) return out;
        for (JsonElement e : arr) {
            JsonObject f = e.getAsJsonObject();
            String server = f.has("server") && !f.get("server").isJsonNull() ? f.get("server").getAsString() : null;
            String status = f.has("status") && !f.get("status").isJsonNull() ? f.get("status").getAsString() : null;
            int unread = f.has("unread") ? f.get("unread").getAsInt() : 0;
            out.add(new Friend(f.get("uuid").getAsString(), f.get("name").getAsString(), f.get("online").getAsBoolean(), server, status, unread));
        }
        return out;
    }

    private static void notifyChanges(List<Friend> newFriends, List<Friend> newIncoming) {
        Set<String> wasOnline = new HashSet<>();
        for (Friend f : friends) if (f.online()) wasOnline.add(f.uuid());
        Set<String> knownRequests = new HashSet<>();
        for (Friend f : incoming) knownRequests.add(f.uuid());

        for (Friend f : newFriends) {
            if (f.online() && !wasOnline.contains(f.uuid())) {
                chat(Text.literal(f.name()).formatted(Formatting.WHITE)
                        .append(Text.literal(" ist jetzt online" + (f.server() != null ? " (" + f.server() + ")" : "")).formatted(Formatting.GRAY)));
            }
        }
        for (Friend f : newIncoming) {
            if (!knownRequests.contains(f.uuid())) {
                chat(Text.literal(f.name()).formatted(Formatting.WHITE)
                        .append(Text.literal(" möchte dein Freund sein. Drücke O zum Annehmen.").formatted(Formatting.GRAY)));
            }
        }
    }

    static void chat(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        Text full = Text.literal("[ShadowClient] ").styled(s -> s.withColor(0x22D3EE)).append(text);
        client.execute(() -> {
            if (client.inGameHud != null) client.inGameHud.getChatHud().addMessage(full);
        });
    }
}
