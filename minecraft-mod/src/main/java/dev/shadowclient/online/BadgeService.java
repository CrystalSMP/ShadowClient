package dev.shadowclient.online;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** ✦-Abzeichen: Liste der aktiven Nutzer laden und Namen markieren. */
public final class BadgeService {
    private static final long FETCH_INTERVAL_MS = 10_000;
    private static final int BADGE_COLOR = 0x22D3EE;
    private static final Set<UUID> users = ConcurrentHashMap.newKeySet();
    private static volatile boolean active, showOthers;
    private static long lastFetch;

    private BadgeService() {}

    public static void start() { active = true; lastFetch = 0; }

    public static void stop() { active = false; users.clear(); }

    public static String status() {
        return OnlineService.status() + (active ? " · " + users.size() + " Nutzer online" : "");
    }

    public static void tick(MinecraftClient client, boolean others, boolean share) {
        showOthers = others;
        if (!active || !OnlineService.configured()) return;
        long now = System.currentTimeMillis();
        if (now - lastFetch > FETCH_INTERVAL_MS) {
            lastFetch = now;
            OnlineService.submit(BadgeService::fetchUsers);
        }
        Session s = client.getSession();
        if (share && OnlineService.loggedIn() && s != null && s.getUuidOrNull() != null) users.add(s.getUuidOrNull());
    }

    /** Wird sehr oft aufgerufen (Nametags, Tab-Liste) – nur ein Set-Lookup. */
    public static Text decorate(UUID uuid, Text name) {
        if (!active || !showOthers || name == null || uuid == null || !users.contains(uuid)) return null;
        MutableText badge = Text.literal("✦ ").styled(st -> st.withColor(BADGE_COLOR));
        return badge.append(name);
    }

    private static void fetchUsers() {
        HttpResponse<String> res = OnlineService.get("users", false);
        if (res == null || res.statusCode() != 200) return;
        try {
            JsonArray arr = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("uuids");
            Set<UUID> fresh = ConcurrentHashMap.newKeySet();
            for (JsonElement e : arr) {
                UUID u = parseUuid(e.getAsString());
                if (u != null) fresh.add(u);
            }
            users.retainAll(fresh);
            users.addAll(fresh);
        } catch (RuntimeException ignored) {
        }
    }

    /** UUID aus einem GameProfile (Authlib: je nach Version id() oder getId()). */
    public static UUID profileId(Object profile) {
        if (profile == null) return null;
        for (String name : new String[]{"id", "getId"}) {
            try {
                Object v = profile.getClass().getMethod(name).invoke(profile);
                if (v instanceof UUID u) return u;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    public static UUID parseUuid(String s) {
        try {
            if (s.length() == 32) {
                s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20);
            }
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
