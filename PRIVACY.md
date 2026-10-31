# Privacy Policy – ShadowClient

_Last updated: 2026-10-29_

ShadowClient is a desktop application that runs locally on your computer.

## What is processed
- **Microsoft / Xbox / Minecraft authentication:** You sign in on Microsoft's own website (device code flow). ShadowClient never sees your password.
- **Stored locally:** your Minecraft username, UUID and skin URL (`accounts.json`), and a Microsoft refresh token in the Windows Credential Manager.
- **Kept in memory only:** Minecraft access tokens. They are never written to disk and are masked in log files.
- **Settings, profiles, logs** are stored locally in `%APPDATA%\dev.shadowclient.launcher`.

## What is sent where
Only to official services, and only to sign you in and download the game:
`login.microsoftonline.com`, `*.xboxlive.com`, `api.minecraftservices.com`, `piston-meta.mojang.com`,
`piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`, `meta.fabricmc.net`, `maven.fabricmc.net`.
The player head avatar is loaded from `mc-heads.net` using your public UUID.

ShadowClient has **no analytics, no tracking and no own servers**. Optional online features (e.g. synced cosmetics) will be opt-in only.

## Removing your data
Sign out in *Settings → Microsoft accounts*. This deletes the stored token. Uninstalling the app and deleting the folder above removes everything else.

## Contact
Open an issue in this repository.
