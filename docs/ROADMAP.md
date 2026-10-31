# Roadmap & ehrlicher Status

## ✅ Phase 1: Grundprojekt
Tauri-2-Launcher, Navigation, Dark/Light-Mode, Akzentfarbe, 3D-Skin-Vorschau, Profilverwaltung, Logging.

## ✅ Phase 2: Microsoft-Login & Spielstart (Code fertig, siehe Einschränkungen)
- Offizieller Microsoft-Login (Device Code Flow) → Xbox Live → XSTS → Minecraft; mehrere Konten
- Refresh-Token im Windows-Anmeldeinformations-Manager, kein Passwort, kein Access-Token auf der Festplatte
- Echter Skin des Kontos in der 3D-Vorschau, Kopf-Avatar in der Seitenleiste
- Automatische Installation: Versions-JSON (piston-meta), Client-JAR, Bibliotheken, Assets.
  Alles mit SHA-1-Prüfung, 3 Wiederholungen, parallel, mit Fortschrittsanzeige
- Fabric-Loader automatisch (neueste stabile Version über meta.fabricmc.net), Kompatibilitätsprüfung MC ↔ Loader
- Java automatisch: offizielle Mojang-Java-Runtime (für 1.21: Java 21), alternativ eigener Pfad
- Eigener Spielordner pro Profil (`instances/<id>/` mit eigenem `mods/`)
- Spiel-Log live im Launcher, Absturzerkennung mit verständlichen Hinweisen und Pfad zum Crash-Report
- Offline-Cache für Versionsdaten; Access-Token wird in Logs maskiert

### Einschränkungen / noch nicht getestet
- **Azure-App + Mojang-Freischaltung nötig** (docs/MICROSOFT_LOGIN.md). Ohne sie kein Login.
- Der komplette Download-/Startablauf konnte in der Entwicklungsumgebung nicht gegen die echten
  Mojang-Server getestet werden (kein Netzzugriff dort). Getestet sind Parser, Regeln, Argumentaufbau und Persistenz
  per Unit-Tests, außerdem kompiliert und startet der Launcher.
- macOS-Runtime-Symlinks werden ignoriert (Ziel ist Windows).
- News weiterhin statisch.

## ✅ Phase 3: Fabric-Mod mit Ingame-Menü (Code fertig, siehe Einschränkungen)
- Fabric-Mod für Minecraft 1.21.11 (Yarn, Fabric API), Menü auf Rechts-Shift (umbelegbar)
- Modulsystem mit Einstellungen, verschiebbare Panels, Live-Vorschau, JSON-Konfiguration
- Module: FPS, Ping, Koordinaten; Client-Einstellungen (Akzentfarbe, Menü-Skalierung, Animationen)
- Launcher installiert Fabric API (Modrinth) und die gebaute Mod automatisch in 1.21.4-Profile
- Spielordner-Button pro Profil

### Einschränkungen
- Die Mod konnte in der Entwicklungsumgebung **nicht mit Gradle gebaut** werden (kein Zugriff auf Maven).
  Geprüft: Java-Syntax und Typen gegen nachgebaute API-Stubs. Der erste echte Build passiert lokal.
- Nur Minecraft 1.21.11 (spätere 1.21.x-Versionen haben das Rendering stark geändert).

## Phase 4–7
4 HUD-Editor & Module · 5 Mod-Manager (Modrinth-API) · 6 Cosmetics + Backend · 7 Tests, CI, signierter Installer
