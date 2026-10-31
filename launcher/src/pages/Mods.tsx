import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronDown, Download, ExternalLink, Loader2, Search, Trash2, X } from "lucide-react";
import { api } from "../lib/api";
import type { Profile } from "@shared/types";

type Kind = "mod" | "resourcepack" | "shader" | "datapack";
const TABS: { id: Kind; label: string }[] = [
  { id: "mod", label: "Mods" }, { id: "resourcepack", label: "Resourcepacks" }, { id: "shader", label: "Shader" }, { id: "datapack", label: "Datapacks" },
];
const SORTS = [["downloads", "Downloads"], ["follows", "Beliebt"], ["newest", "Neu"], ["updated", "Aktualisiert"], ["relevance", "Relevanz"]] as const;

type Hit = { project_id: string; slug: string; title: string; author: string; description: string; icon_url?: string; downloads: number; categories: string[]; display_categories?: string[]; follows: number };
type Cat = { name: string; project_type: string; header: string };

const nf = new Intl.NumberFormat("de-CH");
const short = (n: number) => n >= 1e6 ? `${(n / 1e6).toFixed(1).replace(".", ",")} Mio.` : n >= 1e3 ? `${Math.round(n / 1e3)}k` : `${n}`;
const LOADER_TAGS = new Set(["fabric", "forge", "neoforge", "quilt", "iris", "optifine", "canvas", "minecraft", "datapack", "vanilla", "babric", "liteloader", "rift", "modloader", "bukkit", "paper", "spigot", "purpur", "folia", "sponge", "velocity", "waterfall", "bungeecord"]);
const pretty = (c: string) => c.replace(/-/g, " ").replace(/\b\w/g, (m) => m.toUpperCase());

export default function Mods() {
  const [kind, setKind] = useState<Kind>("mod");
  const [q, setQ] = useState("");
  const [sort, setSort] = useState<(typeof SORTS)[number][0]>("downloads");
  const [cats, setCats] = useState<Cat[]>([]);
  const [picked, setPicked] = useState<string[]>([]);
  const [hits, setHits] = useState<Hit[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState("");
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [pid, setPid] = useState("");
  const [installed, setInstalled] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState<Record<string, "install" | "remove">>({});
  const [toast, setToast] = useState("");
  const [catsOpen, setCatsOpen] = useState(true);
  const req = useRef(0);

  const profile = profiles.find((p) => p.id === pid);
  const version = profile?.minecraftVersion ?? "1.21.11";

  useEffect(() => {
    api.loadProfiles().then((p) => {
      setProfiles(p);
      let wanted: string | null = null;
      try { wanted = localStorage.getItem("browse-profile"); localStorage.removeItem("browse-profile"); } catch { /* egal */ }
      const recent = [...p].sort((a, b) => (b.lastPlayed ?? "").localeCompare(a.lastPlayed ?? ""))[0];
      const pick = p.find((x) => x.id === wanted) ?? recent;
      if (pick) setPid(pick.id);
    }).catch(() => {});
    fetch("https://api.modrinth.com/v2/tag/category").then((r) => r.json()).then(setCats).catch(() => {});
  }, []);

  const refreshInstalled = useCallback(() => {
    if (!pid) return setInstalled(new Set());
    api.contentInstalled(pid).then((l) => setInstalled(new Set(l.map((i) => i.projectId)))).catch(() => {});
  }, [pid]);
  useEffect(refreshInstalled, [refreshInstalled]);

  const search = useCallback(async (offset: number) => {
    const id = ++req.current;
    setLoading(true); setErr("");
    const facets: string[][] = [[`project_type:${kind}`], [`versions:${version}`]];
    if (kind === "mod") facets.push(["categories:fabric"]);
    picked.forEach((c) => facets.push([`categories:${c}`]));
    const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(q)}&index=${sort}&limit=20&offset=${offset}&facets=${encodeURIComponent(JSON.stringify(facets))}`;
    try {
      const r = await fetch(url); const j = await r.json();
      if (id !== req.current) return;
      setHits((h) => offset ? [...h, ...j.hits] : j.hits); setTotal(j.total_hits);
    } catch { if (id === req.current) setErr("Modrinth ist gerade nicht erreichbar."); }
    finally { if (id === req.current) setLoading(false); }
  }, [kind, q, sort, picked, version]);

  useEffect(() => { const t = setTimeout(() => search(0), 250); return () => clearTimeout(t); }, [search]);
  useEffect(() => setPicked([]), [kind]);

  const catList = useMemo(() => cats.filter((c) => c.project_type === kind && c.header !== "resolutions" && !LOADER_TAGS.has(c.name))
    .sort((a, b) => a.name.localeCompare(b.name)), [cats, kind]);

  const flash = (t: string) => { setToast(t); setTimeout(() => setToast(""), 2600); };
  const install = async (h: Hit) => {
    if (!pid) return flash("Lege zuerst ein Profil an.");
    setBusy((b) => ({ ...b, [h.project_id]: "install" }));
    try { const files = await api.contentInstall(pid, h.project_id, kind); flash(`${h.title} installiert${files.length > 1 ? ` (+${files.length - 1} Abhängigkeit${files.length > 2 ? "en" : ""})` : ""}`); refreshInstalled(); }
    catch (e) { flash(`Fehler: ${String(e)}`); }
    finally { setBusy((b) => { const n = { ...b }; delete n[h.project_id]; return n; }); }
  };
  const remove = async (h: Hit) => {
    setBusy((b) => ({ ...b, [h.project_id]: "remove" }));
    try { await api.contentRemove(pid, h.project_id); flash(`${h.title} entfernt`); refreshInstalled(); }
    catch (e) { flash(`Fehler: ${String(e)}`); }
    finally { setBusy((b) => { const n = { ...b }; delete n[h.project_id]; return n; }); }
  };

  return (
    <div className="browse">
      <div className="browse-main">
        <div className="browse-tabs">
          {TABS.map((t) => <button key={t.id} className={kind === t.id ? "on" : ""} onClick={() => setKind(t.id)}>{t.label}</button>)}
          <span className="browse-src"><img src="https://modrinth.com/favicon.ico" alt="" /> Modrinth</span>
        </div>
        <div className="browse-bar">
          <div className="srv-search">
            <Search size={16} />
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={`${TABS.find((t) => t.id === kind)!.label} durchsuchen…`} />
            {q && <button className="clear" onClick={() => setQ("")}><X size={14} /></button>}
          </div>
          <label className="srv-sort">
            <select value={sort} onChange={(e) => setSort(e.target.value as typeof sort)}>
              {SORTS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
            <ChevronDown size={14} />
          </label>
        </div>
        <div className="browse-count">{nf.format(total)} Ergebnisse für Minecraft {version}{kind === "mod" ? " · Fabric" : ""}</div>

        <div className="browse-list">
          {hits.map((h) => {
            const has = installed.has(h.project_id); const b = busy[h.project_id];
            const tags = (h.display_categories ?? h.categories).filter((c) => !LOADER_TAGS.has(c)).slice(0, 4);
            return (
              <article key={h.project_id} className={`mod-row ${has ? "has" : ""}`}>
                {h.icon_url ? <img className="mod-icon" src={h.icon_url} alt="" loading="lazy" /> : <div className="mod-icon ph">{h.title[0]}</div>}
                <div className="mod-body">
                  <div className="mod-head">
                    <b>{h.title}</b><span>von {h.author}</span>
                    <button className="mod-link" title="Auf Modrinth öffnen" onClick={() => api.openUrl(`https://modrinth.com/${kind}/${h.slug}`)}><ExternalLink size={13} /></button>
                  </div>
                  <p>{h.description}</p>
                  <div className="mod-tags">{tags.map((t) => <span key={t}>{pretty(t)}</span>)}</div>
                </div>
                <div className="mod-side">
                  <span className="mod-dl"><Download size={12} /> {short(h.downloads)}</span>
                  {has ? (
                    <div className="mod-actions">
                      <span className="mod-ok"><Check size={14} /> Installiert</span>
                      <button className="mod-del" disabled={!!b} title="Entfernen" onClick={() => remove(h)}>{b === "remove" ? <Loader2 size={14} className="spin" /> : <Trash2 size={14} />}</button>
                    </div>
                  ) : (
                    <button className="mod-install" disabled={!!b} onClick={() => install(h)}>
                      {b ? <Loader2 size={14} className="spin" /> : <Download size={14} />} {b ? "Lädt…" : "Installieren"}
                    </button>
                  )}
                </div>
              </article>
            );
          })}
          {err && <div className="browse-empty">{err}</div>}
          {!loading && !err && hits.length === 0 && <div className="browse-empty">Nichts gefunden.</div>}
          {loading && <div className="browse-empty"><Loader2 className="spin" size={18} /></div>}
          {!loading && hits.length < total && <button className="browse-more" onClick={() => search(hits.length)}>Mehr laden</button>}
        </div>
      </div>

      <aside className="browse-side">
        <div className="side-box">
          <h4>Installieren in</h4>
          {profiles.length ? (
            <label className="srv-sort wide">
              <select value={pid} onChange={(e) => setPid(e.target.value)}>
                {profiles.map((p) => <option key={p.id} value={p.id}>{p.name} · {p.minecraftVersion}</option>)}
              </select>
              <ChevronDown size={14} />
            </label>
          ) : <p className="muted small">Noch kein Profil – lege eins unter „Profile“ an.</p>}
          <p className="muted small">Es werden nur Inhalte für Minecraft {version} angezeigt. Benötigte Abhängigkeiten werden automatisch mitinstalliert.</p>
        </div>
        <div className="side-box">
          <button className="side-head" onClick={() => setCatsOpen(!catsOpen)}>
            <h4>Kategorien {picked.length > 0 && <em>{picked.length}</em>}</h4><ChevronDown size={14} className={catsOpen ? "up" : ""} />
          </button>
          {catsOpen && (
            <div className="chips">
              {catList.map((c) => (
                <button key={c.name} className={picked.includes(c.name) ? "on" : ""}
                  onClick={() => setPicked((p) => p.includes(c.name) ? p.filter((x) => x !== c.name) : [...p, c.name])}>{pretty(c.name)}</button>
              ))}
              {picked.length > 0 && <button className="reset" onClick={() => setPicked([])}>Zurücksetzen</button>}
            </div>
          )}
        </div>
      </aside>
      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}
