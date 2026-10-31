@echo off
setlocal
cd /d "%~dp0"
echo ===== ShadowClient auf GitHub hochladen (CrystalSMP/ShadowClient) =====
echo.

where git >nul 2>nul
if errorlevel 1 (
  echo Git ist nicht installiert - wird jetzt installiert ...
  winget install --id Git.Git -e --silent --accept-package-agreements --accept-source-agreements
  set "PATH=%PATH%;C:\Program Files\Git\cmd"
  where git >nul 2>nul || ( echo [FEHLER] Git konnte nicht installiert werden. Bitte https://git-scm.com installieren und nochmal starten. & pause & exit /b 1 )
)

git config user.name >nul 2>nul || git config user.name "CrystalSMP"
git config user.email >nul 2>nul || git config user.email "maxkohli015@gmail.com"
git remote set-url origin https://github.com/CrystalSMP/ShadowClient.git

echo Gleich oeffnet sich ein Fenster: "Sign in with your browser" klicken und mit CrystalSMP anmelden.
echo.
git push -u origin main --force
if errorlevel 1 (
  echo.
  echo [FEHLER] Hochladen hat nicht geklappt. Gibt es das Repo github.com/CrystalSMP/ShadowClient?
  echo Mach einen Screenshot von diesem Fenster und schick ihn an Claude.
  pause & exit /b 1
)
echo.
echo FERTIG! https://github.com/CrystalSMP/ShadowClient
start https://github.com/CrystalSMP/ShadowClient
pause
