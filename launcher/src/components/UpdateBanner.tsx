import { useEffect, useState } from "react";
import { Download, Loader2, X } from "lucide-react";
import { check, type Update } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";
import { isTauri } from "../lib/api";
import { useLaunch } from "../lib/launch";

/** Prüft beim Start (und alle 6 h) auf eine neue Version und installiert sie auf Wunsch. */
export default function UpdateBanner() {
  const [update, setUpdate] = useState<Update | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [hidden, setHidden] = useState(false);
  const { state } = useLaunch();

  useEffect(() => {
    if (!isTauri || import.meta.env.DEV) return;   // im Entwicklungsmodus keine Updates
    const run = () => check().then((u) => u && setUpdate(u)).catch(() => {});
    run();
    const t = setInterval(run, 6 * 3600_000);
    return () => clearInterval(t);
  }, []);

  if (!update || hidden) return null;

  const install = async () => {
    setError(null); setProgress(0);
    let total = 0, done = 0;
    try {
      await update.downloadAndInstall((e) => {
        if (e.event === "Started") total = e.data.contentLength ?? 0;
        if (e.event === "Progress") { done += e.data.chunkLength; setProgress(total ? Math.round((done / total) * 100) : null); }
      });
      await relaunch();
    } catch (e) { setError(String(e)); setProgress(null); }
  };

  return (
    <div className="update-bar">
      <Download size={15} />
      <span>{progress !== null
        ? <>Update wird geladen{progress ? ` … ${progress} %` : " …"}</>
        : <><b>ShadowClient {update.version}</b> ist verfügbar{update.body ? ` – ${update.body.split("\n")[0].slice(0, 90)}` : ""}</>}</span>
      {error && <em>{error}</em>}
      {progress === null && (
        <button className="btn-play" disabled={state !== "idle"} title={state !== "idle" ? "Erst Minecraft schließen" : undefined} onClick={install}>
          Jetzt aktualisieren
        </button>
      )}
      {progress !== null && <Loader2 size={15} className="spin" />}
      {progress === null && <button className="ghost" onClick={() => setHidden(true)} title="Später"><X size={14} /></button>}
    </div>
  );
}
