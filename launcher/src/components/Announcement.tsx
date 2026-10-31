import { useState } from "react";
import { Sparkles, X } from "lucide-react";

const ID = "ann-0.6.0";
export default function Announcement({ onOpen }: { onOpen: () => void }) {
  const [hidden, setHidden] = useState(() => { try { return localStorage.getItem(ID) === "1"; } catch { return false; } });
  if (hidden) return null;
  const close = () => { setHidden(true); try { localStorage.setItem(ID, "1"); } catch { /* egal */ } };
  return (
    <div className="announce">
      <Sparkles size={14} />
      <span>Neu in 0.6: <b>Profile</b> mit Mods, Welten, Screenshots und Logs – alles an einem Ort.</span>
      <button className="announce-link" onClick={onOpen}>Ansehen</button>
      <button className="announce-x" onClick={close} title="Ausblenden"><X size={14} /></button>
    </div>
  );
}
