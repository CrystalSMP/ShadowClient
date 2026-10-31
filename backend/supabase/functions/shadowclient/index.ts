// ShadowClient Backend (Supabase Edge Function)
//
// Öffentlich:
//   GET  /users                       aktive Nutzer mit Abzeichen → { uuids }
// Anmeldung (Mojang-geprüft, liefert Sitzungs-Token für 24 h):
//   POST /heartbeat  { username, serverId, client?, showBadge?, server?, shareServer? } → { token, uuid }
//   POST /leave      { username, serverId }  (alte Clients: Abzeichen aus)
// Mit Header "x-shadow-token":
//   POST /presence        { server?, showBadge?, shareServer? }
//   POST /logout
//   GET  /friends         → { friends, incoming, outgoing }
//   POST /friends/add     { name }
//   POST /friends/accept  { uuid }
//   POST /friends/remove  { uuid }   (entfernen, ablehnen oder Anfrage zurückziehen)
//   POST /status          { status }   eigener Status-Text (max. 40 Zeichen, leer = keiner)
//   GET  /messages?with=<uuid>          Verlauf mit einem Freund (markiert als gelesen)
//   POST /messages/send   { to, body }
//   POST /invite          { to }        Einladung auf den eigenen aktuellen Server
//   POST /party/create | /party/leave | /party/invite { to } | /party/join { partyId } | /party/kick { uuid }
//   GET  /cosmetics       → { selected, builtin, mine }
//   POST /cosmetics/select { cape: "b:<id>" | "c:<id>" | null }
//   POST /cosmetics/upload { png: <base64> }  → neues Cape (wartet auf Freigabe)
// Öffentlich:
//   GET  /capes           → { capes: { <uuid>: "b:<id>" | <bild-url> } }  aktive Nutzer
//
// Moderation: in der Tabelle shadowclient_capes status auf 'approved' oder 'rejected' setzen.
import { createClient } from "jsr:@supabase/supabase-js@2";

const BADGE_ACTIVE_MIN = 15;
const ONLINE_MIN = 2;
const SESSION_HOURS = 24;
const BUILTIN_CAPES = ["shadow", "ember", "frost", "nova", "midnight", "aurora"];
const MAX_PENDING = 3;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
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

function pngSize(b: Uint8Array): [number, number] | null {
  const sig = [137, 80, 78, 71, 13, 10, 26, 10];
  if (b.length < 33 || sig.some((v, i) => b[i] !== v)) return null;
  if (String.fromCharCode(...b.slice(12, 16)) !== "IHDR") return null;
  const dv = new DataView(b.buffer, b.byteOffset);
  return [dv.getUint32(16), dv.getUint32(20)];
}

const publicCapeUrl = (path: string) => `${SUPABASE_URL}/storage/v1/object/public/capes/${path}`;

async function friendsOf(me: string) {
  const { data: rows, error } = await db.from("shadowclient_friends").select("requester,target,status")
    .or(`requester.eq.${me},target.eq.${me}`);
  if (error) throw error;
  const others = [...new Set(rows.map((r) => (r.requester === me ? r.target : r.requester)))];
  // deno-lint-ignore no-explicit-any
  const users = new Map<string, any>();
  if (others.length) {
    const { data, error: e2 } = await db.from("shadowclient_users").select("*").in("uuid", others);
    if (e2) throw e2;
    for (const u of data) users.set(u.uuid, u);
  }
  // Ungelesene Nachrichten (Tabelle existiert evtl. noch nicht → leer)
  const unread = new Map<string, number>();
  let inbox: unknown[] = [];
  try {
    const { data: msgs } = await db.from("shadowclient_messages").select("id,sender,kind,body,server,party_id,created_at")
      .eq("recipient", me).is("read_at", null).order("created_at", { ascending: true }).limit(50);
    for (const m of msgs ?? []) if (m.kind === "text") unread.set(m.sender, (unread.get(m.sender) ?? 0) + 1);
    const fresh = Date.now() - 30 * 60_000;   // Einladungen gelten 30 Minuten
    inbox = (msgs ?? []).filter((m) => m.kind === "text" || new Date(m.created_at).getTime() > fresh)
      .map((m) => ({ ...m, senderName: users.get(m.sender)?.name ?? "?" }));
  } catch { /* noch keine Tabelle */ }
  const onlineSince = minutesAgo(ONLINE_MIN);
  const view = (uuid: string) => {
    const u = users.get(uuid);
    const online = !!u && u.last_seen > onlineSince;
    return {
      uuid, name: u?.name ?? "?", online, server: online && u?.share_server ? u.server : null,
      status: online ? (u?.status ?? null) : null, unread: unread.get(uuid) ?? 0,
    };
  };
  return {
    friends: rows.filter((r) => r.status === "accepted").map((r) => view(r.requester === me ? r.target : r.requester))
      .sort((a, b) => Number(b.online) - Number(a.online) || a.name.localeCompare(b.name)),
    incoming: rows.filter((r) => r.status === "pending" && r.target === me).map((r) => view(r.requester)),
    outgoing: rows.filter((r) => r.status === "pending" && r.requester === me).map((r) => view(r.target)),
    inbox,
    party: await partyOf(me).catch(() => null),
  };
}

async function partyOf(me: string) {
  const { data: mem } = await db.from("shadowclient_party_members").select("party_id").eq("uuid", me).maybeSingle();
  if (!mem) return null;
  const [{ data: party }, { data: members }] = await Promise.all([
    db.from("shadowclient_parties").select("id,leader").eq("id", mem.party_id).maybeSingle(),
    db.from("shadowclient_party_members").select("uuid,joined_at").eq("party_id", mem.party_id).order("joined_at"),
  ]);
  if (!party) return null;
  const ids = (members ?? []).map((m) => m.uuid);
  const { data: us } = await db.from("shadowclient_users").select("*").in("uuid", ids);
  const byId = new Map((us ?? []).map((u) => [u.uuid, u]));
  const onlineSince = minutesAgo(ONLINE_MIN);
  return {
    id: party.id, leader: party.leader,
    members: ids.map((id) => {
      const u = byId.get(id);
      const online = !!u && u.last_seen > onlineSince;
      return { uuid: id, name: u?.name ?? "?", online, server: online ? u?.server ?? null : null, status: online ? u?.status ?? null : null };
    }),
  };
}

async function areFriends(a: string, b: string) {
  const { data } = await db.from("shadowclient_friends").select("status")
    .or(`and(requester.eq.${a},target.eq.${b}),and(requester.eq.${b},target.eq.${a})`).eq("status", "accepted").maybeSingle();
  return !!data;
}

const cleanUuid = (v: unknown) => { const s = String(v ?? "").replaceAll("-", ""); return /^[0-9a-f]{32}$/.test(s) ? s : null; };

async function rateLimited(me: string) {
  const { data } = await db.from("shadowclient_messages").select("created_at").eq("sender", me)
    .order("created_at", { ascending: false }).limit(5);
  if (!data || data.length < 5) return false;
  return Date.now() - new Date(data[4].created_at).getTime() < 10_000;   // max. 5 Nachrichten / 10 s
}

async function leaveParty(me: string) {
  const { data: mem } = await db.from("shadowclient_party_members").select("party_id").eq("uuid", me).maybeSingle();
  if (!mem) return;
  await db.from("shadowclient_party_members").delete().eq("uuid", me);
  const { data: rest } = await db.from("shadowclient_party_members").select("uuid").eq("party_id", mem.party_id).order("joined_at").limit(1);
  if (!rest || rest.length === 0) await db.from("shadowclient_parties").delete().eq("id", mem.party_id);
  else await db.from("shadowclient_parties").update({ leader: rest[0].uuid }).eq("id", mem.party_id).eq("leader", me);
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

    if (req.method === "GET" && route === "capes") {
      const { data, error } = await db.from("shadowclient_users").select("uuid,cape")
        .not("cape", "is", null).gt("last_seen", minutesAgo(BADGE_ACTIVE_MIN)).limit(10000);
      if (error) throw error;
      const customIds = data.filter((u) => u.cape.startsWith("c:")).map((u) => u.cape.slice(2));
      const paths = new Map<string, string>();
      if (customIds.length) {
        const { data: rows, error: e2 } = await db.from("shadowclient_capes").select("id,path,owner")
          .in("id", customIds).eq("status", "approved");
        if (e2) throw e2;
        for (const r of rows) paths.set(r.id, publicCapeUrl(r.path));
      }
      const capes: Record<string, string> = {};
      for (const u of data) {
        if (u.cape.startsWith("b:") && BUILTIN_CAPES.includes(u.cape.slice(2))) capes[u.uuid] = u.cape;
        else if (paths.has(u.cape.slice(2))) capes[u.uuid] = paths.get(u.cape.slice(2))!;
      }
      return new Response(JSON.stringify({ capes }), {
        headers: { "Content-Type": "application/json", "Cache-Control": "public, max-age=10" },
      });
    }

    if (req.method === "POST" && (route === "heartbeat" || route === "leave")) {
      const profile = await verifyWithMojang(String(body.username ?? ""), String(body.serverId ?? ""));
      if (!profile) return json({ error: "Mojang-Verifizierung fehlgeschlagen" }, 401);
      if (route === "leave") {
        await db.from("shadowclient_users").update({ show_badge: false }).eq("uuid", profile.id);
        return json({ ok: true });
      }
      // Pro Spieler & Client (Spiel oder Launcher) gibt es nur eine Sitzung – der DB-Trigger löscht die alte.
      const client = body.client === "launcher" ? "launcher" : "mod";
      // Der Launcher meldet nur Name/Konto an und überschreibt nicht den Ingame-Status (Server, Abzeichen).
      const { error } = await db.from("shadowclient_users").upsert(client === "launcher"
        ? { uuid: profile.id, name: profile.name }
        : {
          uuid: profile.id,
          name: profile.name,
          last_seen: new Date().toISOString(),
          show_badge: body.showBadge !== false,
          share_server: body.shareServer !== false,
          server: cleanServer(body.server),
        });
      if (error) throw error;
      const token = randomToken();
      const { error: e2 } = await db.from("shadowclient_sessions").insert({
        token, uuid: profile.id, client, expires_at: new Date(Date.now() + SESSION_HOURS * 3600_000).toISOString(),
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

    if (req.method === "POST" && route === "status") {
      const status = String(body.status ?? "").replace(/[\u0000-\u001f§]/g, "").trim().slice(0, 40) || null;
      const { error } = await db.from("shadowclient_users").update({ status }).eq("uuid", me);
      if (error) throw error;
      return json({ ok: true, message: status ? `Status: ${status}` : "Status entfernt" });
    }

    if (req.method === "GET" && route === "messages") {
      const other = cleanUuid(new URL(req.url).searchParams.get("with"));
      if (!other) return json({ error: "Ungültige UUID" }, 400);
      const { data, error } = await db.from("shadowclient_messages").select("id,sender,recipient,kind,body,server,created_at")
        .or(`and(sender.eq.${me},recipient.eq.${other}),and(sender.eq.${other},recipient.eq.${me})`)
        .order("created_at", { ascending: false }).limit(60);
      if (error) throw error;
      await db.from("shadowclient_messages").update({ read_at: new Date().toISOString() })
        .eq("recipient", me).eq("sender", other).is("read_at", null);
      return json({ messages: data.reverse() });
    }

    if (req.method === "POST" && (route === "messages/send" || route === "invite")) {
      const to = cleanUuid(body.to);
      if (!to) return json({ error: "Ungültige UUID" }, 400);
      if (!(await areFriends(me, to))) return json({ error: "Nur an Freunde möglich" }, 403);
      if (await rateLimited(me)) return json({ error: "Langsamer – max. 5 Nachrichten in 10 Sekunden" }, 429);
      if (route === "invite") {
        const { data: u } = await db.from("shadowclient_users").select("server").eq("uuid", me).maybeSingle();
        const server = u?.server ?? "";
        if (!/^[A-Za-z0-9._:-]+$/.test(server) || !server.includes(".")) return json({ error: "Du bist gerade auf keinem Server" }, 400);
        const { error } = await db.from("shadowclient_messages").insert({ sender: me, recipient: to, kind: "invite", server });
        if (error) throw error;
        return json({ ok: true, message: "Einladung gesendet" });
      }
      const text = String(body.body ?? "").replace(/[\u0000-\u001f§]/g, "").trim().slice(0, 256);
      if (!text) return json({ error: "Leere Nachricht" }, 400);
      const { error } = await db.from("shadowclient_messages").insert({ sender: me, recipient: to, kind: "text", body: text });
      if (error) throw error;
      return json({ ok: true });
    }

    if (req.method === "POST" && route === "messages/read") {
      const ids = Array.isArray(body.ids) ? body.ids.filter((x: unknown) => Number.isInteger(x)).slice(0, 100) : [];
      if (ids.length) await db.from("shadowclient_messages").update({ read_at: new Date().toISOString() }).eq("recipient", me).in("id", ids);
      return json({ ok: true });
    }

    if (req.method === "POST" && route === "party/create") {
      const existing = await partyOf(me);
      if (existing) return json({ ok: true, party: existing });
      const id = randomToken().slice(0, 16);
      const { error } = await db.from("shadowclient_parties").insert({ id, leader: me });
      if (error) throw error;
      await db.from("shadowclient_party_members").insert({ party_id: id, uuid: me });
      return json({ ok: true, party: await partyOf(me), message: "Party erstellt" });
    }

    if (req.method === "POST" && route === "party/invite") {
      const to = cleanUuid(body.to);
      if (!to) return json({ error: "Ungültige UUID" }, 400);
      if (!(await areFriends(me, to))) return json({ error: "Nur Freunde einladen" }, 403);
      let party = await partyOf(me);
      if (!party) {
        const id = randomToken().slice(0, 16);
        await db.from("shadowclient_parties").insert({ id, leader: me });
        await db.from("shadowclient_party_members").insert({ party_id: id, uuid: me });
        party = await partyOf(me);
      }
      if (party!.members.length >= 8) return json({ error: "Party ist voll (max. 8)" }, 400);
      await db.from("shadowclient_messages").insert({ sender: me, recipient: to, kind: "party", party_id: party!.id });
      return json({ ok: true, message: "Party-Einladung gesendet" });
    }

    if (req.method === "POST" && route === "party/join") {
      const pid = String(body.partyId ?? "");
      const { data: inv } = await db.from("shadowclient_messages").select("id").eq("recipient", me).eq("kind", "party").eq("party_id", pid).limit(1).maybeSingle();
      if (!inv) return json({ error: "Keine Einladung für diese Party" }, 403);
      const { data: p } = await db.from("shadowclient_parties").select("id").eq("id", pid).maybeSingle();
      if (!p) return json({ error: "Die Party gibt es nicht mehr" }, 404);
      await leaveParty(me);
      const { error } = await db.from("shadowclient_party_members").insert({ party_id: pid, uuid: me });
      if (error) throw error;
      await db.from("shadowclient_messages").update({ read_at: new Date().toISOString() }).eq("id", inv.id);
      return json({ ok: true, party: await partyOf(me), message: "Party beigetreten" });
    }

    if (req.method === "POST" && route === "party/leave") {
      await leaveParty(me);
      return json({ ok: true, message: "Party verlassen" });
    }

    if (req.method === "POST" && route === "party/kick") {
      const who = cleanUuid(body.uuid);
      const party = await partyOf(me);
      if (!who || !party || party.leader !== me || who === me) return json({ error: "Nicht erlaubt" }, 403);
      await db.from("shadowclient_party_members").delete().eq("party_id", party.id).eq("uuid", who);
      return json({ ok: true });
    }

    if (req.method === "GET" && route === "cosmetics") {
      const [{ data: user }, { data: mine, error }] = await Promise.all([
        db.from("shadowclient_users").select("cape").eq("uuid", me).maybeSingle(),
        db.from("shadowclient_capes").select("id,path,status,created_at").eq("owner", me).neq("status", "rejected")
          .order("created_at", { ascending: false }),
      ]);
      if (error) throw error;
      return json({
        selected: user?.cape ?? null,
        builtin: BUILTIN_CAPES,
        mine: mine!.map((c) => ({ id: c.id, status: c.status, url: publicCapeUrl(c.path) })),
      });
    }

    if (req.method === "POST" && route === "cosmetics/select") {
      const cape = body.cape == null ? null : String(body.cape);
      if (cape !== null) {
        if (cape.startsWith("b:")) {
          if (!BUILTIN_CAPES.includes(cape.slice(2))) return json({ error: "Unbekanntes Cape" }, 400);
        } else if (cape.startsWith("c:")) {
          const { data } = await db.from("shadowclient_capes").select("status")
            .eq("id", cape.slice(2)).eq("owner", me).maybeSingle();
          if (!data) return json({ error: "Cape nicht gefunden" }, 404);
          if (data.status !== "approved") return json({ error: "Dieses Cape wurde noch nicht freigegeben" }, 400);
        } else return json({ error: "Ungültiges Cape" }, 400);
      }
      const { error } = await db.from("shadowclient_users").update({ cape }).eq("uuid", me);
      if (error) throw error;
      return json({ ok: true });
    }

    if (req.method === "POST" && route === "cosmetics/upload") {
      const b64 = String(body.png ?? "").replace(/^data:image\/png;base64,/, "");
      if (b64.length > 90_000) return json({ error: "Bild zu groß (max. 64 KB)" }, 400);
      let bytes: Uint8Array;
      try { bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0)); } catch { return json({ error: "Ungültige Datei" }, 400); }
      const size = pngSize(bytes);
      if (!size) return json({ error: "Nur PNG-Dateien erlaubt" }, 400);
      const [w, h] = size;
      if (w !== h * 2 || ![64, 128, 256, 512].includes(w)) {
        return json({ error: `Falsches Format ${w}×${h} – erlaubt sind 64×32, 128×64, 256×128 oder 512×256` }, 400);
      }
      const { count } = await db.from("shadowclient_capes").select("id", { count: "exact", head: true })
        .eq("owner", me).eq("status", "pending");
      if ((count ?? 0) >= MAX_PENDING) return json({ error: `Du hast schon ${MAX_PENDING} Capes, die auf Freigabe warten` }, 429);
      const id = randomToken().slice(0, 20);
      const path = `custom/${me}/${id}.png`;
      const { error: up } = await db.storage.from("capes").upload(path, bytes, { contentType: "image/png", upsert: false });
      if (up) throw up;
      const { error } = await db.from("shadowclient_capes").insert({ id, owner: me, path });
      if (error) throw error;
      return json({ ok: true, id, message: "Hochgeladen – das Cape wird nach der Prüfung freigeschaltet." });
    }

    return json({ error: "Nicht gefunden" }, 404);
  } catch (e) {
    console.error(e);
    return json({ error: "Serverfehler" }, 500);
  }
});
