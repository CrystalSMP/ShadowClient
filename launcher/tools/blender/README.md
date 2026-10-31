# 3D-Banner mit Blender

## Promo-Stil (hell, figurenzentriert) – Standard
```bat
cd launcher\tools\blender
python make_skins.py
blender -b -P promo.py -- friends C:\temp\friends_raw.png 1280 640 32
python overlay.py friends C:\temp\friends_raw.png ..\..\public\news\friends.png
```
Szenen: `friends`, `modules`, `hud`, `badge`, `version`, `keyart` (1080×1080). Titel, Untertitel und Farben stehen in `overlay.py` → `STYLES`,
Szenenaufbau (Figuren, Posen, Landschaft, Kamera) in `promo.py` → `scene_*`.

## Höhlen-Stil (dunkel, Action)

Echte 3D-Renderings: eigene Minecraft-Spielermodelle (Standard-Skin-Layout, 64×64), Kristallhöhle aus Blöcken,
Licht, Nebel, Bloom, Energiebögen und ein extrudierter Pixel-Schriftzug. Alles eigene Inhalte, keine Mojang-Assets.

## Voraussetzungen
- Blender 4.x (`winget install BlenderFoundation.Blender`)
- Python mit Pillow (nur für `make_skins.py`)

## Rendern
```bat
cd launcher\tools\blender
python make_skins.py
blender -b -P keyart.py -- keyart  ..\..\public\news\keyart.png 1080 1080 32
blender -b -P keyart.py -- friends ..\..\public\news\friends.png 1280 640 24
```
Szenen: `keyart`, `friends`, `modules`, `hud`, `badge`, `version` (Konfiguration unten in `keyart.py` → `CONFIGS`).

## Eigene / echte Skins
Lege einen beliebigen 64×64-Skin als PNG in `skins/` (z. B. `skins/maxushky.png`, eigenen Skin von namemc.com
o. ä. herunterladen) und trage den Namen in `CONFIGS` ein, z. B. `("maxushky", (0, 0, 0), 20, "slash", 1.2, None)`.
Posen: `slash`, `leap`, `bow`, `cast`, `wave`, `run`, `point`, `cheer`, `idle`.
