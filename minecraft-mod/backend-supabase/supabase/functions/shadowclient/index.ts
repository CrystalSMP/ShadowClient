// ShadowClient Backend (Supabase Edge Function)
//
// Öffentlich:
//   GET  /users                       aktive Nutzer mit Abzeichen → { uuids }
// Anmeldung (Mojang-geprüft, liefert Sitzungs-Token für 24 h):
//   POST /heartbeat  { username, serverId, showBadge?, server?, shareServer? } → { token, uuid }
//   POST /leave      { username, serverId }  (alte Clients: Abzeichen aus)
// Mit Header "x-shadow-token":
//   POST /presence        { server?, showBadge?, shareServer? }
//   POST /logout
//   GET  /friends         → { friends, incoming, outgoing }
//   POST /friends/add     { name }
//   POST /friends/accept  { uuid }
//   POST /friends/remove  { uuid }   (entfernen, ablehnen oder Anfrage zurückziehen)
import { createClient } from "jsr:@supabase/supabase-js@2";

const BADGE_ACTIVE_MIN = 15;
const ONLINE_MIN = 2;
const SESSION_HOURS = 24;
const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
const minutesAgo = (m: number) => new Date(Date.now() - m * 60_000).toISOString();
const cleanServer = (s: unknown) => (typeof s === "string" && s.length > 0 ? s.slice(0, 64) : null);

async function verifyWithMojang(username: string, serverId: string) {
  if (!/^[A-Za-z0-9_]{1,16}$/.test(username) || !/^[0-9a-f]{8,64}$/.test(serverId)) return null;
  const res = await fetch(
    `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(username)}&serverId=${serverId}`,
  );
  if (res.status !== 200) return null;
  const data = await res.json();
  return typeof data?.id === "string" ? { id: data.id as string, name: data.name as string } : null;
}

async function sessionUuid(req: Request): Promise<string | null> {
  const token = req.headers.get("x-shadow-token");
  if (!token || token.length < 32) return null;
  const { data } = await db.from("shadowclient_sessions").select("uuid")
    .eq("token", token).gt("expires_at", new Date().toISOString()).maybeSingle();
  return data?.uuid ?? null;
}

function randomToken() {
  const b = new Uint8Array(32);
  crypto.getRandomValues(b);
  return Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
}

async function friendsOf(me: string) {
  const { data: rows, error } = await db.from("shadowclient_friends").select("requester,target,status")
    .or(`requester.eq.${me},target.eq.${me}`);
  if (error) throw error;
  const others = [...new Set(rows.map((r) => (r.requester === me ? r.target : r.requester)))];
  const users = new Map<string, { name: string; last_seen: string; server: string | null; share_server: boolean }>();
  if (others.length) {
    const { data, error: e2 } = await db.from("shadowclient_users")
      .select("uuid,name,last_seen,server,share_server").in("uuid", others);
    if (e2) throw e2;
    for (const u of data) users.set(u.uuid, u);
  }
  const onlineSince = minutesAgo(ONLINE_MIN);
  const view = (uuid: string) => {
    const u = users.get(uuid);
    const online = !!u && u.last_seen > onlineSince;
    return { uuid, name: u?.name ?? "?", online, server: online && u?.share_server ? u.server : null };
  };
  return {
    friends: rows.filter((r) => r.status === "accepted").map((r) => view(r.requester === me ? r.target : r.requester))
      .sort((a, b) => Number(b.online) - Number(a.online) || a.name.localeCompare(b.name)),
    incoming: rows.filter((r) => r.status === "pending" && r.target === me).map((r) => view(r.requester)),
    outgoing: rows.filter((r) => r.status === "pending" && r.requester === me).map((r) => view(r.target)),
  };
}

Deno.serve(async (req) => {
  const parts = new URL(req.url).pathname.replace(/\/+$/, "").split("/");
  const fnIndex = parts.indexOf("shadowclient");
  const route = parts.slice(fnIndex >= 0 ? fnIndex + 1 : -1).join("/");
  const body = req.method === "POST" ? await req.json().catch(() => ({})) : {};

  try {
    if (req.method === "GET" && route === "users") {
      const { data, error } = await db.from("shadowclient_users").select("uuid")
        .eq("show_badge", true).gt("last_seen", minutesAgo(BADGE_ACTIVE_MIN)).limit(10000);
      if (error) throw error;
      return json({ uuids: data.map((r) => r.uuid) });
    }

    if (req.method === "POST" && (route === "heartbeat" || route === "leave")) {
      const profile = await verifyWithMojang(String(body.username ?? ""), String(body.serverId ?? ""));
      if (!profile) return json({ error: "Mojang-Verifizierung fehlgeschlagen" }, 401);
      if (route === "leave") {
        await db.from("shadowclient_users").update({ show_badge: false }).eq("uuid", profile.id);
        return json({ ok: true });
      }
      const { error } = await db.from("shadowclient_users").upsert({
        uuid: profile.id,
        name: profile.name,
        last_seen: new Date().toISOString(),
        show_badge: body.showBadge !== false,
        share_server: body.shareServer !== false,
        server: cleanServer(body.server),
      });
      if (error) throw error;
      const token = randomToken();
      await db.from("shadowclient_sessions").delete().lt("expires_at", new Date().toISOString());
      const { error: e2 } = await db.from("shadowclient_sessions").insert({
        token, uuid: profile.id, expires_at: new Date(Date.now() + SESSION_HOURS * 3600_000).toISOString(),
      });
      if (e2) throw e2;
      return json({ token, uuid: profile.id });
    }

    const me = await sessionUuid(req);
    if (!me) return json({ error: "Nicht angemeldet" }, 401);

    if (req.method === "POST" && route === "presence") {
      const { error } = await db.from("shadowclient_users").update({
        last_seen: new Date().toISOString(),
        server: cleanServer(body.server),
        show_badge: body.showBadge !== false,
        share_server: body.shareServer !== false,
      }).eq("uuid", me);
      if (error) throw error;
      return json({ ok: true });
    }

    if (req.method === "POST" && route === "logout") {
      await db.from("shadowclient_sessions").delete().eq("token", req.headers.get("x-shadow-token")!);
      await db.from("shadowclient_users").update({ last_seen: minutesAgo(60) }).eq("uuid", me);
      return json({ ok: true });
    }

    if (req.method === "GET" && route === "friends") return json(await friendsOf(me));

    if (req.method === "POST" && route === "friends/add") {
      const name = String(body.name ?? "").trim();
      if (!/^[A-Za-z0-9_]{1,16}$/.test(name)) return json({ error: "Ungültiger Name" }, 400);
      const { data: target } = await db.from("shadowclient_users").select("uuid,name").ilike("name", name).maybeSingle();
      if (!target) return json({ error: `${name} nutzt ShadowClient (noch) nicht` }, 404);
      if (target.uuid === me) return json({ error: "Du kannst dich nicht selbst hinzufügen" }, 400);
      const { data: reverse } = await db.from("shadowclient_friends").select("status")
        .eq("requester", target.uuid).eq("target", me).maybeSingle();
      if (reverse) {
        await db.from("shadowclient_friends").update({ status: "accepted" }).eq("requester", target.uuid).eq("target", me);
        return json({ ok: true, message: `Du bist jetzt mit ${target.name} befreundet` });
      }
      const { error } = await db.from("shadowclient_friends")
        .upsert({ requester: me, target: target.uuid }, { onConflict: "requester,target", ignoreDuplicates: true });
      if (error) throw error;
      return json({ ok: true, message: `Anfrage an ${target.name} gesendet` });
    }

    if (req.method === "POST" && route === "friends/accept") {
      const other = String(body.uuid ?? "").replaceAll("-", "");
      if (!/^[0-9a-f]{32}$/.test(other)) return json({ error: "Ungültige UUID" }, 400);
      const { error } = await db.from("shadowclient_friends").update({ status: "accepted" })
        .eq("requester", other).eq("target", me).eq("status", "pending");
      if (error) throw error;
      return json({ ok: true });
    }

    if (req.method === "POST" && route === "friends/remove") {
      const other = String(body.uuid ?? "").replaceAll("-", "");
      if (!/^[0-9a-f]{32}$/.test(other)) return json({ error: "Ungültige UUID" }, 400);
      const { error } = await db.from("shadowclient_friends").delete()
        .or(`and(requester.eq.${me},target.eq.${other}),and(requester.eq.${other},target.eq.${me})`);
      if (error) throw error;
      return json({ ok: true });
    }

    return json({ error: "Nicht gefunden" }, 404);
  } catch (e) {
    console.error(e);
    return json({ error: "Serverfehler" }, 500);
  }
});
