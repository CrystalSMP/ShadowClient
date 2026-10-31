import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import type { GameExit, LaunchProgress } from "@shared/types";
import { api, errorText, on } from "./api";

export type LaunchState = "idle" | "preparing" | "running";

interface LogEntry { id: number; line: string; isErr: boolean }

interface Ctx {
  state: LaunchState;
  progress: LaunchProgress | null;
  logs: LogEntry[];
  exit: GameExit | null;
  error: string | null;
  launch: (profileId: string, accountUuid: string, server?: string) => Promise<void>;
  warning: string | null;
  dismiss: () => void;
  clearLogs: () => void;
}

const MAX_LOG = 1000;
const LaunchContext = createContext<Ctx | null>(null);

/** Hält Start-Status und Log global, damit sie beim Seitenwechsel erhalten bleiben. */
export function LaunchProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<LaunchState>("idle");
  const [progress, setProgress] = useState<LaunchProgress | null>(null);
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [exit, setExit] = useState<GameExit | null>(null);
  const [error, setError] = useState<string | null>(null);
  const counter = useRef(0);
  // Logzeilen sammeln und gebündelt rendern, damit die UI bei viel Ausgabe flüssig bleibt.
  const buffer = useRef<LogEntry[]>([]);
  const [warning, setWarning] = useState<string | null>(null);

  useEffect(() => {
    const timer = setInterval(() => {
      if (!buffer.current.length) return;
      const batch = buffer.current;
      buffer.current = [];
      setLogs((l) => [...l, ...batch].slice(-MAX_LOG));
    }, 150);
    const subs = [
      on("launch-progress", (p) => { setProgress(p); setState((s) => (s === "running" ? s : "preparing")); }),
      on("game-started", () => setState("running")),
      on("game-log", (l) => buffer.current.push({ id: counter.current++, ...l })),
      on("game-exit", (e) => { setExit(e); setState("idle"); setProgress(null); }),
      on("launch-error", (e) => { setError(e); setState("idle"); setProgress(null); }),
      on("launch-warning", (w) => setWarning(w)),
    ];
    api.isGameRunning().then((r) => r && setState("running"));
    return () => { clearInterval(timer); subs.forEach((p) => p.then((un) => un())); };
  }, []);

  const launch = useCallback(async (profileId: string, accountUuid: string, server?: string) => {
    setError(null); setWarning(null); setExit(null); setLogs([]);
    setState("preparing");
    setProgress({ stage: "auth", message: "Vorbereiten…", done: 0, total: 0 });
    try { await api.launchProfile(profileId, accountUuid, server); }
    catch (e) { setError(errorText(e)); setState("idle"); setProgress(null); }
  }, []);

  return (
    <LaunchContext.Provider value={{ state, progress, logs, exit, error, warning, launch, clearLogs: () => setLogs([]), dismiss: () => { setError(null); setWarning(null); } }}>
      {children}
    </LaunchContext.Provider>
  );
}

export function useLaunch() {
  const ctx = useContext(LaunchContext);
  if (!ctx) throw new Error("useLaunch außerhalb von LaunchProvider");
  return ctx;
}
