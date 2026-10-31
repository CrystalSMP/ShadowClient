@echo off
chcp 65001 >nul
title ShadowClient Mod - Testen
cd /d "%~dp0"
echo Startet Minecraft 1.21.11 mit der Mod im Entwicklungsmodus (ohne Login, Offline-Testnutzer).
echo Im Spiel: Rechts-Shift druecken.
echo.
set "JDK25="
for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*" "%ProgramFiles%\Eclipse Adoptium\jdk-26*" "%ProgramFiles%\Java\jdk-25*") do if exist "%%~d\bin\java.exe" set "JDK25=%%~d"
if not defined JDK25 (
  echo [FEHLER] Java 25 nicht gefunden: winget install EclipseAdoptium.Temurin.25.JDK
  pause
  exit /b 1
)
set "JAVA_HOME=%JDK25%"
call gradlew.bat runClient --no-daemon
pause
