"""Drafts the_collection.blueprint: Sylar's Collection, the round hall his fight is in.

Run from the repository root:  py -3 tools/draft-the-collection.py
Every number that the fight reads is SylarGeometry's (the case ring, the cases' hours, the pillars' spots, the
arrival), and BlueprintMarkerGameTests-style checks compare the markers with that class, so change both together.

The hall: a dial plate 21 blocks in radius (a brass boss and a white dial on the dais, hour markers in brass, a
chapter ring, a band of tiles where twelve display cases stand at the hours 16 out, a dark oak walk round the edge),
walls of deepslate tiles 14 high with dark oak wainscot, blackstone pilasters every half hour and a brass-framed
panel lit by a copper bulb behind each case, and a dome of deepslate bricks with twelve brass ribs.
"""
import math
import sys

sys.path.insert(0, "tools")
from blueprint_draft import StateGrid  # noqa: E402

SIZE, HEIGHT = 49, 26
AX, AY, AZ = 24, 1, 24          # the anchor: the floor block under the middle
FLOOR_R, DAIS_R, CASE_R, COVER_R = 21, 4, 16, 10
WALL_TOP = 15                   # the last layer of upright wall (world y 78)

g = StateGrid(SIZE, HEIGHT, SIZE)


def r_of(x, z):
    return math.hypot(x - AX, z - AZ)


def ang_of(x, z):
    a = math.atan2(x - AX, -(z - AZ))
    return a + 2 * math.pi if a < 0 else a


def near_hour(x, z, width):
    """True when the cell lies within `width` blocks (across the radius) of one of the twelve hour lines."""
    r = r_of(x, z)
    if r < 0.5:
        return False
    a = ang_of(x, z)
    off = min(abs(((a - h * math.pi / 6) + math.pi) % (2 * math.pi) - math.pi) for h in range(12))
    return off * r <= width


def hour_cell(h, r):
    a = h * math.pi / 6
    return AX + round(math.sin(a) * r), AZ + round(-math.cos(a) * r)


# ----------------------------------------------------------------------------
# Sub-floor and floor
# ----------------------------------------------------------------------------
for z in range(SIZE):
    for x in range(SIZE):
        r = r_of(x, z)
        if r > 23.6:
            continue
        g.put(x, 0, z, "deepslate")
        if r <= 1.0:
            s = "waxed_copper_block"
        elif r <= 3.4:
            s = "smooth_quartz"
        elif r <= 4.4:
            s = "waxed_exposed_cut_copper"
        elif r <= 13.6:
            long_mark = near_hour(x, z, 0.55) and r >= 9.5 and any(
                abs(((ang_of(x, z) - h * math.pi / 6) + math.pi) % (2 * math.pi) - math.pi) * r <= 0.55 for h in (0, 3, 6, 9))
            if (near_hour(x, z, 0.55) and r >= 11.0) or long_mark:
                s = "waxed_exposed_cut_copper"
            elif near_hour(x, z, 0.5) and 5.5 <= r:
                s = "deepslate_tiles"
            else:
                s = "polished_deepslate"
        elif r <= 14.6:
            s = "polished_blackstone_bricks"
        elif r <= 18.4:
            s = "deepslate_tiles"
        elif r <= 19.4:
            s = "polished_blackstone_bricks"
        elif r <= 21.6:
            s = "dark_oak_planks"
        else:
            s = "deepslate_tiles"
        g.put(x, AY, z, s)

# ----------------------------------------------------------------------------
# Walls (layers 2 .. WALL_TOP), pilasters every half hour, a lit panel behind each case
# ----------------------------------------------------------------------------
for y in range(AY + 1, WALL_TOP + 1):
    for z in range(SIZE):
        for x in range(SIZE):
            r = r_of(x, z)
            if r > 23.6 or r <= 21.6:
                continue
            g.put(x, y, z, "deepslate_tiles" if r > 22.6 or y > 4 else "dark_oak_planks")

for y in range(AY + 1, WALL_TOP + 1):
    for z in range(SIZE):
        for x in range(SIZE):
            r = r_of(x, z)
            if not (20.7 < r <= 22.6):
                continue
            a = ang_of(x, z)
            # Pilasters on the half hours, standing a block out from the wall.
            off = min(abs(((a - (k + 0.5) * math.pi / 6) + math.pi) % (2 * math.pi) - math.pi) for k in range(12)) * r
            if off <= 0.75:
                g.put(x, y, z, "polished_blackstone_bricks")

# The dado rail and the cornice: a course of blackstone bricks round the inside of the wall.
for z in range(SIZE):
    for x in range(SIZE):
        r = r_of(x, z)
        if 21.6 < r <= 22.6:
            g.put(x, 5, z, "polished_blackstone_bricks")
        if 20.7 < r <= 22.6:
            g.put(x, WALL_TOP - 1, z, "polished_blackstone_bricks")

# A panel behind each case: dark oak framed in brass, with a copper bulb over it.
for h in range(12):
    a = h * math.pi / 6
    for y in range(6, 12):
        for z in range(SIZE):
            for x in range(SIZE):
                r = r_of(x, z)
                if not (21.6 < r <= 22.6):
                    continue
                off = abs(((ang_of(x, z) - a) + math.pi) % (2 * math.pi) - math.pi) * r
                if off <= 1.6:
                    g.put(x, y, z, "dark_oak_planks" if off <= 0.9 and 7 <= y <= 10 else "waxed_exposed_cut_copper")
    bx, bz = hour_cell(h, 22.1)
    g.put(bx, 12, bz, "waxed_copper_bulb[lit=true] @lamp")

# ----------------------------------------------------------------------------
# The dome: deepslate bricks with twelve brass ribs, and a copper bulb on every rib
# ----------------------------------------------------------------------------
TOP = HEIGHT - 1
for y in range(WALL_TOP + 1, HEIGHT):
    k = (y - WALL_TOP) / (TOP - WALL_TOP + 0.6)
    R = 22.6 * math.sqrt(max(0.0, 1 - k * k))
    for z in range(SIZE):
        for x in range(SIZE):
            r = r_of(x, z)
            outer = R + 1.0
            if r > outer:
                continue
            if y == TOP or r > R - 1.6:
                g.put(x, y, z, "waxed_exposed_cut_copper" if near_hour(x, z, 0.6) and y < TOP else "deepslate_bricks")
            else:
                g.put(x, y, z, ".")
for h in range(12):
    for y, rr in ((18, 20.4), (21, 17.6)):
        bx, bz = hour_cell(h, rr)
        if g.get(bx, y, bz) != ".":
            g.put(bx, y, bz, "waxed_copper_bulb[lit=true] @lamp")
g.put(AX, TOP, AZ, "waxed_copper_block")

# Air inside the walls up to the dome.
for y in range(AY + 1, WALL_TOP + 1):
    for z in range(SIZE):
        for x in range(SIZE):
            r = r_of(x, z)
            if r <= 20.7 or (r <= 21.6 and g.get(x, y, z) == "_"):
                g.put(x, y, z, ".")

# ----------------------------------------------------------------------------
# The twelve cases: a blackstone plinth with a lit centre, thin dark oak posts, a glass bell, a dark cap with a brass knob
# ----------------------------------------------------------------------------
for h in range(12):
    cx, cz = hour_cell(h, CASE_R)
    for dz in (-1, 0, 1):
        for dx in (-1, 0, 1):
            corner = dx != 0 and dz != 0
            g.put(cx + dx, AY + 1, cz + dz, "sea_lantern" if dx == 0 and dz == 0 else "polished_blackstone_bricks")
            for y in (AY + 2, AY + 3):
                g.put(cx + dx, y, cz + dz, "dark_oak_fence" if corner else ".")
            g.put(cx + dx, AY + 4, cz + dz, "waxed_cut_copper_slab[type=bottom]" if dx == 0 and dz == 0
                  else "polished_blackstone_brick_slab[type=bottom]")
    g.put(cx, AY + 2, cz, f"glass @case:{h}")
    g.put(cx, AY + 3, cz, "glass")

# ----------------------------------------------------------------------------
# Markers the fight reads
# ----------------------------------------------------------------------------
for i in range(4):
    a = (1.5 + 3 * i) * math.pi / 6
    px, pz = AX + round(math.sin(a) * COVER_R), AZ + round(-math.cos(a) * COVER_R)
    g.put(px, AY, pz, g.get(px, AY, pz) + f" @cover:{i}")
# Eight copper bulbs set into the dais's rim light the middle of the hall (they go out with the rest in phase two).
for k in range(8):
    a = k * math.pi / 4
    g.put(AX + round(math.sin(a) * 3.9), AY, AZ + round(-math.cos(a) * 3.9), "waxed_copper_bulb[lit=true] @lamp")
g.put(AX, AY, AZ, "waxed_copper_block @spawn:sylar")
g.put(AX, AY, AZ + 11, g.get(AX, AY, AZ + 11) + " @spawn:party")

g.write("src/main/resources/data/heroesevolved/blueprints/the_collection.blueprint", name="the_collection",
        anchor=(AX, AY, AZ),
        notes=["Sylar's Collection: the round hall his fight is in, rebuilt before every fight in heroesevolved:the_collection.",
               "Drafted by tools/draft-the-collection.py from SylarGeometry's numbers: change the script, not this file.",
               "@case:N (lower glass of hour N's case), @cover:N (floor under pillar N), @spawn:sylar, @spawn:party, @lamp."],
        layer_notes={0: "sub-floor", AY: "the dial plate", AY + 1: "plinths and walls", WALL_TOP + 1: "the dome"})
print("wrote the_collection.blueprint")
