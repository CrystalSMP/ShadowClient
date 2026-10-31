import { useEffect, useRef } from "react";
import { AlertTriangle, CheckCircle2, Trash2 } from "lucide-react";
import Card from "./Card";
import { useLaunch } from "../lib/launch";

const STAGES: Record<string, string> = {
  auth: "Anmeldung", meta: "Versionsdaten", libraries: "Bibliotheken", assets: "Assets",
  java: "Java", mods: "Mods", done: "Fertig", launch: "Start",
};

export function ProgressBar() {
  const { progress, state } = useLaunch();
  if (state === "idle" || !progress) return null;
  const pct = progress.total > 0 ? Math.round((progress.done / progress.total) * 100) : null;
  return (
    <div className="progress">
      <div className="progress-head">
        <span><b>{state === "running" ? "Minecraft läuft" : STAGES[progress.stage] ?? progress.stage}</b> · {state === "running" ? "Viel Spaß!" : progress.message}</span>
        {pct !== null && state !== "running" && <span>{progress.done}/{progress.total}</span>}
      </div>
      <div className={`bar ${pct === null && state !== "running" ? "indeterminate" : ""}`}>
        <div style={{ width: state === "running" ? "100%" : pct !== null ? `${pct}%` : undefined }} />
      </div>
    </div>
  );
}

export function LaunchMessages() {
  const { exit, error } = useLaunch();
  return (
    <>
      {error && <div className="banner error row"><AlertTriangle size={18} /> {error}</div>}
      {exit && !exit.success && (
        <div className="banner error">
          <div className="row"><AlertTriangle size={18} /> <b>Minecraft ist abgestürzt</b> (Code {exit.code ?? "?"})</div>
          {exit.hint && <p>{exit.hint}</p>}
          {exit.crashReport && <p className="small">Absturzbericht: <code>{exit.crashReport}</code></p>}
        </div>
      )}
      {exit?.success && <div className="banner row"><CheckCircle2 size={18} /> Minecraft wurde normal beendet.</div>}
    </>
  );
}

export function GameConsole() {
  const { logs, clearLogs } = useLaunch();
  const box = useRef<HTMLDivElement>(null);
  const stick = useRef(true);

  useEffect(() => {
    const el = box.current;
    if (el && stick.current) el.scrollTop = el.scrollHeight;
  }, [logs]);

  if (!logs.length) return null;
  return (
    <Card title={<>Spiel-Log <button className="icon-btn push-right" onClick={clearLogs} title="Leeren"><Trash2 size={16} /></button></>}>
      <div className="console" ref={box}
        onScroll={(e) => { const el = e.currentTarget; stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 40; }}>
        {logs.map((l) => (
          <div key={l.id} className={l.isErr || /\b(ERROR|FATAL)\b/.test(l.line) ? "err" : /\bWARN\b/.test(l.line) ? "warn" : ""}>{l.line}</div>
        ))}
      </div>
    </Card>
  );
}
