import { useCallback, useEffect, useMemo, useState } from "react";
import { convertFileSrc } from "@tauri-apps/api/core";
import {
  ArrowLeft, Box, Camera, Clock, Database, FileText, FolderOpen, Globe2, HardDrive, Image, Pencil, Play, Plus,
  RefreshCw, Search, Settings2, Sparkles, Trash2, X,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import type { Profile } from "@shared/types";
import { api, isTauri, MOD_VERSION, type InstanceEntry, type InstanceKind, type InstanceOverview } from "../../lib/api";
import { ago, agoUnix, bytes, ICONS, playtime, ProfileIcon } from "./shared";

type Tab = InstanceKind | "settings";
const TABS: { id: Tab; label: string; icon: LucideIcon; count?: keyof InstanceOverview; section: string }[] = [
  { id: "mods", label: "Mods", icon: Box, count: "mods", section: "Inhalte" },
  { id: "resourcepacks", label: "Resourcepacks", icon: Image, count: "resourcepacks", section: "Inhalte" },
  { id: "shaderpacks", label: "Shader", icon: Sparkles, count: "shaderpacks", section: "Inhalte" },
  { id: "datapacks", label: "Datapacks", icon: Database, count: "datapacks", section: "Inhalte" },
  { id: "saves", label: "Welten", icon: Globe2, count: "saves", section: "Spiel" },
  { id: "screenshots", label: "Screenshots", icon: Camera, count: "screenshots", section: "Spiel" },
  { id: "logs", label: "Logs", icon: FileText, section: "Spiel" },
  { id: "settings", label: "Einstellungen", icon: Settings2, section: "Profil" },
];
const TOGGLEABLE: Tab[] = ["mods", "resourcepacks", "shaderpacks", "datapacks"];
const BUILTIN = ["fabric-api", "shadowclient"];

interface Props {
  profile: Profile; versions: string[]; busy: boolean;
  onBack: () => void; onPlay: () => void; onPatch: (c: Partial<Profile>) => void; onDelete: () => void; onBrowse: () => void;
}

export default function ProfileDetail({ profile: p, versions, busy, onBack, onPlay, onPatch, onDelete, onBrowse }: Props) {
  const [tab, setTab] = useState<Tab>("mods");
  const [ov, setOv] = useState<InstanceOverview | null>(null);
  const [items, setItems] = useState<InstanceEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [q, setQ] = useState("");
  const [err, setErr] = useState("");
  const [editName, setEditName] = useState(false);
  const [log, setLog] = useState<{ file: string; text: string } | null>(null);
  const [shot, setShot] = useState<InstanceEntry | null>(null);

  const reload = useCallback(() => {
    api.instanceOverview(p.id).then(setOv).catch(() => {});
    if (tab === "settings") return;
    setLoading(true);
    api.instanceList(p.id, tab).then((l) => { setItems(l); setErr(""); }).catch((e) => setErr(String(e))).finally(() => setLoading(false));
  }, [p.id, tab]);
  useEffect(() => { setItems([]); setQ(""); setLog(null); reload(); }, [reload]);

  const filtered = useMemo(() => items.filter((i) => (i.name + " " + i.file).toLowerCase().includes(q.toLowerCase())), [items, q]);

  const toggle = async (e: InstanceEntry) => {
    try {
      const file = await api.instanceToggle(p.id, tab as InstanceKind, e.file, !e.enabled);
      setItems((l) => l.map((x) => (x.file === e.file ? { ...x, file, enabled: !e.enabled } : x)));
    } catch (er) { setErr(String(er)); }
  };
  const remove = async (e: InstanceEntry) => {
    if (!confirm(`„${e.name}“ löschen?`)) return;
    try { await api.instanceRemove(p.id, tab as InstanceKind, e.file); setItems((l) => l.filter((x) => x.file !== e.file)); reload(); }
    catch (er) { setErr(String(er)); }
  };
  const openLog = async (e: InstanceEntry) => {
    try { setLog({ file: e.file, text: await api.instanceReadLog(p.id, e.file) }); } catch (er) { setErr(String(er)); }
  };

  const current = TABS.find((t) => t.id === tab)!;
  const sections = [...new Set(TABS.map((t) => t.section))];

  return (
    <div className="pd">
      <div className="crumb"><button onClick={onBack}><ArrowLeft size={14} /> Profile</button><span>/</span><b>{p.name}</b>
        <div className="crumb-tools">
          <button className="tool" title="Spielordner öffnen" onClick={() => api.instanceOpen(p.id)}><FolderOpen size={15} /></button>
        </div>
      </div>

      <header className="pd-head">
        <ProfileIcon icon={p.icon} size={76} />
        <div className="pd-title">
          {editName ? (
            <input autoFocus className="pd-name-input" defaultValue={p.name} maxLength={48}
              onBlur={(e) => { const v = e.target.value.trim(); if (v && v !== p.name) onPatch({ name: v }); setEditName(false); }}
              onKeyDown={(e) => { if (e.key === "Enter") (e.target as HTMLInputElement).blur(); if (e.key === "Escape") setEditName(false); }} />
          ) : (
            <h1 onDoubleClick={() => setEditName(true)}>{p.name} <button className="ghost" title="Umbenennen" onClick={() => setEditName(true)}><Pencil size={14} /></button></h1>
          )}
          <div className="pills">
            <span>{p.minecraftVersion}</span><span>Fabric</span>
            {p.minecraftVersion === MOD_VERSION && (p.shadowclient ?? true) && <span className="sc">ShadowClient</span>}
            {p.minecraftVersion === MOD_VERSION && p.shadowclient === false && <span>Vanilla-Fabric</span>}
            {p.group && <span>{p.group}</span>}
          </div>
        </div>
        <button className="btn-play big" disabled={busy} onClick={onPlay}><Play size={16} fill="currentColor" /> Spielen</button>
      </header>

      <div className="stats">
        <div><Clock size={15} /><span>Zuletzt gespielt</span><b>{ago(p.lastPlayed)}</b></div>
        <div><Play size={15} /><span>Spielzeit</span><b>{playtime(p.playtimeSecs)}</b></div>
        <div><HardDrive size={15} /><span>Speicher</span><b>{ov ? bytes(ov.sizeBytes) : "…"}</b></div>
      </div>

      <div className="pd-body">
        <section className="pd-main">
          {tab === "settings" ? (
            <SettingsPanel p={p} versions={versions} onPatch={onPatch} onDelete={onDelete} />
          ) : log ? (
            <div className="logview">
              <div className="logview-head"><button className="tool text" onClick={() => setLog(null)}><ArrowLeft size={14} /> Zurück</button><b>{log.file}</b></div>
              <pre>{log.text || "(leer)"}</pre>
            </div>
          ) : (
            <>
              <div className="pd-bar">
                <div className="field grow"><Search size={15} /><input value={q} onChange={(e) => setQ(e.target.value)} placeholder={`${current.label} durchsuchen`} /></div>
                <button className="tool" title="Neu laden" onClick={reload}><RefreshCw size={15} className={loading ? "spin" : ""} /></button>
                <button className="tool" title="Ordner öffnen" onClick={() => api.instanceOpen(p.id, tab as InstanceKind)}><FolderOpen size={15} /></button>
                {TOGGLEABLE.includes(tab) && <button className="tool text accent" onClick={onBrowse}><Plus size={15} /> Hinzufügen</button>}
              </div>
              {err && <div className="banner error">{err}</div>}
              {!loading && filtered.length === 0 && (
                <div className="plist-empty small">
                  <current.icon size={24} strokeWidth={1.5} />
                  <p>{q ? "Nichts gefunden." : emptyText(tab)}</p>
                  {TOGGLEABLE.includes(tab) && !q && <button className="tool text" onClick={onBrowse}><Plus size={14} /> Auf Modrinth suchen</button>}
                </div>
              )}

              {TOGGLEABLE.includes(tab) && (
                <div className="mgrid">
                  {filtered.map((e) => {
                    const locked = tab === "mods" && BUILTIN.some((b) => e.file.toLowerCase().startsWith(b));
                    return (
                      <div key={e.file} className={`mcard ${e.enabled ? "" : "off"}`}>
                        {e.icon ? <img src={e.icon} alt="" /> : <div className="mcard-ph">{e.name.slice(0, 1).toUpperCase()}</div>}
                        <div className="mcard-text">
                          <b title={e.description ?? e.file}>{e.name}</b>
                          <span>{e.version ?? bytes(e.size)}{e.authors ? ` · ${e.authors}` : ""}</span>
                        </div>
                        {!locked && <button className="ghost del" title="Löschen" onClick={() => remove(e)}><Trash2 size={14} /></button>}
                        <button className={`switch ${e.enabled ? "on" : ""}`} disabled={locked} title={locked ? "Wird vom Launcher verwaltet" : e.enabled ? "Deaktivieren" : "Aktivieren"} onClick={() => toggle(e)}><i /></button>
                      </div>
                    );
                  })}
                </div>
              )}

              {tab === "saves" && (
                <div className="wlist">
                  {filtered.map((e) => (
                    <div key={e.file} className="wrow">
                      {e.icon ? <img src={e.icon} alt="" /> : <div className="mcard-ph"><Globe2 size={18} /></div>}
                      <div className="mcard-text"><b>{e.name}</b><span>{agoUnix(e.modified)} gespielt · {bytes(e.size)}</span></div>
                      <button className="ghost del" title="Welt löschen" onClick={() => remove(e)}><Trash2 size={14} /></button>
                    </div>
                  ))}
                </div>
              )}

              {tab === "screenshots" && (
                <div className="shots">
                  {filtered.map((e) => (
                    <button key={e.file} className="shot" onClick={() => setShot(e)}>
                      {isTauri ? <img src={convertFileSrc(e.path)} alt="" loading="lazy" /> : <div className="mcard-ph" />}
                      <span>{agoUnix(e.modified)}</span>
                    </button>
                  ))}
                </div>
              )}

              {tab === "logs" && (
                <div className="wlist">
                  {filtered.map((e) => (
                    <button key={e.file} className="wrow click" onClick={() => openLog(e)}>
                      <div className="mcard-ph"><FileText size={16} /></div>
                      <div className="mcard-text"><b>{e.file}</b><span>{agoUnix(e.modified)} · {bytes(e.size)}</span></div>
                    </button>
                  ))}
                </div>
              )}
            </>
          )}
        </section>

        <nav className="pd-nav">
          {sections.map((s) => (
            <div key={s}>
              <h6>{s}</h6>
              {TABS.filter((t) => t.section === s).map((t) => (
                <button key={t.id} className={tab === t.id ? "on" : ""} onClick={() => setTab(t.id)}>
                  <t.icon size={15} strokeWidth={1.75} /> {t.label}
                  {t.count && ov && <em>{ov[t.count]}</em>}
                </button>
              ))}
            </div>
          ))}
        </nav>
      </div>

      {shot && (
        <div className="modal-bg" onMouseDown={() => setShot(null)}>
          <div className="shot-view" onMouseDown={(e) => e.stopPropagation()}>
            <img src={convertFileSrc(shot.path)} alt="" />
            <div className="shot-bar"><span>{shot.file}</span>
              <button className="tool" title="Löschen" onClick={() => { remove(shot); setShot(null); }}><Trash2 size={15} /></button>
              <button className="tool" onClick={() => setShot(null)}><X size={15} /></button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function emptyText(tab: Tab) {
  return ({ mods: "Noch keine Mods. Fabric API und ShadowClient kommen beim ersten Start automatisch dazu.", resourcepacks: "Keine Resourcepacks installiert.",
    shaderpacks: "Keine Shader installiert. Für Shader brauchst du Iris (unter Mods).", datapacks: "Keine Datapacks.", saves: "Noch keine Welten.",
    screenshots: "Noch keine Screenshots – ingame mit F2.", logs: "Noch keine Logs. Starte das Profil einmal." } as Record<string, string>)[tab] ?? "";
}

function SettingsPanel({ p, versions, onPatch, onDelete }: { p: Profile; versions: string[]; onPatch: (c: Partial<Profile>) => void; onDelete: () => void }) {
  const [ram, setRam] = useState(p.memoryMb);
  return (
    <div className="pset">
      <div className="pset-row"><div><b>Minecraft-Version</b><span>Beim Wechsel werden Mods für die neue Version benötigt.</span></div>
        <select value={p.minecraftVersion} onChange={(e) => onPatch({ minecraftVersion: e.target.value })}>
          {[...new Set([p.minecraftVersion, ...versions])].map((v) => <option key={v} value={v}>{v}</option>)}
        </select>
      </div>
      <div className="pset-row"><div><b>ShadowClient-Mod</b><span>{p.minecraftVersion === MOD_VERSION
          ? "Aus = nur Fabric und deine eigenen Mods, ohne ShadowClient-Menü, Abzeichen und Freunde ingame."
          : `Die Mod gibt es nur für ${MOD_VERSION}. Dieses Profil startet immer ohne.`}</span></div>
        <button className={`switch ${(p.shadowclient ?? true) && p.minecraftVersion === MOD_VERSION ? "on" : ""}`} disabled={p.minecraftVersion !== MOD_VERSION}
          onClick={() => onPatch({ shadowclient: !(p.shadowclient ?? true) })}><i /></button>
      </div>
      <div className="pset-row"><div><b>Arbeitsspeicher</b><span>{(ram / 1024).toFixed(1)} GB · 4–6 GB reichen für die meisten Modpacks.</span></div>
        <input type="range" min={1024} max={16384} step={512} value={ram} onChange={(e) => setRam(+e.target.value)} onPointerUp={() => onPatch({ memoryMb: ram })} />
      </div>
      <div className="pset-row"><div><b>Gruppe</b><span>Zum Filtern in der Profilliste.</span></div>
        <input defaultValue={p.group ?? ""} placeholder="keine" onBlur={(e) => onPatch({ group: e.target.value.trim() || null })} />
      </div>
      <div className="pset-row"><div><b>Icon</b></div>
        <div className="icon-pick">{Object.keys(ICONS).map((k) => <button key={k} className={(p.icon ?? "box") === k ? "on" : ""} onClick={() => onPatch({ icon: k })}><ProfileIcon icon={k} size={34} /></button>)}</div>
      </div>
      <div className="pset-row col"><div><b>JVM-Argumente</b><span>Nur ändern, wenn du weißt, was du tust.</span></div>
        <input className="mono" defaultValue={p.jvmArgs.join(" ")} placeholder="-XX:+UseG1GC" onBlur={(e) => onPatch({ jvmArgs: e.target.value.split(/\s+/).filter(Boolean) })} />
      </div>
      <div className="pset-row danger"><div><b>Profil löschen</b><span>Entfernt das Profil aus der Liste. Der Spielordner bleibt auf der Festplatte.</span></div>
        <button className="tool text danger" onClick={onDelete}><Trash2 size={14} /> Löschen</button>
      </div>
    </div>
  );
}
