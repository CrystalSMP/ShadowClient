# ShadowClient veröffentlichen

## Einmalig einrichten
1. Repo auf GitHub hochladen (GitHub Desktop → Push).
2. **Settings → Pages → Source: GitHub Actions** einstellen. Danach ist die Webseite unter
   `https://<dein-name>.github.io/<repo>/` erreichbar (der Workflow „Webseite" baut sie bei jeder Änderung in `website/`).
3. **Settings → Actions → General → Workflow permissions: Read and write** aktivieren.

4. **Auto-Updates einrichten:** Settings → Secrets and variables → Actions → **New repository secret**
   - Name `TAURI_SIGNING_PRIVATE_KEY`, Wert = kompletter Inhalt der Datei `shadowclient-updater.key`
   - (kein Passwort gesetzt → `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` nicht nötig)
   Die Datei **niemals** ins Repo hochladen oder teilen – wer sie hat, kann Updates an alle Nutzer verteilen.
   Verlierst du sie, bekommen bestehende Installationen keine Auto-Updates mehr.

## Neue Version veröffentlichen
- GitHub → **Actions → Release → Run workflow** → Version eintragen (z. B. `0.7.0`) → Start.
  *oder* in GitHub Desktop einen Tag `v0.7.0` erstellen und pushen.
- Nach ca. 10–15 Minuten gibt es unter **Releases** automatisch:
  - `ShadowClient-Setup.exe` (Installer mit eingebauter Mod) – darauf zeigt der Download-Button der Webseite
  - `ShadowClient-Setup-0.7.0.exe`, `shadowclient-mod-0.7.0.jar`, `latest.json`, `SHA256SUMS.txt`
- Alle installierten Launcher sehen beim nächsten Start „ShadowClient 0.7.0 ist verfügbar“ → *Jetzt aktualisieren*.
  Die Mod ist im Installer enthalten und wird beim nächsten Spielstart automatisch mit aktualisiert.
- Wichtig: Die Version muss **höher** sein als die letzte (0.7.0 → 0.7.1 → 0.8.0 …).

## Lokal bauen (ohne GitHub)
`release.bat` doppelklicken → Installer liegt in `launcher/src-tauri/target/release/bundle/nsis/`.
