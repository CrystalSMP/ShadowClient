// News mit eigenen, gerenderten Bannern (public/news/*.png, erzeugt mit tools/banners/render.cjs).
const NEWS: { title: string; text: string; image: string; tag: string }[] = [
  { title: "Freundesliste", tag: "Neu", image: "/news/friends.png", text: "Freunde ingame (Taste O) und hier im Launcher, inkl. Server-Anzeige." },
  { title: "32 Module", tag: "Update", image: "/news/modules.png", text: "Target-HUD, Combo, Reichweite, Item-Zähler, Freelook, Zoom und mehr." },
  { title: "HUD-Editor", tag: "Feature", image: "/news/hud.png", text: "Alles per Drag-and-drop verschieben und skalieren." },
  { title: "Abzeichen", tag: "Online", image: "/news/badge.png", text: "Erkenne andere ShadowClient-Nutzer am ✦ im Nametag und in der Tab-Liste." },
  { title: "Minecraft 1.21.11", tag: "Version", image: "/news/version.png", text: "Die ShadowClient-Mod läuft auf der neuesten 1.21-Version." },
];

export default function NewsColumn() {
  return (
    <aside className="news">
      <h3 className="news-head">Neuigkeiten</h3>
      <div className="news-scroll">
        {NEWS.map((n) => (
          <article key={n.title} className="news-card">
            <span className="news-label">{n.title}</span>
            <div className="news-banner image">
              <img src={n.image} alt={n.title} loading="lazy" draggable={false} />
              <span className="news-tag">{n.tag}</span>
            </div>
            <p>{n.text}</p>
          </article>
        ))}
      </div>
    </aside>
  );
}
