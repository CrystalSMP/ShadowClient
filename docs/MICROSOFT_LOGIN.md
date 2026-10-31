# Microsoft-Login einrichten

ShadowClient nutzt den offiziellen **OAuth 2.0 Device Code Flow** von Microsoft.
Du meldest dich in deinem eigenen Browser bei Microsoft an. Der Launcher sieht nie ein Passwort.
Gespeichert wird nur das Refresh-Token, und zwar im **Windows-Anmeldeinformations-Manager**
(Eintrag „ShadowClient“), nicht in einer Datei.

Ablauf: Microsoft-Token → Xbox Live → XSTS → Minecraft-Token → Profil (Kaufprüfung).

## 1. Azure-App registrieren (einmalig, kostenlos)
1. https://portal.azure.com → **Microsoft Entra ID** → **App-Registrierungen** → **Neue Registrierung**
2. Name: `ShadowClient`
3. Unterstützte Kontotypen: **„Nur persönliche Microsoft-Konten“**
4. Umleitungs-URI: leer lassen → **Registrieren**
5. Links **Authentifizierung** → ganz unten **„Öffentliche Clientflows zulassen“ = Ja** → Speichern
6. Auf der Übersicht die **Anwendungs-ID (Client-ID)** kopieren

## 2. Freischaltung für Minecraft beantragen (wichtig!)
Neue Azure-Apps dürfen die Minecraft-Dienste (`api.minecraftservices.com`) erst nutzen, wenn
Microsoft/Mojang sie freigeschaltet hat. Antrag: **https://aka.ms/mce-reviewappid**
(Client-ID und kurze Beschreibung angeben). Die Bearbeitung kann einige Tage dauern.

Ohne Freischaltung klappt die Microsoft-Anmeldung im Browser zwar, aber danach meldet der Launcher:
„Minecraft-Dienste lehnen diese Azure-App ab“. Das ist kein Fehler im Code.

## 3. Client-ID
Die ShadowClient-App-ID `7339d6c7-637e-4b1c-8d0d-712f90607a77` ist fest eingebaut. Nutzer müssen nichts eintragen.
Forks können unter **Einstellungen → Microsoft-Konten** eine eigene ID setzen.

Für einen Release-Build kannst du die ID auch fest einbauen:
```powershell
$env:SHADOWCLIENT_MS_CLIENT_ID="deine-client-id"; npm run tauri build
```

## Häufige Fehlermeldungen
| Meldung | Ursache |
|---|---|
| Client-ID ungültig / öffentlicher Client-Flow | Schritt 1.3 oder 1.5 fehlt |
| Kein Xbox-Profil | Einmal auf minecraft.net / xbox.com anmelden |
| Kinderkonto | Konto muss zu einer Microsoft-Familie hinzugefügt werden |
| Minecraft-Dienste lehnen Azure-App ab | Schritt 2 (Freischaltung) noch ausstehend |
| Konto besitzt Minecraft: Java Edition nicht | Spiel ist auf diesem Konto nicht gekauft |
