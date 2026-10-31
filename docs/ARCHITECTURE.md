# Architektur

```
launcher/
  src/                 React-Frontend
    components/        Sidebar, Card, SkinViewer (skinview3d / WebGL)
    pages/             Home, Profiles, Mods, Cosmetics, Settings
    lib/api.ts         Einziger Zugang zum Rust-Backend (invoke) + Browser-Fallback
    lib/settings.tsx   Settings-Context: Theme & Akzentfarbe als CSS-Variablen
    lib/skin.ts        Prozedurale Standard-Skin (keine Mojang-Assets)
    lib/accounts.tsx   Konten-Context
    lib/launch.tsx     Start-Status + Log (Events vom Backend, gebündeltes Rendering)
  src-tauri/src/
    lib.rs             App-Aufbau, Plugin-Registrierung (Logging)
    commands.rs        Tauri-Commands (Einstellungen, Profile, Konten, Start)
    auth.rs            Microsoft → Xbox → XSTS → Minecraft, Keyring-Speicher
    minecraft/
      net.rs           HTTP-Client, verifizierte parallele Downloads
      version.rs       Mojang-Versionsformat, Regeln, Argumente, Maven-Pfade
      fabric.rs        Fabric-Meta-API
      java.rs          Offizielle Mojang-Java-Runtime
      install.rs       Installation → LaunchPlan
      launch.rs        Argumente ersetzen, Prozess starten, Log streamen, Absturzanalyse
    models.rs          Rust-Spiegel von shared/types + Validierung
    storage.rs         Asynchrone, atomare JSON-Persistenz (tokio::fs)
    error.rs           Fehler → lesbarer Text für die UI
shared/
  types/index.ts       Quelle der Wahrheit für Datenmodelle
  schemas/*.json       JSON-Schema (für Backend/Mod-Validierung)
```

## Prinzipien
- Frontend spricht **nur** über `lib/api.ts` mit dem Backend.
- Alle Datei-I/O ist asynchron; synchrone Systemabfragen laufen in `spawn_blocking`.
- Validierung immer zusätzlich in Rust, nie nur im Frontend.
- Schreiben atomar (Temp-Datei + rename), damit Abstürze keine Konfiguration zerstören.
- Kein Speichern von Microsoft-Passwörtern: Phase 2 nutzt den OAuth-Device-Code-/Browser-Flow; nur Refresh-Tokens werden im Windows Credential Manager abgelegt.

## Datenordner (`%APPDATA%\dev.shadowclient.launcher`)
```
settings.json, profiles.json, accounts.json   (accounts: nur Name/UUID/Skin-URL)
versions/<mc>/<mc>.json, <mc>.jar              Vanilla
versions/fabric-loader-<l>-<mc>/…json          Fabric-Profil (Cache)
libraries/  assets/  runtime/<component>/      Gemeinsam für alle Profile
instances/<profil-id>/                         Spielordner pro Profil (mods, saves, options.txt …)
```

## Start-Ablauf
`launch_profile` → Token erneuern → `install()` (Events `launch-progress`) → `launch::run()`
(Events `game-started`, `game-log`) → `game-exit` mit Hinweis, oder `launch-error`.
