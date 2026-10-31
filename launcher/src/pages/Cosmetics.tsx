import { useCallback, useEffect, useRef, useState } from "react";
import { Check, Clock, Loader2, LogIn, RotateCcw, Upload, X } from "lucide-react";
import SkinViewer from "../components/SkinViewer";
import { api } from "../lib/api";
import { useAccounts } from "../lib/accounts";
import { useSettings } from "../lib/settings";

const NAMES: Record<string, string> = {
  shadow: "Shadow", ember: "Ember", frost: "Frost", nova: "Nova", midnight: "Midnight", aurora: "Aurora",
};
type Data = Awaited<ReturnType<typeof api.cosmeticsGet>>;
type Cape = { key: string; name: string; url: string; status?: "pending" | "approved" };

/** Zeichnet die Rückseite eines Cape-Bildes groß und pixelgenau (Vorschau-Kachel). */
function CapeThumb({ url }: { url: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const img = new Image(); img.crossOrigin = "anonymous";
    img.onload = () => {
      const c = ref.current; if (!c) return; const g = c.getContext("2d")!; const s = img.width / 64;
      g.imageSmoothingEnabled = false; g.clearRect(0, 0, c.width, c.height);
      g.drawImage(img, 1 * s, 1 * s, 10 * s, 16 * s, 0, 0, c.width, c.height);
    };
    img.src = url;
  }, [url]);
  return <canvas ref={ref} width={60} height={96} className="cape-thumb" />;
}

export default function Cosmetics({ onLogin }: { onLogin: () => void }) {
  const { settings } = useSettings();
  const { active } = useAccounts();
  const [data, setData] = useState<Data | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [back, setBack] = useState(true);
  const file = useRef<HTMLInputElement>(null);

  const load = useCallback(() => {
    if (!active) return;
    api.cosmeticsGet(active.uuid).then((d) => { setData(d); setPreview(d.selected); setMsg(null); })
      .catch(() => setMsg({ ok: false, text: "Cosmetics-Server gerade nicht erreichbar – die eingebauten Capes kannst du trotzdem ansehen. Neuer Versuch in 30 s." }));
  }, [active]);
  useEffect(() => { load(); const t = setInterval(() => { if (!data) load(); }, 30_000); return () => clearInterval(t); }, [load, data]);

  const capes: Cape[] = [
    ...(data?.builtin ?? Object.keys(NAMES)).map((id) => ({ key: `b:${id}`, name: NAMES[id] ?? id, url: `/capes/${id}.png` })),
    ...(data?.mine ?? []).map((c, i) => ({ key: `c:${c.id}`, name: `Eigenes Cape ${i + 1}`, url: c.url, status: c.status })),
  ];
  const urlOf = (key: string | null) => capes.find((c) => c.key === key)?.url ?? null;
  const selected = data?.selected ?? null;

  const select = async (key: string | null) => {
    if (!active) return onLogin();
    setBusy(true); setMsg(null);
    try { await api.cosmeticsSelect(active.uuid, key); setData((d) => d && { ...d, selected: key }); setPreview(key); setMsg({ ok: true, text: key ? "Cape ausgewählt – andere ShadowClient-Spieler sehen es ingame." : "Cape abgelegt." }); }
    catch (e) { setMsg({ ok: false, text: String(e) }); }
    finally { setBusy(false); }
  };

  const upload = async (f: File) => {
    if (!active) return onLogin();
    setMsg(null);
    if (f.type !== "image/png") return setMsg({ ok: false, text: "Bitte eine PNG-Datei wählen." });
    if (f.size > 64 * 1024) return setMsg({ ok: false, text: "Die Datei ist größer als 64 KB." });
    const url = URL.createObjectURL(f);
    const dims = await new Promise<[number, number]>((res) => { const i = new Image(); i.onload = () => res([i.width, i.height]); i.src = url; });
    URL.revokeObjectURL(url);
    if (dims[0] !== dims[1] * 2 || ![64, 128, 256, 512].includes(dims[0])) return setMsg({ ok: false, text: `Format ${dims[0]}×${dims[1]} passt nicht – nimm 64×32 (oder 128×64, 256×128, 512×256).` });
    const b64 = await new Promise<string>((res) => { const r = new FileReader(); r.onload = () => res(String(r.result).split(",")[1]); r.readAsDataURL(f); });
    setBusy(true);
    try { const m = await api.cosmeticsUpload(active.uuid, b64); setMsg({ ok: true, text: m ?? "Hochgeladen." }); load(); }
    catch (e) { setMsg({ ok: false, text: String(e) }); }
    finally { setBusy(false); }
  };

  if (!active) {
    return (
      <div className="cos-empty">
        <h2>Cosmetics</h2>
        <p className="muted">Melde dich an, um Capes auszuwählen. Andere ShadowClient-Spieler sehen dein Cape dann ingame.</p>
        <button className="mod-install" onClick={onLogin}><LogIn size={14} /> Anmelden</button>
      </div>
    );
  }

  return (
    <div className="cos">
      <section className="cos-stage">
        <SkinViewer accent={settings.accentColor} skinUrl={active.skinUrl} nameTag={active.name} capeUrl={urlOf(preview)}
          rotation={back ? Math.PI - 0.5 : 0.45} width={300} height={420} />
        <button className="cos-turn" onClick={() => setBack(!back)} title="Drehen"><RotateCcw size={15} /> {back ? "Vorderseite" : "Rückseite"}</button>
        <div className="cos-current">
          {selected ? <>Aktiv: <b>{capes.find((c) => c.key === selected)?.name ?? "Cape"}</b></> : "Kein Cape aktiv"}
          {preview !== selected && preview && <span className="cos-hint">Vorschau</span>}
        </div>
      </section>

      <section className="cos-main">
        <div className="cos-head">
          <div><h3>Capes</h3><p className="muted">Klicke für die Vorschau, „Anlegen“ speichert es für alle ShadowClient-Spieler.</p></div>
          <div className="cos-actions">
            {selected && <button className="mod-del wide" disabled={busy} onClick={() => select(null)}><X size={14} /> Ablegen</button>}
            <button className="mod-install" disabled={busy || !preview || preview === selected || capes.find((c) => c.key === preview)?.status === "pending"}
              onClick={() => select(preview)}>{busy ? <Loader2 size={14} className="spin" /> : <Check size={14} />} Anlegen</button>
          </div>
        </div>
        {msg && <div className={`cos-msg ${msg.ok ? "ok" : "err"}`}>{msg.text}</div>}

        <div className="cape-grid">
          {capes.map((c) => (
            <button key={c.key} className={`cape-card ${preview === c.key ? "on" : ""} ${selected === c.key ? "active" : ""}`} onClick={() => setPreview(c.key)}>
              <CapeThumb url={c.url} />
              <span>{c.name}</span>
              {selected === c.key && <i className="tag ok"><Check size={11} /> Aktiv</i>}
              {c.status === "pending" && <i className="tag wait"><Clock size={11} /> In Prüfung</i>}
            </button>
          ))}
          <button className="cape-card upload" disabled={busy} onClick={() => file.current?.click()}>
            <Upload size={22} />
            <span>Eigenes Cape</span>
            <small>PNG 64×32</small>
          </button>
          <input ref={file} type="file" accept="image/png" hidden onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ""; if (f) upload(f); }} />
        </div>
        <p className="muted small">Eigene Capes werden vor der Freischaltung geprüft (keine Beleidigungen, Logos fremder Marken oder anstößigen Inhalte). Maximal 3 gleichzeitig in Prüfung.</p>
      </section>
    </div>
  );
}
