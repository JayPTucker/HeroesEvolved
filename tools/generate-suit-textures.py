"""Generate the original textures for lead, the protective suits, the Suit Fabricator and the ember body under a burning
player's skin (standard library only).

Run: py -3 tools/generate-suit-textures.py
Output is deterministic, so regenerating does not change committed files.

Palettes:
  lead / galena   blue-grey metal          (58,62,70) .. (226,232,238)
  radiation suit  faded hazard yellow      (201,162,39), grey lead seams, black rubber, amber leaded-glass visor
  velocity suit   greyscale (dyed in game, red by default) with undyed white trim: a raised chest plate with ">>"
                  cut through it, a belt, forearm bands, boots, swept fins at the temples, pale lenses
  aramid fibre    Kevlar gold              (214,170,58)
  ember body      charred muscle (92,20,14), charcoal bones (30,8,6), glowing cracks (255,120,30)
"""
import pathlib
import random
import struct
import zlib

root = pathlib.Path(__file__).resolve().parents[1]
tex = root / "src/main/resources/assets/heroesevolved/textures"

T = (0, 0, 0, 0)


def png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + bytes(max(0, min(255, int(c))) for px in row for c in px) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def canvas(w, h, fill=T):
    return [[list(fill) for _ in range(w)] for _ in range(h)]


def shade(c, k):
    return [c[0] * k, c[1] * k, c[2] * k, c[3] if len(c) > 3 else 255]


def rgba(c, a=255):
    return [c[0], c[1], c[2], a]


def mix(a, b, k):
    return [a[i] + (b[i] - a[i]) * k for i in range(3)] + [255]


# ---------------------------------------------------------------- blocks

GALENA = [(58, 62, 70), (100, 108, 120), (150, 158, 172), (196, 204, 214), (232, 236, 242)]


def host(seed, base, spread, streaks):
    rng = random.Random(seed)
    rows = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            s = base + rng.randint(-spread, spread)
            if streaks and (y % 5 == 0 or (y + x // 6) % 7 == 0):
                s -= 8
            rows[y][x] = [s, s, s + 2, 255]
    return rows


def galena(px, seed):
    """Cubic galena crystals: little squares with a lit top-left and a dark bottom-right edge."""
    rng = random.Random(seed)
    for cx, cy, size in ((3, 3, 3), (10, 2, 2), (8, 8, 3), (2, 11, 2), (12, 12, 2), (5, 7, 1)):
        for y in range(size):
            for x in range(size):
                tone = 2 + (1 if x == 0 or y == 0 else 0) - (1 if x == size - 1 or y == size - 1 else 0)
                if rng.random() < 0.15:
                    tone = 4
                px[cy + y][cx + x] = rgba(GALENA[max(0, min(4, tone))])
        px[cy + size - 1][cx + size - 1] = rgba(GALENA[0])


def lead_ores():
    stone = host(7, 120, 9, False)
    galena(stone, 1)
    png(tex / "block/lead_ore.png", stone)
    deep = host(11, 70, 7, True)
    galena(deep, 2)
    png(tex / "block/deepslate_lead_ore.png", deep)


def leaded_glass():
    """A leaded window: dark lead cames in a diamond lattice holding pale amber panes."""
    px = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            came = x in (0, 15) or y in (0, 15) or (x + y) % 8 == 0 or (x - y) % 8 == 0
            if came:
                px[y][x] = [70, 74, 80, 255]
            else:
                glint = 40 if (x + 2 * y) % 11 == 0 else 0
                px[y][x] = [226 + glint // 4, 198 + glint // 3, 140 + glint, 72 + glint]
    png(tex / "block/leaded_glass.png", px)


STEEL = [(54, 58, 64), (92, 98, 106), (132, 138, 146), (170, 176, 184)]


def bench_side(seed, top=False, front=False, bottom=False):
    rng = random.Random(seed)
    px = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            px[y][x] = rgba(STEEL[0] if edge else STEEL[1 + (rng.random() < 0.25)])
    if bottom:
        return px
    if top:
        # A green cutting mat with a grid, a bolt of ember-red cloth and a sheet of lead.
        for y in range(2, 14):
            for x in range(2, 14):
                px[y][x] = [46, 104, 70, 255] if (x % 4 and y % 4) else [80, 150, 104, 255]
        for y in range(3, 7):
            for x in range(3, 10):
                px[y][x] = rgba(shade((176, 46, 32), 0.85 + 0.15 * (y % 2)))
        for y in range(9, 13):
            for x in range(8, 13):
                px[y][x] = rgba(GALENA[2 + (x + y) % 2])
        px[11][3] = px[11][4] = [210, 210, 214, 255]  # shears
        px[10][5] = px[12][5] = [210, 210, 214, 255]
        return px
    if front:
        # A press with a hazard stripe, a lit gauge and a drawer.
        for x in range(1, 15):
            for y in (2, 3):
                px[y][x] = [214, 170, 40, 255] if (x + y) % 4 < 2 else [30, 30, 32, 255]
        for y in range(5, 10):
            for x in range(3, 13):
                px[y][x] = rgba(STEEL[2] if y == 5 else STEEL[0])
        px[7][7] = px[7][8] = [255, 140, 50, 255]
        for x in range(4, 12):
            px[12][x] = rgba(STEEL[3])
        px[13][7] = px[13][8] = rgba(STEEL[0])
        return px
    # Sides: rolls of lead sheet and aramid cloth on rails.
    for y in range(4, 7):
        for x in range(2, 14):
            px[y][x] = rgba(GALENA[1 + (y == 4) + (x % 5 == 0)])
    for y in range(9, 12):
        for x in range(2, 14):
            px[y][x] = rgba(shade((214, 170, 58), 0.8 + 0.2 * (y == 9)))
    return px


def fabricator():
    png(tex / "block/suit_fabricator_top.png", bench_side(3, top=True))
    png(tex / "block/suit_fabricator_front.png", bench_side(4, front=True))
    png(tex / "block/suit_fabricator_side.png", bench_side(5))
    png(tex / "block/suit_fabricator_bottom.png", bench_side(6, bottom=True))


# ---------------------------------------------------------------- material items

def sprite(mask, colors):
    """A 16x16 icon from a mask of characters, each mapped to a colour; '.' is clear. Edges are shaded."""
    px = canvas(16, 16)
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            c = list(colors[ch]) + [255]
            up = y == 0 or mask[y - 1][x] == "."
            left = x == 0 or row[x - 1] == "."
            down = y == 15 or mask[y + 1][x] == "."
            right = x == 15 or row[x + 1] == "."
            if ch == "#":
                if up or left:
                    c = shade(c, 1.18)
                if down or right:
                    c = shade(c, 0.62)
            px[y][x] = c
    return px


INGOT = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......####.....",
    ".....########...",
    "...###########..",
    "..############..",
    "..###########...",
    "..#########.....",
    "...######.......",
    "................",
    "................",
    "................",
    "................",
]
RAW = [
    "................",
    "................",
    "................",
    "......###.......",
    "....######......",
    "...########.....",
    "..##########....",
    "..###########...",
    "...##########...",
    "...#########....",
    "....#######.....",
    ".....####.......",
    "................",
    "................",
    "................",
    "................",
]
SKEIN = [
    "................",
    "................",
    "....######......",
    "...#lllll##.....",
    "..##l###ll##....",
    "..#l##.##l##....",
    "..#l#...#l##....",
    "..#l##.##l##....",
    "..##l###l##.....",
    "...##lll##......",
    "....######......",
    ".......##.......",
    "........##......",
    ".........##.....",
    "..........#.....",
    "................",
]
TILE = [
    "................",
    "................",
    "................",
    "...##########...",
    "...#llllllll#...",
    "...#l######d#...",
    "...#l######d#...",
    "...#l######d#...",
    "...#l######d#...",
    "...#l######d#...",
    "...#dddddddd#...",
    "...##########...",
    "................",
    "................",
    "................",
    "................",
]
FILTER = [
    "................",
    "................",
    ".....######.....",
    "....#llllll#....",
    "....########....",
    "....#yyyyyy#....",
    "....########....",
    "....#dddddd#....",
    "....#d#d#d##....",
    "....#dddddd#....",
    "....#d#d#d##....",
    "....########....",
    ".....######.....",
    "................",
    "................",
    "................",
]


def materials():
    lead = {"#": (130, 138, 150)}
    png(tex / "item/lead_ingot.png", sprite(INGOT, lead))
    png(tex / "item/raw_lead.png", sprite(RAW, {"#": (112, 116, 126)}))
    png(tex / "item/aramid_fiber.png", sprite(SKEIN, {"#": (190, 146, 44), "l": (238, 204, 104)}))
    png(tex / "item/unfired_ceramic_plate.png", sprite(TILE, {"#": (166, 150, 132), "l": (196, 182, 164), "d": (128, 114, 98)}))
    png(tex / "item/ceramic_plate.png", sprite(TILE, {"#": (232, 228, 216), "l": (252, 250, 244), "d": (60, 58, 56)}))
    png(tex / "item/respirator_filter.png", sprite(FILTER, {"#": (60, 62, 66), "l": (120, 124, 130), "y": (214, 170, 40), "d": (34, 34, 36)}))


# ---------------------------------------------------------------- suit icons

HELMET = [
    "................",
    "................",
    "....########....",
    "...##########...",
    "..############..",
    "..###vvvvvv###..",
    "..##vvvvvvvv##..",
    "..##vvvvvvvv##..",
    "..###vvvvvv###..",
    "..####ffff####..",
    "...###ffff###...",
    "......ffff......",
    "................",
    "................",
    "................",
    "................",
]
COWL = [
    "................",
    "................",
    "....########....",
    "...##########...",
    ".#############..",
    "################",
    "..##ee####ee##..",
    "..##ee####ee##..",
    "..############..",
    "..###......###..",
    "...##......##...",
    "................",
    "................",
    "................",
    "................",
    "................",
]
CHEST = [
    "................",
    "..###......###..",
    ".####......####.",
    ".#####....#####.",
    ".##############.",
    ".##############.",
    "..#####tt#####..",
    "...####tt####...",
    "...##########...",
    "...##########...",
    "...##########...",
    "...##########...",
    "...##########...",
    "...##########...",
    "....########....",
    "................",
]
LEGS = [
    "................",
    "...##########...",
    "...##########...",
    "...####..####...",
    "...###....###...",
    "...###....###...",
    "...###....###...",
    "...###....###...",
    "...###....###...",
    "...###....###...",
    "...###....###...",
    "...###....###...",
    "...###....###...",
    "...rrr....rrr...",
    "................",
    "................",
]
BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..###......###..",
    "..###......###..",
    "..###......###..",
    "..###......###..",
    ".####.....####..",
    ".rrrrr...rrrrr..",
    ".rrrrr...rrrrr..",
    "................",
]

HAZMAT = (201, 162, 39)
RUBBER = (34, 34, 36)
AMBER = (196, 140, 48)


WHITE = (240, 238, 232)
WHITE_SHADE = (196, 194, 190)
LENS = (206, 226, 240)
# The velocity suit's chest mark: a bold V (for velocity) cut through a white plate, so the suit's own colour shows in
# it. 1 marks a cut.
CHEVRONS = [
    "100001",
    "110011",
    "011110",
    "001100",
    "000000",
]


def trim(name, mask):
    """The velocity suit's undyed overlay for an icon: its white trim and pale lenses, clear everywhere else."""
    px = canvas(16, 16)
    white = lambda x, y: rgba(WHITE if (x + y) % 4 else WHITE_SHADE)
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if name == "velocity_cowl" and ch == "e":
                px[y][x] = rgba(LENS)
            elif name == "velocity_cowl" and ((x, y) in {(1, 4), (2, 5), (1, 5), (14, 4), (13, 5), (14, 5)}):
                px[y][x] = white(x, y)  # the fins
            elif name == "velocity_suit" and (y == 13 or y == 2 and ch == "#" and (x < 4 or x > 11)):
                px[y][x] = white(x, y)  # the belt and the bands
            elif name == "velocity_leggings" and y == 1:
                px[y][x] = white(x, y)
            elif name == "velocity_boots":
                px[y][x] = rgba(WHITE_SHADE) if ch == "r" else white(x, y)
    if name == "velocity_suit":
        for y in range(4, 9):
            for x in range(5, 11):
                if CHEVRONS[y - 4][x - 5] == "0":
                    px[y][x] = rgba(WHITE)
    if name == "velocity_cowl":
        for (x, y) in ((1, 4), (14, 4)):
            px[y][x] = rgba(WHITE)
    return px


def suit_icons():
    hazard = {"#": HAZMAT, "v": AMBER, "f": RUBBER, "t": (30, 30, 30), "r": RUBBER, "e": AMBER}
    png(tex / "item/radiation_hood.png", sprite(HELMET, hazard))
    png(tex / "item/radiation_coat.png", sprite(CHEST, hazard))
    png(tex / "item/radiation_trousers.png", sprite(LEGS, hazard))
    png(tex / "item/radiation_boots.png", sprite(BOOTS, hazard))
    grey = {"#": (210, 210, 210), "e": (210, 210, 210), "t": (210, 210, 210), "r": (210, 210, 210), "v": (210, 210, 210), "f": (210, 210, 210)}
    for name, mask in (("velocity_cowl", COWL), ("velocity_suit", CHEST), ("velocity_leggings", LEGS), ("velocity_boots", BOOTS)):
        png(tex / f"item/{name}.png", sprite(mask, grey))
        png(tex / f"item/{name}_overlay.png", trim(name, mask))


# ---------------------------------------------------------------- worn armour (64x32, the humanoid armour layout)

def faces(ox, oy, w, h, d):
    """A box's six faces in a texture: name -> (x, y, width, height)."""
    return {"top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d), "right": (ox, oy + d, d, h),
            "front": (ox + d, oy + d, w, h), "left": (ox + d + w, oy + d, d, h), "back": (ox + 2 * d + w, oy + d, w, h)}


HEAD = faces(0, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARM = faces(40, 16, 4, 12, 4)
LEG = faces(0, 16, 4, 12, 4)


def paint(px, face, fn):
    x0, y0, w, h = face
    for y in range(h):
        for x in range(w):
            c = fn(x, y, w, h)
            if c is not None:
                px[y0 + y][x0 + x] = list(c)


def cloth(base, seed, side=False):
    rng = random.Random(seed)

    def fn(x, y, w, h):
        k = 0.92 + 0.08 * rng.random()
        if side:
            k *= 0.82
        if x == 0 or x == w - 1:
            k *= 0.85
        return shade(base, k)
    return fn


def box(px, f, base, seed, rows=None):
    """Paints every face of a box, optionally only the rows in {@code rows} of its side faces (a band)."""
    for name, face in f.items():
        side = name in ("right", "left", "back")
        if rows is not None and name not in ("top", "bottom"):
            x0, y0, w, h = face
            face = (x0, y0 + rows[0], w, rows[1] - rows[0])
        elif rows is not None and name == "top" and rows[0] > 0:
            continue
        paint(px, face, cloth(base, seed + sum(map(ord, name)), side))


def radiation_suit():
    l1 = canvas(64, 32)
    box(l1, HEAD, HAZMAT, 1)
    # The hood's face: a wide amber visor, a respirator canister under it.
    paint(l1, HEAD["front"], lambda x, y, w, h: (rgba(mix(AMBER, (255, 236, 180), 0.5 if (x + y) % 5 == 0 else 0)) if 1 <= x <= 6 and 2 <= y <= 4
                                                 else rgba(RUBBER) if 3 <= x <= 4 and 5 <= y <= 7
                                                 else [214, 170, 40, 255] if x in (2, 5) and y == 6 else None))
    box(l1, BODY, HAZMAT, 2)
    # The trefoil on the chest, and a grey lead seam down the front.
    tre = {(3, 2), (4, 2), (2, 3), (5, 3), (3, 4), (4, 4), (2, 5), (3, 5), (4, 5), (5, 5), (3, 6), (4, 6)}
    paint(l1, BODY["front"], lambda x, y, w, h: [24, 24, 24, 255] if (x, y) in tre
          else [120, 126, 134, 255] if y == 9 else None)
    paint(l1, BODY["back"], lambda x, y, w, h: [120, 126, 134, 255] if 2 <= x <= 5 and 2 <= y <= 7 else None)
    box(l1, ARM, HAZMAT, 3)
    paint(l1, ARM["front"], lambda x, y, w, h: rgba(RUBBER) if y >= 9 else None)  # gloves
    for name in ("right", "left", "back"):
        paint(l1, ARM[name], lambda x, y, w, h: rgba(RUBBER) if y >= 9 else None)
    paint(l1, ARM["bottom"], lambda x, y, w, h: rgba(RUBBER))
    box(l1, LEG, RUBBER, 4, rows=(7, 12))  # boots: black rubber over the lower leg
    paint(l1, LEG["bottom"], lambda x, y, w, h: rgba(shade(RUBBER, 0.7)))
    # The 3D parts (see client/SuitModels): a respirator canister with a yellow band, a steel air tank with a valve and
    # straps, thick black soles.
    steel = (150, 156, 164)
    extra(l1, 32, 0, 3, 3, 2, lambda n, x, y, fw, fh: [214, 170, 40, 255] if y == 1 and n != "front" else
          rgba(shade(RUBBER, 0.6)) if n == "front" and (x + y) % 2 else rgba(RUBBER))
    extra(l1, 42, 0, 4, 7, 3, lambda n, x, y, fw, fh: rgba(shade(steel, 1.15)) if n == "top" and 1 <= x <= 2 else
          rgba(RUBBER) if y in (1, 5) and n not in ("top", "bottom") else rgba(shade(steel, 0.85 if x == 0 or x == fw - 1 else 1)))
    extra(l1, 32, 10, 5, 1, 5, lambda n, x, y, fw, fh: rgba(shade(RUBBER, 0.8)))
    png(tex / "models/armor/radiation_suit_layer_1.png", l1)

    l2 = canvas(64, 32)
    box(l2, BODY, HAZMAT, 5, rows=(8, 12))
    paint(l2, BODY["front"], lambda x, y, w, h: [120, 126, 134, 255] if y == 9 else None)
    box(l2, LEG, HAZMAT, 6, rows=(0, 12))
    paint(l2, LEG["front"], lambda x, y, w, h: [120, 126, 134, 255] if y in (5, 6) else None)
    png(tex / "models/armor/radiation_suit_layer_2.png", l2)


def extra(px, u, v, w, h, d, fn):
    """Paints every face of one of a model's extra boxes, whose texture starts at (u, v)."""
    for name, face in faces(u, v, w, h, d).items():
        paint(px, face, lambda x, y, fw, fh, name=name: fn(name, x, y, fw, fh))


def velocity_suit():
    """Red (the dye, light grey here) with undyed white trim, and its 3D parts (see client/SuitModels)."""
    grey = (214, 214, 214)
    white = lambda x, y: rgba(WHITE if (x + 2 * y) % 5 else WHITE_SHADE)
    l1, o1 = canvas(64, 32), canvas(64, 32)
    box(l1, HEAD, grey, 11)
    # The cowl leaves the mouth and chin bare; pale lenses sit in the overlay.
    paint(l1, HEAD["front"], lambda x, y, w, h: T if y >= 5 and 1 <= x <= 6 else None)
    paint(o1, HEAD["front"], lambda x, y, w, h: rgba(LENS) if y == 3 and x in (1, 2, 5, 6) else None)
    box(l1, BODY, grey, 12)
    box(l1, ARM, grey, 13)
    box(l1, LEG, grey, 14, rows=(8, 12))
    # White boots with a pale grey welt and sole.
    for name in ("right", "front", "left", "back"):
        paint(o1, LEG[name], lambda x, y, w, h: rgba(WHITE_SHADE) if y == 11 else white(x, y) if y >= 8 else None)
    paint(o1, LEG["bottom"], lambda x, y, w, h: rgba(shade(WHITE_SHADE, 0.7)))
    # Seams: a darker line down the sides of the body and arms in the dyed layer.
    for name in ("right", "left"):
        paint(l1, BODY[name], lambda x, y, w, h: rgba(shade(grey, 0.7)) if x == 1 else None)
        paint(l1, ARM[name], lambda x, y, w, h: rgba(shade(grey, 0.7)) if x == 1 else None)
    # The 3D parts: base grey under all of them (it shows through the chevrons), white over.
    for u, v, w, h, d in ((32, 4, 4, 1, 4), (32, 10, 1, 2, 3), (40, 10, 8, 2, 4), (50, 0, 6, 5, 1)):
        extra(l1, u, v, w, h, d, lambda n, x, y, fw, fh: rgba(grey))
    extra(o1, 32, 4, 4, 1, 4, lambda n, x, y, fw, fh: white(x, y))       # forearm bands
    extra(o1, 32, 10, 1, 2, 3, lambda n, x, y, fw, fh: white(x, y))      # fins
    # The belt, with the suit's colour showing at the buckle.
    extra(o1, 40, 10, 8, 2, 4, lambda n, x, y, fw, fh: None if n == "front" and x in (3, 4) else white(x, y))
    # The chest plate: white, the chevrons cut through it, a shaded rim.
    extra(o1, 50, 0, 6, 5, 1, lambda n, x, y, fw, fh: (None if CHEVRONS[y][x] == "1" else rgba(WHITE)) if n == "front"
          else rgba(WHITE_SHADE))
    png(tex / "models/armor/velocity_suit_layer_1.png", l1)
    png(tex / "models/armor/velocity_suit_layer_1_overlay.png", o1)

    l2, o2 = canvas(64, 32), canvas(64, 32)
    box(l2, BODY, grey, 15, rows=(7, 12))
    box(l2, LEG, grey, 16, rows=(0, 12))
    for name in ("right", "left"):
        paint(l2, LEG[name], lambda x, y, w, h: rgba(shade(grey, 0.7)) if x == 1 else None)
    png(tex / "models/armor/velocity_suit_layer_2.png", l2)
    png(tex / "models/armor/velocity_suit_layer_2_overlay.png", o2)


# ---------------------------------------------------------------- the ember body (64x64 player skin layout)

MUSCLE = (92, 20, 14)
BONE = (30, 8, 6)
CRACK = (255, 120, 30)

SKIN_HEAD = faces(0, 0, 8, 8, 8)
SKIN_BODY = faces(16, 16, 8, 12, 4)
SKIN_RARM = faces(40, 16, 4, 12, 4)
SKIN_LARM = faces(32, 48, 4, 12, 4)
SKIN_RLEG = faces(0, 16, 4, 12, 4)
SKIN_LLEG = faces(16, 48, 4, 12, 4)


def ember_body():
    rng = random.Random(42)
    body, glow = canvas(64, 64), canvas(64, 64)

    def flesh(x, y, w, h):
        k = 0.75 + 0.35 * rng.random()
        if (x * 3 + y * 5) % 7 == 0:
            k *= 0.7  # fibres
        return shade(MUSCLE, k)

    for f in (SKIN_HEAD, SKIN_BODY, SKIN_RARM, SKIN_LARM, SKIN_RLEG, SKIN_LLEG):
        for face in f.values():
            paint(body, face, flesh)
    bone = lambda: rgba(shade(BONE, 0.8 + 0.4 * rng.random()))
    # The skull: sockets, a nose, a row of teeth.
    skull = {(1, 2), (2, 2), (5, 2), (6, 2), (1, 3), (2, 3), (5, 3), (6, 3), (3, 4), (4, 4)} | {(x, 6) for x in range(1, 7)}
    paint(body, SKIN_HEAD["front"], lambda x, y, w, h: bone() if (x, y) in skull or (y == 7 and x % 2) else None)
    paint(body, SKIN_HEAD["top"], lambda x, y, w, h: bone() if x in (0, 7) or y in (0, 7) else None)
    # The ribcage: a breastbone and ribs, a pelvis below; the spine behind.
    paint(body, SKIN_BODY["front"], lambda x, y, w, h: bone() if x in (3, 4) and y <= 7
          or y in (1, 3, 5) and 1 <= x <= 6 or y >= 9 and (x <= 2 or x >= 5) else None)
    paint(body, SKIN_BODY["back"], lambda x, y, w, h: bone() if x in (3, 4) or y in (2, 4) and 1 <= x <= 6 else None)
    # A bone down every limb, with a knot at the elbow or knee.
    for f in (SKIN_RARM, SKIN_LARM, SKIN_RLEG, SKIN_LLEG):
        for name in ("front", "back", "right", "left"):
            paint(body, f[name], lambda x, y, w, h: bone() if x in (1, 2) and y != 0 or y in (5, 6) and 0 <= x <= 3 else None)
    png(tex / "entity/burn/ember_body.png", body)

    # Glowing cracks, wandering over every face.
    for f in (SKIN_HEAD, SKIN_BODY, SKIN_RARM, SKIN_LARM, SKIN_RLEG, SKIN_LLEG):
        for face in f.values():
            x0, y0, w, h = face
            for _ in range(max(1, w * h // 18)):
                x, y = rng.randrange(w), rng.randrange(h)
                for _ in range(rng.randint(2, 5)):
                    glow[y0 + y][x0 + x] = rgba(mix(CRACK, (255, 220, 140), rng.random() * 0.5))
                    x = max(0, min(w - 1, x + rng.choice((-1, 0, 1))))
                    y = max(0, min(h - 1, y + rng.choice((-1, 1))))
    # Ember light in the skull's eyes.
    for (x, y) in ((1, 2), (2, 3), (5, 2), (6, 3)):
        glow[SKIN_HEAD["front"][1] + y][SKIN_HEAD["front"][0] + x] = rgba(CRACK)
    png(tex / "entity/burn/ember_body_glow.png", glow)


if __name__ == "__main__":
    lead_ores()
    leaded_glass()
    fabricator()
    materials()
    suit_icons()
    radiation_suit()
    velocity_suit()
    ember_body()
    print("suit textures written")
