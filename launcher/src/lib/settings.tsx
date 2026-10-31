import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { DEFAULT_SETTINGS, type LauncherSettings } from "@shared/types";
import { api, errorText } from "./api";

interface Ctx {
  settings: LauncherSettings;
  loaded: boolean;
  update: (patch: Partial<LauncherSettings>) => Promise<void>;
  error: string | null;
}

const SettingsContext = createContext<Ctx | null>(null);

function applyTheme(s: LauncherSettings) {
  const root = document.documentElement;
  root.dataset.theme = s.theme;
  root.style.setProperty("--accent", s.accentColor);
}

export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<LauncherSettings>(DEFAULT_SETTINGS);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.loadSettings()
      .then((s) => setSettings({ ...DEFAULT_SETTINGS, ...s }))
      .catch((e) => setError(errorText(e)))
      .finally(() => setLoaded(true));
  }, []);

  useEffect(() => applyTheme(settings), [settings]);

  const update = useCallback(async (patch: Partial<LauncherSettings>) => {
    const next = { ...settings, ...patch };
    setSettings(next); // sofortige Vorschau
    try {
      await api.saveSettings(next);
      setError(null);
    } catch (e) {
      setError(errorText(e));
    }
  }, [settings]);

  return <SettingsContext.Provider value={{ settings, loaded, update, error }}>{children}</SettingsContext.Provider>;
}

export function useSettings() {
  const ctx = useContext(SettingsContext);
  if (!ctx) throw new Error("useSettings außerhalb von SettingsProvider");
  return ctx;
}
