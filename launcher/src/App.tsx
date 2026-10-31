import { useState } from "react";
import Sidebar, { type PageId } from "./components/Sidebar";
import TopBar from "./components/TopBar";
import Home from "./pages/Home";
import Profiles from "./pages/Profiles";
import Mods from "./pages/Mods";
import Cosmetics from "./pages/Cosmetics";
import Friends from "./pages/Friends";
import Settings from "./pages/Settings";
import Servers from "./pages/Servers";
import Announcement from "./components/Announcement";
import LaunchNotice from "./components/LaunchNotice";
import UpdateBanner from "./components/UpdateBanner";
import LoginDialog from "./components/LoginDialog";
import { useSettings } from "./lib/settings";

export default function App() {
  const [page, setPage] = useState<PageId>("home");
  const [loginOpen, setLoginOpen] = useState(false);
  const { error } = useSettings();
  const login = () => setLoginOpen(true);

  return (
    <div className="shell">
      <Sidebar active={page} onSelect={setPage} />
      <div className="shell-main">
        <TopBar page={page} onNavigate={setPage} onLogin={login} />
        <UpdateBanner />
        <Announcement onOpen={() => setPage("profiles")} />
        <main className={`view ${page === "home" ? "view-home" : ""}`} key={page}>
          {error && <div className="banner error">Einstellungen: {error}</div>}
          {page === "home" && <Home onNavigate={setPage} onLogin={login} />}
          {page === "servers" && <Servers onLogin={login} />}
          {page === "profiles" && <Profiles onLogin={login} onBrowse={(id) => { try { localStorage.setItem("browse-profile", id); } catch { /* egal */ } setPage("mods"); }} />}
          {page === "mods" && <Mods />}
          {page === "friends" && <Friends onLogin={login} />}
          {page === "cosmetics" && <Cosmetics onLogin={login} />}
          {page === "settings" && <Settings onLogin={login} />}
        </main>
      </div>
      <LaunchNotice />
      {loginOpen && <LoginDialog onClose={() => setLoginOpen(false)} />}
    </div>
  );
}
