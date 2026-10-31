"""Cinematic-Overlay: Color-Grading (Kontrast, Wärme, Bloom, Vignette, Korn) + moderne Typografie (Inter)."""
import sys, os, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance, ImageChops

HERE = os.path.dirname(os.path.abspath(__file__))
INTER = "/usr/share/fonts/opentype/inter/"
def F(w, size):
    for p in (INTER + f"InterDisplay-{w}.otf", INTER + f"Inter-{w}.otf", os.path.join(HERE, f"Inter-{w}.otf")):
        if os.path.exists(p): return ImageFont.truetype(p, size)
    return ImageFont.load_default()
LOGO = os.path.join(HERE, "..", "..", "src-tauri", "icons", "128x128.png")

def grade(im):
    im = im.convert("RGB")
    im = ImageEnhance.Contrast(im).enhance(1.18)
    im = ImageEnhance.Color(im).enhance(1.32)
    # warme Lichter / kühle Schatten (Split-Toning)
    r, g, b = im.split()
    lum = im.convert("L")
    warm = Image.merge("RGB", (r.point(lambda v: min(255, v * 1.06 + 4)), g.point(lambda v: min(255, v * 1.01)), b.point(lambda v: v * 0.9)))
    cool = Image.merge("RGB", (r.point(lambda v: v * 0.9), g.point(lambda v: v * 0.98), b.point(lambda v: min(255, v * 1.08 + 6))))
    im = Image.composite(warm, cool, lum.point(lambda v: min(255, max(0, (v - 60) * 2))))
    # Bloom
    hi = im.point(lambda v: max(0, v - 185) * 3).filter(ImageFilter.GaussianBlur(im.width / 60))
    im = ImageChops.screen(im, ImageEnhance.Brightness(hi).enhance(0.55))
    return im.convert("RGBA")

def vignette(im, left_text, centered):
    W, H = im.size; v = Image.new("L", (W, H), 0); d = ImageDraw.Draw(v)
    for i in range(80):
        a = int(140 * (i / 80) ** 2); m = int((80 - i) * min(W, H) / 220)
        d.rectangle([m * 0, 0, W, H], outline=None)
    big = Image.new("L", (W * 2, H * 2), 0); bd = ImageDraw.Draw(big)
    bd.ellipse([W * 0.15, H * 0.1, W * 1.85, H * 1.9], fill=255)
    mask = big.resize((W, H)).filter(ImageFilter.GaussianBlur(min(W, H) / 5))
    dark = Image.new("RGBA", (W, H), (6, 8, 14, 255))
    inv = mask.point(lambda v: int((255 - v) * 0.75))
    im = Image.composite(dark, im, inv)
    g = Image.new("L", (W, H), 0); gd = ImageDraw.Draw(g)
    if centered:
        for y in range(int(H * 0.45)): gd.line([(0, y), (W, y)], fill=int(170 * (1 - y / (H * 0.45)) ** 1.6))
        for y in range(int(H * 0.72), H): gd.line([(0, y), (W, y)], fill=int(190 * max(0.0, (y - H * 0.72) / (H * 0.28)) ** 1.6))
    else:
        for x in range(int(W * 0.62)): gd.line([(x, 0), (x, H)], fill=int(215 * (1 - x / (W * 0.62)) ** 1.5))
    return Image.composite(Image.new("RGBA", (W, H), (5, 7, 13, 255)), im, g)

def grain(im, amt=7):
    rnd = random.Random(3); W, H = im.size
    n = Image.effect_noise((W, H), 40).point(lambda v: 128 + (v - 128) * amt // 40)
    return Image.composite(im, Image.merge("RGBA", (n, n, n, Image.new("L", (W, H), 255))), Image.new("L", (W, H), 245))

def shadow_text(base, xy, text, font, fill, blur, alpha=150, anchor="la"):
    sh = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).text((xy[0], xy[1] + blur * 0.6), text, font=font, fill=(0, 0, 0, alpha), anchor=anchor)
    base.alpha_composite(sh.filter(ImageFilter.GaussianBlur(blur)))
    ImageDraw.Draw(base).text(xy, text, font=font, fill=fill, anchor=anchor)

def kicker(base, xy, text, accent, s, anchor_center=False):
    d = ImageDraw.Draw(base); f = F("Bold", int(17 * s))
    tw = d.textlength(text, font=f) + len(text) * 2.2 * s
    x, y = xy
    if anchor_center: x -= (tw + 26 * s) / 2
    d.rounded_rectangle([x, y, x + tw + 26 * s, y + 32 * s], radius=int(16 * s), fill=accent)
    cx = x + 13 * s
    for ch in text:
        d.text((cx, y + 16 * s), ch, font=f, fill=(8, 10, 18, 255), anchor="lm"); cx += d.textlength(ch, font=f) + 2.2 * s

def logo(base, xy, s, center=False):
    d = ImageDraw.Draw(base); f = F("Bold", int(20 * s)); sz = int(34 * s)
    w = sz + 10 * s + d.textlength("ShadowClient", font=f)
    x, y = (xy[0] - w / 2, xy[1]) if center else xy
    if os.path.exists(LOGO):
        base.alpha_composite(Image.open(LOGO).convert("RGBA").resize((sz, sz), Image.LANCZOS), (int(x), int(y)))
    d.text((x + sz + 10 * s, y + sz / 2), "Shadow", font=f, fill=(255, 255, 255, 235), anchor="lm")
    d.text((x + sz + 10 * s + d.textlength("Shadow", font=f), y + sz / 2), "Client", font=f, fill=(103, 232, 249, 255), anchor="lm")

BUNGEE = os.path.join(HERE, "Bungee.ttf")
def bolt(base, xy, s, accent):
    "Kleines ShadowClient-Logo unten rechts (eigenes Icon, weiß mit Schatten)."
    if os.path.exists(LOGO):
        sz = int(64 * s); lg = Image.open(LOGO).convert("RGBA").resize((sz, sz), Image.LANCZOS)
        sh = Image.new("RGBA", base.size, (0, 0, 0, 0)); a = lg.split()[3].point(lambda v: v * 0.55)
        sh.paste((0, 0, 0, 255), (int(xy[0] - sz), int(xy[1] - sz + 4 * s)), a)
        base.alpha_composite(sh.filter(ImageFilter.GaussianBlur(6 * s)))
        base.alpha_composite(lg, (int(xy[0] - sz), int(xy[1] - sz)))

def compose(raw, out, kick, title, sub, accent, centered=False):
    im = Image.open(raw).convert("RGB")
    im = ImageEnhance.Contrast(im).enhance(1.12); im = ImageEnhance.Color(im).enhance(1.35)
    hi = im.point(lambda v: max(0, v - 200) * 3).filter(ImageFilter.GaussianBlur(im.width / 70))
    base = ImageChops.screen(im, ImageEnhance.Brightness(hi).enhance(0.4)).convert("RGBA")
    W, H = base.size; s = W / 1280 if not centered else W / 1080
    # sanfte Vignette
    big = Image.new("L", (W * 2, H * 2), 0); ImageDraw.Draw(big).ellipse([-W * 0.3, -H * 0.4, W * 2.3, H * 2.4], fill=255)
    m = big.resize((W, H)).filter(ImageFilter.GaussianBlur(min(W, H) / 4)).point(lambda v: int((255 - v) * 0.45))
    base = Image.composite(Image.new("RGBA", (W, H), (10, 14, 20, 255)), base, m)
    title = title.upper()
    f = ImageFont.truetype(BUNGEE, int((150 if len(title) <= 8 else 118 if len(title) <= 11 else 96) * s))
    d = ImageDraw.Draw(base)
    if centered:
        while d.textlength(title, font=f) > W * 0.88: f = ImageFont.truetype(BUNGEE, f.size - 4)
        xy, anchor = (W / 2, 70 * s), "mt"
    else:
        while d.textlength(title, font=f) > W * 0.44: f = ImageFont.truetype(BUNGEE, f.size - 4)
        xy, anchor = (52 * s, 52 * s), "la"
    # weißer Blocktitel mit weichem Schatten, Akzentbalken und Untertitel
    sh = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).text((xy[0] + 3 * s, xy[1] + 8 * s), title, font=f, fill=(0, 0, 0, 170), anchor=anchor)
    base.alpha_composite(sh.filter(ImageFilter.GaussianBlur(12 * s)))
    d = ImageDraw.Draw(base)
    d.text(xy, title, font=f, fill=(255, 255, 255, 255), anchor=anchor, stroke_width=max(2, int(4 * s)), stroke_fill=(20, 22, 30, 255))
    bb = d.textbbox(xy, title, font=f, anchor=anchor, stroke_width=max(2, int(4 * s)))
    sf = F("Bold", int(26 * s)); sub1 = sub.replace("\n", " ")
    bx = bb[0] if not centered else W / 2 - d.textlength(sub1, font=sf) / 2 - 20 * s
    by = bb[3] + 16 * s
    tw = d.textlength(sub1, font=sf)
    pill = Image.new("RGBA", base.size, (0, 0, 0, 0)); pd = ImageDraw.Draw(pill)
    pd.rounded_rectangle([bx, by, bx + tw + 40 * s, by + 46 * s], radius=int(23 * s), fill=(12, 14, 22, 215))
    pd.rounded_rectangle([bx + 8 * s, by + 8 * s, bx + 14 * s, by + 38 * s], radius=int(3 * s), fill=accent)
    base.alpha_composite(pill)
    ImageDraw.Draw(base).text((bx + 26 * s, by + 23 * s), sub1, font=sf, fill=(255, 255, 255, 240), anchor="lm")
    bolt(base, (W - 40 * s, H - 36 * s), s, accent)
    base.convert("RGB").save(out, quality=95)

STYLES = {
    "friends": ("SOZIAL", "Freunde", "Sieh, wer online ist", (125, 211, 252, 255)),
    "modules": ("FEATURES", "32 Module", "HUD, Optik & Komfort", (134, 239, 172, 255)),
    "hud": ("ANPASSUNG", "HUD-Editor", "Alles frei platzieren", (253, 186, 116, 255)),
    "badge": ("COMMUNITY", "Abzeichen", "Erkenne andere Nutzer", (216, 180, 254, 255)),
    "version": ("UPDATE", "1.21.11", "Ab sofort unterstützt.", (253, 224, 71, 255)),
    "keyart": ("MINECRAFT JAVA 1.21.11", "ShadowClient", "Dein Minecraft-Client", (103, 232, 249, 255)),
}

if __name__ == "__main__":
    name, raw, out = sys.argv[1], sys.argv[2], sys.argv[3]
    if name == "custom":   # overlay.py custom raw out "Titel" "Untertitel" r,g,b
        k, t, sub, acc = "", sys.argv[4], sys.argv[5], tuple(int(v) for v in sys.argv[6].split(",")) + (255,)
    else:
        k, t, sub, acc = STYLES[name]
    compose(raw, out, k, t, sub, acc, centered=(name == "keyart"))
    print("composed", out)
