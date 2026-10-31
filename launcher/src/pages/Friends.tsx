import { useCallback, useEffect, useRef, useState } from "react";
import { Check, Crown, LogOut, Mail, Play, RefreshCw, Send, UserPlus, Users, X } from "lucide-react";
import type { Friend, FriendsData, InboxMessage } from "@shared/types";
import { headUrl } from "../components/AccountBox";
import { api, errorText } from "../lib/api";
import { useAccounts } from "../lib/accounts";
import { useLaunch } from "../lib/launch";

const EMPTY: FriendsData = { friends: [], incoming: [], outgoing: [], inbox: [], party: null };
const joinable = (s?: string | null) => !!s && /^[A-Za-z0-9._:-]+$/.test(s) && s.includes(".");
type Msg = { id: number; sender: string; kind: string; body: string | null; server: string | null; created_at: string };

export default function Friends({ onLogin }: { onLogin: () => void }) {
  const { active } = useAccounts();
  const { launch, state } = useLaunch();
  const [data, setData] = useState<FriendsData>(EMPTY);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [status, setStatus] = useState("");
  const [sel, setSel] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!active) return;
    setLoading(true);
    try { setData({ ...EMPTY, ...(await api.onlineGet<FriendsData>(active.uuid, "friends")) }); setError(null); }
    catch (e) { setError(errorText(e)); }
    finally { setLoading(false); }
  }, [active]);
  useEffect(() => { load(); const t = setInterval(load, 10_000); return () => clearInterval(t); }, [load]);

  const post = async (path: string, payload: Record<string, unknown> = {}) => {
    if (!active) return;
    try { const m = await api.onlinePost(active.uuid, path, payload); if (m) setInfo(m); await load(); }
    catch (e) { setInfo(errorText(e)); }
  };
  const act = async (action: "add" | "accept" | "remove", value: string) => {
    if (!active) return;
    try { const m = await api.friendsAction(active.uuid, action, value); setInfo(m); if (action === "add") setName(""); await load(); }
    catch (e) { setInfo(errorText(e)); }
  };
  const join = async (server: string, who: string) => {
    if (!active) return;
    if (state !== "idle") return setInfo(`Minecraft läuft schon – drück ingame O und tritt ${who} dort bei.`);
    const profiles = await api.loadProfiles().catch(() => []);
    const p = [...profiles].sort((a, b) => (b.lastPlayed ?? "").localeCompare(a.lastPlayed ?? ""))[0];
    if (!p) return setInfo("Lege zuerst ein Profil an.");
    setInfo(`Starte Minecraft und verbinde mit ${server} …`);
    launch(p.id, active.uuid, server);
  };

  if (!active) {
    return (
      <div className="plist-empty">
        <Users size={28} strokeWidth={1.5} />
        <p>Melde dich an, um Freunde, Nachrichten und Partys zu sehen. Ingame geht es auch über Taste O.</p>
        <button className="btn-play" onClick={onLogin}>Anmelden</button>
      </div>
    );
  }

  const selected = data.friends.find((f) => f.uuid === sel) ?? null;
  const invites = (data.inbox ?? []).filter((m) => m.kind !== "text");
  const online = data.friends.filter((f) => f.online).length;

  return (
    <div className="social">
      <aside className="social-list">
        <div className="social-me">
          <span className="avatar small" data-letter={active.name[0]}><img src={headUrl(active.uuid)} alt="" /></span>
          <div><b>{active.name}</b>
            <form onSubmit={(e) => { e.preventDefault(); post("status", { status }); }}>
              <input value={status} maxLength={40} onChange={(e) => setStatus(e.target.value)} placeholder="Status setzen (z. B. „BedWars“)" />
            </form>
          </div>
        </div>
        <form className="field" onSubmit={(e) => { e.preventDefault(); if (name.trim()) act("add", name.trim()); }}>
          <UserPlus size={15} /><input placeholder="Freund hinzufügen (Name)" value={name} maxLength={16} onChange={(e) => setName(e.target.value)} />
        </form>

        {data.incoming.length > 0 && <h6>Anfragen</h6>}
        {data.incoming.map((f) => (
          <div key={f.uuid} className="frow">
            <Head f={f} /><div className="frow-text"><b>{f.name}</b><span>möchte dein Freund sein</span></div>
            <button className="ghost" title="Annehmen" onClick={() => act("accept", f.uuid)}><Check size={15} /></button>
            <button className="ghost" title="Ablehnen" onClick={() => act("remove", f.uuid)}><X size={15} /></button>
          </div>
        ))}

        <h6>Freunde · {online} online <button className="ghost" onClick={load} title="Aktualisieren"><RefreshCw size={12} className={loading ? "spin" : ""} /></button></h6>
        {data.friends.length === 0 && <p className="muted small pad">Noch keine Freunde.</p>}
        {data.friends.map((f) => (
          <button key={f.uuid} className={`frow click ${sel === f.uuid ? "on" : ""}`} onClick={() => setSel(f.uuid)}>
            <Head f={f} />
            <div className="frow-text"><b>{f.name}</b><span>{f.online ? [f.server ?? "Online", f.status].filter(Boolean).join(" · ") : "Offline"}</span></div>
            {!!f.unread && <em className="unread">{f.unread}</em>}
          </button>
        ))}
        {data.outgoing.length > 0 && <h6>Gesendet</h6>}
        {data.outgoing.map((f) => (
          <div key={f.uuid} className="frow">
            <Head f={f} /><div className="frow-text"><b>{f.name}</b><span>wartet auf Antwort</span></div>
            <button className="ghost" title="Zurückziehen" onClick={() => act("remove", f.uuid)}><X size={15} /></button>
          </div>
        ))}
      </aside>

      <section className="social-main">
        {error && (
          <div className="soft-note">
            <b>Freunde gerade nicht erreichbar</b>
            <span>Der Launcher versucht es automatisch erneut. Ingame (Taste O) funktioniert alles unabhängig davon.</span>
            <details><summary>Details</summary><code>{error}</code></details>
          </div>
        )}
        {info && <div className="soft-info">{info}<button className="ghost" onClick={() => setInfo(null)}><X size={13} /></button></div>}

        {invites.length > 0 && (
          <div className="invites">
            {invites.map((m) => <Invite key={m.id} m={m} onJoinServer={() => join(m.server!, m.senderName)} onJoinParty={() => post("party/join", { partyId: m.party_id })} />)}
          </div>
        )}

        {selected ? (
          <Chat key={selected.uuid} me={active.uuid} friend={selected} account={active.uuid}
            onJoin={() => join(selected.server!, selected.name)} onParty={() => post("party/invite", { to: selected.uuid })}
            onRemove={() => { if (confirm(`${selected.name} als Freund entfernen?`)) { act("remove", selected.uuid); setSel(null); } }} />
        ) : (
          <PartyPanel data={data} me={active.uuid} onLeave={() => post("party/leave")} onKick={(u) => post("party/kick", { uuid: u })}
            onJoin={(s, n) => join(s, n)} />
        )}
      </section>
    </div>
  );
}

function Head({ f }: { f: Friend }) {
  return (
    <span className="fhead">
      <span className={`avatar small ${f.online ? "" : "offline"}`} data-letter={f.name[0]}><img src={headUrl(f.uuid)} alt="" onError={(e) => (e.currentTarget.style.display = "none")} /></span>
      <i className={f.online ? "on" : ""} />
    </span>
  );
}

function Invite({ m, onJoinServer, onJoinParty }: { m: InboxMessage; onJoinServer: () => void; onJoinParty: () => void }) {
  return (
    <div className="invite">
      <Mail size={15} />
      <span><b>{m.senderName}</b> {m.kind === "invite" ? <>lädt dich nach <b>{m.server}</b> ein</> : "lädt dich in die Party ein"}</span>
      <button className="btn-play" onClick={m.kind === "invite" ? onJoinServer : onJoinParty}>{m.kind === "invite" ? "Beitreten" : "Party beitreten"}</button>
    </div>
  );
}

function Chat({ me, friend, account, onJoin, onParty, onRemove }: {
  me: string; friend: Friend; account: string; onJoin: () => void; onParty: () => void; onRemove: () => void;
}) {
  const [msgs, setMsgs] = useState<Msg[]>([]);
  const [text, setText] = useState("");
  const [err, setErr] = useState("");
  const end = useRef<HTMLDivElement>(null);
  const load = useCallback(() => {
    api.onlineGet<{ messages: Msg[] }>(account, `messages?with=${friend.uuid}`).then((r) => setMsgs(r.messages)).catch((e) => setErr(errorText(e)));
  }, [account, friend.uuid]);
  useEffect(() => { load(); const t = setInterval(load, 4000); return () => clearInterval(t); }, [load]);
  useEffect(() => end.current?.scrollIntoView({ block: "end" }), [msgs.length]);
  const send = async () => {
    const t = text.trim(); if (!t) return;
    setText(""); setMsgs((m) => [...m, { id: -Date.now(), sender: me.replace(/-/g, ""), kind: "text", body: t, server: null, created_at: new Date().toISOString() }]);
    try { await api.onlinePost(account, "messages/send", { to: friend.uuid, body: t }); setErr(""); load(); } catch (e) { setErr(errorText(e)); }
  };
  const mine = (m: Msg) => m.sender === me.replace(/-/g, "");
  return (
    <div className="chat">
      <header>
        <Head f={friend} />
        <div className="frow-text"><b>{friend.name}</b><span>{friend.online ? [friend.server ?? "Online", friend.status].filter(Boolean).join(" · ") : "Offline"}</span></div>
        {friend.online && joinable(friend.server) && <button className="btn-play" onClick={onJoin}><Play size={13} fill="currentColor" /> Beitreten</button>}
        <button className="tool text" onClick={onParty}><Users size={14} /> In Party einladen</button>
        <button className="tool" title="Freund entfernen" onClick={onRemove}><X size={15} /></button>
      </header>
      <div className="chat-log">
        {msgs.length === 0 && <p className="muted small center">Noch keine Nachrichten. Nachrichten kommen auch ingame an – egal auf welchem Server.</p>}
        {msgs.map((m) => (
          <div key={m.id} className={`bubble ${mine(m) ? "me" : ""} ${m.kind !== "text" ? "sys" : ""}`}>
            {m.kind === "invite" ? `Einladung nach ${m.server}` : m.kind === "party" ? "Party-Einladung" : m.body}
            <time>{new Date(m.created_at).toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit" })}</time>
          </div>
        ))}
        <div ref={end} />
      </div>
      {err && <div className="chat-err">{err}</div>}
      <form className="chat-input" onSubmit={(e) => { e.preventDefault(); send(); }}>
        <input value={text} maxLength={256} onChange={(e) => setText(e.target.value)} placeholder={`Nachricht an ${friend.name}`} />
        <button className="btn-play" disabled={!text.trim()}><Send size={14} /></button>
      </form>
    </div>
  );
}

function PartyPanel({ data, me, onLeave, onKick, onJoin }: {
  data: FriendsData; me: string; onLeave: () => void; onKick: (u: string) => void; onJoin: (server: string, name: string) => void;
}) {
  const p = data.party;
  const myId = me.replace(/-/g, "");
  if (!p) {
    return (
      <div className="plist-empty">
        <Users size={28} strokeWidth={1.5} />
        <p>Wähle links einen Freund, um zu schreiben oder ihn einzuladen.<br />Lade Freunde in eine <b>Party</b> ein – wechselt der Leader den Server, folgen alle automatisch.</p>
      </div>
    );
  }
  const leader = p.members.find((m) => m.uuid === p.leader);
  return (
    <div className="party">
      <header><h3><Users size={17} /> Party <em>{p.members.length}/8</em></h3><button className="tool text danger" onClick={onLeave}><LogOut size={14} /> Verlassen</button></header>
      {leader?.server && leader.uuid !== myId && joinable(leader.server) && (
        <div className="soft-info">{leader.name} ist auf <b>{leader.server}</b>
          <button className="btn-play" onClick={() => onJoin(leader.server!, leader.name)}><Play size={13} fill="currentColor" /> Folgen</button></div>
      )}
      {p.members.map((m) => (
        <div key={m.uuid} className="frow">
          <Head f={{ uuid: m.uuid, name: m.name, online: m.online, server: m.server }} />
          <div className="frow-text"><b>{m.name} {m.uuid === p.leader && <Crown size={12} className="crown" />}</b>
            <span>{m.online ? [m.server ?? "Online", m.status].filter(Boolean).join(" · ") : "Offline"}</span></div>
          {p.leader === myId && m.uuid !== myId && <button className="ghost" title="Entfernen" onClick={() => onKick(m.uuid)}><X size={14} /></button>}
        </div>
      ))}
      <p className="muted small">Ingame folgst du dem Leader automatisch (Einstellung im Freunde-Modul). Befehle: <code>/sc party</code>, <code>/sc join</code>, <code>/sc dm</code>.</p>
    </div>
  );
}
