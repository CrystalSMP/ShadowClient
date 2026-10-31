import { useEffect, useRef, useState } from "react";
import { ChevronDown, Circle, LogIn, Plus, Trash2, Users } from "lucide-react";
import { useAccounts } from "../lib/accounts";
import { useLaunch } from "../lib/launch";
import { headUrl } from "./AccountBox";
import type { PageId } from "./Sidebar";

const TITLES: Record<PageId, string> = {
  home: "Start", servers: "Server", profiles: "Profile", mods: "Mods", friends: "Freunde", cosmetics: "Cosmetics", settings: "Einstellungen",
};

export default function TopBar({ page, onNavigate, onLogin }: { page: PageId; onNavigate: (p: PageId) => void; onLogin: () => void }) {
  const { accounts, active, setActive, remove } = useAccounts();
  const { state } = useLaunch();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const close = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  const status = state === "running" ? "Spiel läuft" : state === "preparing" ? "Startet…" : "Bereit";

  return (
    <header className="topbar">
      <div className="topbar-title crumbs">
        <button onClick={() => onNavigate("home")}>ShadowClient</button>
        <span className="sep">/</span>
        <h1>{TITLES[page]}</h1>
        <span className="ver">v0.6.0</span>
      </div>
      <div className="topbar-actions">
        <span className={`pill status ${state}`}><Circle size={8} fill="currentColor" /> {status}</span>

        <div className="account-menu" ref={ref}>
          {active ? (
            <button className="pill account" onClick={() => setOpen(!open)}>
              <span className="avatar tiny" data-letter={active.name[0]}>
                <img src={headUrl(active.uuid)} alt="" onError={(e) => (e.currentTarget.style.display = "none")} />
              </span>
              {active.name}
              <ChevronDown size={14} />
            </button>
          ) : (
            <button className="pill account" onClick={onLogin}><LogIn size={14} /> Anmelden</button>
          )}
          {open && active && (
            <div className="dropdown">
              {accounts.map((a) => (
                <div key={a.uuid} className={`dropdown-item ${a.uuid === active.uuid ? "on" : ""}`}>
                  <button onClick={() => { setActive(a.uuid); setOpen(false); }}>
                    <span className="avatar tiny" data-letter={a.name[0]}>
                      <img src={headUrl(a.uuid)} alt="" onError={(e) => (e.currentTarget.style.display = "none")} />
                    </span>
                    {a.name}
                  </button>
                  <button className="icon-btn danger" title="Abmelden" onClick={() => confirm(`${a.name} abmelden?`) && remove(a.uuid)}><Trash2 size={14} /></button>
                </div>
              ))}
              <button className="dropdown-add" onClick={() => { setOpen(false); onLogin(); }}><Plus size={14} /> Konto hinzufügen</button>
            </div>
          )}
        </div>

        <button className={`round-btn ${page === "friends" ? "on" : ""}`} title="Freunde" onClick={() => onNavigate("friends")}>
          <Users size={18} />
        </button>
      </div>
    </header>
  );
}
