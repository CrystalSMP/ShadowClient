import { AlertTriangle, X } from "lucide-react";
import { useLaunch } from "../lib/launch";

/** Zeigt Startfehler und Hinweise (z. B. „Mod nicht gefunden“) unten rechts an. */
export default function LaunchNotice() {
  const { error, warning, dismiss } = useLaunch();
  const text = error ?? warning;
  if (!text) return null;
  return (
    <div className={`launch-notice ${error ? "err" : "warn"}`}>
      <AlertTriangle size={16} />
      <div><b>{error ? "Start fehlgeschlagen" : "Hinweis"}</b><span>{text}</span></div>
      <button className="ghost" onClick={dismiss}><X size={14} /></button>
    </div>
  );
}
