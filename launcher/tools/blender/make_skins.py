"""
Detaillierte, eigene 64x64-Skins (beide Schichten) für die Banner.
Schattierung pro Fläche (oben hell, unten dunkel, Seiten dunkler), Haarsträhnen, Jacke als Außenschicht.
"""
import random
from PIL import Image

def hexc(h):
    h = h.lstrip("#"); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def mul(c, f): return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)
def mix(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)

class Skin:
    def __init__(self, seed):
        self.im = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); self.p = self.im.load(); self.r = random.Random(seed)
    def px(self, x, y, c): self.p[x, y] = c if len(c) == 4 else c + (255,)
    def clear(self, x, y, w, h):
        for i in range(x, x + w):
            for j in range(y, y + h): self.p[i, j] = (0, 0, 0, 0)
    def face(self, x, y, w, h, col, side=1.0, grad=0.18, noise=0.035, edge=0.0):
        """Fläche mit vertikalem Verlauf, Rauschen und optional dunklem Rand."""
        for i in range(x, x + w):
            for j in range(y, y + h):
                t = (j - y) / max(1, h - 1)
                f = side * (1.06 - grad * t) * (1 + self.r.uniform(-noise, noise))
                if edge and (i in (x, x + w - 1)): f *= 1 - edge
                self.px(i, j, mul(col, f))
    def box(self, u, v, w, h, d, col, grad=0.18, noise=0.035, edge=0.06):
        self.face(u + d, v, w, d, col, 1.12, 0.04, noise)                 # oben
        self.face(u + d + w, v, w, d, col, 0.62, 0.0, noise)              # unten
        self.face(u, v + d, d, h, col, 0.84, grad, noise, edge)           # rechts
        self.face(u + d, v + d, w, h, col, 1.0, grad, noise, edge)        # vorne
        self.face(u + d + w, v + d, d, h, col, 0.84, grad, noise, edge)   # links
        self.face(u + 2 * d + w, v + d, w, h, col, 0.78, grad, noise, edge)  # hinten
    def front(self, u, v, d): return (u + d, v + d)

def hair_strands(s, x, y, w, h, base, depth_rows):
    """Haarfläche mit Strähnen in 3 Tönen + Glanz."""
    tones = [mul(base, 1.18), mul(base, 1.0), mul(base, 0.82), mul(base, 0.68)]
    for i in range(x, x + w):
        k = s.r.random()
        for j in range(y, y + h):
            t = (j - y) / max(1, h)
            idx = min(3, int((t * 2.2 + k * 1.3)))
            if (i + j) % 5 == 0 and t < 0.4: idx = 0
            s.px(i, j, tones[idx])

def character(name, seed, skin, hair, eye, shirt, jacket, accent, pants, shoe, style, smile=True, blush=True):
    s = Skin(seed)
    S, H, E, SH, J, A, P, F = map(hexc, (skin, hair, eye, shirt, jacket, accent, pants, shoe))
    # ---------------- Kopf (Basis)
    s.box(0, 0, 8, 8, 8, S, grad=0.08, edge=0.04)
    hair_strands(s, 8, 0, 8, 8, H, 0)                       # oben
    hair_strands(s, 24, 8, 8, 8, H, 0)                      # hinten (komplett)
    for (x0) in (0, 16):                                    # Seiten: oben Haar, Ohr-Bereich Haut
        hair_strands(s, x0, 8, 8, 4, H, 0)
        for j in range(12, 15): s.px(x0 + (7 if x0 == 0 else 0), j, mul(H, 0.8))
        for j in range(12, 16): s.px(x0 + (6 if x0 == 0 else 1), j, mul(H, 0.85))
    # Pony vorne mit unregelmäßiger Kante
    fx, fy = 8, 8
    fringe = [2, 3, 2, 2, 3, 2, 3, 2] if style != "short" else [1, 2, 1, 1, 2, 1, 2, 1]
    for i, depth in enumerate(fringe):
        for j in range(depth): s.px(fx + i, fy + j, mul(H, 1.08 - 0.1 * j))
    # Augenbrauen, Augen (2x2 mit Glanz), Nase, Mund, Wangen
    ey = 12
    for bx in (9, 13): s.px(bx, ey - 1, mul(H, 0.75)); s.px(bx + 1, ey - 1, mul(H, 0.75))
    for (wx, ix) in ((9, 10), (14, 13)):
        s.px(wx, ey, (250, 250, 250)); s.px(ix, ey, mul(E, 1.0))      # klassisches 2-Pixel-Auge
        s.px(wx, ey + 1, mul(S, 0.96)); s.px(ix, ey + 1, mul(S, 0.96))
    s.px(10, ey, (255, 255, 255)) if False else None
    s.px(11, 13, mul(S, 0.9)); s.px(12, 13, mul(S, 0.9))      # Nase (Schatten)
    s.px(11, 14, mul(S, 0.86))
    if smile:
        for x in (11, 12): s.px(x, 15, (120, 60, 55))
        s.px(10, 14, (130, 70, 62)); s.px(13, 14, (130, 70, 62))
    else:
        for x in (11, 12): s.px(x, 15, mul(S, 0.65))
    if blush:
        s.px(9, 14, mix(S, (235, 120, 120), 0.35)); s.px(14, 14, mix(S, (235, 120, 120), 0.35))
    # ---------------- Kopf-Außenschicht (Haarvolumen)
    for x in range(32, 64):
        for y in range(0, 16): s.p[x, y] = (0, 0, 0, 0)
    hair_strands(s, 40, 0, 8, 8, H, 0)                       # oben, Volumen
    hair_strands(s, 56, 8, 8, 7, H, 0)                       # hinten
    for x0 in (32, 48):
        hair_strands(s, x0, 8, 8, 3, H, 0)
        for j in range(11, 14):                              # Strähnen über die Ohren
            if s.r.random() < 0.7: s.px(x0 + (7 if x0 == 32 else 0), j, mul(H, 0.9))
    for i in range(8):                                       # Pony-Volumen
        if s.r.random() < 0.75: s.px(40 + i, 8, mul(H, 1.15))
    if style == "hood":
        for x in range(32, 64):
            for y in range(8, 16):
                if not (40 <= x < 48 and y >= 10): s.px(x, y, mul(J, 1.0 - 0.15 * ((y - 8) / 8)))
        s.box(32, 0, 8, 8, 8, J, grad=0.1)
        s.clear(41, 10, 6, 6)                                 # Gesichtsöffnung
        for x in range(40, 48): s.px(x, 8, mul(J, 1.2)); s.px(x, 9, mul(J, 1.05))
        for y in range(10, 16): s.px(40, y, mul(J, 0.9)); s.px(47, y, mul(J, 0.9))
    if style == "bandana":
        for x in range(32, 64):
            for y in (9, 10): s.px(x, y, mul(A, 1.0 if y == 9 else 0.82))
        s.px(58, 11, mul(A, 0.85)); s.px(59, 11, mul(A, 0.85)); s.px(58, 12, mul(A, 0.75)); s.px(60, 12, mul(A, 0.75))
    if style == "visor":
        for x in range(40, 48):
            s.px(x, 11, mul(A, 1.25)); s.px(x, 12, mul(A, 0.9))
        for x in (32, 39, 48, 55):
            for y in (11, 12): s.px(x, y, mul(A, 0.7))
    if style == "crown":
        for i, x in enumerate(range(40, 48)):
            s.px(x, 7, (250, 204, 21)); 
            if i % 2 == 0: s.px(x, 6, (253, 224, 71))
        for x in range(32, 64):
            s.px(x, 8, (234, 179, 8))
        s.px(43, 8, mul(A, 1.2)); s.px(44, 8, mul(A, 1.2))
    # ---------------- Oberkörper (Basis = Shirt), Arme (Haut/Ärmel), Beine
    s.box(16, 16, 8, 12, 4, SH, grad=0.2)
    s.box(40, 16, 4, 12, 4, SH, grad=0.2); s.box(32, 48, 4, 12, 4, SH, grad=0.2)
    for (u, v) in ((40, 16), (32, 48)):                      # Hände
        for x in range(u, u + 16):
            for y in (v + 14, v + 15): s.px(x, y, mul(S, 0.95 - 0.08 * (y - v - 14)))
        s.face(u + 8, v, 4, 4, S, 0.62, 0)                   # Handfläche unten
    s.box(0, 16, 4, 12, 4, P, grad=0.15); s.box(16, 48, 4, 12, 4, P, grad=0.15)
    for (u, v) in ((0, 16), (16, 48)):                       # Knie-Falten + Schuhe
        for x in range(u + 4, u + 8):
            s.px(x, v + 4 + 5, mul(P, 0.82))
        for x in range(u, u + 16):
            s.px(x, v + 4 + 9, mul(F, 1.2)); s.px(x, v + 4 + 10, mul(F, 1.0)); s.px(x, v + 4 + 11, (235, 235, 240) if x % 4 else mul(F, 0.6))
        s.face(u + 8, v, 4, 4, F, 0.5, 0)
        s.face(u + 4, v, 4, 4, P, 1.1, 0)
    # Shirt-Details vorne
    fxb, fyb = 20, 20
    for x in range(fxb + 2, fxb + 6):
        s.px(x, fyb, mul(SH, 0.75))                           # Kragen
    # ---------------- Jacke als Außenschicht
    for (u, v, w, h, d) in ((16, 32, 8, 12, 4), (40, 32, 4, 12, 4), (48, 48, 4, 12, 4)):
        s.box(u, v, w, h, d, J, grad=0.22, edge=0.1)
    # Jacke vorne offen, Reißverschluss/Akzent, Taschen, Saum
    ju, jv = 16 + 4, 32 + 4
    for y in range(jv, jv + 12):
        for x in range(ju + 3, ju + 5): s.p[x, y] = (0, 0, 0, 0)      # offen → Shirt sichtbar
        s.px(ju + 2, y, mul(A, 1.0 - 0.15 * (y - jv) / 12)); s.px(ju + 5, y, mul(A, 1.0 - 0.15 * (y - jv) / 12))
    for x in range(ju, ju + 8):
        if s.p[x, jv + 11][3]: s.px(x, jv + 11, mul(J, 0.62))           # Saum
    for (x0) in (ju, ju + 6):
        s.px(x0, jv + 7, mul(J, 0.7)); s.px(x0 + 1, jv + 7, mul(J, 0.7))  # Taschen
    for (u, v) in ((40, 32), (48, 48)):                       # Ärmel-Bündchen + Akzentstreifen
        for x in range(u, u + 16):
            s.px(x, v + 4 + 9, mul(A, 0.95)); s.px(x, v + 4 + 10, mul(J, 0.6))
            s.p[x, v + 4 + 11] = (0, 0, 0, 0)
        for y in range(v + 4, v + 4 + 9): s.px(u + 4 + 1, y, mul(A, 0.85))
    # Shirt-Akzent (unter offener Jacke sichtbar)
    for y in range(22, 30): s.px(23, y, mul(A, 1.15)); s.px(24, y, mul(A, 1.15))
    s.im.save(f"skins/{name}.png")

character("shadow", 1, "#d6a582", "#1c1714", "#22d3ee", "#e2e8f0", "#111827", "#22d3ee", "#1f2937", "#0b0f19", "hood")
character("ember", 2, "#e8b58e", "#8a3b12", "#16a34a", "#fde68a", "#b91c1c", "#f59e0b", "#3f2a1d", "#2b1d16", "bandana")
character("frost", 3, "#f3d6bc", "#eef2f7", "#38bdf8", "#bfdbfe", "#1e40af", "#7dd3fc", "#1e293b", "#0f172a", "visor")
character("nova", 4, "#b07b5c", "#1a1024", "#c084fc", "#f5d0fe", "#5b21b6", "#e879f9", "#1e1b4b", "#120a24", "crown")
print("skins v2 ok")
