import { useEffect, useRef, useState } from "react";
import { Copy, ExternalLink, Loader2, X } from "lucide-react";
import type { DeviceCode } from "@shared/types";
import { api, errorText } from "../lib/api";
import { useAccounts } from "../lib/accounts";

/** Microsoft-Anmeldung per Gerätecode: Der Nutzer meldet sich im eigenen Browser bei Microsoft an. */
export default function LoginDialog({ onClose }: { onClose: () => void }) {
  const { reload, setActive } = useAccounts();
  const [code, setCode] = useState<DeviceCode | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const finished = useRef(false);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const c = await api.authStart();
        if (!alive) return;
        setCode(c);
        const account = await api.authComplete(c);
        finished.current = true;
        await reload();
        setActive(account.uuid);
        if (alive) onClose();
      } catch (e) {
        if (alive) setError(errorText(e));
      }
    })();
    return () => { alive = false; if (!finished.current) api.authCancel(); };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const openBrowser = async () => {
    if (!code) return;
    try { await navigator.clipboard.writeText(code.userCode); setCopied(true); } catch { /* Kopieren optional */ }
    await api.openUrl(code.verificationUri);
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal card" onClick={(e) => e.stopPropagation()}>
        <button className="icon-btn modal-close" onClick={onClose} aria-label="Schließen"><X size={18} /></button>
        <h3>Mit Microsoft anmelden</h3>
        {error ? (
          <div className="banner error">{error}</div>
        ) : !code ? (
          <p className="muted row"><Loader2 className="spin" size={18} /> Verbinde mit Microsoft…</p>
        ) : (
          <>
            <ol className="steps">
              <li>Öffne <b>{code.verificationUri}</b></li>
              <li>Gib diesen Code ein:</li>
            </ol>
            <div className="device-code">{code.userCode}</div>
            <button className="btn wide" onClick={openBrowser}>
              {copied ? <Copy size={18} /> : <ExternalLink size={18} />} Code kopieren & Browser öffnen
            </button>
            <p className="muted row small"><Loader2 className="spin" size={14} /> Warte auf Bestätigung im Browser…</p>
            <p className="hint">ShadowClient sieht dein Passwort nie. Die Anmeldung läuft direkt bei Microsoft.</p>
          </>
        )}
      </div>
    </div>
  );
}
