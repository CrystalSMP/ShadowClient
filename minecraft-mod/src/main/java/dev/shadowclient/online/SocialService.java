package dev.shadowclient.online;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.shadowclient.ShadowClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Direktnachrichten, Einladungen, Party und Status (Daten kommen mit der Freundesliste). */
public final class SocialService {
    public record Member(String uuid, String name, boolean online, String server, String status) {}
    public record Party(String id, String leader, List<Member> members) {}
    public record Msg(long id, String sender, String kind, String body, String server, String partyId, String senderName) {}

    private static volatile Party party;
    private static final Set<Long> seen = new HashSet<>();
    private static volatile Msg lastInvite;
    private static String followedServer;
    public static volatile String lastResult;

    private SocialService() {}

    public static Party party() { return party; }
    public static Msg lastInvite() { return lastInvite; }

    public static String myUuid() {
        var s = MinecraftClient.getInstance().getSession();
        UUID u = s == null ? null : s.getUuidOrNull();
        return u == null ? "" : u.toString().replace("-", "");
    }

    static void update(JsonObject o, boolean notify) {
        try {
            JsonElement p = o.get("party");
            if (p == null || p.isJsonNull()) party = null;
            else {
                JsonObject po = p.getAsJsonObject();
                List<Member> ms = new ArrayList<>();
                for (JsonElement e : po.getAsJsonArray("members")) {
                    JsonObject m = e.getAsJsonObject();
                    ms.add(new Member(str(m, "uuid"), str(m, "name"), m.get("online").getAsBoolean(), str(m, "server"), str(m, "status")));
                }
                party = new Party(str(po, "id"), str(po, "leader"), ms);
            }
            JsonArray inbox = o.has("inbox") ? o.getAsJsonArray("inbox") : new JsonArray();
            for (JsonElement e : inbox) {
                JsonObject m = e.getAsJsonObject();
                Msg msg = new Msg(m.get("id").getAsLong(), str(m, "sender"), str(m, "kind"), str(m, "body"), str(m, "server"), str(m, "party_id"), str(m, "senderName"));
                if (!seen.add(msg.id())) continue;
                if (!"text".equals(msg.kind())) lastInvite = msg;
                if (notify) announce(msg);
            }
        } catch (RuntimeException ignored) {}
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static void announce(Msg m) {
        switch (m.kind()) {
            case "text" -> FriendsService.chat(Text.literal("✉ " + m.senderName() + ": ").formatted(Formatting.AQUA)
                    .append(Text.literal(m.body()).formatted(Formatting.WHITE))
                    .append(Text.literal("  (O → Nachricht)").formatted(Formatting.DARK_GRAY)));
            case "invite" -> FriendsService.chat(Text.literal(m.senderName() + " lädt dich nach ").formatted(Formatting.GRAY)
                    .append(Text.literal(m.server()).formatted(Formatting.WHITE))
                    .append(Text.literal(" ein – tippe ").formatted(Formatting.GRAY))
                    .append(Text.literal("/sc join").formatted(Formatting.AQUA)));
            case "party" -> FriendsService.chat(Text.literal(m.senderName() + " lädt dich in die Party ein – tippe ").formatted(Formatting.GRAY)
                    .append(Text.literal("/sc join").formatted(Formatting.AQUA)));
            default -> {}
        }
        var mod = ShadowClient.modules().friends();
        MinecraftClient c = MinecraftClient.getInstance();
        if (mod.dmSound.get() && c.player != null) c.execute(() -> { if (c.player != null) c.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f); });
    }

    /** Jeden Tick: Party-Leader folgen, wenn er den Server wechselt. */
    public static void tick(MinecraftClient c, boolean follow) {
        Party p = party;
        if (!follow || p == null || p.leader() == null || p.leader().equals(myUuid()) || c.world == null) return;
        Member leader = p.members().stream().filter(m -> m.uuid().equals(p.leader())).findFirst().orElse(null);
        if (leader == null || !leader.online() || leader.server() == null) return;
        String s = leader.server();
        if (!s.matches("[A-Za-z0-9._:-]+") || !s.contains(".")) return;
        ServerInfo cur = c.getCurrentServerEntry();
        if (cur != null && cur.address.equalsIgnoreCase(s)) { followedServer = s; return; }
        if (s.equalsIgnoreCase(followedServer)) return;  // nur einmal pro Wechsel
        followedServer = s;
        FriendsService.chat(Text.literal("Folge " + leader.name() + " nach " + s + " …").formatted(Formatting.GRAY));
        FriendsService.join(s);
    }

    // ---------- Aktionen ----------
    private static void post(String path, JsonObject body, Consumer<String> done) {
        OnlineService.submit(() -> {
            HttpResponse<String> res = OnlineService.postAuthenticated(path, body);
            String msg;
            if (res == null) msg = OnlineService.loggedIn() ? "Server nicht erreichbar" : "Nicht angemeldet – Freunde-Modul an?";
            else {
                try {
                    JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                    msg = o.has("error") ? o.get("error").getAsString() : o.has("message") ? o.get("message").getAsString() : null;
                } catch (RuntimeException e) { msg = "Fehler " + res.statusCode(); }
            }
            lastResult = msg;
            if (done != null) done.accept(msg);
            FriendsService.refreshNow();
        });
    }

    private static JsonObject obj(String k, String v) { JsonObject o = new JsonObject(); o.addProperty(k, v); return o; }

    public static void sendDm(String uuid, String text, Consumer<String> done) {
        JsonObject o = obj("to", uuid); o.addProperty("body", text); post("messages/send", o, done);
    }
    public static void invite(String uuid, Consumer<String> done) { post("invite", obj("to", uuid), done); }
    public static void partyInvite(String uuid, Consumer<String> done) { post("party/invite", obj("to", uuid), done); }
    public static void partyLeave(Consumer<String> done) { post("party/leave", new JsonObject(), done); }
    public static void setStatus(String status, Consumer<String> done) { post("status", obj("status", status), done); }

    /** Nimmt die letzte Einladung an (Server beitreten oder Party beitreten). */
    public static String acceptLast() {
        Msg m = lastInvite;
        if (m == null) return "Keine offene Einladung";
        lastInvite = null;
        if ("invite".equals(m.kind())) { FriendsService.join(m.server()); return "Verbinde mit " + m.server() + " …"; }
        post("party/join", obj("partyId", m.partyId()), r -> { if (r != null) FriendsService.chat(Text.literal(r).formatted(Formatting.GRAY)); });
        return "Trete der Party von " + m.senderName() + " bei …";
    }

    /** Lädt den Verlauf mit einem Freund (blockierend – im Hintergrund aufrufen). */
    public static List<Msg> conversation(String uuid) {
        List<Msg> out = new ArrayList<>();
        HttpResponse<String> res = OnlineService.get("messages?with=" + uuid, true);
        if (res == null || res.statusCode() != 200) return out;
        try {
            for (JsonElement e : JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("messages")) {
                JsonObject m = e.getAsJsonObject();
                out.add(new Msg(m.get("id").getAsLong(), str(m, "sender"), str(m, "kind"), str(m, "body"), str(m, "server"), null, null));
            }
        } catch (RuntimeException ignored) {}
        return out;
    }
}
