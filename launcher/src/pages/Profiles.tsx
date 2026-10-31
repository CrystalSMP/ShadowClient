import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowDownAZ, Clock, Copy, FolderOpen, MoreHorizontal, Package, Play, Plus, Search, Trash2, X } from "lucide-react";
import type { Profile } from "@shared/types";
import { api, errorText, fetchMinecraftVersions, MOD_VERSION } from "../lib/api";
import { useSettings } from "../lib/settings";
import { useAccounts } from "../lib/accounts";
import { useLaunch } from "../lib/launch";
import { ago, ICONS, playtime, ProfileIcon } from "./profiles/shared";
import ProfileDetail from "./profiles/ProfileDetail";

const newId = () => crypto.randomUUID();

export default function Profiles({ onLogin, onBrowse }: { onLogin: () => void; onBrowse: (profileId: string) => void }) {
  const { settings } = useSettings();
  const { active } = useAccounts();
  const { launch, state } = useLaunch();
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [versions, setVersions] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [q, setQ] = useState("");
  const [group, setGroup] = useState<string>("");
  const [sort, setSort] = useState<"recent" | "name">("recent");
  const [creating, setCreating] = useState(false);
  const [openId, setOpenId] = useState<string | null>(null);
  const [menu, setMenu] = useState<string | null>(null);

  useEffect(() => {
    api.loadProfiles().then(setProfiles).catch((e) => setError(errorText(e)));
    fetchMinecraftVersions().then(({ versions }) => setVersions(versions));
  }, []);
  useEffect(() => {
    const close = () => setMenu(null);
    document.addEventListener("click", close);
    return () => document.removeEventListener("click", close);
  }, []);

  const persist = async (next: Profile[]) => {
    const prev = profiles;
    setProfiles(next);
    try { await api.saveProfiles(next); setError(null); }
    catch (e) { setProfiles(prev); setError(errorText(e)); }
  };
  const patch = (id: string, change: Partial<Profile>) => persist(profiles.map((p) => (p.id === id ? { ...p, ...change } : p)));
  const duplicate = (p: Profile) => persist([...profiles, { ...p, id: newId(), name: `${p.name} (Kopie)`.slice(0, 48), createdAt: new Date().toISOString(), lastPlayed: null, playtimeSecs: 0 }]);
  const remove = (p: Profile) => { if (confirm(`Profil „${p.name}“ löschen? Der Spielordner bleibt erhalten.`)) { persist(profiles.filter((x) => x.id !== p.id)); setOpenId(null); } };
  const play = (p: Profile) => {
    if (!active) return onLogin();
    launch(p.id, active.uuid);
    patch(p.id, { lastPlayed: new Date().toISOString() });
  };

  const groups = useMemo(() => [...new Set(profiles.map((p) => p.group).filter(Boolean) as string[])].sort(), [profiles]);
  const list = useMemo(() => profiles
    .filter((p) => (!group || p.group === group) && p.name.toLowerCase().includes(q.toLowerCase()))
    .sort((a, b) => sort === "name" ? a.name.localeCompare(b.name) : (b.lastPlayed ?? b.createdAt).localeCompare(a.lastPlayed ?? a.createdAt)),
  [profiles, group, q, sort]);

  const open = profiles.find((p) => p.id === openId);
  if (open) {
    return <ProfileDetail profile={open} versions={versions} busy={state !== "idle"} onBack={() => setOpenId(null)}
      onPlay={() => play(open)} onPatch={(c) => patch(open.id, c)} onDelete={() => remove(open)} onBrowse={() => onBrowse(open.id)} />;
  }

  return (
    <div className="plist">
      {error && <div className="banner error">{error}</div>}
      <div className="chips-row">
        <button className={!group ? "on" : ""} onClick={() => setGroup("")}>Alle <em>{profiles.length}</em></button>
        {groups.map((g) => <button key={g} className={group === g ? "on" : ""} onClick={() => setGroup(g)}>{g}</button>)}
      </div>
      <div className="plist-bar">
        <div className="field grow"><Search size={15} /><input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Profile durchsuchen" /></div>
        <button className="tool" title={sort === "recent" ? "Sortiert nach: zuletzt gespielt" : "Sortiert nach: Name"} onClick={() => setSort(sort === "recent" ? "name" : "recent")}>
          {sort === "recent" ? <Clock size={15} /> : <ArrowDownAZ size={15} />}
        </button>
        <button className="tool text" onClick={() => setCreating(true)}><Plus size={15} /> Neues Profil</button>
      </div>

      {list.length === 0 && (
        <div className="plist-empty">
          <Package size={28} strokeWidth={1.5} />
          <p>{profiles.length ? "Kein Profil passt zur Suche." : "Du hast noch kein Profil."}</p>
          {!profiles.length && <button className="tool text" onClick={() => setCreating(true)}><Plus size={15} /> Profil erstellen</button>}
        </div>
      )}

      <div className="plist-rows">
        {list.map((p) => (
          <div key={p.id} className="prow" onDoubleClick={() => setOpenId(p.id)}>
            <button className="prow-main" onClick={() => setOpenId(p.id)}>
              <ProfileIcon icon={p.icon} />
              <div>
                <b>{p.name}</b>
                <span className="meta">
                  <i>{p.minecraftVersion}</i><i>Fabric</i>{p.minecraftVersion === MOD_VERSION && (p.shadowclient ?? true) && <i className="sc">ShadowClient</i>}
                  <i>{ago(p.lastPlayed)}</i>{!!p.playtimeSecs && <i>{playtime(p.playtimeSecs)}</i>}
                </span>
              </div>
            </button>
            <div className="prow-actions">
              <button className="btn-play" disabled={state !== "idle"} onClick={() => play(p)}><Play size={13} fill="currentColor" /> Spielen</button>
              <button className="tool text" onClick={() => setOpenId(p.id)}><Package size={14} /> Inhalte</button>
              <div className="menu-wrap" onClick={(e) => e.stopPropagation()}>
                <button className="tool" onClick={() => setMenu(menu === p.id ? null : p.id)}><MoreHorizontal size={16} /></button>
                {menu === p.id && (
                  <div className="menu">
                    <button onClick={() => { api.instanceOpen(p.id); setMenu(null); }}><FolderOpen size={14} /> Ordner öffnen</button>
                    <button onClick={() => { duplicate(p); setMenu(null); }}><Copy size={14} /> Duplizieren</button>
                    <hr />
                    <button className="danger" onClick={() => { remove(p); setMenu(null); }}><Trash2 size={14} /> Löschen</button>
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      {creating && <CreateDialog versions={versions} groups={groups} memory={settings.defaultMemoryMb} onClose={() => setCreating(false)}
        onCreate={(p) => { persist([...profiles, p]); setCreating(false); }} />}
    </div>
  );
}

function CreateDialog({ versions, groups, memory, onClose, onCreate }: {
  versions: string[]; groups: string[]; memory: number; onClose: () => void; onCreate: (p: Profile) => void;
}) {
  const [name, setName] = useState("");
  const [version, setVersion] = useState(versions.includes(MOD_VERSION) ? MOD_VERSION : versions[0] ?? MOD_VERSION);
  const [icon, setIcon] = useState("box");
  const [group, setGroup] = useState("");
  const [withMod, setWithMod] = useState(true);
  const ref = useRef<HTMLInputElement>(null);
  useEffect(() => ref.current?.focus(), []);
  const submit = () => name.trim() && onCreate({
    id: newId(), name: name.trim(), minecraftVersion: version, fabricLoaderVersion: null, memoryMb: memory, jvmArgs: [],
    createdAt: new Date().toISOString(), lastPlayed: null, playtimeSecs: 0, icon, group: group.trim() || null, shadowclient: withMod,
  });
  return (
    <div className="modal-bg" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head"><h3>Neues Profil</h3><button className="tool" onClick={onClose}><X size={16} /></button></div>
        <label className="lbl">Name<input ref={ref} value={name} maxLength={48} onChange={(e) => setName(e.target.value)} onKeyDown={(e) => e.key === "Enter" && submit()} placeholder="z. B. PvP, Survival, OPSUCHT" /></label>
        <div className="two">
          <label className="lbl">Minecraft-Version
            <select value={version} onChange={(e) => setVersion(e.target.value)}>
              {versions.map((v) => <option key={v} value={v}>{v}{v === MOD_VERSION ? "  (mit ShadowClient)" : ""}</option>)}
            </select>
          </label>
          <label className="lbl">Gruppe <span className="opt">optional</span>
            <input list="pgroups" value={group} onChange={(e) => setGroup(e.target.value)} placeholder="z. B. Server" />
            <datalist id="pgroups">{groups.map((g) => <option key={g} value={g} />)}</datalist>
          </label>
        </div>
        <div className="lbl">Icon
          <div className="icon-pick">
            {Object.keys(ICONS).map((k) => <button key={k} className={icon === k ? "on" : ""} onClick={() => setIcon(k)}><ProfileIcon icon={k} size={38} /></button>)}
          </div>
        </div>
        {version === MOD_VERSION && (
          <label className="check-row"><button type="button" className={`switch ${withMod ? "on" : ""}`} onClick={() => setWithMod(!withMod)}><i /></button>
            <span><b>Mit ShadowClient-Mod</b> – Menü, HUD-Module, Abzeichen und Freunde ingame</span></label>
        )}
        {version !== MOD_VERSION && <p className="note">Die ShadowClient-Mod gibt es nur für {MOD_VERSION}. Dieses Profil startet mit Fabric, aber ohne ShadowClient-Menü.</p>}
        <div className="modal-foot"><button className="tool text" onClick={onClose}>Abbrechen</button><button className="btn-play" disabled={!name.trim()} onClick={submit}>Erstellen</button></div>
      </div>
    </div>
  );
}
