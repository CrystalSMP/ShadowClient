import { useEffect, useRef } from "react";
import { SkinViewer as Viewer, IdleAnimation, NameTagObject } from "skinview3d";
import { createShadowSkin } from "../lib/skin";

// Standard-Steve direkt von Mojangs Texture-Server (wie im offiziellen Launcher)
const STEVE = "https://textures.minecraft.net/texture/31f477eb1a7beee631c2ca64d06f8f68fa93a3386d04452ab27f43acdf1b60cb";

interface Props {
  accent: string;
  skinUrl?: string | null;
  nameTag?: string | null;
  capeUrl?: string | null;
  rotation?: number;
  animate?: boolean;
  width?: number;
  height?: number;
}

/**
 * 3D-Skin-Vorschau (WebGL via skinview3d). Maus: drehen, Mausrad: zoomen.
 * Mit `skinUrl` wird der echte Skin geladen, sonst die eigene Standard-Skin.
 */
export default function SkinViewer({ accent, skinUrl, nameTag, capeUrl, rotation = 0.45, animate = true, width = 280, height = 380 }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const viewerRef = useRef<Viewer | null>(null);

  useEffect(() => {
    const viewer = new Viewer({ canvas: canvasRef.current!, width, height });
    viewer.playerObject.rotation.y = 0.45;
    viewer.zoom = 0.8;
    viewer.fov = 55;
    viewer.controls.enableZoom = false;
    viewerRef.current = viewer;
    return () => { viewer.dispose(); viewerRef.current = null; };
  }, [width, height]);

  useEffect(() => {
    const v = viewerRef.current;
    if (!v) return;
    const fallback = () => v.loadSkin(createShadowSkin(accent));
    v.loadSkin(skinUrl ? skinUrl.replace(/^http:/, "https:") : STEVE).catch(fallback);
  }, [accent, skinUrl, width, height]);

  useEffect(() => {
    const v = viewerRef.current;
    if (!v) return;
    if (capeUrl) v.loadCape(capeUrl).catch(() => v.loadCape(null)); else v.loadCape(null);
  }, [capeUrl, width, height]);

  useEffect(() => { if (viewerRef.current) viewerRef.current.playerObject.rotation.y = rotation; }, [rotation, width, height]);

  useEffect(() => {
    let cancelled = false;
    // Pixel-Schrift erst laden, sonst zeichnet das Canvas mit der Standardschrift
    document.fonts.load('48px "Pixelify Sans"').catch(() => {}).finally(() => {
      const v = viewerRef.current;
      if (!v || cancelled) return;
      v.nameTag = nameTag ? new NameTagObject(nameTag, { font: '600 46px "Pixelify Sans", monospace' }) : null;
    });
    return () => { cancelled = true; };
  }, [nameTag, width, height]);

  useEffect(() => {
    const v = viewerRef.current;
    if (!v) return;
    v.animation = animate ? new IdleAnimation() : null;
  }, [animate, width, height]);

  return <canvas ref={canvasRef} className="skin-canvas" />;
}
