# Installation (Windows 10/11)

## Voraussetzungen
1. **Node.js 20+** – https://nodejs.org
2. **Rust (stable)** – https://rustup.rs (bei der Installation „Desktop development with C++“ / MSVC Build Tools zulassen)
3. **Microsoft Edge WebView2** – auf Windows 10/11 meist vorinstalliert
4. Ab Phase 3: **JDK 21** (Temurin), nur zum Bauen der Fabric-Mod

## Launcher starten (Entwicklung)
```powershell
cd launcher
npm install
npm run tauri dev
```
Der erste Start kompiliert die Rust-Abhängigkeiten (einige Minuten). Danach öffnet sich das ShadowClient-Fenster mit Hot-Reload.

Nur die Oberfläche im Browser (ohne Speichern): `npm run dev` → http://localhost:1420

## Microsoft-Login
Vor dem ersten Start: [MICROSOFT_LOGIN.md](MICROSOFT_LOGIN.md) (Azure-App + Client-ID).
Java musst du nicht installieren, der Launcher lädt die offizielle Java-Runtime automatisch.

## Testen
```powershell
cd launcher
npm run build                     # TypeScript-Typprüfung + Frontend-Build
cd src-tauri && cargo test        # Rust-Tests (Persistenz, Wiederherstellung)
```

## Installer bauen
```powershell
cd launcher
npm run tauri build
```
Ergebnis: `launcher/src-tauri/target/release/bundle/nsis/*.exe` und `.../msi/*.msi`.

## Daten & Logs
- Einstellungen/Profile: `%APPDATA%\dev.shadowclient.launcher\settings.json`, `profiles.json`
- Logs: `%LOCALAPPDATA%\dev.shadowclient.launcher\logs\`
- Spieldaten: `%APPDATA%\dev.shadowclient.launcher\instances\<profil-id>\` (u. a. `mods\`, `crash-reports\`)
- Beschädigte JSON-Dateien werden als `*.json.broken` gesichert und durch Standardwerte ersetzt.
