"""The Wither's original skin (charred bone, pale bone, armour plate, horn, ivory and the star), its two emissive maps,
the Fractured Star / Breach Seed / record icons, the Hunger Mark's effect icon, and the Guide and fight text. No external
artwork.

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

# ---- its boss bar (client/WitherBossBar): a bone bar with rib teeth, knuckled ends and three skulls as its emblem ----
# 256 x 48. The bar's channel is x 24..232, y 30..36 (filled in code with the phase's colour); the eyes are a separate
# white map the code tints with the same colour, so they burn mint, violet or ember with the phase.
BAR_W, BAR_H = 256, 48
bar = Image.new('RGBA', (BAR_W, BAR_H), (0, 0, 0, 0)); bp = bar.load()
eyes = Image.new('RGBA', (BAR_W, BAR_H), (0, 0, 0, 0)); ep = eyes.load()
OUT, DARKB, MID, LIGHT, HI = (16, 14, 14, 255), (92, 84, 70, 255), (168, 158, 134, 255), (212, 203, 178, 255), (236, 230, 210, 255)
def bpx(x, y, c):
    if 0 <= x < BAR_W and 0 <= y < BAR_H: bp[x, y] = c
def shade(x, y, top, bottom):
    t = (y - top) / max(1, bottom - top)
    return HI if t < .15 else LIGHT if t < .45 else MID if t < .8 else DARKB
# The shaft: an outlined bone tube round a dark channel.
for x in range(20, 237):
    for y in range(27, 40):
        edge = y in (27, 39) or x in (20, 236)
        bpx(x, y, OUT if edge else shade(x, y, 27, 39))
for x in range(23, 234):
    for y in range(29, 38):
        bpx(x, y, OUT if y in (29, 37) or x in (23, 233) else (8, 9, 12, 255))
# Rib teeth along the top and bottom, curving toward the middle like a ribcage.
for x in range(28, 230, 9):
    lean = -1 if x < 128 else 1
    for k in range(4):
        bpx(x + lean * (k // 2), 26 - k, LIGHT if k < 3 else MID); bpx(x + 1 + lean * (k // 2), 26 - k, MID)
        bpx(x - lean * (k // 2), 40 + k, MID); bpx(x + 1 - lean * (k // 2), 40 + k, DARKB)
# Knuckled ends, like the heads of two long bones.
for cx in (11, 244):
    for oy in (-4, 4):
        cy = 33 + oy
        for x in range(cx - 7, cx + 8):
            for y in range(cy - 6, cy + 7):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** .5
                if d <= 6.4: bpx(x, y, OUT if d > 5.4 else shade(x, y, cy - 6, cy + 6))
# Three skulls over the middle: the center one larger, crowned with small horns.
def skull(cx, top, w, h, horns):
    for x in range(cx - w // 2, cx + w // 2 + 1):
        for y in range(top, top + h):
            u = (x - cx) / (w / 2); v = (y - top) / h
            inside = u * u + (max(0, v - .55) / .45) ** 2 * .6 <= 1 and not (v > .78 and abs(u) > .62)
            if inside:
                border = u * u + (max(0, v - .55) / .45) ** 2 * .6 > .72 or y == top
                bpx(x, y, OUT if border else shade(x, y, top, top + h))
    ew = max(2, w // 5); ey = top + h * 45 // 100
    for side in (-1, 1):
        ex = cx + side * (w // 4) - (ew // 2 if side < 0 else ew // 2 - 1)
        for x in range(ex, ex + ew):
            for y in range(ey, ey + ew):
                bpx(x, y, (6, 6, 8, 255)); ep[x, y] = (255, 255, 255, 255)
    bpx(cx, ey + ew + 1, OUT); bpx(cx - 1, ey + ew + 1, OUT)
    for x in range(cx - w // 3, cx + w // 3 + 1, 2): bpx(x, top + h - 2, OUT)
    if horns:
        # Two horns, thick at the root, curving out and up.
        for side in (-1, 1):
            for k in range(7):
                hx = cx + side * (w // 2 - 4 + (k + 1) // 2 + (k > 3)); hy = top + 3 - k
                bpx(hx, hy, OUT if k == 6 else MID if k > 2 else LIGHT)
                if k < 5: bpx(hx - side, hy, DARKB if k > 2 else MID)
                if k < 2: bpx(hx - 2 * side, hy, OUT)
skull(128, 4, 18, 18, True)
for nx in (108, 148):
    # A short neck of two vertebrae down to the bar.
    for y in range(22, 27):
        for x in (nx - 1, nx, nx + 1):
            bpx(x, y, OUT if x != nx or y in (22, 24, 26) else MID)
skull(108, 10, 13, 13, False)
skull(148, 10, 13, 13, False)
# The star: a small gem where the emblem meets the bar, lit with the eyes.
for x in range(124, 133):
    for y in range(22, 31):
        if abs(x - 128) + abs(y - 26) <= 4:
            bpx(x, y, OUT if abs(x - 128) + abs(y - 26) == 4 else (40, 44, 48, 255)); ep[x, y] = (255, 255, 255, 255) if abs(x - 128) + abs(y - 26) < 4 else ep[x, y]
(assets / 'textures/gui').mkdir(parents=True, exist_ok=True)
bar.save(assets / 'textures/gui/wither_bar.png')
eyes.save(assets / 'textures/gui/wither_bar_eyes.png')

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

# The Hunger Mark's effect icon (18 x 18): a violet thread drawn taut from a ringed eye into a white star point.
mark = Image.new('RGBA', (18, 18)); d = ImageDraw.Draw(mark)
d.ellipse((1, 6, 9, 14), fill='#1C1430', outline='#A18AE4')
d.ellipse((4, 9, 6, 11), fill='#FF6F8E')
d.line([(8, 9), (11, 7), (13, 6)], fill='#A18AE4', width=1)
d.line([(9, 11), (12, 8), (14, 6)], fill='#6B57A8', width=1)
for (x, y) in [(15, 4), (14, 5), (16, 5), (15, 3), (15, 6)]: d.point((x, y), fill='#E8FFF4')
d.point((15, 5), fill='#FFFFFF')
(assets / 'textures/mob_effect').mkdir(parents=True, exist_ok=True)
mark.save(assets / 'textures/mob_effect/hungering.png')

lang_path = assets / 'lang/en_us.json'; lang = json.loads(lang_path.read_text(encoding='utf-8'))
lang.update({
    'guide.heroesevolved.wither.title': 'The Hungry Cage',
    'guide.heroesevolved.wither.1': 'Build a Wither and it never gets loose: it is dragged backwards through a tear in the air behind it, and the tear stays as a breach. Walk into it with an awakened gift at Power Level 16 or above, alone or with friends. Inside is the Ossuary, a walled floor under a giant fossil ribcage with room to fly; the Wither falls out of a second tear over the dais to meet you. The breach in the entrance alcove is a sheltered way home, and anything you drop comes back when you respawn.',
    'guide.heroesevolved.wither.2': 'Read its body. The center head coils before it snaps; an arm cocks across its chest before it rakes; both fists climb overhead before the slam; the claws dig in before bone spires burst along a crack; it heaves and retches before it spews wither skeletons onto circles beside you (they crumble in thirty seconds and drop nothing); the cage opens before the star discharges. Every hit is also marked on the floor or in the air, and the mark never moves: step off it. When two tears open, one past you, it is about to step through and come out of that one behind you: turn and leave the ring under it. When it crouches and coils its tail, a low band all round it will be swept: jump as the white edge reaches you, and do not hug its back. When it tears ribs from its chest, they land in a ring round you: step out, or use your power to break the cage before its mouths turn on you. In Critical Mass it spits a starving black star that drifts after whoever is farthest away and collapses where it is: keep moving. From the second phase some moves are feints that hold their coil longer: dodge when the mark fills, not when the body moves. A normal jump clears the rolling ring of thrown-up floor. Good gear helps, but no weapon carries the fight and part of every blow it lands goes through any armour.',
    'guide.heroesevolved.wither.3': 'It is not beaten with a blade. It arrives under the Star Ward, a lattice of light over its ribs fed by three Hunger Hearts floating in the bone pylons: while the ward stands it takes a quarter of every blow and cannot be brought below seven tenths of its health. Break the hearts. A broken one re-forms in thirty seconds, so keep at them; break all three and the ward shatters and throws it down with its chest open. Plain weapons do half to a heart and little to its body; a power\'s blows do it all, and some powers answer a heart outright: look for the turning diamond of light only your power sees, and see below for what yours does. Its skulls can be sent back (a punch works; Wind Arms, Bullet Catch, a force field or a quake\'s push work better): a returned skull shatters a heart and hits it past the ward.',
    'guide.heroesevolved.wither.4': 'After a chain it slumps with its chest open and takes half again as much damage. Three spaced charged strikes during its windups break its stance. In the Broken Vessel its brood carries bone back to it: a skeleton that reaches it climbs into its cage and mends it, so clear them, hold them or turn them first. A bite or a hook leaves the Hunger Mark, a thread to its star that drinks from you while it can see you: put stone between you for three seconds, drink milk, or let a cleansing power lift it. When it winds up a heavy move a sleeping heart lights and drinks for seven seconds: break it and the hunger backfires.',
    'guide.heroesevolved.wither.5': 'It charges along a lane; if the lane ends in stone the warning shows a burst there: stand in front of a pillar, step aside, and it crashes and is stunned. In Critical Mass its star is open: blows that land on it (shots, beams aimed straight at it, blows from up at its height) do more, and three in quick succession make it reel. Its final discharge reaches everyone it can see: hide behind a pillar, hold it on a shield power, or use your power as the white ring closes on the star to slip out (a jet step, a blur, a hop, stopped time); a regenerator outlasts it.',
    'guide.heroesevolved.wither.6': 'When it dies, its star swells, drinks everything in and detonates: raise a shield toward it (or let a shield-making power hold it) or you are thrown flat, ears ringing, until you come to. The way out tears open where it died, with its prizes. If everyone leaves mid-fight, it heals and the next attempt starts fresh.',
    'guide.heroesevolved.wither.yours': 'What %s does to it:',
    'guide.heroesevolved.wither.key': '  %s. %s: %s.',
    'wither.heroesevolved.phase_name.1': 'Threefold Hunger',
    'wither.heroesevolved.phase_name.2': 'The Broken Vessel',
    'wither.heroesevolved.phase_name.3': 'Critical Mass',
    'lock.heroesevolved.wither.shield': 'The Star Ward\'s hearts',
    'lock.heroesevolved.wither.projectiles': 'Its skulls',
    'lock.heroesevolved.wither.bodies': 'Its mending brood',
    'lock.heroesevolved.wither.room_weapon': 'A feeding heart',
    'lock.heroesevolved.wither.curse': 'The Hunger Mark',
    'lock.heroesevolved.wither.unblockable': 'Event Horizon',
    'lock.heroesevolved.wither.weak_point': 'Its open star',
    'entity.heroesevolved.hunger_heart': 'Hunger Heart',
    'death.attack.heroesevolved.star_hunger': '%1$s was drunk dry by a hungry star',
    'death.attack.heroesevolved.star_hunger.player': '%1$s was drunk dry by %2$s',
    'effect.heroesevolved.hungering': 'Hunger Mark',
    'wither.heroesevolved.enter': 'It comes warded. Break the three hearts in the pylons to bring its ward down, and send its skulls back; your power sees what it can answer (the Guide has the rest). The breach behind you leads home.',
    'wither.heroesevolved.refused.open': 'The skulls will not take. A Wither breach is already open at %s (%s): only one can be open at a time. Finish or abandon that fight first (an operator can cancel it in the admin console).',
    'wither.heroesevolved.refused.inside': 'The skulls will not take here, inside the cage.',
    'wither.heroesevolved.refused.cannot': 'The skulls will not take: this world has no room for a Wither breach.',
    'wither.heroesevolved.ward_broken': 'The ward shatters off its ribs and throws it down. It is open to you.',
    'wither.heroesevolved.phase_tip.2': 'The vessel breaks. Its brood will carry bone back to mend it, and its hearts wake whenever it draws on them.',
    'wither.heroesevolved.phase_tip.3': 'Its star burns open. Strike the star itself, and when the cage opens, get out of its sight.',
    'wither.heroesevolved.collapsing': 'The Wither — Its Star Is Collapsing',
    'wither.heroesevolved.blast': 'The star goes off like a wall of light. You are on your back before you hear it.',
    'wither.heroesevolved.braced': 'Your shield takes the blast. You slide back, still standing.',
    'wither.heroesevolved.come_to': 'You come to. Where it died, the air has torn open: the way out.',
})
lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
print('wrote rift_wither, glow, cracks, rift_skull, icons and guide text')
