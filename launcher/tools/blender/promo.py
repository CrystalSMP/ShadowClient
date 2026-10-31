"""
ShadowClient Promo-Banner (heller "Studio"-Stil): eigene Spielermodelle mit Capes, Begleiter,
Block-Landschaften, weiches Licht, Tiefenunschärfe. Titel wird danach mit overlay.py gesetzt.
Aufruf: blender -b -P promo.py -- <scene> <out.png> [width height samples]
"""
import bpy, bmesh, math, random, sys, os
IS_MODULE = not bpy.app.binary_path  # läuft als "pip install bpy"-Modul
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
SCENE = argv[0] if argv else "friends"
OUT = argv[1] if len(argv) > 1 else os.path.join(HERE, f"{SCENE}_raw.png")
W = int(argv[2]) if len(argv) > 2 else 1280
H = int(argv[3]) if len(argv) > 3 else 640
SAMPLES = int(argv[4]) if len(argv) > 4 else 32
PX = 1 / 16
rnd = random.Random(int(os.environ.get('SC_SEED', '7')))

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
col = bpy.context.collection

# ------------------------------------------------------------------ Materialien
_img = {}
def image(name):
    if name not in _img: _img[name] = bpy.data.images.load(os.path.join(HERE, name))
    return _img[name]

def tex_mat(name, tex, rough=0.8, alpha=False, emission=0.0, sheen=0.0):
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree; b = nt.nodes["Principled BSDF"]
    t = nt.nodes.new("ShaderNodeTexImage"); t.image = image(tex); t.interpolation = "Closest"
    nt.links.new(t.outputs["Color"], b.inputs["Base Color"])
    b.inputs["Roughness"].default_value = rough
    if sheen: b.inputs["Coat Weight"].default_value = sheen
    if emission:
        nt.links.new(t.outputs["Color"], b.inputs["Emission Color"]); b.inputs["Emission Strength"].default_value = emission
    if alpha:
        nt.links.new(t.outputs["Alpha"], b.inputs["Alpha"])
        for attr, val in (("blend_method", "CLIP"), ("shadow_method", "CLIP")):
            try: setattr(m, attr, val)
            except Exception: pass
    return m

def emit_mat(name, color, strength):
    m = bpy.data.materials.new(name); m.use_nodes = True; nt = m.node_tree; nt.nodes.clear()
    o = nt.nodes.new("ShaderNodeOutputMaterial"); e = nt.nodes.new("ShaderNodeEmission")
    e.inputs["Color"].default_value = (*color, 1); e.inputs["Strength"].default_value = strength
    nt.links.new(e.outputs[0], o.inputs[0]); return m

def color_mat(name, color, rough=0.6, metal=0.0):
    m = bpy.data.materials.new(name); m.use_nodes = True; b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1); b.inputs["Roughness"].default_value = rough; b.inputs["Metallic"].default_value = metal
    return m

# ------------------------------------------------------------------ Minecraft-Quader mit UV
def mc_box(name, size, offset, uv, mat, inflate=0.0, parent=None, location=(0, 0, 0), tex=(64, 64), seg=None):
    w, h, d = size; u, v = uv; tw, th = tex; i = inflate; x0, y0, z0 = offset
    # seg=(a, b): nur die Zeilen a..b der Seitenflächen (für geteilte Glieder mit Ellbogen/Knie)
    sa, sb = (v + d + seg[0], v + d + seg[1]) if seg else (v + d, v + d + h)
    X0, X1 = (x0 - i) * PX, (x0 + w + i) * PX
    Y0, Y1 = (y0 - i) * PX, (y0 + d + i) * PX
    Z0, Z1 = (z0 - i) * PX, (z0 + h + i) * PX
    me = bpy.data.meshes.new(name); bm = bmesh.new()
    U = lambda a, b: (a / tw, 1 - b / th)
    faces = [
        ([(X0, Y0, Z0), (X1, Y0, Z0), (X1, Y0, Z1), (X0, Y0, Z1)], [U(u+d, sb), U(u+d+w, sb), U(u+d+w, sa), U(u+d, sa)]),
        ([(X1, Y1, Z0), (X0, Y1, Z0), (X0, Y1, Z1), (X1, Y1, Z1)], [U(u+2*d+w, sb), U(u+2*d+2*w, sb), U(u+2*d+2*w, sa), U(u+2*d+w, sa)]),
        ([(X0, Y1, Z0), (X0, Y0, Z0), (X0, Y0, Z1), (X0, Y1, Z1)], [U(u, sb), U(u+d, sb), U(u+d, sa), U(u, sa)]),
        ([(X1, Y0, Z0), (X1, Y1, Z0), (X1, Y1, Z1), (X1, Y0, Z1)], [U(u+d+w, sb), U(u+2*d+w, sb), U(u+2*d+w, sa), U(u+d+w, sa)]),
        ([(X0, Y0, Z1), (X1, Y0, Z1), (X1, Y1, Z1), (X0, Y1, Z1)], [U(u+d, v+d), U(u+d+w, v+d), U(u+d+w, v), U(u+d, v)]),
        ([(X0, Y1, Z0), (X1, Y1, Z0), (X1, Y0, Z0), (X0, Y0, Z0)], [U(u+d+w, v+d), U(u+2*d+w, v+d), U(u+2*d+w, v), U(u+d+w, v)]),
    ]
    uvl = bm.loops.layers.uv.new()
    for corners, uvs in faces:
        f = bm.faces.new([bm.verts.new(c) for c in corners])
        for loop, c in zip(f.loops, uvs): loop[uvl].uv = c
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(name, me); ob.data.materials.append(mat); col.objects.link(ob)
    if parent: ob.parent = parent
    ob.location = location
    # leicht abgerundete Kanten fangen Glanzlichter (wie bei Profi-Renders)
    if inflate == 0:
        bev = ob.modifiers.new("bevel", "BEVEL"); bev.width = 0.004; bev.segments = 2; bev.limit_method = "ANGLE"
    return ob

def empty(name, parent=None, loc=(0, 0, 0)):
    e = bpy.data.objects.new(name, None); col.objects.link(e); e.parent = parent; e.location = loc; return e

POSES = {  # Winkel in Grad; relb/lelb = Ellbogen (negativ = nach vorne beugen), rknee/lknee = Knie (positiv = beugen)
    "idle":  {"rarm": (6, 0, 6), "relb": (-12, 0, 0), "larm": (-6, 0, -6), "lelb": (-12, 0, 0), "head": (-4, 0, 8), "rknee": (6, 0, 0)},
    "walk":  {"rarm": (32, 0, 8), "relb": (-28, 0, 0), "larm": (-38, 0, -8), "lelb": (-45, 0, 0),
              "rleg": (-32, 0, 3), "rknee": (38, 0, 0), "lleg": (22, 0, -3), "lknee": (12, 0, 0), "head": (4, 0, 22)},
    "run":   {"rarm": (62, 0, 10), "relb": (-75, 0, 0), "larm": (-70, 0, -10), "lelb": (-85, 0, 0),
              "rleg": (-70, 0, 0), "rknee": (80, 0, 0), "lleg": (38, 0, 0), "lknee": (65, 0, 0), "head": (-14, 0, 6)},
    "wave":  {"rarm": (-150, 0, -30), "relb": (-35, 0, 0), "larm": (12, 0, -10), "lelb": (-25, 0, 0),
              "rleg": (-6, 0, 2), "rknee": (10, 0, 0), "lleg": (6, 0, -6), "head": (-6, 0, 14)},
    "point": {"rarm": (-88, 0, 14), "relb": (-6, 0, 0), "larm": (25, 0, -30), "lelb": (-70, 0, 0),
              "rleg": (-10, 0, 4), "rknee": (14, 0, 0), "lleg": (8, 0, -6), "head": (-4, 0, 26)},
    "cheer": {"rarm": (-165, 0, 30), "relb": (-35, 0, 0), "larm": (-165, 0, -30), "lelb": (-35, 0, 0),
              "rleg": (-8, 0, 6), "rknee": (12, 0, 0), "lleg": (8, 0, -6), "head": (-16, 0, 0)},
    "jump":  {"rarm": (-150, 0, 35), "relb": (-40, 0, 0), "larm": (-30, 0, -40), "lelb": (-70, 0, 0),
              "rleg": (-75, 0, 0), "rknee": (100, 0, 0), "lleg": (12, 0, 0), "lknee": (55, 0, 0), "head": (-14, 0, 0)},
    "look":  {"rarm": (18, 0, 14), "relb": (-20, 0, 0), "larm": (-10, 0, -10), "lelb": (-30, 0, 0),
              "rleg": (-10, 0, 4), "rknee": (16, 0, 0), "lleg": (8, 0, -4), "head": (8, 0, -32)},
    "thumbs": {"rarm": (-55, 0, 25), "relb": (-70, 0, 0), "larm": (8, 0, -8), "lelb": (-15, 0, 0),
               "rleg": (-6, 0, 4), "rknee": (8, 0, 0), "lleg": (10, 0, -5), "head": (-8, 0, 18)},
}

def player(skin, loc, rz=0, pose="idle", scale=1.0, cape=True, tilt=(0, 0)):
    mat = tex_mat(f"skin_{skin}", f"skins/{skin}.png", rough=0.7, alpha=True, sheen=0.15)
    root = empty(f"pl_{skin}", loc=loc); root.rotation_euler = (math.radians(tilt[0]), math.radians(tilt[1]), math.radians(rz)); root.scale = (scale,) * 3
    P = POSES[pose]
    def part(n, pivot, size, off, uv, layer=None, inf=0.5):
        p = empty(f"{skin}_{n}", root, tuple(c * PX for c in pivot))
        mc_box(f"{skin}_{n}_m", size, off, uv, mat, parent=p)
        if layer: mc_box(f"{skin}_{n}_l", size, off, layer, mat, inflate=inf, parent=p)
        if n in P: p.rotation_euler = tuple(math.radians(a) for a in P[n])
        return p
    def limb(n, joint, pivot, top, uv, layer):
        """Glied in zwei Hälften: oben am Gelenk (Schulter/Hüfte), unten am Ellbogen/Knie."""
        up = empty(f"{skin}_{n}", root, tuple(c * PX for c in pivot))
        mc_box(f"{skin}_{n}_a", (4, 6.4, 4), (-2, -2, top - 6.2), uv, mat, parent=up, seg=(0, 6))
        mc_box(f"{skin}_{n}_al", (4, 6.4, 4), (-2, -2, top - 6.2), layer, mat, inflate=0.25, parent=up, seg=(0, 6))
        lo = empty(f"{skin}_{joint}", up, (0, 0, (top - 6) * PX))
        mc_box(f"{skin}_{n}_b", (4, 6, 4), (-2, -2, -6), uv, mat, parent=lo, seg=(6, 12))
        mc_box(f"{skin}_{n}_bl", (4, 6, 4), (-2, -2, -6), layer, mat, inflate=0.25, parent=lo, seg=(6, 12))
        if n in P: up.rotation_euler = tuple(math.radians(a) for a in P[n])
        if joint in P: lo.rotation_euler = tuple(math.radians(a) for a in P[joint])
    limb("rleg", "rknee", (-2, 0, 12), 0, (0, 16), (0, 32))
    limb("lleg", "lknee", (2, 0, 12), 0, (16, 48), (0, 48))
    body = part("body", (0, 0, 12), (8, 12, 4), (-4, -2, 0), (16, 16), layer=(16, 32), inf=0.25)
    limb("rarm", "relb", (-6, 0, 22), 2, (40, 16), (40, 32))
    limb("larm", "lelb", (6, 0, 22), 2, (32, 48), (48, 48))
    part("head", (0, 0, 24), (8, 8, 8), (-4, -4, 0), (0, 0), layer=(32, 0), inf=0.5)
    if cape:
        cm = tex_mat(f"cape_{skin}", "tex_cape.png", rough=0.6)
        cp = empty(f"{skin}_cape", body, (0, 2.1 * PX, 12 * PX))
        mc_box(f"{skin}_cape_m", (10, 16, 1), (-5, 0, -16), (0, 0), cm, parent=cp, tex=(64, 32))
        cp.rotation_euler = (math.radians(10 if pose not in ("run", "jump") else 50), 0, 0)
    return root

def buddy(loc, scale=1.0, rz=0):
    m = tex_mat("buddy", "tex_buddy.png", rough=0.4, sheen=0.4)
    b = mc_box("buddy", (6, 6, 6), (-3, -3, -3), (0, 0), m, location=loc, tex=(32, 32))
    b.scale = (scale * 0.85,) * 3; b.rotation_euler = (math.radians(8), 0, math.radians(rz))
    glow = emit_mat("buddyglow", (0.4, 0.9, 1.0), 6)
    for side in (-1, 1):  # kleine Flügel
        wgt = mc_box(f"wing{side}", (1, 3, 4), (side * 3.5 - 0.5, -1, -1), (0, 0), glow, parent=b)
    return b

# ------------------------------------------------------------------ Blöcke
def grass_block(loc, top="tex_grass_top.png", side="tex_grass_side.png", bottom="tex_dirt.png", size=1.0):
    key = (top, side, bottom)
    if key not in grass_block.mats:
        grass_block.mats[key] = [tex_mat("t", top, 0.9), tex_mat("s", side, 0.9), tex_mat("b", bottom, 0.9)]
    me = bpy.data.meshes.new("gb"); bm = bmesh.new(); bmesh.ops.create_cube(bm, size=size)
    uvl = bm.loops.layers.uv.new()
    for f in bm.faces:
        n = f.normal
        f.material_index = 0 if n.z > 0.5 else (2 if n.z < -0.5 else 1)
        # Seitenflächen so ausrichten, dass Gras oben ist
        cs = [l.vert.co for l in f.loops]
        for loop in f.loops:
            c = loop.vert.co
            if abs(n.z) > 0.5: loop[uvl].uv = (c.x / size + 0.5, c.y / size + 0.5)
            else: loop[uvl].uv = ((c.x if abs(n.y) > 0.5 else c.y) / size + 0.5, c.z / size + 0.5)
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new("gb", me)
    for m in grass_block.mats[key]: ob.data.materials.append(m)
    ob.location = loc; col.objects.link(ob); return ob
grass_block.mats = {}

def cube(loc, mat, size=1.0, scale=(1, 1, 1), rot=(0, 0, 0)):
    me = bpy.data.meshes.new("c"); bm = bmesh.new(); bmesh.ops.create_cube(bm, size=size)
    uvl = bm.loops.layers.uv.new()
    for f in bm.faces:
        for loop, c in zip(f.loops, [(0, 0), (1, 0), (1, 1), (0, 1)]): loop[uvl].uv = c
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new("c", me); ob.data.materials.append(mat); ob.location = loc; ob.scale = scale
    ob.rotation_euler = tuple(math.radians(a) for a in rot); col.objects.link(ob); return ob

def tree(x, y, z, h=4):
    log = tex_mat("log", "tex_log.png", 0.9); leaves = tex_mat("leaves", "tex_leaves_cut.png", 0.85, alpha=True)
    for k in range(h): cube((x, y, z + k), log)
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            for dz in range(0, 3):
                if abs(dx) + abs(dy) + dz < 4 and rnd.random() < 0.9: cube((x + dx, y + dy, z + h - 1 + dz), leaves)

def cloud(x, y, z, w=6):
    m = color_mat("cloud", (1, 1, 1), 0.9)
    cube((x, y, z), m, scale=(w, w * 0.6, 0.8)); cube((x + w * 0.15, y, z + 0.6), m, scale=(w * 0.6, w * 0.4, 0.6))

_cross = {}
def tallgrass(x, y, z, size=0.7, tex="tex_tallgrass.png"):
    if tex not in _cross: _cross[tex] = tex_mat(tex, tex, 0.9, alpha=True)
    mat = _cross[tex]
    for rot in (45, -45):
        me = bpy.data.meshes.new("tg"); bm = bmesh.new()
        v = [bm.verts.new(c) for c in ((-size / 2, 0, 0), (size / 2, 0, 0), (size / 2, 0, size), (-size / 2, 0, size))]
        f = bm.faces.new(v); uvl = bm.loops.layers.uv.new()
        for loop, c in zip(f.loops, [(0, 0), (1, 0), (1, 1), (0, 1)]): loop[uvl].uv = c
        bm.to_mesh(me); bm.free()
        ob = bpy.data.objects.new("tg", me); ob.data.materials.append(mat)
        ob.location = (x, y, z); ob.rotation_euler = (0, 0, math.radians(rot + rnd.uniform(-10, 10))); col.objects.link(ob)

def house(x, y, w=5, d=4, h=3, rot=0):
    planks = tex_mat("planks", "tex_planks.png", 0.85); log = tex_mat("log", "tex_log.png", 0.9)
    stone = tex_mat("stone", "tex_stone.png", 0.9); roof = color_mat("roof", (0.42, 0.14, 0.09), 0.7)
    lamp = emit_mat("lamp", (1.0, 0.72, 0.35), 5); glass = emit_mat("win", (1.0, 0.8, 0.5), 1.2)
    gz = 0.5
    for i in range(w):
        for j in range(d):
            for k in range(h):
                edge_x, edge_y = i in (0, w - 1), j in (0, d - 1)
                if not (edge_x or edge_y): continue
                m = log if (edge_x and edge_y) else (stone if k == 0 else planks)
                if k == 1 and not (edge_x and edge_y) and (i + j) % 2 == 1 and j == 0: m = glass
                cube((x + i, y + j, gz + k), m)
    for r in range(d // 2 + 1):   # Satteldach in Stufen
        for i in range(-1, w + 1):
            cube((x + i, y + r - 0.0, gz + h + r), roof); cube((x + i, y + d - 1 - r, gz + h + r), roof)
    cube((x + w // 2, y - 0.62, gz + 1.7), lamp, size=0.28)

def village(seed_rows=((-9, 13), (-2, 15), (5, 13), (12, 16), (-15, 18), (1, 22), (9, 23))):
    for (x, y) in seed_rows: house(x * 1.3, y + 10, rnd.choice([4, 5, 6]), rnd.choice([4, 5]), rnd.choice([3, 3, 4]))
    path = tex_mat("path", "tex_stone.png", 0.95)
    for x in range(-24, 25):
        for y in (19, 20): cube((x, y, 0.01), path, scale=(1, 1, 0.02))
    lamp = emit_mat("lamp", (1.0, 0.72, 0.35), 5); log = tex_mat("log", "tex_log.png", 0.9)
    for x in (-14, -5, 4, 13):   # Laternenpfähle
        for k in range(3): cube((x, 18.3, 0.5 + k * 0.6), log, scale=(0.25, 0.25, 0.6))
        cube((x, 18.3, 2.35), lamp, size=0.35)

FLOWERS = ["tex_flower_red.png", "tex_flower_yellow.png", "tex_flower_blue.png", "tex_flower_white.png", "tex_flower_tulip.png"]
def flower(x, y, z, colr=None):
    tallgrass(x, y, z, rnd.uniform(0.4, 0.55), rnd.choice(FLOWERS))

def meadow(n_grass=260, n_flower=70, keep=((-1.5, 3.0), 2.2)):
    (kx0, kx1), ky = keep
    for i in range(n_grass + n_flower):
        gx, gy = rnd.uniform(-9, 10), rnd.uniform(-1.6, 9)
        if gy < ky and kx0 < gx < kx1: continue
        if i < n_grass: tallgrass(gx, gy, 0.0, rnd.uniform(0.3, 0.55))
        else: flower(gx, gy, 0.0)

def terrain(cx=0, cy=0, rx=10, ry=14, base=-0.5, kind="grass", hills=True, island=False):
    top = {"grass": "tex_grass_top.png", "sand": "tex_sand.png", "snow": "tex_snow.png"}[kind]
    side = {"grass": "tex_grass_side.png", "sand": "tex_sand.png", "snow": "tex_snow.png"}[kind]
    for x in range(cx - rx, cx + rx + 1):
        for y in range(cy - 3, cy + ry):
            if island and (x - cx) ** 2 / rx ** 2 + (y - cy - 2) ** 2 / (ry * 0.5) ** 2 > 1: continue
            hgt = 0
            if hills and y > 9: hgt = int(max(0, math.sin(x * 0.35) + math.sin(y * 0.25 + x * 0.1) - 0.6) * 1.2)
            for k in range(hgt + 1):
                grass_block((x, y, base + k), top if k == hgt else "tex_dirt.png", side if k == hgt else "tex_dirt.png")
            if island:
                for k in range(1, 3 + int(rnd.random() * 3)):
                    if (x - cx) ** 2 / rx ** 2 + (y - cy - 2) ** 2 / (ry * 0.5) ** 2 < 1 - k * 0.22:
                        grass_block((x, y, base - k), "tex_dirt.png", "tex_dirt.png")

# ------------------------------------------------------------------ Welt (Verlaufshimmel) & Licht
def sky(horizon, zenith, strength=1.0, stars=False):
    w = bpy.data.worlds.new("w"); scene.world = w; w.use_nodes = True; nt = w.node_tree
    bg = nt.nodes["Background"]; tc = nt.nodes.new("ShaderNodeTexCoord"); sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].position = 0.0; ramp.color_ramp.elements[0].color = (*horizon, 1)
    ramp.color_ramp.elements[1].position = 0.55; ramp.color_ramp.elements[1].color = (*zenith, 1)
    nt.links.new(tc.outputs["Generated"], sep.inputs[0]); nt.links.new(sep.outputs["Z"], ramp.inputs["Fac"])
    nt.links.new(ramp.outputs["Color"], bg.inputs["Color"]); bg.inputs["Strength"].default_value = strength * (0.35 if os.environ.get("SC_ENGINE", "CYCLES") == "CYCLES" else 0.55)

def light(kind, loc, energy, color, rot=(0, 0, 0), size=1.0, angle=None):
    ld = bpy.data.lights.new(kind, kind); ld.energy = energy; ld.color = color
    if kind == "AREA": ld.size = size
    if kind == "SUN" and angle: ld.angle = math.radians(angle)
    ob = bpy.data.objects.new(kind, ld); ob.location = loc; ob.rotation_euler = tuple(math.radians(a) for a in rot)
    col.objects.link(ob); return ob

def studio(key=(1.0, 0.93, 0.82), rim=(0.6, 0.85, 1.0), sun=6.5, sun_rot=(50, 0, -40)):
    light("SUN", (0, 0, 10), sun, key, rot=sun_rot, angle=3)
    light("AREA", (-2.6, -3.2, 2.6), 380, key, rot=(62, 0, -38), size=2.5)      # Key auf Gesichter
    light("AREA", (2.8, -2.4, 1.6), 160, (0.8, 0.9, 1.0), rot=(72, 0, 48), size=3)  # Fill
    light("AREA", (0.5, 2.5, 3.2), 900, rim, rot=(-130, 0, 175), size=2.5)      # Rim/Kante

def cine(elev=9, az=-35, strength=0.35, haze=0.004, haze_col=(1.0, 0.82, 0.62), sun=14.0, sun_col=(1.0, 0.7, 0.42),
         fill=(0.55, 0.7, 1.0), key=150, night=False):
    """Golden-Hour: physikalischer Himmel, tiefe Gegenlicht-Sonne, Dunst, weiches Key-Light auf Gesichter."""
    w = bpy.data.worlds.new("w"); scene.world = w; w.use_nodes = True; nt = w.node_tree
    bg = nt.nodes["Background"]; sk = nt.nodes.new("ShaderNodeTexSky")
    try: sk.sky_type = "MULTIPLE_SCATTERING"
    except Exception: sk.sky_type = "NISHITA"
    sk.sun_elevation = math.radians(elev); sk.sun_rotation = math.radians(az + 90); sk.sun_disc = False
    try: sk.air_density = 1.0; sk.aerosol_density = 1.2
    except Exception:
        try: sk.dust_density = 3.0
        except Exception: pass
    nt.links.new(sk.outputs["Color"], bg.inputs["Color"]); bg.inputs["Strength"].default_value = strength
    if haze and os.environ.get("SC_HAZE"):
        vol = nt.nodes.new("ShaderNodeVolumePrincipled"); vol.inputs["Density"].default_value = haze
        vol.inputs["Color"].default_value = (*haze_col, 1); vol.inputs["Anisotropy"].default_value = 0.6
        nt.links.new(vol.outputs[0], nt.nodes["World Output"].inputs["Volume"])
    # Sonne kommt aus derselben Richtung wie im Himmel (von hinten/seitlich = Gegenlicht-Kante)
    light("SUN", (0, 0, 10), sun, sun_col, rot=(90 - elev, 0, az), angle=1.5)
    light("AREA", (-2.4, -3.6, 2.4), key, (1.0, 0.86, 0.72), rot=(64, 0, -34), size=3.5)   # weiches Key
    light("AREA", (3.2, -2.6, 1.4), key * 0.35, fill, rot=(74, 0, 52), size=4)               # kühles Fill

# ------------------------------------------------------------------ Kamera
def camera(loc, target, lens=55, fstop=2.0, focus=None):
    cd = bpy.data.cameras.new("cam"); cam = bpy.data.objects.new("cam", cd); col.objects.link(cam); scene.camera = cam
    t = Vector(target)
    if W > H: t.x -= 1.1  # Breitbild: Figuren nach rechts, links Platz für den Titel
    cam.location = loc; cam.rotation_euler = (t - cam.location).to_track_quat("-Z", "Y").to_euler()
    cd.lens = lens; cd.dof.use_dof = True; cd.dof.aperture_fstop = fstop
    cd.dof.focus_distance = focus or (t - cam.location).length
    return cam

# ------------------------------------------------------------------ Szenen (Cinematic)
def hillsY(x, y): return 0.5
def forest(pos):
    for (x, y) in pos: tree(x * 1.3, y + 22, 0.5, rnd.choice([5, 6, 6, 7]))
BACK = [(-12, 16), (-4, 18), (4, 17), (12, 19), (-18, 14), (18, 15), (0, 22)]
def ground(keep): terrain(rx=22, ry=40, hills=False); meadow(n_grass=200, n_flower=60, keep=keep)

def scene_friends():
    ground(((-1.5, 3.5), 1.6)); village(); forest(BACK)
    player("shadow", (0.4, 0.3, 0), -28, "walk", 1.0)
    player("ember", (1.85, 0.9, 0), -30, "wave", 1.0)
    cine(elev=35, az=-40); camera((0.9, -7.6, 0.75), (1.2, 0.5, 1.35), lens=50, fstop=0.6)

def scene_modules():
    ground(((-0.5, 3.0), 1.6)); village(((-8, 12), (-1, 14), (6, 13), (13, 15), (2, 20))); forest(BACK)
    player("frost", (1.1, 0.4, 0), -40, "run", 1.0, tilt=(10, 0))
    cine(elev=35, az=-60); camera((0.9, -7.6, 0.75), (1.3, 0.4, 1.35), lens=50, fstop=0.6)

def scene_hud():
    ground(((-0.5, 2.6), 1.6)); village(((-10, 13), (-3, 14), (4, 12), (11, 14))); forest(BACK)
    player("ember", (1.2, 0.4, 0), -35, "point", 1.0)
    cine(elev=12, az=-70, sun_col=(1.0, 0.62, 0.38)); camera((0.8, -7.6, 0.75), (1.2, 0.4, 1.35), lens=50, fstop=0.6)

def scene_badge():
    ground(((-0.5, 3.2), 1.6)); village(((-9, 12), (-2, 15), (5, 13), (12, 14))); forest(BACK)
    player("nova", (0.45, 0.3, 0), -20, "wave", 1.0)
    player("shadow", (1.85, 0.9, 0), -45, "thumbs", 1.0)
    badge = emit_mat("badge", (0.4, 0.95, 1.0), 10)
    for (x, y) in [(0.45, 0.3), (1.85, 0.9)]:
        for r in (45, 0): cube((x, y, 2.35), badge, scale=(0.1, 0.02, 0.1), rot=(0, r, 0))
    cine(elev=30, az=-30); camera((0.9, -7.6, 0.75), (1.2, 0.6, 1.4), lens=50, fstop=0.6)

def scene_version():
    ground(((-0.5, 3.2), 1.6)); village(); forest(BACK)
    player("shadow", (0.5, 0.3, 0.35), -25, "jump", 1.0, tilt=(0, 6))
    player("frost", (1.9, 0.9, 0), -45, "cheer", 1.0)
    conf = [color_mat(f"conf{i}", c, 0.4) for i, c in enumerate([(1, 0.3, 0.3), (1, 0.85, 0.1), (0.3, 0.9, 0.4), (0.3, 0.6, 1), (0.9, 0.4, 1)])]
    for _ in range(60):
        cube((rnd.uniform(-0.5, 3.5), rnd.uniform(-0.5, 2.5), rnd.uniform(0.8, 3.0)), rnd.choice(conf), size=0.05,
             scale=(1, 0.3, 1.6), rot=(rnd.uniform(0, 90), rnd.uniform(0, 90), rnd.uniform(0, 90)))
    cine(elev=35, az=-45); camera((0.9, -7.6, 0.75), (1.3, 0.5, 1.4), lens=50, fstop=0.6)

def scene_keyart():
    ground(((-2.8, 2.8), 2.4)); village(); forest(BACK)
    player("ember", (-1.5, 0.8, 0), 25, "wave", 1.0)
    player("shadow", (-0.4, 0.0, 0), -10, "walk", 1.05)
    player("frost", (0.7, 0.4, 0), -25, "thumbs", 1.0)
    player("nova", (1.75, 1.0, 0.0), -35, "look", 1.0)
    cine(elev=35, az=-25); camera((0.1, -6.2, 0.75), (0.1, 0.5, 1.25), lens=45, fstop=0.8)


# ------------------------------------------------------------------ Server-Banner (eigene Motive je Genre)
def _duo(poses=("walk", "wave")):
    a, b = rnd.sample(["shadow", "ember", "frost", "nova"], 2)
    player(a, (0.45, 0.3, 0), -26, poses[0], 1.0); player(b, (1.85, 0.9, 0), -40, poses[1], 1.0)

def scene_srv_city():
    ground(((-1.5, 3.5), 1.6)); village(((-10, 12), (-4, 14), (2, 12), (8, 15), (14, 13), (-14, 18), (5, 21), (-2, 24)))
    forest(BACK); _duo(("walk", "thumbs"))
    gold = emit_mat("coin", (1.0, 0.78, 0.2), 4)
    for _ in range(14): cube((rnd.uniform(-0.5, 3.2), rnd.uniform(-0.3, 1.8), rnd.uniform(1.2, 2.9)), gold, size=0.09, scale=(1, 0.25, 1), rot=(0, 0, rnd.uniform(0, 90)))
    cine(elev=14, az=-45, sun_col=(1.0, 0.68, 0.42)); camera((0.9, -7.6, 0.75), (1.2, 0.5, 1.35), lens=50, fstop=0.6)

def scene_srv_sky():
    terrain(cx=1, cy=-1, rx=4, ry=7, hills=False, island=True)
    for _ in range(40): tallgrass(rnd.uniform(-2, 4), rnd.uniform(0.2, 3), 0, rnd.uniform(0.3, 0.5))
    for (x, y, z) in [(-9, 16, 2), (10, 20, 5), (15, 12, -1), (-16, 24, 6), (3, 30, 8)]:
        terrain(cx=x, cy=y, rx=3, ry=5, base=z, hills=False, island=True)
    for (x, y, z) in [(-6, 9, 1), (7, 11, 3), (-3, 26, 4), (12, 28, 9)]:
        terrain(cx=x, cy=y, rx=2, ry=3, base=z, hills=False, island=True)
    _duo(("run", "point"))
    cine(elev=30, az=-40); camera((0.9, -7.6, 0.75), (1.2, 0.5, 1.2), lens=50, fstop=0.7)

def scene_srv_arena():
    stone = tex_mat("stone", "tex_stone.png", 0.9); planks = tex_mat("planks", "tex_planks.png", 0.85)
    for x in range(-14, 16):
        for y in range(-4, 30): grass_block((x, y, -0.5), "tex_stone.png", "tex_stone.png", "tex_stone.png")
    for x in range(-14, 16, 1):            # Tribünen hinten
        for k in range(6): cube((x, 24 + k, 0.5 + k), stone if k % 2 else planks)
    lamp = emit_mat("lamp", (1.0, 0.72, 0.35), 8)
    for x in (-10, -3, 4, 11): cube((x, 23, 3.5), lamp, size=0.5)
    a, b = rnd.sample(["shadow", "ember", "frost", "nova"], 2)
    player(a, (0.4, 0.5, 0), -75, "point", 1.0); player(b, (2.1, 0.7, 0), 60, "point", 1.0)
    sword = color_mat("blade", (0.75, 0.95, 1.0), 0.15, 1.0)
    cine(elev=8, az=-70, sun_col=(1.0, 0.55, 0.32)); camera((0.9, -7.6, 0.75), (1.2, 0.5, 1.3), lens=50, fstop=0.6)

def scene_srv_forest():
    ground(((-1.5, 3.5), 1.6))
    forest([(x, y) for x in range(-16, 18, 4) for y in (-8, -4, 0)])
    house(9, 18, 5, 4, 3)
    _duo(("look", "point"))
    cine(elev=20, az=-30); camera((0.9, -7.6, 0.75), (1.2, 0.5, 1.35), lens=50, fstop=0.6)

def scene_srv_ruin():
    terrain(rx=22, ry=40, hills=False)
    deep = tex_mat("deep", "tex_deepslate.png", 0.9); stone = tex_mat("stone", "tex_stone.png", 0.9)
    for _ in range(90):
        x, y = rnd.uniform(-16, 16), rnd.uniform(10, 32); h = rnd.randint(1, 6)
        for k in range(h): cube((round(x), round(y), 0.5 + k), rnd.choice([deep, stone]))
    fire = emit_mat("fire", (1.0, 0.45, 0.12), 12)
    for _ in range(10): cube((rnd.uniform(-12, 12), rnd.uniform(12, 30), 0.4), fire, size=0.6)
    _duo(("look", "walk"))
    cine(elev=3, az=-30, strength=0.25, sun=6.0, sun_col=(1.0, 0.45, 0.3), key=170)
    camera((0.9, -7.6, 0.75), (1.2, 0.5, 1.35), lens=50, fstop=0.6)

{"friends": scene_friends, "modules": scene_modules, "hud": scene_hud, "badge": scene_badge,
 "version": scene_version, "keyart": scene_keyart, "srv_city": scene_srv_city, "srv_sky": scene_srv_sky,
 "srv_arena": scene_srv_arena, "srv_forest": scene_srv_forest, "srv_ruin": scene_srv_ruin}[SCENE]()

# ------------------------------------------------------------------ Rendern
r = scene.render; r.resolution_x = W; r.resolution_y = H; r.resolution_percentage = 100
r.filepath = OUT; r.image_settings.file_format = "PNG"
looks = [i.identifier for i in bpy.types.ColorManagedViewSettings.bl_rna.properties["look"].enum_items]
scene.view_settings.view_transform = "Standard" if False and "AgX" in [i.identifier for i in bpy.types.ColorManagedViewSettings.bl_rna.properties["view_transform"].enum_items] else "Filmic"
scene.view_settings.look = next((l for l in looks if "Punchy" in l), next((l for l in looks if "High Contrast" in l), "None"))
scene.view_settings.exposure = float(os.environ.get("SC_EXPOSURE", "-0.2"))
ENGINE = os.environ.get("SC_ENGINE", "CYCLES")
if ENGINE == "CYCLES":
    r.engine = "CYCLES"; c = scene.cycles
    c.device = "CPU"; c.samples = SAMPLES; c.use_adaptive_sampling = True; c.adaptive_threshold = 0.02
    c.use_denoising = True
    try:
        c.denoiser = "OPENIMAGEDENOISE"; c.denoising_input_passes = "RGB_ALBEDO_NORMAL"
    except Exception:
        c.use_denoising = False  # Blender ohne OIDN (z. B. Ubuntu-Paket): mehr Samples nötig
    c.volume_bounces = 0; c.max_bounces = 5; c.diffuse_bounces = 3; c.glossy_bounces = 2; c.transparent_max_bounces = 48
    c.blur_glossy = 1.0; c.caustics_reflective = False; c.caustics_refractive = False
    r.film_transparent = False; r.use_persistent_data = True
    scene.render.threads_mode = "AUTO"
else:
    r.engine = "BLENDER_EEVEE"; e = scene.eevee
    e.taa_render_samples = SAMPLES; e.use_gtao = True; e.gtao_distance = 0.6; e.use_soft_shadows = True
    e.use_bloom = True; e.bloom_intensity = 0.03; e.bloom_threshold = 1.4; e.use_ssr = True
    e.shadow_cube_size = "2048"; e.shadow_cascade_size = "4096"
bpy.ops.render.render(write_still=True)
print("RENDERED", OUT)
