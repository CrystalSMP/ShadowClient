@echo off
setlocal
rem ===== ShadowClient Release bauen: Mod-JAR + Launcher-Installer =====
cd /d "%~dp0"

set MODDIR=
if exist "minecraft-mod\build.bat" set MODDIR=minecraft-mod
if exist "..\ShadowClient-Mod\build.bat" set MODDIR=..\ShadowClient-Mod
if "%MODDIR%"=="" ( echo [FEHLER] Mod-Ordner nicht gefunden. & pause & exit /b 1 )

echo [1/3] Baue die Mod in %MODDIR% ...
pushd "%MODDIR%"
call build.bat nopause
popd

set JAR=
for /f "delims=" %%f in ('dir /b /o-d "%MODDIR%\build\libs\shadowclient-*.jar" 2^>nul ^| findstr /v /i "sources dev"') do if not defined JAR set JAR=%%f
if "%JAR%"=="" ( echo [FEHLER] Keine Mod-JAR gefunden – ist der Mod-Build durchgelaufen? & pause & exit /b 1 )
echo     gefunden: %JAR%

echo [2/3] Lege die Mod in den Launcher ...
if not exist "launcher\src-tauri\resources\mods" mkdir "launcher\src-tauri\resources\mods"
copy /y "%MODDIR%\build\libs\%JAR%" "launcher\src-tauri\resources\mods\shadowclient.jar" >nul

if exist "%~dp0shadowclient-updater.key" (
  set /p TAURI_SIGNING_PRIVATE_KEY=<"%~dp0shadowclient-updater.key"
) else (
  echo [FEHLER] shadowclient-updater.key fehlt neben release.bat - ohne Schluessel keine Auto-Updates.
  pause & exit /b 1
)
echo [3/3] Baue den Installer (dauert beim ersten Mal ein paar Minuten) ...
cd launcher
call npm install || ( pause & exit /b 1 )
call npm run tauri build -- --config src-tauri/tauri.release.json || ( pause & exit /b 1 )

echo.
echo Fertig! Der Installer liegt hier:
dir /b "src-tauri\target\release\bundle\nsis\*.exe"
explorer "src-tauri\target\release\bundle\nsis"
pause
