@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
title ShadowClient Mod - Build
cd /d "%~dp0"

echo ==========================================
echo   ShadowClient Mod - Build (Minecraft 1.21.11)
echo ==========================================
echo.

rem ---- 1. Richtige Projektdateien? ----
if not exist gradlew.bat (
  echo [FEHLER] gradlew.bat fehlt. build.bat muss im Ordner ShadowClient-Mod liegen.
  goto :fail
)
findstr /c:"yarn_mappings=1.21.11" gradle.properties >nul 2>nul
if errorlevel 1 (
  echo [FEHLER] Dieser Ordner enthaelt noch ALTE Projektdateien ^(nicht 1.21.11^).
  echo Bitte ShadowClient-Mod-0.6.0-1.21.11.zip in einen NEUEN, leeren Ordner entpacken.
  goto :fail
)
if exist "src\main\java\dev\shadowclient\module\impl\FpsModule.java" (
  echo [INFO] Entferne alte Dateien aus Version 0.3 ...
  del /q "src\main\java\dev\shadowclient\module\impl\FpsModule.java" "src\main\java\dev\shadowclient\module\impl\PingModule.java" "src\main\java\dev\shadowclient\module\impl\CoordinatesModule.java" 2>nul
)

rem ---- 2. Java 25 suchen (Gradle/Loom braucht es, die Mod selbst laeuft mit Java 21) ----
set "JDK25="
for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*" "%ProgramFiles%\Eclipse Adoptium\jdk-26*" "%ProgramFiles%\Java\jdk-25*" "%ProgramFiles%\Microsoft\jdk-25*" "%ProgramFiles%\Zulu\zulu-25*") do (
  if exist "%%~d\bin\java.exe" set "JDK25=%%~d"
)
if not defined JDK25 (
  echo [FEHLER] Java 25 nicht gefunden.
  echo Installieren mit:   winget install EclipseAdoptium.Temurin.25.JDK
  echo Danach dieses Fenster schliessen und build.bat erneut starten.
  goto :fail
)
set "JAVA_HOME=%JDK25%"
set "PATH=%JDK25%\bin;%PATH%"
echo Java:  %JDK25%
echo.

rem ---- 3. Bauen ----
echo Baue Mod ... ^(beim ersten Mal laedt Gradle Minecraft, das dauert ein paar Minuten^)
echo.
call gradlew.bat build --no-daemon
if errorlevel 1 (
  echo.
  echo [FEHLER] Build fehlgeschlagen. Schick die Ausgabe oben an Claude.
  goto :fail
)

rem ---- 4. Ergebnis ----
if not exist output mkdir output
del /q output\*.jar >nul 2>nul
for %%f in (build\libs\shadowclient-*.jar) do (
  echo %%~nxf | findstr /i "sources" >nul || copy /y "%%f" "output\%%~nxf" >nul
)
echo.
echo ==========================================
echo   Fertig! Die Mod liegt im Ordner "output".
echo ==========================================
echo.
echo Installation: Jar aus "output" + Fabric API fuer 1.21.11
echo (https://modrinth.com/mod/fabric-api) in den mods-Ordner legen.
echo.
set COPY=n
if /i not "%~1"=="nopause" set /p COPY=Direkt nach %%APPDATA%%\.minecraft\mods kopieren? (j/n): 
if /i "!COPY!"=="j" (
  if not exist "%APPDATA%\.minecraft\mods" mkdir "%APPDATA%\.minecraft\mods"
  copy /y output\*.jar "%APPDATA%\.minecraft\mods\" >nul
  echo Kopiert nach %APPDATA%\.minecraft\mods
)
start "" "%~dp0output"
if /i not "%~1"=="nopause" pause
exit /b 0

:fail
echo.
if /i not "%~1"=="nopause" pause
exit /b 1
