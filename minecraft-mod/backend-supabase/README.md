# ShadowClient Backend (Supabase): Abzeichen & Freunde

Damit ShadowClient-Nutzer sich gegenseitig am **✦ vor dem Namen** erkennen.

- Nur mit Zustimmung: Das Modul „ShadowClient-Abzeichen“ (Kategorie Online) ist standardmäßig **aus**.
- Gespeichert werden nur **Minecraft-UUID, Name und Zeitpunkt der letzten Meldung**.
- **Fälschungssicher:** Die Mod meldet sich vorher bei Mojang an (wie beim Betreten eines Servers),
  das Backend prüft das über `sessionserver.mojang.com/hasJoined`.

## Einrichten (ca. 10 Minuten)

1. Auf https://supabase.com ein neues Projekt anlegen (kostenlos).
2. **Tabellen anlegen:** Supabase → *SQL Editor* → nacheinander ausführen (**Run**):
   - `supabase/migrations/20261029000000_shadowclient_users.sql`
   - `supabase/migrations/20261030000000_shadowclient_friends.sql` (Freunde, Sitzungen)
3. **Edge Function anlegen:** Supabase → *Edge Functions* → *Deploy a new function* → *Via Editor*
   - Name: `shadowclient`
   - Code: Inhalt von `supabase/functions/shadowclient/index.ts` einfügen → **Deploy**
   - Danach in den Einstellungen der Function **„Verify JWT“ / „Enforce JWT verification“ AUSschalten**.
     Wichtig: Der neue `sb_publishable_…`-Key ist kein JWT, sonst lehnt Supabase jede Anfrage mit 401 ab.

   (Alternativ mit der Supabase CLI: `supabase functions deploy shadowclient --no-verify-jwt`)
4. URL und Publishable Key sind bereits in `src/main/java/dev/shadowclient/online/OnlineConfig.java` eingetragen
   (Projekt `qshyrzvaspluttbvicnw`). Bei einem anderen Projekt dort ändern und neu bauen.

## Testen
Im Browser öffnen: https://qshyrzvaspluttbvicnw.supabase.co/functions/v1/shadowclient/users
Erwartet: `{"uuids":[]}`. Kommt 401, ist „Verify JWT“ noch an.

Ingame: **Rechts-Shift → Online → ShadowClient-Abzeichen** einschalten.
Beim Darüberfahren zeigt das Menü den Status (z. B. „Verbunden · 3 Nutzer online“).

## Hinweise
- Der **Offline-Testmodus** (`test-im-spiel.bat`) hat kein echtes Konto, deshalb klappt das Melden dort nicht. Das Abrufen der Liste funktioniert trotzdem.
- Einträge, die älter als 15 Minuten sind, gelten als offline.
