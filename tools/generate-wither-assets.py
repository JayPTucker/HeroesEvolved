"""The Wither's original skin (charred bone, pale bone, armour plate, horn, ivory and the star), its two emissive maps,
the Fractured Star / Breach Seed / record icons, and the Guide text. No external artwork.

Layout (shared with entity/client/RiftWitherModel): 256 x 256.
  pale bone  (0..256, 0..64)     charred bone (0..256, 64..128)
  plate      (0..128, 128..192)  craniums     (128..256, 128..192), faces at (138,138) 12x9, (137,159) and (185,159) 10x8
  star       (0..24, 192..208)   chain metal  (24..64, 192..224)
  ivory      (64..128, 192..224) horn         (128..256, 192..224)
Run with: py -3 tools/generate-wither-assets.py
"""
from pathlib import Path
import sys, json, random, math
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'build/title-asset-tools'))
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
assets = ROOT / 'src/main/resources/assets/heroesevolved'
W = H = 256
rand = random.Random(3616)

# ---- value noise, tileable over the whole sheet ----
def lattice(seed, size):
    r = random.Random(seed)
    return [[r.random() for _ in range(size)] for _ in range(size)]
GRIDS = {s: lattice(900 + s, s) for s in (8, 16, 32, 64)}
def noise(x, y, size):
    g = GRIDS[size]; cell = W / size
    fx, fy = x / cell, y / cell
    x0, y0 = int(fx) % size, int(fy) % size
    x1, y1 = (x0 + 1) % size, (y0 + 1) % size
    tx, ty = fx - int(fx), fy - int(fy)
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = g[y0][x0] + (g[y0][x1] - g[y0][x0]) * tx
    b = g[y1][x0] + (g[y1][x1] - g[y1][x0]) * tx
    return a + (b - a) * ty
def fbm(x, y):
    return .45 * noise(x, y, 8) + .3 * noise(x, y, 16) + .17 * noise(x, y, 32) + .08 * noise(x, y, 64)
def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

skin = Image.new('RGBA', (W, H), (0, 0, 0, 0)); px = skin.load()
glow = Image.new('RGBA', (W, H), (0, 0, 0, 255)); gx = glow.load()
cracks = Image.new('RGBA', (W, H), (0, 0, 0, 255)); cx = cracks.load()

def region(x0, y0, x1, y1, paint):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = paint(x, y) + (255,)

# Pale bone: weathered ivory with grain running along each bone, pits and soot in the low spots.
def bone(x, y):
    n = fbm(x, y)
    grain = .5 + .5 * math.sin(y * 1.9 + noise(x, y, 32) * 6)
    shade = .38 + .5 * n + .12 * grain
    c = mix((86, 80, 68), (214, 206, 184), shade)
    if rand.random() < .025: c = mix(c, (52, 48, 42), .6)
    if noise(x * 1.7, y * 1.3, 16) < .2: c = mix(c, (60, 58, 54), .35)
    return c
# Charred bone: soot-black with ash-grey ridges and a faint green cast from the star.
def char(x, y):
    n = fbm(x + 77, y + 31)
    ridge = .5 + .5 * math.sin(x * .7 + y * 1.3 + n * 5)
    c = mix((30, 28, 27), (98, 92, 82), .25 + .55 * n + .15 * ridge)
    if rand.random() < .03: c = mix(c, (128, 122, 108), .5)
    if noise(x, y, 64) > .82: c = mix(c, (60, 82, 74), .25)
    return c
# Armour plate: layered grey bone with darker seams every few pixels.
def plate(x, y):
    n = fbm(x + 13, y + 99)
    band = (y % 5 == 0) or ((x + y // 5 * 3) % 11 == 0)
    c = mix((70, 70, 72), (164, 160, 150), .3 + .6 * n)
    return mix(c, (36, 36, 40), .55) if band else c
def ivory(x, y):
    n = fbm(x + 5, y + 50)
    tip = ((y - 192) % 6) / 6
    return mix(mix((150, 140, 116), (238, 230, 208), .35 + .6 * n), (90, 84, 72), tip * .5)
def horn(x, y):
    n = fbm(x + 300, y + 7)
    band = .5 + .5 * math.sin(y * 1.6 + x * .4)
    return mix((24, 22, 22), (88, 80, 70), .2 + .45 * n + .3 * band)
def star(x, y):
    n = fbm(x, y)
    facet = abs(((x + y) % 6) - 3) / 3
    return mix((120, 220, 196), (236, 255, 248), .4 + .4 * n + .2 * facet)
def metal(x, y):
    link = (x % 4 in (0, 3)) or (y % 4 in (0, 3))
    return (58, 62, 64) if link else (26, 28, 30)

region(0, 0, 256, 64, bone)
region(0, 64, 256, 128, char)
region(0, 128, 128, 192, plate)
region(128, 128, 256, 192, bone)
region(0, 192, 24, 208, star)
region(24, 192, 64, 224, metal)
region(64, 192, 128, 224, ivory)
region(128, 192, 256, 224, horn)

# Fractures: random walks over every bone. Dark in the skin, light in the crack map (shown faintly, then brighter each phase).
def walk(x, y, steps, bounds):
    x0, y0, x1, y1 = bounds
    angle = rand.random() * math.tau
    for _ in range(steps):
        angle += rand.uniform(-.7, .7)
        x += math.cos(angle); y += math.sin(angle)
        if not (x0 <= x < x1 and y0 <= y < y1): return
        ix, iy = int(x), int(y)
        r, g, b, a = px[ix, iy]
        px[ix, iy] = (max(0, r // 3), max(0, g // 3), max(0, b // 3), 255)
        cx[ix, iy] = (255, 255, 255, 255)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = ix + dx, iy + dy
            if x0 <= nx < x1 and y0 <= ny < y1 and cx[nx, ny][0] < 90: cx[nx, ny] = (90, 90, 90, 255)
        if rand.random() < .06: walk(x, y, steps // 3, bounds)
for bounds, count in (((0, 0, 256, 64), 70), ((0, 64, 256, 128), 55), ((0, 128, 128, 192), 24), ((128, 128, 256, 150), 10)):
    for _ in range(count):
        walk(rand.uniform(bounds[0], bounds[2]), rand.uniform(bounds[1], bounds[3]), rand.randint(8, 26), bounds)

# The plate's inlay: thin chevrons of the star's light set into the armour.
for y in range(128, 192, 8):
    for x in range(0, 128):
        yy = y + abs((x % 8) - 4) - 2
        if 128 <= yy < 192 and x % 16 < 12:
            gx[x, yy] = (200, 200, 200, 255)
            r, g, b, a = px[x, yy]; px[x, yy] = (min(255, r // 2 + 40), min(255, g // 2 + 70), min(255, b // 2 + 62), 255)

# Faces: deep sockets with a lit pupil, a nasal cavity and a brow shadow.
def face(ox, oy, w, h, big):
    for x in range(w):
        for y in range(2):
            r, g, b, a = px[ox + x, oy + y]; px[ox + x, oy + y] = (r * 2 // 3, g * 2 // 3, b * 2 // 3, 255)
    eye_w, eye_h = (4, 4) if big else (3, 3)
    for ex in ((1, w - 1 - eye_w)):
        for x in range(eye_w):
            for y in range(eye_h):
                corner = (x in (0, eye_w - 1)) and (y in (0, eye_h - 1))
                X, Y = ox + ex + x, oy + 2 + y
                px[X, Y] = (40, 36, 34, 255) if corner else (8, 10, 12, 255)
                inner = 0 < x < eye_w - 1 and 0 < y < eye_h - 1 if big else (x == 1 and y == 1)
                gx[X, Y] = (255, 255, 255, 255) if inner else (70, 70, 70, 255) if not corner else (0, 0, 0, 255)
    mid = w // 2
    for y in range(2):
        for x in (mid - 1, mid):
            px[ox + x, oy + 2 + eye_h + y] = (14, 14, 16, 255)
    for x in range(1, w - 1):
        if x % 2: px[ox + x, oy + h - 1] = (226, 220, 200, 255)
face(138, 138, 12, 9, True)
face(137, 159, 10, 8, False)
face(185, 159, 10, 8, False)

# The star glows through and through; its chains do not.
for y in range(192, 208):
    for x in range(0, 24):
        v = int(180 + 75 * fbm(x * 3, y * 3))
        gx[x, y] = (v, v, v, 255)

skin.save(assets / 'textures/entity/rift_wither.png')
glow.save(assets / 'textures/entity/rift_wither_glow.png')
cracks.save(assets / 'textures/entity/rift_wither_cracks.png')

# ---- the big burning skull it spits: its own 64 x 32 skin, eyes and mouth lit ----
sk = Image.new('RGBA', (64, 32), (0, 0, 0, 0)); sp = sk.load()
sg = Image.new('RGBA', (64, 32), (0, 0, 0, 255)); sgp = sg.load()
for y in range(32):
    for x in range(64):
        sp[x, y] = char(x * 3, y * 3 + 64) + (255,)
for (fx, fy) in ((8, 8),):
    for x in range(8):
        sp[fx + x, fy] = (24, 22, 22, 255)
    for ex in (1, 5):
        for x in range(2):
            for y in range(2):
                sp[fx + ex + x, fy + 2 + y] = (6, 8, 10, 255); sgp[fx + ex + x, fy + 2 + y] = (255, 255, 255, 255)
    for x in range(2, 6):
        sp[fx + x, fy + 6] = (10, 10, 12, 255); sgp[fx + x, fy + 6] = (190, 190, 190, 255)
sk.save(assets / 'textures/entity/rift_skull.png')
sg.save(assets / 'textures/entity/rift_skull_glow.png')

for name in ['fractured_star', 'breach_seed', 'music_disc_containment']:
    icon = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(icon)
    if name == 'fractured_star':
        d.polygon([(8, 1), (10, 5), (14, 6), (11, 9), (12, 14), (8, 11), (3, 14), (4, 9), (1, 6), (6, 5)], fill='#B9F9E7', outline='#315A63')
        d.line([(8, 2), (7, 7), (10, 9), (7, 13)], fill='#152535', width=1)
    elif name == 'breach_seed':
        d.ellipse((2, 1, 13, 14), fill='#172033', outline='#8FE8DE', width=2)
        d.ellipse((5, 4, 10, 11), fill='#9C80C5'); d.line((8, 4, 7, 12), fill='#E9FFF6')
    else:
        d.ellipse((1, 1, 14, 14), fill='#1D242B', outline='#3C4C52'); d.ellipse((5, 5, 10, 10), fill='#8AE3D7'); d.point((8, 8), fill='#172033')
    icon.save(assets / f'textures/item/{name}.png')
    (assets / f'models/item/{name}.json').write_text(json.dumps({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'heroesevolved:item/{name}'}}, indent=2) + '\n')

lang_path = assets / 'lang/en_us.json'; lang = json.loads(lang_path.read_text(encoding='utf-8'))
lang.update({
    'guide.heroesevolved.wither.title': 'The Hungry Cage',
    'guide.heroesevolved.wither.1': 'A Wither construction leaves an unstable breach instead of a Wither. Walk into it with an awakened gift at Power Level 16 or above, alone or with friends. Inside is the Ossuary: a walled floor under a giant fossil ribcage, with room to fly. The breach behind you is a sheltered way home, and anything you drop comes back when you respawn.',
    'guide.heroesevolved.wither.2': 'Read its body. The center head coils before it snaps; an arm cocks across its chest before it rakes; both fists climb overhead before the slam; the claws dig in before bone spires burst along a crack; the cage opens before the star discharges. Every hit is also marked on the floor or in the air, and the mark never moves: step off it. A normal jump clears the rolling shock of thrown-up floor.',
    'guide.heroesevolved.wither.3': 'After a chain it slumps with its chest open and takes half again as much damage. Three spaced charged strikes during its windups break its stance. From the second phase it charges along a lane; stand in front of a pillar and step aside, and it crashes into the stone and is stunned for several seconds. Its final discharge reaches everyone it can see: put a pillar between you and the star.',
    'guide.heroesevolved.wither.4': 'When it winds up a heavy move, the nearest unused anchor pylon lights and a filament runs to the star. It stays lit for seven seconds. Break the glass in the pylon\'s open face, or bounce a skull into it, and the stored hunger backfires: a tenth of its health, its chain cancelled and its core exposed. Each pylon works once per attempt. If everyone leaves, it heals and the next attempt starts fresh.',
    'wither.heroesevolved.enter': 'Read its body, then step off the mark. Punish it when it slumps. Bait its charge into a pillar. Break a lit pylon to turn its hunger back on it. The breach behind you leads home.',
})
lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
print('wrote rift_wither, glow, cracks, rift_skull, icons and guide text')
