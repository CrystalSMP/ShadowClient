import { Globe, Layers, Play, Puzzle, Settings, Shirt, Users } from "lucide-react";
import type { LucideIcon } from "lucide-react";

export type PageId = "home" | "servers" | "profiles" | "mods" | "friends" | "cosmetics" | "settings";

const ITEMS: { id: PageId; label: string; icon: LucideIcon }[] = [
  { id: "home", label: "Spielen", icon: Play },
  { id: "servers", label: "Server", icon: Globe },
  { id: "profiles", label: "Profile", icon: Layers },
  { id: "mods", label: "Mods", icon: Puzzle },
  { id: "friends", label: "Freunde", icon: Users },
  { id: "cosmetics", label: "Cosmetics", icon: Shirt },
];

export default function Sidebar({ active, onSelect }: { active: PageId; onSelect: (p: PageId) => void }) {
  return (
    <nav className="rail">
      <img className="rail-logo" src="/logo.svg" alt="ShadowClient" />
      <div className="rail-items">
        {ITEMS.map(({ id, label, icon: Icon }) => (
          <button key={id} title={label} className={`rail-item ${active === id ? "active" : ""}`} onClick={() => onSelect(id)}>
            <Icon size={22} fill={id === "home" && active === id ? "currentColor" : "none"} />
            <span>{label}</span>
          </button>
        ))}
      </div>
      <button className={`rail-item rail-bottom ${active === "settings" ? "active" : ""}`} onClick={() => onSelect("settings")} title="Einstellungen">
        <Settings size={22} />
      </button>
    </nav>
  );
}
