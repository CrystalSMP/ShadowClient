import { useEffect, useMemo, useState } from "react";
import { ChevronDown, Copy, ImagePlus, Play, Search, Users, Zap } from "lucide-react";
import { api } from "../lib/api";
import { useAccounts } from "../lib/accounts";
import { useLaunch } from "../lib/launch";
import type { Profile } from "@shared/types";

type Server = { ip: string; name: string; tags: string[]; lang: "DE" | "EN"; hue: number };
type Status = { online: boolean; players: number; max: number; icon?: string; motd?: string; version?: string };

const SERVERS: Server[] = [
  { ip: "opsucht.net", name: "OPSUCHT", tags: ["Citybuild", "Survival"], lang: "DE", hue: 28 },
  { ip: "gommehd.net", name: "GommeHD", tags: ["Minigames", "BedWars"], lang: "DE", hue: 205 },
  { ip: "griefergames.net", name: "GrieferGames", tags: ["Citybuild", "Economy"], lang: "DE", hue: 140 },
  { ip: "hypixel.net", name: "Hypixel", tags: ["Minigames", "SkyBlock"], lang: "EN", hue: 45 },
  { ip: "play.mccisland.net", name: "MCC Island", tags: ["Minigames", "Events"], lang: "EN", hue: 330 },
  { ip: "play.cubecraft.net", name: "CubeCraft", tags: ["Minigames", "EggWars"], lang: "EN", hue: 190 },
  { ip: "play.wynncraft.com", name: "Wynncraft", tags: ["MMORPG", "Abenteuer"], lang: "EN", hue: 95 },
  { ip: "mc.hglabor.de", name: "HGLabor", tags: ["PvP", "Hunger Games"], lang: "DE", hue: 0 },
  { ip: "minemen.club", name: "Minemen Club", tags: ["PvP", "Practice"], lang: "EN", hue: 265 },
  { ip: "2b2t.org", name: "2b2t", tags: ["Anarchie", "Survival"], lang: "EN", hue: 0 },
  { ip: "mc.rewinside.tv", name: "Rewinside", tags: ["Minigames", "Community"], lang: "DE", hue: 170 },
  { ip: "play.pika-network.net", name: "Pika Network", tags: ["Minigames", "Survival"], lang: "EN", hue: 300 },
];

const cache = new Map<string, Status>();
async function fetchStatus(ip: string): Promise<Status> {
  if (cache.has(ip)) return cache.get(ip)!;
  const r = await fetch(`https://api.mcsrvstat.us/3/${encodeURIComponent(ip)}`);
  const j = await r.json();
  const s: Status = { online: !!j.online, players: j.players?.online ?? 0, max: j.players?.max ?? 0, icon: j.icon, motd: j.motd?.clean?.join(" "), version: j.protocol?.name ?? j.version };
  cache.set(ip, s);
  return s;
}

// Banner: 1) eigener Link (per Button gesetzt, lokal gespeichert)  2) Datei public/servers/<ip>.png  3) Icon-Kachel
const BANNER_KEY = "server-banners";
function loadBanners(): Record<string, string> { try { return JSON.parse(localStorage.getItem(BANNER_KEY) || "{}"); } catch { return {}; } }
function saveBanners(b: Record<string, string>) { try { localStorage.setItem(BANNER_KEY, JSON.stringify(b)); } catch { /* egal */ } }

function Banner({ ip, custom }: { ip: string; custom?: string }) {
  const sources = [custom, `/servers/${ip}.png`, `/servers/${ip}.jpg`, `/servers/${ip}.webp`].filter(Boolean) as string[];
  const [i, setI] = useState(0);
  useEffect(() => setI(0), [custom]);
  if (i >= sources.length) return null;
  return <img className="srv-banner" src={sources[i]} alt="" onError={() => setI(i + 1)} onLoad={(e) => e.currentTarget.classList.add("ok")} />;
}

const fmt = (n: number) => n >= 1000 ? `${(n / 1000).toFixed(n >= 10000 ? 0 : 1).replace(".", ",")}k` : `${n}`;

export default function Servers({ onLogin }: { onLogin: () => void }) {
  const [status, setStatus] = useState<Record<string, Status>>({});
  const [q, setQ] = useState("");
  const [sort, setSort] = useState<"players" | "name">("players");
  const [lang, setLang] = useState<"ALL" | "DE" | "EN">("ALL");
  const [custom, setCustom] = useState("");
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [copied, setCopied] = useState("");
  const [banners, setBanners] = useState<Record<string, string>>(loadBanners);
  const setBanner = (ip: string) => {
    const url = prompt(`Banner-Bild für ${ip} (Bild-Link oder leer zum Entfernen):`, banners[ip] ?? "");
    if (url === null) return;
    const next = { ...banners }; if (url.trim()) next[ip] = url.trim(); else delete next[ip];
    setBanners(next); saveBanners(next);
  };
  const { active } = useAccounts();
  const { launch, state } = useLaunch();

  useEffect(() => {
    SERVERS.forEach((s) => fetchStatus(s.ip).then((st) => setStatus((o) => ({ ...o, [s.ip]: st }))).catch(() => {}));
    api.loadProfiles().then(setProfiles).catch(() => {});
  }, []);

  const list = useMemo(() => SERVERS
    .filter((s) => (lang === "ALL" || s.lang === lang) && (s.name + s.ip + s.tags.join(" ")).toLowerCase().includes(q.toLowerCase()))
    .sort((a, b) => sort === "name" ? a.name.localeCompare(b.name) : (status[b.ip]?.players ?? -1) - (status[a.ip]?.players ?? -1)),
  [q, sort, lang, status]);

  const total = Object.values(status).reduce((n, s) => n + s.players, 0);

  const join = (ip: string) => {
    if (!active) return onLogin();
    const p = [...profiles].sort((a, b) => (b.lastPlayed ?? "").localeCompare(a.lastPlayed ?? ""))[0];
    if (!p) return alert("Lege zuerst ein Profil an.");
    launch(p.id, active.uuid, ip);
  };
  const copy = (ip: string) => { navigator.clipboard?.writeText(ip); setCopied(ip); setTimeout(() => setCopied(""), 1200); };

  return (
    <div className="servers">
      <div className="srv-toolbar">
        <div className="srv-search">
          <Search size={16} />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={`Durchsuche ${SERVERS.length} Server…`} />
        </div>
        <div className="seg">
          {(["ALL", "DE", "EN"] as const).map((l) => (
            <button key={l} className={lang === l ? "on" : ""} onClick={() => setLang(l)}>{l === "ALL" ? "Alle" : l}</button>
          ))}
        </div>
        <label className="srv-sort">
          <select value={sort} onChange={(e) => setSort(e.target.value as "players" | "name")}>
            <option value="players">Meiste Spieler</option>
            <option value="name">Name A–Z</option>
          </select>
          <ChevronDown size={14} />
        </label>
        <div className="srv-direct">
          <Zap size={15} />
          <input value={custom} onChange={(e) => setCustom(e.target.value)} placeholder="Eigene IP direkt beitreten"
            onKeyDown={(e) => e.key === "Enter" && custom.trim() && join(custom.trim())} />
          <button disabled={!custom.trim() || state !== "idle"} onClick={() => join(custom.trim())}><Play size={14} fill="currentColor" /></button>
        </div>
      </div>

      <div className="srv-meta"><Users size={14} /> {fmt(total)} Spieler gerade online auf den empfohlenen Servern</div>

      <div className="srv-grid">
        {list.map((s) => {
          const st = status[s.ip];
          return (
            <article key={s.ip} className="srv-card" style={{ ["--h" as string]: s.hue }}>
              <div className="srv-art" onDoubleClick={() => join(s.ip)}>
                {st?.icon && <img className="srv-art-bg" src={st.icon} alt="" />}
                <Banner ip={s.ip} custom={banners[s.ip]} />
                <div className="srv-art-shine" />
                {st?.icon ? <img className="srv-art-icon" src={st.icon} alt="" /> : <div className="srv-art-icon ph">{s.name[0]}</div>}
                <div className="srv-art-name">{s.name}</div>
                <div className="srv-tags">{s.tags.map((t) => <span key={t}>{t}</span>)}<span className="lang">{s.lang}</span></div>
                <button className="srv-play" disabled={state !== "idle"} onClick={() => join(s.ip)}>
                  <Play size={18} fill="currentColor" /> Beitreten
                </button>
              </div>
              <div className="srv-foot">
                {st?.icon ? <img className="srv-mini" src={st.icon} alt="" /> : <div className="srv-mini ph">{s.name[0]}</div>}
                <div className="srv-info">
                  <b>{s.name}</b>
                  <span className={st ? (st.online ? "on" : "off") : ""}>
                    <i /> {st ? (st.online ? `${fmt(st.players)} online` : "Offline") : "Lädt…"}
                  </span>
                </div>
                <button className="icon-btn" title="Banner ändern" onClick={() => setBanner(s.ip)}><ImagePlus size={15} /></button>
                <button className="icon-btn" title={copied === s.ip ? "Kopiert!" : `IP kopieren (${s.ip})`} onClick={() => copy(s.ip)}><Copy size={15} /></button>
                <button className="icon-btn play" title="Beitreten" disabled={state !== "idle"} onClick={() => join(s.ip)}><Play size={15} fill="currentColor" /></button>
              </div>
            </article>
          );
        })}
      </div>
    </div>
  );
}
