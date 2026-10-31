import { useEffect, useState } from "react";
import { LogIn, Moon, Sun, Trash2 } from "lucide-react";
import type { SystemInfo } from "@shared/types";
import Card from "../components/Card";
import { api, isTauri } from "../lib/api";
import { useSettings } from "../lib/settings";
import { useAccounts } from "../lib/accounts";
import { headUrl } from "../components/AccountBox";

const PRESETS = ["#22d3ee", "#3b82f6", "#a855f7", "#22c55e", "#f97316", "#ef4444"];

export default function Settings({ onLogin }: { onLogin: () => void }) {
  const { settings, update } = useSettings();
  const { accounts, active, setActive, remove } = useAccounts();
  const [sys, setSys] = useState<SystemInfo | null>(null);
  useEffect(() => { api.systemInfo().then(setSys).catch(() => {}); }, []);

  return (
    <div className="page">
      <h2>Einstellungen</h2>
      <Card title="Darstellung">
        <div className="setting">
          <span>Theme</span>
          <div className="segmented">
            <button className={settings.theme === "dark" ? "on" : ""} onClick={() => update({ theme: "dark" })}><Moon size={16} /> Dark</button>
            <button className={settings.theme === "light" ? "on" : ""} onClick={() => update({ theme: "light" })}><Sun size={16} /> Light</button>
          </div>
        </div>
        <div className="setting">
          <span>Akzentfarbe</span>
          <div className="swatches">
            {PRESETS.map((c) => (
              <button key={c} className={`swatch ${settings.accentColor === c ? "on" : ""}`} style={{ background: c }} onClick={() => update({ accentColor: c })} aria-label={c} />
            ))}
            <input type="color" value={settings.accentColor} onChange={(e) => update({ accentColor: e.target.value })} />
          </div>
        </div>
      </Card>

      <Card title="Microsoft-Konten">
        {accounts.length === 0 && <p className="muted">Noch kein Konto angemeldet.</p>}
        {accounts.map((a) => (
          <div key={a.uuid} className={`setting account-row ${active?.uuid === a.uuid ? "on" : ""}`}>
            <button className="account-pick" onClick={() => setActive(a.uuid)}>
              <span className="avatar small" data-letter={a.name[0]}><img src={headUrl(a.uuid)} alt="" onError={(e) => (e.currentTarget.style.display = "none")} /></span>
              <b>{a.name}</b> {active?.uuid === a.uuid && <span className="badge">aktiv</span>}
            </button>
            <button className="icon-btn danger" title="Abmelden" onClick={() => confirm(`${a.name} abmelden?`) && remove(a.uuid)}><Trash2 size={18} /></button>
          </div>
        ))}
        <div className="setting">
          <button className="btn" onClick={onLogin}><LogIn size={18} /> Konto hinzufügen</button>
        </div>
      </Card>

      <Card title="Discord">
        <div className="setting">
          <span>Rich Presence <small className="muted">(zeigt in Discord „Spielt auf …“)</small></span>
          <input type="checkbox" checked={settings.discordPresence ?? true} onChange={(e) => update({ discordPresence: e.target.checked })} />
        </div>
      </Card>

      <Card title="Java & Speicher">
        <div className="setting">
          <span>Standard-RAM für neue Profile</span>
          <select value={settings.defaultMemoryMb} onChange={(e) => update({ defaultMemoryMb: +e.target.value })}>
            {[2048, 3072, 4096, 6144, 8192, 12288, 16384].map((m) => <option key={m} value={m}>{m / 1024} GB</option>)}
          </select>
        </div>
        <div className="setting">
          <span>Java-Pfad <small className="muted">(leer = offizielle Java-Runtime wird automatisch geladen)</small></span>
          <input defaultValue={settings.javaPath ?? ""} placeholder="C:\Program Files\Java\jdk-21\bin\javaw.exe"
            onBlur={(e) => update({ javaPath: e.target.value.trim() || null })} />
        </div>
        <div className="setting">
          <span>Launcher nach Spielstart schließen</span>
          <input type="checkbox" checked={settings.closeOnLaunch} onChange={(e) => update({ closeOnLaunch: e.target.checked })} />
        </div>
      </Card>

      <Card title="System">
        {!isTauri && <div className="banner">Browser-Modus: Daten werden nicht gespeichert. Starte mit <code>npm run tauri dev</code>.</div>}
        {sys && (
          <dl className="sysinfo">
            <dt>Version</dt><dd>{sys.appVersion}</dd>
            <dt>System</dt><dd>{sys.os} / {sys.arch}</dd>
            <dt>Arbeitsspeicher</dt><dd>{(sys.totalMemoryMb / 1024).toFixed(1)} GB</dd>
            <dt>Datenordner</dt><dd><code>{sys.dataDir}</code></dd>
          </dl>
        )}
      </Card>
    </div>
  );
}
