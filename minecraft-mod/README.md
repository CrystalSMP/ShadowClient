# ShadowClient Fabric-Mod

Clientseitige Fabric-Mod für **Minecraft 1.21.11**: Ingame-Menü, Module, HUD.
Rein visuell, also keine Änderungen an Serverdaten und keine Umgehung von Anti-Cheat.

## Bedienung
- **Rechts-Shift**: Menü öffnen (Optionen → Steuerung → ShadowClient zum Umbelegen)
- Linksklick: Modul an/aus · Rechtsklick: Einstellungen · Panel-Kopf ziehen: verschieben
- **✥ HUD-Editor** (oben im Menü): HUD-Elemente ziehen, Mausrad = Größe, Rechtsklick = Größe zurücksetzen, rastet an Rändern/Mitte ein
- **C** halten: Zoom
- **O** oder **✦-Button im Hauptmenü**: Freunde-Fenster (Social-Ansicht)
- **Linke Alt-Taste** halten: Freelook (frei umschauen, Laufrichtung bleibt)

## Module
| Kategorie | Module |
|---|---|
| **HUD** | FPS, Ping, Koordinaten (+ Richtung), CPS, Keystrokes (WASD, Maus mit CPS, Leertaste), Rüstung mit Haltbarkeit, Trank-Effekte mit Restzeit, Uhrzeit, Server-IP, Geschwindigkeit, Spieltag, Arbeitsspeicher, **Target-HUD** (Leben des Gegners), **Combo**, **Reichweite**, **Item-Zähler** (Totems, Perlen, Goldäpfel, Pfeile), **Biom**, **Sättigung**, **Sitzungszeit** |
| **Optik** | Eigenes Fadenkreuz (Länge, Abstand, Dicke, Farbe, Punkt, Umrandung), Fullbright, Zoom (Stärke, weiche Kamera), Freelook, Kleines Feuer, Kein Schadens-Wackeln, Klares Wetter, **Leben-Warnung** (roter Rand), **Scoreboard ausblenden** |
| **Komfort** | Toggle Sprint, Chat-Zeitstempel |
| **Online** | ShadowClient-Abzeichen ✦ (Nametag + Tab-Liste), **Freundesliste** mit Online-Status und Server (Taste **O**), Benachrichtigungen. Alles opt-in, siehe `backend-supabase/README.md` |
| **Client** | Akzentfarbe, Menü-Skalierung, Animationen, HUD bei F3 ausblenden |

Alles ist rein clientseitig: keine Änderungen an Serverdaten, keine Vorteile durch Pakete, kein Anti-Cheat-Umgehen.
Einstellungen und HUD-Positionen werden in `config/shadowclient.json` gespeichert.

### Bekannte Grenzen
- Nur Minecraft **1.21.11**.
- Die Taste **C** ist in Vanilla auch für „Hotbar speichern“ (C + Zahl) belegt. Bei Bedarf in der Steuerung umbelegen.
- Freelook, Chat-Zeitstempel und Kleines Feuer greifen tiefer ins Spiel ein. Falls sich in 1.21.11 eine interne Methode anders verhält, schaltet sich nur das betroffene Feature still ab, das Spiel stürzt nicht ab.

## Bauen (Windows)
Voraussetzung: **JDK 25** zum Bauen (Fabric Loom 1.19 verlangt das), z. B. `winget install EclipseAdoptium.Temurin.25.JDK`.
Die fertige Mod läuft weiterhin mit Java 21, also genau wie Minecraft 1.21.11.

- **`build.bat`** doppelklicken. Die fertige Mod landet in `output\shadowclient-0.8.1.jar`.
  Auf Wunsch kopiert das Script sie direkt nach `%APPDATA%\.minecraft\mods`.
- **`test-im-spiel.bat`** startet Minecraft 1.21.11 mit der Mod im Entwicklungsmodus, ganz ohne Login.

Beim ersten Mal lädt Gradle Minecraft und die Mappings, das dauert ein paar Minuten.

## Installieren
1. Fabric Loader für 1.21.11 installieren (https://fabricmc.net/use/installer)
2. In `%APPDATA%\.minecraft\mods` legen:
   - `shadowclient-0.8.1.jar`
   - **Fabric API** für 1.21.11 (https://modrinth.com/mod/fabric-api)
3. Im offiziellen Launcher das Profil „fabric-loader-1.21.11“ starten und ingame **Rechts-Shift** drücken.

## Struktur
```
dev/shadowclient/
  ShadowClient.java          Einstieg: Tasten, Events
  module/                    Module, Kategorien, ModuleManager
  module/setting/            Boolean- und Zahl-Einstellungen
  module/impl/hud|visual|utility   alle Module
  hud/                       HudModule, TextHudModule, HudRenderer
  gui/                       ClickGuiScreen (Menü), HudEditorScreen
  online/                    BadgeService, OnlineConfig (Backend-Adresse)
  mixin/                     Fadenkreuz, Hurt-Cam, Abzeichen, Options-Zugriff
  config/ConfigManager.java  JSON-Speicherung
backend-supabase/            Server für die Abzeichen
```

## Versionen
In `gradle.properties`. Falls Gradle eine Version nicht findet, aktuelle Werte für 1.21.11 auf
https://fabricmc.net/develop nachschlagen und eintragen.
