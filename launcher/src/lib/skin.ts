/**
 * Erzeugt eine eigene, prozedurale 64x64-Skin-Textur (Standard-Layout) im
 * ShadowClient-Stil. Damit wird kein Mojang-Asset mitgeliefert. Ab Phase 2
 * wird stattdessen der echte Skin des angemeldeten Accounts geladen.
 */
export function createShadowSkin(accent: string): HTMLCanvasElement {
  const c = document.createElement("canvas");
  c.width = c.height = 64;
  const g = c.getContext("2d")!;
  const rect = (x: number, y: number, w: number, h: number, col: string) => { g.fillStyle = col; g.fillRect(x, y, w, h); };

  const skin = "#c99a7a", hoodie = "#0f172a", hoodie2 = "#1e293b", pants = "#111827", shoe = "#020617";

  // Kopf (8..32 x 0..16): Haut + Haare
  rect(0, 0, 32, 16, skin);
  rect(8, 0, 8, 8, "#1f1a17"); rect(16, 0, 8, 8, "#1f1a17");          // oben/unten
  rect(0, 8, 32, 2, "#1f1a17");                                        // Haaransatz rundum
  rect(9, 11, 2, 1, "#fff"); rect(13, 11, 2, 1, "#fff");               // Augen (Front: 8..16)
  rect(10, 11, 1, 1, accent); rect(14, 11, 1, 1, accent);
  rect(11, 14, 2, 1, "#8a5a44");
  // Kapuze als Overlay-Schicht (32..64 x 0..16)
  rect(32, 8, 32, 2, hoodie); rect(40, 0, 8, 8, hoodie);

  // Körper (16..40 x 16..32)
  rect(16, 16, 24, 16, hoodie);
  rect(20, 20, 8, 12, hoodie2);
  rect(23, 22, 2, 6, accent);                                          // Reißverschluss-Akzent
  // Rechter Arm (40..56 x 16..32) & linker Arm (32..48 x 48..64)
  rect(40, 16, 16, 16, hoodie); rect(40, 29, 16, 3, skin);
  rect(32, 48, 16, 16, hoodie); rect(32, 61, 16, 3, skin);
  rect(44, 26, 4, 1, accent); rect(36, 58, 4, 1, accent);
  // Beine (0..16 x 16..32) & (16..32 x 48..64)
  rect(0, 16, 16, 16, pants); rect(0, 29, 16, 3, shoe);
  rect(16, 48, 16, 16, pants); rect(16, 61, 16, 3, shoe);
  return c;
}
