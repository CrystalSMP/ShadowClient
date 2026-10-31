"""
ShadowClient Key-Art in Blender (eigene Figuren, eigene Szene).
Aufruf:  blender -b -P keyart.py -- <scene> <out.png> [width height samples]
Szenen: keyart, friends, modules, hud, badge, version
"""
import bpy, bmesh, math, random, sys, os
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
SCENE = argv[0] if argv else "keyart"
OUT = argv[1] if len(argv) > 1 else os.path.join(HERE, f"{SCENE}.png")
W = int(argv[2]) if len(argv) > 2 else 1080
H = int(argv[3]) if len(argv) > 3 else 1080
SAMPLES = int(argv[4]) if len(argv) > 4 else 48
PX = 1 / 16  # ein Skin-Pixel = 1/16 Block
rnd = random.Random(42)

# ---------------------------------------------------------------- Grundaufbau
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene

def image(name):
    path = os.path.join(HERE, name)
    img = bpy.data.images.get(os.path.basename(path)) or bpy.data.images.load(path)
    return img

def pixel_material(name, tex, emission=0.0, emission_color=None, alpha=False, rough=0.75, metal=0.0, tint=None):
    mat = bpy.data.materials.new(name); mat.use_nodes = True
    nt = mat.node_tree; bsdf = nt.nodes["Principled BSDF"]
    t = nt.nodes.new("ShaderNodeTexImage"); t.image = image(tex); t.interpolation = "Closest"
    color_out = t.outputs["Color"]
    if tint:  # Textur einfärben (z. B. Kristalle passend zur Szene)
        mix = nt.nodes.new("ShaderNodeMixRGB"); mix.blend_type = "MULTIPLY"; mix.inputs["Fac"].default_value = 1.0
        bw = nt.nodes.new("ShaderNodeRGBToBW"); nt.links.new(t.outputs["Color"], bw.inputs[0])
        nt.links.new(bw.outputs[0], mix.inputs[1]); mix.inputs[2].default_value = (*tint, 1)
        color_out = mix.outputs[0]
    nt.links.new(color_out, bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metal
    if emission > 0:
        nt.links.new(color_out, bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = emission
    if alpha:
        nt.links.new(t.outputs["Alpha"], bsdf.inputs["Alpha"])
        mat.blend_method = "CLIP"; mat.shadow_method = "CLIP"
    return mat

def emission_material(name, color, strength):
    mat = bpy.data.materials.new(name); mat.use_nodes = True
    nt = mat.node_tree; nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial"); em = nt.nodes.new("ShaderNodeEmission")
    em.inputs["Color"].default_value = (*color, 1); em.inputs["Strength"].default_value = strength
    nt.links.new(em.outputs[0], out.inputs[0])
    return mat

# ---------------------------------------------------------------- Minecraft-Quader mit Skin-UV
def mc_box(name, size, offset, uv, mat, inflate=0.0, parent=None, location=(0, 0, 0)):
    """size=(w,h,d) in Skin-Pixeln, offset=Lage relativ zum Pivot (x,y,z in Pixeln), uv=(u,v)."""
    w, h, d = size; u, v = uv
    x0, y0, z0 = offset
    i = inflate
    X0, X1 = (x0 - i) * PX, (x0 + w + i) * PX
    Y0, Y1 = (y0 - i) * PX, (y0 + d + i) * PX
    Z0, Z1 = (z0 - i) * PX, (z0 + h + i) * PX
    me = bpy.data.meshes.new(name); bm = bmesh.new()
    V = lambda x, y, z: bm.verts.new((x, y, z))
    def U(a, b): return (a / 64, 1 - b / 64)
    faces = [
        # (Ecken gegen den Uhrzeigersinn von außen, UV-Ecken passend)
        ("front", [(X0, Y0, Z0), (X1, Y0, Z0), (X1, Y0, Z1), (X0, Y0, Z1)], [U(u+d, v+d+h), U(u+d+w, v+d+h), U(u+d+w, v+d), U(u+d, v+d)]),
        ("back",  [(X1, Y1, Z0), (X0, Y1, Z0), (X0, Y1, Z1), (X1, Y1, Z1)], [U(u+2*d+w, v+d+h), U(u+2*d+2*w, v+d+h), U(u+2*d+2*w, v+d), U(u+2*d+w, v+d)]),
        ("right", [(X0, Y1, Z0), (X0, Y0, Z0), (X0, Y0, Z1), (X0, Y1, Z1)], [U(u, v+d+h), U(u+d, v+d+h), U(u+d, v+d), U(u, v+d)]),
        ("left",  [(X1, Y0, Z0), (X1, Y1, Z0), (X1, Y1, Z1), (X1, Y0, Z1)], [U(u+d+w, v+d+h), U(u+2*d+w, v+d+h), U(u+2*d+w, v+d), U(u+d+w, v+d)]),
        ("top",   [(X0, Y0, Z1), (X1, Y0, Z1), (X1, Y1, Z1), (X0, Y1, Z1)], [U(u+d, v+d), U(u+d+w, v+d), U(u+d+w, v), U(u+d, v)]),
        ("bottom",[(X0, Y1, Z0), (X1, Y1, Z0), (X1, Y0, Z0), (X0, Y0, Z0)], [U(u+d+w, v+d), U(u+2*d+w, v+d), U(u+2*d+w, v), U(u+d+w, v)]),
    ]
    uvl = bm.loops.layers.uv.new()
    for _, corners, uvs in faces:
        f = bm.faces.new([V(*c) for c in corners])
        for loop, uvc in zip(f.loops, uvs): loop[uvl].uv = uvc
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(name, me); ob.data.materials.append(mat)
    bpy.context.collection.objects.link(ob)
    if parent: ob.parent = parent
    ob.location = location
    return ob

def player(skin, location, rot_z=0.0, pose=None, scale=1.0, held=None):
    """Baut ein Spielermodell. pose: Dict Körperteil → (rx, ry, rz) in Grad."""
    mat = pixel_material(f"skin_{skin}", f"skins/{skin}.png", alpha=True, rough=0.85)
    root = bpy.data.objects.new(f"player_{skin}", None); bpy.context.collection.objects.link(root)
    root.location = location; root.rotation_euler = (0, 0, math.radians(rot_z)); root.scale = (scale,) * 3
    def part(name, pivot, size, offset, uv, overlay_uv=None):
        piv = bpy.data.objects.new(f"{skin}_{name}", None); bpy.context.collection.objects.link(piv)
        piv.parent = root; piv.location = tuple(p * PX for p in pivot)
        mc_box(f"{skin}_{name}_mesh", size, offset, uv, mat, parent=piv)
        if overlay_uv: mc_box(f"{skin}_{name}_layer", size, offset, overlay_uv, mat, inflate=0.5, parent=piv)
        r = (pose or {}).get(name)
        if r: piv.rotation_euler = tuple(math.radians(a) for a in r)
        return piv
    part("rleg", (-2, 0, 12), (4, 12, 4), (-2, -2, -12), (0, 16))
    part("lleg", (2, 0, 12), (4, 12, 4), (-2, -2, -12), (16, 48))
    body = part("body", (0, 0, 12), (8, 12, 4), (-4, -2, 0), (16, 16))
    rarm = part("rarm", (-6, 0, 22), (4, 12, 4), (-2, -2, -10), (40, 16))
    part("larm", (6, 0, 22), (4, 12, 4), (-2, -2, -10), (32, 48))
    part("head", (0, 0, 24), (8, 8, 8), (-4, -4, 0), (0, 0), overlay_uv=(32, 0))
    if held:
        color, strength = held
        blade = bpy.data.objects.new(f"{skin}_blade", bpy.data.meshes.new("blade"))
        bm = bmesh.new(); bmesh.ops.create_cube(bm, size=1); bm.to_mesh(blade.data); bm.free()
        bpy.context.collection.objects.link(blade)
        blade.parent = rarm; blade.scale = (0.1, 0.1, 1.1); blade.location = (0, -0.05, -0.62 - 0.95 / 2 + 0.06)
        blade.rotation_euler = (math.radians(90), 0, 0)
        blade.location = (0, -0.62, -0.68)
        blade.data.materials.append(emission_material(f"blade_{skin}", color, strength))
        hilt = mc_box(f"{skin}_hilt", (3, 1, 3), (-1.5, -1.5, -0.5), (0, 0), pixel_material(f"gold_{skin}", "tex_gold.png", metal=0.8, rough=0.3), parent=rarm, location=(0, -0.05, -0.7))
    return root

def block(loc, tex_mat, size=1.0, rot=(0, 0, 0)):
    me = bpy.data.meshes.new("blk"); bm = bmesh.new(); bmesh.ops.create_cube(bm, size=size)
    uvl = bm.loops.layers.uv.new()
    for f in bm.faces:
        for loop, c in zip(f.loops, [(0, 0), (1, 0), (1, 1), (0, 1)]): loop[uvl].uv = c
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new("blk", me); ob.data.materials.append(tex_mat)
    ob.location = loc; ob.rotation_euler = tuple(math.radians(a) for a in rot)
    bpy.context.collection.objects.link(ob); return ob

# ---------------------------------------------------------------- Szenen-Konfiguration
PALETTES = {
    "cyan":   dict(world=(0.01, 0.03, 0.08), fog=(0.05, 0.25, 0.45), rim=(0.2, 0.85, 1.0), key=(1.0, 0.62, 0.3), crystal=(0.13, 0.83, 0.93), floor="tex_deepslate.png"),
    "purple": dict(world=(0.03, 0.01, 0.07), fog=(0.3, 0.1, 0.5), rim=(0.85, 0.45, 1.0), key=(0.4, 0.9, 1.0), crystal=(0.85, 0.4, 0.98), floor="tex_sculk.png"),
    "green":  dict(world=(0.01, 0.05, 0.03), fog=(0.1, 0.4, 0.2), rim=(0.6, 1.0, 0.4), key=(1.0, 0.85, 0.5), crystal=(0.5, 0.95, 0.3), floor="tex_stone.png"),
    "orange": dict(world=(0.06, 0.02, 0.01), fog=(0.5, 0.2, 0.05), rim=(1.0, 0.55, 0.15), key=(0.5, 0.8, 1.0), crystal=(1.0, 0.6, 0.15), floor="tex_stone.png"),
}

ACTION = {  # Dynamische Posen (Grad)
    "slash":  {"rarm": (-150, 0, 25), "larm": (40, 0, -20), "rleg": (35, 0, 0), "lleg": (-30, 0, 0), "body": (8, 0, -12), "head": (-8, 0, 15)},
    "leap":   {"rarm": (-60, 0, 30), "larm": (-120, 0, -35), "rleg": (-70, 0, 0), "lleg": (20, 0, 0), "body": (-10, 0, 0), "head": (10, 0, -10)},
    "bow":    {"rarm": (-90, 0, -25), "larm": (-90, 0, 35), "rleg": (15, 0, 0), "lleg": (-15, 0, 0), "head": (0, 0, 20)},
    "cast":   {"rarm": (-170, 0, 15), "larm": (-160, 0, -15), "rleg": (10, 0, 0), "lleg": (-10, 0, 0), "head": (-15, 0, 0)},
    "wave":   {"rarm": (-170, 0, 20), "larm": (10, 0, -10), "head": (0, 0, 10)},
    "run":    {"rarm": (50, 0, 10), "larm": (-55, 0, -10), "rleg": (-45, 0, 0), "lleg": (40, 0, 0), "body": (12, 0, 0)},
    "point":  {"rarm": (-90, 0, 10), "larm": (15, 0, -10), "head": (0, 0, 15)},
    "cheer":  {"rarm": (-165, 0, 25), "larm": (-165, 0, -25), "head": (-10, 0, 0)},
    "idle":   {"rarm": (5, 0, 5), "larm": (-5, 0, -5)},
}

CONFIGS = {
    "keyart":  dict(pal="cyan", title="SHADOW", title2="CLIENT", heroes=[("shadow", (0.3, -0.2, 0.35), 18, "slash", 1.2, ((0.2, 0.9, 1.0), 25)),
                                                                   ("ember", (-1.3, 1.0, 0.15), 35, "leap", 1.0, ((1.0, 0.5, 0.1), 20)),
                                                                   ("frost", (1.6, 1.2, 0.0), -30, "bow", 1.0, None),
                                                                   ("nova", (-0.2, 2.4, 0.0), 10, "cast", 0.95, None)], arc=True),
    "friends": dict(pal="cyan", title="FREUNDE", heroes=[("shadow", (0.5, 0, 0), 25, "wave", 1.1, None), ("ember", (1.9, 0.6, 0), -25, "wave", 1.05, None)], arc=False),
    "modules": dict(pal="green", title="32 MODULE", heroes=[("frost", (1.2, 0, 0), -20, "run", 1.15, None), ("ember", (2.5, 1.2, 0), -35, "slash", 1.0, ((1.0, 0.5, 0.1), 18))], arc=True, arc_at=(2.3, 1.3, 0.0)),
    "hud":     dict(pal="orange", title="HUD-EDITOR", heroes=[("ember", (1.4, 0, 0), -25, "point", 1.15, None)], arc=False),
    "badge":   dict(pal="purple", title="ABZEICHEN", heroes=[("nova", (0.7, 0, 0), 25, "cast", 1.1, None), ("shadow", (2.0, 0.5, 0), -20, "idle", 1.05, None)], arc=False),
    "version": dict(pal="cyan", title="1.21.11", heroes=[("shadow", (1.0, 0, 0.5), 15, "leap", 1.15, ((0.2, 0.9, 1.0), 25)), ("frost", (2.4, 1.0, 0), -30, "cheer", 1.0, None)], arc=True, arc_at=(0.7, 0.1, 0.2)),
}
cfg = CONFIGS[SCENE]; pal = PALETTES[cfg["pal"]]
wide = W > H

# ---------------------------------------------------------------- Welt & Licht
world = bpy.data.worlds.new("world"); scene.world = world; world.use_nodes = True
bg = world.node_tree.nodes["Background"]; bg.inputs["Color"].default_value = (*[c * 0.6 for c in pal["world"]], 1); bg.inputs["Strength"].default_value = 1.0
vol = world.node_tree.nodes.new("ShaderNodeVolumePrincipled")
vol.inputs["Density"].default_value = 0.006; vol.inputs["Color"].default_value = (*pal["fog"], 1)
world.node_tree.links.new(vol.outputs[0], world.node_tree.nodes["World Output"].inputs["Volume"])

def light(kind, loc, energy, color, rot=(0, 0, 0), size=1.0):
    ld = bpy.data.lights.new(kind, kind); ld.energy = energy; ld.color = color
    if kind == "AREA": ld.size = size
    ob = bpy.data.objects.new(kind, ld); ob.location = loc; ob.rotation_euler = tuple(math.radians(a) for a in rot)
    bpy.context.collection.objects.link(ob); return ob

light("SUN", (0, 0, 10), 1.6, pal["key"], rot=(60, 0, -30))           # warmes Hauptlicht von vorne links
light("AREA", (-2.5, -4.5, 3.5), 900, pal["key"], rot=(60, 0, -30), size=3)   # Key-Light auf die Helden
light("AREA", (2.5, -3.5, 2.0), 260, (0.7, 0.85, 1.0), rot=(70, 0, 35), size=3)  # Fülllicht
light("AREA", (0, 4, 4), 700, pal["rim"], rot=(-125, 0, 180), size=7)  # Randlicht von hinten

# ---------------------------------------------------------------- Höhle aus Blöcken
floor_mat = pixel_material("floor", pal["floor"], rough=0.9, tint=(0.42, 0.46, 0.55))
wall_mat = pixel_material("wall", "tex_stone.png", rough=0.95)
crystal_mat = pixel_material("crystal", "tex_crystal.png", emission=7.0, tint=pal["crystal"])
glow_mat = emission_material("glowcube", pal["crystal"], 7)

for x in range(-9, 10):
    for y in range(-3, 12):
        h = 0 if abs(x) < 5 and y < 6 else rnd.choice([0, 0, 1])
        block((x, y, -0.5 + h * (1 if y > 4 else 0)), floor_mat)
# Rückwand mit Torbogen
for x in range(-10, 11):
    for z in range(0, 14):
        if abs(x) < 3 and z < 6 and not (abs(x) == 2 and z > 3):  # Öffnung
            continue
        y = 9 + (abs(x) // 4) + rnd.choice([0, 0, 1])
        block((x, y, z), wall_mat)
# Decke mit hängenden Blöcken (rahmt das Bild oben ein)
for x in range(-10, 11):
    for y in range(-2, 10):
        if rnd.random() < 0.55:
            block((x, y, 9 + rnd.choice([0, 0, 1, 2])), wall_mat)
        if rnd.random() < 0.12:
            for k in range(rnd.randint(1, 3)):
                block((x, y, 8 - k), rnd.choice([wall_mat, wall_mat, crystal_mat]))
# Leuchtendes Portal im Bogen
portal = block((0, 9.6, 2.4), emission_material("portal", pal["crystal"], 0.55), size=1.0); portal.scale = (4.5, 0.2, 5.5)
# Kristalle
for _ in range(14):
    x = rnd.uniform(-7, 7); y = rnd.uniform(2, 8.5)
    if abs(x) < 2.2 and y < 6: continue
    c = block((x, y, 0.3), crystal_mat, size=1.0, rot=(rnd.uniform(-15, 15), rnd.uniform(-15, 15), rnd.uniform(0, 90)))
    c.scale = (0.35, 0.35, rnd.uniform(0.8, 2.2))
# Schwebende Blöcke
for _ in range(12):
    block((rnd.uniform(-8, 8), rnd.uniform(2, 9), rnd.uniform(3, 8)), rnd.choice([wall_mat, floor_mat, crystal_mat]),
          size=rnd.uniform(0.5, 1.0), rot=(rnd.uniform(0, 45), rnd.uniform(0, 45), rnd.uniform(0, 45)))
# Funken
spark_mat = emission_material("spark", (1.0, 0.85, 0.5), 12)
for _ in range(70):
    s = block((rnd.uniform(-5, 6), rnd.uniform(-1.5, 6), rnd.uniform(0.2, 5)), rnd.choice([spark_mat, glow_mat]), size=rnd.uniform(0.03, 0.07))

# ---------------------------------------------------------------- Helden
for skin, loc, rz, pose, sc, held in cfg["heroes"]:
    player(skin, loc, rot_z=rz, pose=ACTION[pose], scale=sc, held=held)

# Energiebogen (wie ein Schwerthieb)
if cfg.get("arc"):
    curve = bpy.data.curves.new("arc", "CURVE"); curve.dimensions = "3D"
    sp = curve.splines.new("BEZIER"); sp.bezier_points.add(3)
    pts = [(-1.0, -0.9, 0.5), (-0.6, -1.3, 2.0), (0.8, -1.2, 2.5), (1.4, -0.7, 1.2)]
    for bp, p in zip(sp.bezier_points, pts):
        bp.co = p; bp.handle_left_type = bp.handle_right_type = "AUTO"
    curve.bevel_depth = 0.07; curve.bevel_resolution = 2
    sp.bezier_points[0].radius = 0.1; sp.bezier_points[1].radius = 1.0; sp.bezier_points[2].radius = 1.2; sp.bezier_points[3].radius = 0.15
    arc = bpy.data.objects.new("arc", curve); bpy.context.collection.objects.link(arc)
    arc.location = cfg.get("arc_at", (0, 0, 0))
    arc.data.materials.append(emission_material("arc", (1.0, 0.5, 0.08), 14))
    arc2 = arc.copy(); arc2.data = curve.copy(); arc2.data.bevel_depth = 0.025
    arc2.data.materials.clear(); arc2.data.materials.append(emission_material("arc_core", (1.0, 0.9, 0.6), 28))
    arc2.location = arc.location
    bpy.context.collection.objects.link(arc2)

# ---------------------------------------------------------------- 3D-Titel
font = bpy.data.fonts.load(os.path.join(HERE, "Silkscreen-Bold.ttf"))
gold = bpy.data.materials.new("gold"); gold.use_nodes = True
gb = gold.node_tree.nodes["Principled BSDF"]
gb.inputs["Base Color"].default_value = (1.0, 0.5, 0.04, 1); gb.inputs["Metallic"].default_value = 0.9; gb.inputs["Roughness"].default_value = 0.28
gb.inputs["Emission Color"].default_value = (1.0, 0.55, 0.1, 1); gb.inputs["Emission Strength"].default_value = 0.25
white = bpy.data.materials.new("titlewhite"); white.use_nodes = True
wb = white.node_tree.nodes["Principled BSDF"]; wb.inputs["Base Color"].default_value = (0.9, 0.95, 1, 1); wb.inputs["Roughness"].default_value = 0.4
dark = bpy.data.materials.new("titledark"); dark.use_nodes = True
dark.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.02, 0.02, 0.04, 1)

def text3d(body, size, loc, mat, extrude=0.12):
    cu = bpy.data.curves.new("txt", "FONT"); cu.body = body; cu.font = font; cu.size = size
    cu.align_x = "CENTER"; cu.align_y = "CENTER"; cu.extrude = extrude; cu.bevel_depth = 0.01
    ob = bpy.data.objects.new("txt", cu); bpy.context.collection.objects.link(ob)
    ob.data.materials.append(mat); ob.data.materials.append(dark)
    ob.location = loc; ob.rotation_euler = (math.radians(90), 0, 0)
    return ob

# ---------------------------------------------------------------- Kamera
cam_data = bpy.data.cameras.new("cam"); cam = bpy.data.objects.new("cam", cam_data); bpy.context.collection.objects.link(cam)
scene.camera = cam
if wide:
    cam.location = (0.6, -4.9, 0.7); cam_data.lens = 24
    target = Vector((1.1, 0.8, 1.7))
else:
    cam.location = (0.35, -4.4, 0.5); cam_data.lens = 22
    target = Vector((0.45, 0.8, 2.25))
cam.rotation_euler = (target - cam.location).to_track_quat("-Z", "Y").to_euler()
cam_data.dof.use_dof = True; cam_data.dof.focus_distance = (target - cam.location).length - 1.0; cam_data.dof.aperture_fstop = 3.5

# Titel vor die Kamera setzen (immer lesbar)
fwd = (target - cam.location).normalized()
right = fwd.cross(Vector((0, 0, 1))).normalized(); up = right.cross(fwd).normalized()
base = cam.location + fwd * 2.4
if wide:
    title_loc = base - right * 0.78 + up * 0.52
    t = text3d(cfg["title"], 0.25 if len(cfg["title"]) < 9 else 0.2, title_loc, gold, extrude=0.07)
else:
    t = text3d(cfg["title"], 0.34, base + up * 1.12, white if cfg.get("title2") else gold, extrude=0.08)
    if cfg.get("title2"):
        t2 = text3d(cfg["title2"], 0.44, base + up * 0.80, gold, extrude=0.11)
        t2.rotation_euler = cam.rotation_euler.copy(); t2.rotation_euler.rotate_axis("X", 0)
t.rotation_euler = cam.rotation_euler.copy()

# ---------------------------------------------------------------- Rendern
scene.render.resolution_x = W; scene.render.resolution_y = H; scene.render.resolution_percentage = 100
scene.render.filepath = OUT; scene.render.image_settings.file_format = "PNG"
scene.view_settings.view_transform = "AgX" if "AgX" in [i.name for i in bpy.types.ColorManagedViewSettings.bl_rna.properties["view_transform"].enum_items] else "Filmic"
looks = [i.identifier for i in bpy.types.ColorManagedViewSettings.bl_rna.properties["look"].enum_items]
scene.view_settings.look = "AgX - Punchy" if "AgX - Punchy" in looks else "None"
engine = os.environ.get("SC_ENGINE", "BLENDER_EEVEE")
scene.render.engine = engine
if engine == "BLENDER_EEVEE":
    e = scene.eevee
    e.taa_render_samples = SAMPLES; e.use_bloom = True; e.bloom_intensity = 0.035; e.bloom_threshold = 1.2; e.bloom_radius = 6.5
    e.use_gtao = True; e.use_soft_shadows = True; e.use_volumetric_lights = True; e.volumetric_tile_size = "4"
    e.use_ssr = True
else:
    scene.cycles.samples = SAMPLES; scene.cycles.use_denoising = True; scene.cycles.device = "CPU"
bpy.ops.render.render(write_still=True)
print("RENDERED", OUT)
