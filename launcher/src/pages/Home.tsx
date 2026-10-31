import { useEffect, useRef, useState } from "react";
import { Check, ChevronDown, Clock, Cpu, Layers, Loader2, ScrollText } from "lucide-react";
import Backdrop from "../components/Backdrop";
import type { Profile } from "@shared/types";
import SkinViewer from "../components/SkinViewer";
import NewsColumn from "../components/NewsColumn";
import { GameConsole, LaunchMessages, ProgressBar } from "../components/LaunchPanel";
import { api } from "../lib/api";
import { useSettings } from "../lib/settings";
import { useAccounts } from "../lib/accounts";
import { useLaunch } from "../lib/launch";
import type { PageId } from "../components/Sidebar";

/** Passt die Größe der 3D-Figur an den verfügbaren Platz an. */
function useStageSize(ref: React.RefObject<HTMLDivElement>) {
  const [size, setSize] = useState({ w: 360, h: 460 });
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => {
      const h = Math.max(260, Math.min(560, e.contentRect.height - 170));
      setSize({ w: Math.round(h * 0.8), h: Math.round(h) });
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [ref]);
  return size;
}

export default function Home({ onNavigate, onLogin }: { onNavigate: (p: PageId) => void; onLogin: () => void }) {
  const { settings } = useSettings();
  const { active } = useAccounts();
  const { state, launch, logs, progress } = useLaunch();
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [selected, setSelected] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);
  const [animate, setAnimate] = useState(true);
  const [showLog, setShowLog] = useState(false);
  const stageRef = useRef<HTMLDivElement>(null);
  const size = useStageSize(stageRef);

  useEffect(() => {
    api.loadProfiles().then((p) => {
      setProfiles(p);
      const recent = [...p].sort((a, b) => (b.lastPlayed ?? "").localeCompare(a.lastPlayed ?? ""))[0];
      if (recent) setSelected(recent.id);
    }).catch(() => {});
  }, []);

  const profile = profiles.find((p) => p.id === selected);
  const busy = state !== "idle";
  const play = () => {
    if (!active) return onLogin();
    if (!profile) return onNavigate("profiles");
    launch(profile.id, active.uuid);
  };

  const label = state === "running" ? "LÄUFT" : busy ? "STARTET" : !active ? "ANMELDEN" : !profile ? "PROFIL ERSTELLEN" : "LAUNCH";

  return (
    <div className="home-layout">
      <section className="stage" ref={stageRef}>
        <Backdrop />
        <div className="stage-info">
          <InfoCard icon={Layers} label="Profil" value={profile?.name ?? "–"} />
          <InfoCard icon={Cpu} label="Arbeitsspeicher" value={profile ? `${(profile.memoryMb / 1024).toFixed(1)} GB` : "–"} />
          <InfoCard icon={Clock} label="Zuletzt gespielt" value={profile?.lastPlayed ? timeAgo(profile.lastPlayed) : "Noch nie"} />
        </div>
        <label className="toggle">
          Skin-Animation
          <input type="checkbox" checked={animate} onChange={(e) => setAnimate(e.target.checked)} />
          <span />
        </label>

        <div className="stage-center">
          <div className="figure">
            <SkinViewer accent={settings.accentColor} skinUrl={active?.skinUrl} nameTag={active?.name ?? null}
              animate={animate} width={size.w} height={size.h} />
            <div className="pedestal" />
          </div>

          <div className="launch-sub">{profile ? `Minecraft ${profile.minecraftVersion} · Fabric` : "Kein Profil"}</div>

          <div className="launch-group">
            <button className={`launch-btn ${busy ? "busy" : ""}`} onClick={play} disabled={busy}
              style={{ "--fill": progress && progress.total > 0 ? `${Math.round((progress.done / progress.total) * 100)}%` : busy ? "100%" : "0%" } as React.CSSProperties}>
              <span className="launch-fill" />
              <span className="launch-shine" />
              <span className="launch-label">{busy && <Loader2 size={18} className="spin" />} {label}</span>
              <span className="launch-profile">{profile ? profile.name : active ? "Lege zuerst ein Profil an" : "Mit Microsoft anmelden"}</span>
            </button>
            <div className="launch-menu">
              <button className="launch-arrow" disabled={busy || profiles.length === 0} onClick={() => setMenuOpen(!menuOpen)}>
                <ChevronDown size={20} />
              </button>
              {menuOpen && (
                <div className="dropdown up">
                  {profiles.map((p) => (
                    <button key={p.id} className={`dropdown-row ${p.id === selected ? "on" : ""}`}
                      onClick={() => { setSelected(p.id); setMenuOpen(false); }}>
                      <span><b>{p.name}</b><small>{p.minecraftVersion}</small></span>
                      {p.id === selected && <Check size={14} />}
                    </button>
                  ))}
                  <button className="dropdown-add" onClick={() => { setMenuOpen(false); onNavigate("profiles"); }}>Profile verwalten…</button>
                </div>
              )}
            </div>
          </div>

          <div className="stage-progress"><ProgressBar /></div>
        </div>

        <div className="stage-bottom">
          <LaunchMessages />
          {logs.length > 0 && (
            <button className="link-btn" onClick={() => setShowLog(!showLog)}>
              <ScrollText size={14} /> {showLog ? "Log ausblenden" : `Spiel-Log anzeigen (${logs.length})`}
            </button>
          )}
          {showLog && <GameConsole />}
        </div>
      </section>

      <NewsColumn />
    </div>
  );
}

function InfoCard({ icon: Icon, label, value }: { icon: typeof Clock; label: string; value: string }) {
  return (
    <div className="info-card">
      <Icon size={16} />
      <div>
        <span>{label}</span>
        <b>{value}</b>
      </div>
    </div>
  );
}

function timeAgo(iso: string): string {
  const diff = (Date.now() - new Date(iso).getTime()) / 1000;
  if (diff < 60) return "Gerade eben";
  if (diff < 3600) return `vor ${Math.floor(diff / 60)} Min.`;
  if (diff < 86400) return `vor ${Math.floor(diff / 3600)} Std.`;
  const d = Math.floor(diff / 86400);
  return d === 1 ? "Gestern" : `vor ${d} Tagen`;
}
