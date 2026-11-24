"""The Wither's Ossuary: a round blackstone floor walled in by a charnel ring of skull niches, under a vault of seven
giant fossil ribs on one spine, as if the fight happens inside the ribcage of something far bigger than the Wither.
Regenerates its blueprint; no valuable ore. Every position the fight reads (anchors, pillars, entrance, exit, walls)
is WitherGeometry's.

    py -3 tools/generate-wither-arena.py
"""
from pathlib import Path
import math, random
from blueprint_draft import StateGrid

ROOT = Path(__file__).resolve().parents[1]
rand = random.Random(4141)

# ---- WitherGeometry's numbers (a game test compares the markers with the Java) ----
RADIUS, WALL, WALL_HEIGHT = 29, 31, 10
FLOOR = 2                      # the floor's surface layer; fighters stand at y 3
TOP = FLOOR + WALL_HEIGHT      # the wall's top course: the ribs spring from here
ANCHORS = [(-20, 6, -12), (20, 6, -12), (0, 6, 23)]
PILLARS = [(-15, 6, 2, 12), (15, 6, 2, 11), (-9, -15, 1, 9), (9, -15, 1, 10), (-10, 17, 1, 8), (10, 17, 1, 9)]
EXIT_Z = -37
PEAK = FLOOR + 49              # the spine's underside at the vault's crown; usable flight to floor + 44

W, H, D, AX, AZ = 85, 58, 90, 42, 43
g = StateGrid(W, H, D, fill='.')


def put(x, y, z, block):
    if not (-AX <= x < W - AX and 0 <= y < H and -AZ <= z < D - AZ): return
    g.put(x + AX, y, z + AZ, block if ':' in block else 'minecraft:' + block)


def get(x, y, z):
    if not (-AX <= x < W - AX and 0 <= y < H and -AZ <= z < D - AZ): return '.'
    return g.cells[y][z + AZ][x + AX]


def box(x0, y0, z0, x1, y1, z1, block):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1): put(x, y, z, block)


def brick(chance=.18):
    r = rand.random()
    return 'cracked_polished_blackstone_bricks' if r < chance else 'polished_blackstone_bricks'


def bone(axis='y'):
    return f'bone_block[axis={axis}]'


# ---------------------------------------------------------------- foundation and floor
for x in range(-34, 35):
    for z in range(-34, 35):
        r = math.hypot(x, z)
        if r > 33.5: continue
        put(x, 0, z, 'polished_blackstone_bricks')
        put(x, 1, z, 'blackstone')
        angle = math.atan2(z, x)
        if r <= RADIUS + .5:
            # Worn hardest where it fights: cracks gather toward the dais.
            floor = brick(.34 if r < 10 else .2 if r < 20 else .1)
            if 12.4 < r < 13.6 or 21.4 < r < 22.6: floor = 'polished_blackstone'
            if 4 < r < 5.2: floor = 'chiseled_polished_blackstone'
            if r <= 4: floor = 'polished_blackstone'
            # Six channels run from the dais to the wall: dark now, lit by the later phases.
            spoke = min(abs(((angle + math.pi / 6) % (math.pi / 3)) - math.pi / 6) * r, 99)
            if 5.2 < r < 27.5 and spoke < .55: floor = 'polished_basalt[axis=y] @wither:channel'
            if rand.random() < .025 and r > 6: floor = 'blackstone'
            put(x, FLOOR, z, floor)
        else:
            put(x, FLOOR, z, 'polished_blackstone_bricks')

# The dais: a six-pointed star of basalt round the center, its points meeting the channels.
for x in range(-5, 6):
    for z in range(-5, 6):
        r = math.hypot(x, z)
        a = math.atan2(z, x)
        star = 2.2 + 1.6 * abs(math.cos(3 * a)) ** 4
        if r <= star: put(x, FLOOR, z, 'polished_basalt[axis=y]')
put(0, FLOOR, 0, 'chiseled_polished_blackstone')

# Lit nodes where the channels cross the inner ring and where they meet the wall.
for i in range(6):
    a = i * math.pi / 3
    for rr in (13, 27):
        put(round(math.cos(a) * rr), FLOOR, round(math.sin(a) * rr), 'sea_lantern')

# ---------------------------------------------------------------- the charnel wall
NICHES = 24
for x in range(-34, 35):
    for z in range(-34, 35):
        r = math.hypot(x, z)
        if not (RADIUS + .5 < r <= WALL + 1.5): continue
        put(x, FLOOR + 1, z, 'polished_blackstone_bricks')          # plinth course
        for y in range(FLOOR + 2, TOP):
            put(x, y, z, brick(.15))
        put(x, TOP, z, 'polished_blackstone')
        if r > WALL + .5: put(x, TOP + 1, z, 'polished_blackstone_brick_wall')
for i in range(NICHES * 2):
    a = i * math.tau / (NICHES * 2)
    deg = math.degrees(a)
    if i % 2 == 0:
        # Pilaster: a basalt column standing proud of the wall, capped where the ribs will spring.
        for y in range(FLOOR + 1, TOP + 1):
            for rr in (RADIUS + .2, RADIUS + 1):
                put(round(math.cos(a) * rr), y, round(math.sin(a) * rr), 'polished_basalt[axis=y]' if y < TOP else 'chiseled_polished_blackstone')
    else:
        # A niche of the dead: a recess three high with bones heaped in it and a skull watching the floor.
        for y in range(FLOOR + 3, FLOOR + 6):
            for rr in (RADIUS + 1, RADIUS + 1.8):
                put(round(math.cos(a) * rr), y, round(math.sin(a) * rr), 'air')
        nx, nz = round(math.cos(a) * (RADIUS + 1.8)), round(math.sin(a) * (RADIUS + 1.8))
        put(nx, FLOOR + 2, nz, 'bone_block[axis=y]')
        fx, fz = round(math.cos(a) * (RADIUS + 1)), round(math.sin(a) * (RADIUS + 1))
        put(fx, FLOOR + 2, fz, 'polished_blackstone_brick_slab[type=top]')
        rot = round((deg + 90) / 22.5) % 16
        put(fx, FLOOR + 3, fz, f'skeleton_skull[rotation={rot}]' if i % 6 != 3 else f'wither_skeleton_skull[rotation={rot}]')
        put(nx, FLOOR + 3, nz, 'bone_block[axis=x]' if i % 4 == 1 else 'bone_block[axis=z]')
        if i % 4 == 1: put(nx, FLOOR + 4, nz, 'verdant_froglight[axis=y]')
        put(fx, FLOOR + 6, fz, 'chiseled_polished_blackstone')

# ---------------------------------------------------------------- the way in: an arch through the wall to the return alcove
box(-3, FLOOR + 1, -34, 3, FLOOR + 6, -29, 'air')
box(-4, 0, -42, 4, FLOOR, -29, 'polished_blackstone_bricks')
for z in range(-42, -29):
    put(0, FLOOR, z, 'polished_blackstone')
box(-4, FLOOR + 1, -42, 4, FLOOR + 6, -42, 'polished_blackstone_bricks')
box(-4, FLOOR + 1, -42, -4, FLOOR + 6, -32, 'polished_blackstone_bricks')
box(4, FLOOR + 1, -42, 4, FLOOR + 6, -32, 'polished_blackstone_bricks')
box(-3, FLOOR + 1, -41, 3, FLOOR + 5, -32, 'air')
box(-4, FLOOR + 6, -42, 4, FLOOR + 7, -30, 'polished_blackstone')
box(-1, FLOOR + 1, -34, 1, FLOOR + 4, -34, 'polished_blackstone_bricks')     # the baffle: no sniping from the shelter
put(0, FLOOR + 5, -34, 'chiseled_polished_blackstone')
for x in (-3, 3):
    put(x, FLOOR + 5, -38, 'verdant_froglight[axis=y]')
# The arch's frame inside the Ossuary.
for y in range(FLOOR + 1, FLOOR + 8):
    for x in (-4, 4):
        put(x, y, -29, 'polished_basalt[axis=y]')
box(-4, FLOOR + 7, -29, 4, FLOOR + 7, -29, 'chiseled_polished_blackstone')
put(0, FLOOR + 1, -25, 'minecraft:air @spawn:fighter')

# A great fossil skull above the arch, staring down the Ossuary.
for x in range(-4, 5):
    for y in range(TOP, TOP + 7):
        for z in range(-35, -29):
            dx, dy, dz = x / 4.6, (y - TOP - 3) / 3.6, (z + 32) / 3.2
            if dx * dx + dy * dy + dz * dz <= 1: put(x, y, z, 'bone_block[axis=y]' if (x + y + z) % 4 else 'calcite')
for x in (-2, 2):
    box(x - 1 if x < 0 else x, TOP + 3, -30, x if x < 0 else x + 1, TOP + 4, -29, 'air')
    put(x, TOP + 3, -31, 'verdant_froglight[axis=y]')
for x in range(-3, 4, 2):
    put(x, TOP - 1, -29, 'calcite')          # teeth along the arch's lintel

# ---------------------------------------------------------------- the vault: seven ribs on a spine
def spine_height(z):
    k = min(1.0, abs(z) / 31.5)
    return TOP + (PEAK + 1 - TOP) * (1 - k ** 2.5) ** (1 / 2.5)


def stamp(cx, cy, cz, radius, block, along='y'):
    r = int(math.ceil(radius))
    for x in range(-r, r + 1):
        for y in range(-r, r + 1):
            if x * x + y * y > radius * radius: continue
            px, py = round(cx + x), round(cy + y)
            if math.hypot(px, cz) > WALL + 1.5 and py < TOP + 2: continue
            put(px, py, cz, block)


lamps = []
for zi, z in enumerate((-24, -16, -8, 0, 8, 16, 24)):
    x0 = math.sqrt(max(0, (WALL - .5) ** 2 - z * z))
    hz = spine_height(z)
    for side in (-1, 1):
        steps = 140
        prev = None
        for s in range(steps + 1):
            u = s / steps
            x = x0 * (1 - u)
            y = TOP + (hz - TOP) * (1 - (x / x0) ** 2.3) ** (1 / 2.3) if x0 > 0 else hz
            px = side * x
            thick = 1.55 - .45 * u
            for dz in (-1, 0, 1):
                stamp(px, y, z + dz, thick if dz == 0 else thick - .55, bone('y' if u < .45 else 'x') if (s + dz) % 9 else 'calcite')
            # Lanterns hang from the rib's underside.
            if s in (40, 82):
                lamps.append((round(px), round(y - 2.2), z))
# The spine: vertebrae with dorsal spikes along the crown, unbroken where it falls steeply to the wall.
for z in range(-31, 32):
    hy = spine_height(z)
    low = min(round(hy), round(spine_height(z - 1 if z > 0 else z + 1)))
    for dx in (-1, 0, 1):
        for y in range(low, round(hy) + 2):
            put(dx, y, z, bone('z' if y >= round(hy) else 'y'))
    if z % 4 == 0:
        box(-2, round(hy) - 1, z, 2, round(hy) + 2, z, 'calcite')
        for dy in range(3, 6):
            put(0, round(hy) + dy, z, bone('y'))
# Soul lanterns hanging from the knuckles on chains.
for (x, y, z) in lamps:
    if y <= FLOOR + 6: continue
    for dy in range(0, 3):
        if get(x, y - dy, z) == '.': put(x, y - dy, z, 'chain[axis=y]')
    put(x, y - 3, z, 'soul_lantern[hanging=true]')
    put(x, y + 1, z, 'bone_block[axis=y]')

# ---------------------------------------------------------------- broken pillars: cover, and what its charge breaks on
for (px, pz, half, height) in PILLARS:
    box(px - half - 1, FLOOR + 1, pz - half - 1, px + half + 1, FLOOR + 1, pz + half + 1, 'chiseled_polished_blackstone')
    tallest = None
    for x in range(px - half, px + half + 1):
        for z in range(pz - half, pz + half + 1):
            edge = x in (px - half, px + half) or z in (pz - half, pz + half)
            top = FLOOR + height - (rand.randint(0, 3) if edge else rand.randint(0, 1))
            for y in range(FLOOR + 2, top + 1):
                put(x, y, z, 'polished_basalt[axis=y]' if (x == px - half or x == px + half) and (z == pz - half or z == pz + half) else brick(.3))
            if tallest is None or top > tallest[1]: tallest = ((x, z), top)
    (tx, tz), ty = tallest
    if half >= 2:
        put(tx, ty + 1, tz, 'soul_campfire[lit=true,facing=north,signal_fire=false]')
    # Fallen pieces heaped against its plinth (never loose on the open floor, where they would snag the Wither).
    for k in range(3 + half):
        a = rand.random() * math.tau
        fx, fz = round(px + math.cos(a) * (half + 2)), round(pz + math.sin(a) * (half + 2))
        if math.hypot(fx, fz) < RADIUS - 2 and get(fx, FLOOR + 1, fz) == '.':
            put(fx, FLOOR + 1, fz, 'polished_blackstone_brick_slab[type=bottom]' if k % 2 else 'cracked_polished_blackstone_bricks')

# ---------------------------------------------------------------- the three anchors, each cradled in bone
for i, (x, y, z) in enumerate(ANCHORS):
    box(x - 2, FLOOR + 1, z - 2, x + 2, FLOOR + 1, z + 2, 'polished_blackstone')       # the approach plate
    box(x - 1, FLOOR + 2, z - 1, x + 1, FLOOR + 2, z + 1, 'chiseled_polished_blackstone')
    put(x, FLOOR + 3, z, 'sea_lantern')
    put(x - 1, FLOOR + 3, z, 'polished_blackstone_bricks'); put(x + 1, FLOOR + 3, z, 'polished_blackstone_bricks')
    put(x, y, z, f'minecraft:tinted_glass @anchor:{i}')
    put(x - 1, y, z, 'polished_blackstone_bricks'); put(x + 1, y, z, 'polished_blackstone_bricks')
    box(x - 1, y + 1, z, x + 1, y + 1, z, 'chiseled_polished_blackstone')
    # Bone talons at the corners, curling over the glass without closing its open faces.
    for sx in (-1, 1):
        for sz in (-1, 1):
            for yy in range(FLOOR + 2, y + 2):
                put(x + 2 * sx, yy, z + 2 * sz, bone('y'))
            put(x + 2 * sx, y + 2, z + sz, bone('z'))
            put(x + sx, y + 3, z + sz, 'calcite')
    put(x, y + 3, z, bone('y'))

# Scattered remains on the floor near the wall: no clutter in the fighting space.
for k in range(26):
    a = rand.random() * math.tau
    r = RADIUS - .6 - rand.random() * 1.2
    bx, bz = round(math.cos(a) * r), round(math.sin(a) * r)
    if abs(bx) <= 5 and bz < -20: continue
    if get(bx, FLOOR + 1, bz) != '.': continue
    put(bx, FLOOR + 1, bz, 'bone_block[axis=x]' if k % 3 == 0 else 'skeleton_skull[rotation=%d]' % rand.randint(0, 15) if k % 3 == 1 else 'polished_blackstone_brick_slab[type=bottom]')

# Fill light a few blocks over the floor (invisible, no collision), so the floor reads without lamps standing in the
# fight; the vault's lanterns and the channel nodes carry the look.
for x in range(-24, 25, 8):
    for z in range(-24, 25, 8):
        if math.hypot(x, z) < 27 and get(x, FLOOR + 5, z) == '.': put(x, FLOOR + 5, z, 'light[level=12]')

put(0, FLOOR + 1, 0, 'minecraft:air @spawn:wither')

g.write(ROOT / 'src/main/resources/data/heroesevolved/blueprints/wither_cage.blueprint',
        name='wither_cage', anchor=(AX, 0, AZ),
        notes=['Generated by tools/generate-wither-arena.py: change the script, never this file. Floor at y=2, fighters at y=3.',
               'The Ossuary: floor radius 29, charnel wall to 31 and 10 high, seven fossil ribs on a spine; the crown is 49 above the floor.',
               'WitherGeometry: anchors (-20,6,-12), (20,6,-12), (0,6,23); entrance (0,3,-25); return breach (0,~5,-37) in the alcove.'])
print('wrote wither_cage.blueprint')
