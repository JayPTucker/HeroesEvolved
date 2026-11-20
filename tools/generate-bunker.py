"""Draws the Odessa stasis bunker (Primatech Sublevel 4) and writes its room blueprints, twice over.

    py -3 tools/generate-bunker.py

Every room is drawn once, as the Company built it, in one grid in bunker coordinates (the origin is the floor under the
old shaft, in front of the vault; x east, y up, z south). Then a second copy is aged by a hundred and three years, and
every room is written both ways:

- `stasis_<room>_past.blueprint`: the lockdown. New copper, every light on, nothing grown in. The Last Day and Night
  Three build these (`PastBunker`).
- `stasis_<room>.blueprint`: the present, the bunker players wake in. Everything the years did, each for a reason:
  copper oxidised (most where the water came in), the blast's cracks along the ceilings and the beams that carried it, a
  cave that broke into the commons and the azalea in the commons' planter that grew into a tree on its water, moss and
  roots creeping out from it, a ceiling that came down in the stasis hall, lights that died, glass that broke, cobwebs in
  corners nobody cleaned, the doctor who could not climb out of the sealed lab, and the blood trail from Pod 14 to Gray's
  cell. Cells code reads (pods, logs, chests, signs, survivor spots) are never aged.

The room boxes and their anchors are the `stasis_*` rooms in `story/bunker/BunkerLayout.java`; the two must agree.
Generated files: change this script, not the blueprints.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__))))
from blueprint_draft import StateGrid, painting_anchor  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data", "heroesevolved", "blueprints")

AIR = "."


def noise(*v):
    """A deterministic hash in 0..2^31: the same bunker every time the script runs."""
    h = 0x2545F491
    for i in v:
        h ^= (i * 0x9E3779B9 + (h << 6) + (h >> 2)) & 0xFFFFFFFF
        h = (h * 0x85EBCA6B) & 0xFFFFFFFF
        h ^= h >> 13
    return h & 0x7FFFFFFF


def chance(p, *v):
    return noise(*v) % 10000 < p * 10000


class World:
    """Block states by bunker cell. Missing cells are left as they are (rock, or whatever the world has there)."""

    def __init__(self):
        self.c = {}
        self.owner = {}
        self.room = None
        self.protected = set()

    def set(self, x, y, z, s):
        self.c[(x, y, z)] = s
        if self.room:
            self.owner[(x, y, z)] = self.room
        if "@" in s:
            self.protected.add((x, y, z))
        return self

    def get(self, x, y, z, default=None):
        return self.c.get((x, y, z), default)

    def box(self, x0, y0, z0, x1, y1, z1, s):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for x in range(min(x0, x1), max(x0, x1) + 1):
                    self.set(x, y, z, s)
        return self

    def shell(self, x0, y0, z0, x1, y1, z1, wall, floor, ceiling):
        """A closed room: walls, floor at y0, ceiling at y1, air inside."""
        self.box(x0, y0, z0, x1, y1, z1, wall)
        self.box(x0, y0, z0, x1, y0, z1, floor)
        self.box(x0, y1, z0, x1, y1, z1, ceiling)
        self.box(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, AIR)
        return self

    def is_air(self, x, y, z):
        return self.c.get((x, y, z)) == AIR

    def solid(self, x, y, z):
        s = self.c.get((x, y, z))
        return s is not None and s != AIR and block(s) in FULL


def block(s):
    """The block id of a state string, without properties or marker."""
    return s.split("@")[0].strip().split("[")[0]


def props(s):
    s = s.split("@")[0].strip()
    if "[" not in s:
        return {}
    return dict(kv.split("=") for kv in s[s.index("[") + 1:-1].split(","))


def with_props(s, **kw):
    base = block(s)
    p = props(s)
    p.update({k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in kw.items()})
    marker = s.split("@")[1].strip() if "@" in s else None
    out = base + ("[" + ",".join(f"{k}={v}" for k, v in p.items()) + "]" if p else "")
    return out + (f" @{marker}" if marker else "")


# ----------------------------------------------------------------------------------------------------------------------
# Palette (Primatech bunker): tiles walls, polished deepslate frames and floors, brick ceilings, tuff accents, and new
# copper as the one accent colour (it turns green with the years).
# ----------------------------------------------------------------------------------------------------------------------
WALL = "deepslate_tiles"
FRAME = "polished_deepslate"
CEIL = "deepslate_bricks"
FLOOR = "polished_deepslate"
RUNNER = "polished_tuff"
TRIM = "tuff_bricks"
CHISEL = "chiseled_tuff_bricks"
PANEL = "polished_tuff"
CU = "waxed_copper_block"
CUT = "waxed_cut_copper"
CUT_TOP = "waxed_cut_copper_slab[type=top]"
CUT_BOTTOM = "waxed_cut_copper_slab[type=bottom]"
GRATE = "waxed_copper_grate"
BULB = "waxed_copper_bulb[lit=true,powered=false]"
LAMP = "pearlescent_froglight"
WARM = "ochre_froglight"
BARS = "iron_bars"
CHAIN = "chain"
SCREEN = "black_stained_glass"
PANE = "light_gray_stained_glass_pane"
FROST = "light_blue_stained_glass_pane"

FULL = {WALL, FRAME, CEIL, FLOOR, RUNNER, TRIM, CHISEL, PANEL, CU, CUT, GRATE, LAMP, WARM, "deepslate_bricks",
        "cracked_deepslate_tiles", "cracked_deepslate_bricks", "polished_blackstone_bricks", "chiseled_polished_blackstone",
        "tuff", "deepslate", "cobbled_deepslate", "smooth_stone", "white_concrete", "light_gray_concrete", "gray_concrete",
        "black_concrete", "yellow_concrete", "iron_block", "barrel", "smooth_quartz", "sea_lantern", "glowstone",
        "waxed_copper_bulb", "chiseled_deepslate", "polished_deepslate", "polished_tuff", "tuff_bricks", "stone_bricks",
        "moss_block", "rooted_dirt", "dirt", "clay", "calcite", "dripstone_block", "black_stained_glass", "tinted_glass",
        "white_stained_glass", "glass", "smooth_basalt", "polished_basalt", "blue_ice", "packed_ice", "bookshelf",
        "spruce_planks", "dark_oak_planks", "stripped_dark_oak_log", "white_wool", "light_gray_wool", "quartz_block",
        "cut_copper", "waxed_cut_copper", "waxed_copper_grate", "lodestone", "smithing_table", "crafting_table",
        "cartography_table", "fletching_table", "loom", "blast_furnace", "smoker", "furnace", "chiseled_tuff",
        "chiseled_tuff_bricks", "mud_bricks", "packed_mud", "coarse_dirt", "gravel", "stone", "andesite", "cobblestone",
        "mossy_cobblestone", "blackstone", "coal_block", "basalt", "redstone_lamp", "target", "note_block", "jukebox",
        "dropper", "dispenser", "observer", "lectern", "deepslate_tiles"}

W = World()
SIGNS = {}


PRESENT_SIGNS = {}


def sign(sid, text, color="white", glow=True, present=None):
    """A sign's words; `present` replaces them in the present-day blueprint."""
    SIGNS[sid] = (text, color, glow)
    if present:
        PRESENT_SIGNS[sid] = (present, color, glow)
    return sid


def wall_sign(facing, sid, wood="spruce"):
    return f"{wood}_wall_sign[facing={facing}] @sign:{sid}"


# --- small builders -------------------------------------------------------------------------------------------------

OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
STEP = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


def light_box(x, y, z, top, lamp=LAMP, shade="waxed_copper_trapdoor"):
    """A hanging light box: a lamp on a chain from the ceiling at `top`, shaded on four sides by open trapdoors."""
    for cy in range(y + 1, top):
        W.set(x, cy, z, CHAIN)
    W.set(x, y, z, lamp)
    for facing, (dx, dz) in STEP.items():
        # Each shade stands against the lamp: its panel lies on the edge opposite its facing, so it faces away.
        W.set(x + dx, y, z + dz, f"{shade}[facing={facing},open=true,half=top]")


def chair(x, y, z, back, wood="spruce", arms=True):
    """A stair seat whose back is to `back` (the sitter faces away), with trapdoor arms."""
    W.set(x, y, z, f"{wood}_stairs[facing={back}]")
    if arms:
        side = {"north": ("west", "east"), "south": ("east", "west"), "east": ("north", "south"), "west": ("south", "north")}[back]
        for f in side:
            dx, dz = STEP[f]
            if W.get(x + dx, y, z + dz) in (None, AIR):
                W.set(x + dx, y, z + dz, f"{wood}_trapdoor[facing={f},open=true]")


def desk(x0, z0, x1, z1, y=1, top="polished_deepslate_slab[type=top]", base="polished_deepslate"):
    """A desk: a solid base under a top slab is a counter; here a plinth with an overhanging top."""
    W.box(x0, y, z0, x1, y, z1, top)


def strongbox(facing, marker):
    """A chest holding a log (its @log:<id> marker puts the book in it)."""
    return f"chest[facing={facing},type=single,waterlogged=false] @{marker}"


def stairs(s, facing, half="bottom", shape=None):
    st = f"{s}[facing={facing},half={half}"
    if shape:
        st += f",shape={shape}"
    return st + "]"


# ======================================================================================================================
# THE STASIS HALL  (x -10..10, z 32..70, y 0..13)
# ======================================================================================================================
HALL_X = 10            # outer walls at x = -10 and 10
HALL_Z0, HALL_Z1 = 32, 70
HALL_TOP = 12          # ceiling layer (the bunker stays 13 high: the Last Day's stairwell meets it at a fixed depth)
CAT_Y = 6              # catwalk floor layer (top slabs: walked on at y 7)
PODS = [38 + 4 * i for i in range(7)]          # each wall's pods, north to south
PILASTERS = [36 + 4 * i for i in range(8)]     # between and either side of them
LECTERN = (0, 1, 52)                           # the orientation log, in the aisle
STAIR_Z0 = 61                                  # the grand stair climbs south from here to the far balcony
BALCONY = (67, 69)                             # the far balcony's rows
VENT_Z = 46                                    # where the sealed lab's duct comes out on the east catwalk
CLAIRE_LECTERN = (7, 1, 63)                    # put there by code (BunkerBuilder.releaseClaire), kept clear here


BRACED = []


def hall():
    x0, x1, z0, z1 = -HALL_X, HALL_X, HALL_Z0, HALL_Z1
    W.shell(x0, 0, z0, x1, HALL_TOP + 1, z1, WALL, FLOOR, CEIL)
    W.box(x0 + 1, HALL_TOP, z0 + 1, x1 - 1, HALL_TOP, z1 - 1, CEIL)
    # Floor: polished deepslate, a tuff runner down the aisle with tile borders, bands of tiles under every pilaster line,
    # and a copper grate drain along each side where the pods' frost ran off.
    W.box(-1, 0, z0 + 1, 1, 0, z1 - 1, RUNNER)
    for z in range(z0 + 1, z1):
        W.set(-2, 0, z, WALL)
        W.set(2, 0, z, WALL)
        W.set(-6, 0, z, GRATE)
        W.set(6, 0, z, GRATE)
    for pz in PILASTERS:
        for x in range(-7, 8):
            if abs(x) not in (1, 0, 6):
                W.set(x, 0, pz, WALL)
        W.set(0, 0, pz, CHISEL)
    # The side walls: a niche for every pod, a pilaster between niches, a catwalk above.
    for side in (-1, 1):
        sx = lambda d: side * d  # noqa: E731  (distance from the axis, signed to this side)
        face = "east" if side < 0 else "west"       # the way things on this wall stick out into the hall
        for z in range(z0 + 1, z1):
            # Lower wall: the niche band (x = +-9 back, +-8 the pod's line) is carved per bay below.
            W.box(sx(9), 1, z, sx(8), 5, z, WALL)
        for pz in PILASTERS:
            # Pilaster: polished frame up to the catwalk, its nose a slim wall column, a bracket under the catwalk.
            W.box(sx(9), 1, pz, sx(8), 5, pz, FRAME)
            W.set(sx(7), 1, pz, FRAME)
            for y in range(2, 5):
                W.set(sx(7), y, pz, "polished_deepslate_wall")
            W.set(sx(7), 5, pz, stairs("polished_deepslate_stairs", OPP[face], "top"))
            W.set(sx(6), 5, pz, stairs("polished_deepslate_stairs", OPP[face], "top"))
            BRACED.extend([(sx(7), pz), (sx(6), pz)])
            # Above the catwalk the pilaster runs on to the ceiling as a rib.
            W.box(sx(9), 7, pz, sx(9), HALL_TOP - 1, pz, FRAME)
        for i, pz in enumerate(PODS):
            index = i + (0 if side < 0 else 7)
            # The niche: 3 wide, 4 high; the pod in the middle, a copper pipe either side, a lamp over the nameplate.
            W.box(sx(8), 1, pz - 1, sx(8), 4, pz + 1, AIR)
            W.box(sx(9), 1, pz - 1, sx(9), 4, pz + 1, PANEL)
            W.set(sx(9), 1, pz, GRATE)
            W.set(sx(9), 2, pz, GRATE)
            W.set(sx(9), 3, pz, FRAME)
            W.set(sx(9), 4, pz, LAMP)
            W.set(sx(9), 0, pz, FLOOR)
            for dz in (-1, 1):
                W.set(sx(8), 1, pz + dz, CUT)
                for y in (2, 3, 4):
                    W.set(sx(8), y, pz + dz, "lightning_rod[facing=up]")
                W.set(sx(9), 3, pz + dz, CUT)
                W.set(sx(9), 4, pz + dz, CUT)
            W.box(sx(9), 5, pz - 1, sx(8), 5, pz + 1, FRAME)
            W.set(sx(8), 5, pz, CUT)
            pod = f"heroesevolved:cryochamber[facing={face},half=lower,open=false] @pod"
            W.set(sx(8), 1, pz, pod)
            W.set(sx(8), 2, pz, f"heroesevolved:cryochamber[facing={face},half=upper,open=false]")
            W.set(sx(8), 3, pz, wall_sign(face, sign(f"pod{index + 1:02d}", f"SUBJECT {index + 1:02d} | STASIS SEALED | AWAITING | REVIVAL", "light_blue")))
            W.set(sx(8), 0, pz, FLOOR)
            # A step of grate in front of the pod, where the sleeper comes out.
            W.set(sx(7), 0, pz, GRATE)
        # The catwalk: top slabs from the wall out to the railing, iron bars on its edge.
        for z in range(z0 + 1, z1):
            for d in (9, 8, 7, 6):
                W.set(sx(d), CAT_Y, z, "polished_deepslate_slab[type=top]")
            W.set(sx(6), CAT_Y, z, CUT_TOP)
            if z0 + 3 <= z <= BALCONY[0]:
                W.set(sx(6), CAT_Y + 1, z, BARS)
            W.box(sx(9), CAT_Y + 1, z, sx(7), CAT_Y + 3, z, AIR)
            # Upper wall: tiles, a tuff band at eye height, a lamp in each panel.
            W.box(sx(10), CAT_Y, z, sx(10), HALL_TOP, z, WALL)
            W.set(sx(10), CAT_Y + 2, z, TRIM)
        for pz in PILASTERS:
            W.box(sx(9), CAT_Y + 1, pz, sx(9), HALL_TOP - 1, pz, FRAME)
        # Where a bracket holds the catwalk, the catwalk is a full block on it (a top slab would float half a block up).
        for bx, bz in BRACED:
            W.set(bx, CAT_Y, bz, CUT if abs(bx) == 6 else FRAME)
        for pz in PODS:
            W.set(sx(10), CAT_Y + 4, pz, BULB)
            W.set(sx(10), CAT_Y + 4, pz - 1, CUT)
            W.set(sx(10), CAT_Y + 4, pz + 1, CUT)
    # Ceiling: a rib across the hall over every pilaster, two copper ducts along it, and a light box in every bay.
    for z in range(z0 + 1, z1):
        for dx in (-4, 4):
            W.set(dx, HALL_TOP - 2, z, CUT)
    for pz in PILASTERS:
        W.box(-9, HALL_TOP - 1, pz, 9, HALL_TOP - 1, pz, FRAME)
        W.box(-2, HALL_TOP - 1, pz, 2, HALL_TOP - 1, pz, CEIL)
        W.set(0, HALL_TOP - 1, pz, CHISEL)
        W.set(-8, HALL_TOP - 2, pz, stairs("polished_deepslate_stairs", "west", "top"))
        W.set(8, HALL_TOP - 2, pz, stairs("polished_deepslate_stairs", "east", "top"))
    for pz in PODS:
        light_box(0, 9, pz, HALL_TOP)
        for dx in (-4, 4):
            W.set(dx, HALL_TOP - 2, pz, GRATE)
    # The north end: the doors to the commons (5 wide below, 3 wide on the catwalk level) and a cross-catwalk joining
    # both sides to the commons' gallery.
    W.box(-2, 1, z0, 2, 4, z0, AIR)
    W.box(-3, 1, z0, -3, 4, z0, FRAME)
    W.box(3, 1, z0, 3, 4, z0, FRAME)
    W.box(-3, 5, z0, 3, 5, z0, FRAME)
    for x in range(-2, 3):
        W.set(x, 5, z0, CUT if x else CHISEL)
    W.box(-1, CAT_Y + 1, z0, 1, CAT_Y + 3, z0, AIR)
    for z in (z0 + 1, z0 + 2, z0 + 3):
        for x in range(-8, 9):
            W.set(x, CAT_Y, z, "polished_deepslate_slab[type=top]")
            W.box(x, CAT_Y + 1, z, x, CAT_Y + 3, z, AIR)
    for x in range(-6, 7):
        W.set(x, CAT_Y, z0 + 3, CUT_TOP)
        W.set(x, CAT_Y + 1, z0 + 3, BARS)
    # Brackets under the cross-catwalk where it meets the wall.
    for x in (-4, 4):
        W.set(x, CAT_Y - 1, z0 + 1, stairs("polished_deepslate_stairs", "north", "top"))
        W.set(x, CAT_Y, z0 + 1, FRAME)
    # The far end: Exhibit 1 high on the far wall over a balcony, the analysts' station under the balcony, and a grand
    # stair climbing the aisle to it.
    for z in range(BALCONY[0], BALCONY[1] + 1):
        for x in range(-9, 10):
            W.set(x, CAT_Y, z, "polished_deepslate_slab[type=top]")
            W.box(x, CAT_Y + 1, z, x, CAT_Y + 3, z, AIR)
    for x in range(-6, 7):
        if abs(x) > 1:
            W.set(x, CAT_Y, BALCONY[0], CUT_TOP)
            W.set(x, CAT_Y + 1, BALCONY[0], BARS)
    for x in (-5, 5):
        W.set(x, CAT_Y - 1, BALCONY[0], stairs("polished_deepslate_stairs", "south", "top"))
        W.set(x, CAT_Y, BALCONY[0], CUT)
    # The stair: six steps up the aisle, three wide, with a copper string course down each side.
    for k in range(6):
        z = STAIR_Z0 + k
        for x in (-1, 0, 1):
            W.set(x, k + 1, z, stairs("polished_deepslate_stairs", "south"))
            for y in range(1, k + 1):
                W.set(x, y, z, FRAME)
        for x in (-2, 2):
            for y in range(1, k + 2):
                W.set(x, y, z, FRAME if y <= k else CUT_BOTTOM)
            W.set(x, k + 2, z, BARS) if k < 5 else None
    # Under the balcony: the analysts' station against the far wall, its screens dead or alive.
    zf = z1 - 1
    for x in range(-7, 8):
        if abs(x) <= 1:
            continue
        W.set(x, 1, zf, FRAME)
        W.set(x, 2, zf, SCREEN if x % 2 else "gray_stained_glass")
        W.set(x, 3, zf, SCREEN)
        W.set(x, 1, zf - 1, "polished_deepslate_slab[type=top]")
        W.set(x, 2, zf - 1, f"polished_blackstone_button[face=floor,facing=north]") if x % 3 == 0 else None
    for x in (-6, -4, 4, 6):
        chair(x, 1, zf - 2, "north", arms=False)
    for x in (-1, 0, 1):
        W.set(x, 1, zf, FRAME)
        W.set(x, 2, zf, FRAME)
        W.set(x, 3, zf, FRAME)
    W.set(0, 4, zf, wall_sign("north", sign("wing", "STASIS WING | SUBLEVEL 4 | ODESSA SITE | PRIMATECH")))
    W.box(-7, 4, zf, 7, 5, zf, WALL)
    W.set(0, 4, zf, wall_sign("north", "wing"))
    for x in (-1, 1):
        W.set(x, 4, zf, LAMP)
    W.set(0, 5, zf, CHISEL)
    for x in range(-7, 8):
        W.set(x, 5, zf, CUT if abs(x) > 1 else CHISEL)
    # Exhibit 1: Isaac's painting of the explosion, 6 by 4, on the far wall above the balcony (anchor from the
    # bottom-left as seen from the hall: x 2 .. -3 seen from the north means the painting's left is east).
    ax, ay, az = painting_anchor("north", (2, zf), CAT_Y + 1, 6, 4)
    W.set(ax, ay, az, "air @painting:explosion:north")
    W.set(3, CAT_Y + 2, zf, wall_sign("north", sign("exhibit1", "EXHIBIT 1 | I. MENDEZ | ODESSA | RECOVERED ART", "yellow")))
    # Lamps at the catwalks' corners, where the wall bulbs don't reach once the copper dims them.
    for sx_ in (-HALL_X, HALL_X):
        for z in (34, 66):
            W.set(sx_, CAT_Y + 3, z, LAMP)
    for x in (-8, 8):
        W.set(x, CAT_Y + 3, z1, LAMP)
    # The way in from the sealed lab: a vent through the east upper wall onto the catwalk.
    W.box(HALL_X - 1, CAT_Y + 1, VENT_Z, HALL_X, CAT_Y + 2, VENT_Z, AIR)
    W.set(HALL_X, CAT_Y + 1, VENT_Z, CUT_BOTTOM)
    W.set(HALL_X - 1, CAT_Y + 3, VENT_Z, wall_sign("west", sign("vent", "VENT 4-R | MAINTENANCE | ACCESS | ")))
    W.set(HALL_X, CAT_Y + 3, VENT_Z, FRAME)
    # The orientation log, in the aisle.
    W.set(*LECTERN, strongbox("north", "log:orientation"))
    # Alarm beacons: one over the door, two on the ribs halfway along, one over the far balcony.
    W.set(0, 5, z0 + 1, "heroesevolved:alarm_beacon[facing=south,lit=true] @beacon")
    for x in (-6, 6):
        W.set(x, HALL_TOP - 1, 48, "heroesevolved:alarm_beacon[facing=down,lit=true] @beacon")
    W.set(0, HALL_TOP - 1, 66, "heroesevolved:alarm_beacon[facing=down,lit=true] @beacon")


# ======================================================================================================================
# THE COMMONS  (a chamfered rotunda, x -11..11, z 10..32, y 0..13)
# ======================================================================================================================
HUB_Z = 21
HUB_TOP = 13
PLANTER = (-6, 15)          # the azalea's planter in the lockdown; the tree's root in the present
HUB_COLUMNS = [(x, 13) for x in (-7, -3, 3, 7)] + [(x, 29) for x in (-7, -3, 3, 7)] + \
              [(8, z) for z in (14, 18)] + [(-8, z) for z in (14, 18)]
HUB_SATELLITES = [(4, 0), (-4, 0), (0, 4), (0, -4), (3, 3), (3, -3), (-3, 3), (-3, -3)]


def ring(x, z):
    """How far a cell is inside the commons' wall: 0 is the wall, negative is outside."""
    ax, dz = abs(x), abs(z - HUB_Z)
    return min(11 - ax, 11 - dz, 19 - ax - dz)


def hub():
    for x in range(-11, 12):
        for z in range(10, 33):
            r = ring(x, z)
            if r < 0:
                continue
            W.set(x, 0, z, FLOOR)
            if r == 0:
                W.box(x, 1, z, x, HUB_TOP, z, WALL)
                W.set(x, 5, z, TRIM)
                W.set(x, 10, z, TRIM)
                continue
            top = 11 if r <= 3 else 12 if r <= 6 else 13
            W.box(x, 1, z, x, top - 1, z, AIR)
            W.box(x, top, z, x, HUB_TOP, z, CEIL)
            if r == 3:
                W.set(x, 11, z, CUT)
            if r == 6:
                W.set(x, 12, z, CUT)
            if r <= 3:
                W.set(x, 6, z, CUT_TOP if r == 3 else "polished_deepslate_slab[type=top]")
                if r == 3:
                    W.set(x, 7, z, BARS)
            # The floor: a runner from the vault to the hall and across from wing to wing, a medallion at the middle.
            if abs(x) <= 1 or 20 <= z <= 22:
                W.set(x, 0, z, RUNNER)
            m = max(abs(x), abs(z - HUB_Z))
            if m <= 3:
                W.set(x, 0, z, TRIM if m == 3 else CUT if m == 2 else CHISEL if m == 0 else PANEL)
    # The colonnade under the gallery, copper-capped.
    for cx, cz in HUB_COLUMNS:
        W.box(cx, 1, cz, cx, 4, cz, FRAME)
        W.set(cx, 5, cz, CUT)
        W.set(cx, 6, cz, CUT)
    # Doors: north to the airlock, east and west to the wings (the hall draws its own doors in the south face).
    W.box(-1, 1, 10, 1, 3, 10, AIR)
    W.box(-11, 1, 20, -11, 3, 22, AIR)
    W.box(11, 1, 20, 11, 3, 22, AIR)
    for x, z0, z1 in ((-11, 19, 23), (11, 19, 23)):
        W.box(x, 1, z0, x, 4, z0, FRAME)
        W.box(x, 1, z1, x, 4, z1, FRAME)
        W.box(x, 4, z0, x, 4, z1, CUT)
    W.box(-2, 1, 10, -2, 4, 10, FRAME)
    W.box(2, 1, 10, 2, 4, 10, FRAME)
    W.box(-2, 4, 10, 2, 4, 10, CUT)
    # Stairs up to the gallery, one each side, climbing south from the wing doors.
    for side in (-1, 1):
        for k in range(1, 7):
            z = 22 + k
            for d in (10, 9):
                x = side * d
                for y in range(1, k):
                    W.set(x, y, z, FRAME)
                W.set(x, k, z, stairs("polished_deepslate_stairs", "south"))
                for y in range(k + 1, k + 4):
                    W.set(x, y, z, AIR)
            x = side * 8
            for y in range(1, k + 1):
                W.set(x, y, z, FRAME)
            W.set(x, k + 1, z, BARS)
            for y in range(k + 2, 8):
                W.set(x, y, z, AIR)
        W.set(side * 8, 7, 28, BARS)
    # Wall lamps: built into the wall under the gallery, copper bulbs above it.
    for x, z in ((-5, 10), (5, 10), (-5, 32), (5, 32), (-11, 16), (-11, 26), (11, 16), (11, 26)):
        W.set(x, 3, z, LAMP)
        W.set(x, 2, z, FRAME)
        W.set(x, 4, z, FRAME)
    for x, z in ((-4, 10), (4, 10), (-4, 32), (4, 32), (-11, 17), (-11, 25), (11, 17), (11, 25)):
        W.set(x, 9, z, BULB)
    # The chandelier: a warm lamp at the middle, eight light boxes round it.
    light_box(0, 8, HUB_Z, HUB_TOP, lamp=WARM)
    W.set(0, 7, HUB_Z, GRATE)
    for dx, dz in HUB_SATELLITES:
        light_box(dx, 9, HUB_Z + dz, HUB_TOP)
    # The serving line along the north face, east of the door: cabinets, a sink with a tap, two hot cabinets.
    for x, st in ((3, "smoker[facing=south]"), (4, "barrel[facing=south]"), (5, "water_cauldron[level=1]"),
                  (6, "barrel[facing=south]"), (7, "smoker[facing=south]")):
        W.set(x, 1, 11, st)
    W.set(5, 2, 11, "lever[face=wall,facing=south]")
    W.set(4, 2, 11, "brewing_stand")
    W.set(6, 2, 11, "flower_pot")
    W.set(5, 4, 11, wall_sign("south", sign("mess", "COMMONS | MESS 0600-2000 | CLEAN AS | YOU GO")))
    # Mess tables, east of the runner: smooth stone tops, spruce benches.
    for tz in (15, 18, 24, 27):
        for x in range(3, 8):
            W.set(x, 1, tz, "smooth_stone_slab[type=top]")
            W.set(x, 1, tz - 1, "spruce_stairs[facing=north]")
            W.set(x, 1, tz + 1, "spruce_stairs[facing=south]")
    # The reading lounge, south-west: two sofas facing over a low table, potted plants.
    for x in range(-6, -2):
        W.set(x, 1, 24, "spruce_stairs[facing=north]")
        W.set(x, 1, 28, "spruce_stairs[facing=south]")
        W.set(x, 1, 26, "spruce_slab[type=top]")
    for z in (24, 28):
        W.set(-7, 1, z, "spruce_trapdoor[facing=west,open=true]")
        W.set(-2, 1, z, "spruce_trapdoor[facing=east,open=true]")
    W.set(-5, 2, 26, "potted_fern")
    W.set(-3, 2, 26, "potted_azure_bluet")
    # The planter, north-west: an azalea in a copper-cornered bed, with seats facing it.
    px, pz = PLANTER
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            W.set(px + dx, 1, pz + dz, CUT if dx and dz else FRAME)
    W.set(px, 1, pz, "dirt")
    W.set(px, 2, pz, "flowering_azalea")
    W.set(px, 1, pz + 2, "spruce_stairs[facing=south]")
    W.set(px + 2, 1, pz, "spruce_stairs[facing=east]")
    # Wayfinding over each door, and the Company on the north face above the gallery.
    W.set(0, 5, 11, wall_sign("south", sign("to_vault", "NORTH | AIRLOCK | VAULT | SURFACE")))
    W.set(0, 5, 31, wall_sign("north", sign("to_hall", "SOUTH | STASIS HALL | SUBLEVEL 4 | ")))
    W.set(10, 4, 21, wall_sign("west", sign("to_east", "EAST | RECORDS | GENERATOR | 4-R")))
    W.set(-10, 4, 21, wall_sign("east", sign("to_west", "WEST | INFIRMARY | CONTAINMENT | ")))
    W.box(-2, 9, 10, 2, 9, 10, CUT)
    W.set(0, 8, 11, wall_sign("south", sign("commons", "PRIMATECH | ODESSA SITE | COMMONS | ", "yellow")))
    # Alarm beacons on the walls above the gallery, north and south.
    W.set(0, 10, 11, "heroesevolved:alarm_beacon[facing=south,lit=true] @beacon")
    W.set(0, 10, 31, "heroesevolved:alarm_beacon[facing=north,lit=true] @beacon")


# ======================================================================================================================
# THE AIRLOCK  (x -6..6, z 1..10, y 0..6): the vault's door, a decontamination arch, the security booth
# ======================================================================================================================
HATCH = (4, 0, 9)


def airlock():
    W.shell(-6, 0, 1, 6, 6, 10, WALL, FLOOR, CEIL)
    W.box(-1, 0, 2, 1, 0, 9, RUNNER)
    # The vault opening in the north wall, hazard-striped like the vault frame outside.
    W.box(-1, 1, 1, 1, 3, 1, AIR)
    for x in (-2, 2):
        for y in range(1, 4):
            W.set(x, y, 1, "yellow_concrete" if y % 2 else "black_concrete")
    W.box(-2, 4, 1, 2, 4, 1, "polished_blackstone_bricks")
    W.set(0, 4, 1, "chiseled_polished_blackstone")
    W.box(-1, 1, 10, 1, 3, 10, AIR)
    # The decontamination arch: posts, a copper spray bar with nozzles, a drain, glass to either side.
    for z in (4, 5):
        for x in (-3, 3):
            W.box(x, 1, z, x, 4, z, FRAME)
        W.box(-3, 5, z, 3, 5, z, CUT)
        for x in range(-2, 3):
            W.set(x, 4, z, "lightning_rod[facing=down]")
            W.set(x, 0, z, GRATE)
        for x in (-5, -4):
            if z == 4:
                W.box(x, 1, z, x, 3, z, PANE)
            W.set(x, 4, z, FRAME)
    W.set(-3, 3, 6, wall_sign("south", sign("decon", "DECONTAMINATION | STAND STILL | ARMS OUT | WAIT FOR GREEN")))
    # The security booth, east: glass front onto the lane, a door at its north end, a desk, the hatch.
    # The booth runs the whole east side, behind the arch's east post: a window onto the lane, the door at its south end.
    for z in range(4, 10):
        W.set(3, 1, z, FRAME)
        W.box(3, 2, z, 3, 3, z, PANE)
        W.set(3, 4, z, CUT)
        W.box(4, 1, z, 5, 4, z, AIR)
    W.box(3, 1, 8, 3, 4, 8, FRAME)
    W.set(3, 1, 9, "spruce_door[facing=east,half=lower,hinge=left,open=false]")
    W.set(3, 2, 9, "spruce_door[facing=east,half=upper,hinge=left,open=false]")
    W.set(3, 3, 9, FRAME)
    W.box(4, 5, 4, 5, 5, 9, CEIL)
    W.set(5, 1, 4, FRAME)
    W.set(5, 2, 4, "lever[face=floor,facing=west]")
    W.set(5, 1, 5, "chest[facing=west] @loot:security")
    chair(4, 1, 4, "west", arms=False)
    W.set(*HATCH, "spruce_trapdoor[facing=north,half=top,open=false]")
    W.set(4, 2, 9, wall_sign("north", sign("maintenance_hatch", "MAINTENANCE | ACCESS HATCH | DOWN | ")))
    W.set(2, 4, 8, wall_sign("west", sign("checkpoint", "CHECKPOINT | SHOW YOUR | BADGE | ")))
    # Emergency lockers, west: barrels two high under a slab, a bench in front.
    for z in range(6, 10):
        W.set(-5, 1, z, "barrel[facing=east]")
        W.set(-5, 2, z, "barrel[facing=east]")
        W.set(-5, 3, z, "polished_deepslate_slab[type=bottom]")
    for z in (7, 8):
        W.set(-3, 1, z, "spruce_stairs[facing=west]")
    W.set(-5, 4, 8, wall_sign("east", sign("lockers", "EMERGENCY | GEAR | TAKE WHAT | YOU NEED")))
    # Lights: two strips in the ceiling, and a beacon over the vault door.
    for z in (3, 8):
        for x in range(-2, 3):
            W.set(x, 6, z, LAMP if x % 2 == 0 else CUT)
    W.set(0, 5, 2, "heroesevolved:alarm_beacon[facing=south,lit=true] @beacon")


# ======================================================================================================================
# MAINTENANCE  (under the security booth, x -2..8, z 1..10, y -8..-1): the hatch's secret
# ======================================================================================================================
def maintenance():
    W.shell(-2, -8, 1, 8, -1, 10, CEIL, FLOOR, CEIL)
    hx, _, hz = HATCH
    for y in range(-7, 0):
        W.set(hx, y, hz, "ladder[facing=north]")
    # Workbench, map desk, a cot, storage, and Bennet's own notes on the room nobody else was told about.
    W.set(6, -7, 2, "crafting_table")
    W.set(7, -7, 2, "smithing_table")
    W.set(5, -7, 2, "barrel[facing=up]")
    W.set(7, -7, 3, "barrel[facing=west]")
    W.set(7, -6, 3, "barrel[facing=west]")
    W.set(0, -7, 2, "spruce_slab[type=top]")
    W.set(1, -7, 2, "cartography_table")
    W.set(2, -7, 2, "spruce_slab[type=top]")
    W.set(0, -6, 2, "white_carpet")
    W.set(2, -6, 2, "white_carpet")
    chair(1, -7, 3, "south", arms=False)
    W.set(-1, -7, 7, "red_bed[facing=north,part=head]")
    W.set(-1, -7, 8, "red_bed[facing=north,part=foot]")
    W.set(-1, -7, 5, "barrel[facing=up]")
    W.set(2, -7, 8, strongbox("east", "log:bennet4r"))
    W.set(3, -5, 2, wall_sign("south", sign("office", "MAINTENANCE | WORKBENCH | SPARE STORAGE | ")))
    for x in (1, 6):
        W.set(x, -1, 6, LAMP)
    W.set(3, -1, 3, BULB)


# ======================================================================================================================
# RECORDS AND COMMS  (x 11..21, z 7..19, y 0..6)
# ======================================================================================================================
def records():
    W.shell(11, 0, 7, 21, 6, 19, WALL, FLOOR, CEIL)
    W.box(15, 1, 19, 17, 3, 19, AIR)
    W.box(14, 1, 19, 14, 4, 19, FRAME)
    W.box(18, 1, 19, 18, 4, 19, FRAME)
    W.box(14, 4, 19, 18, 4, 19, CUT)
    W.box(15, 0, 8, 17, 0, 18, RUNNER)
    # The radio console against the north wall: Simple Radio's receiver with the transmitter on it, a speaker and a
    # microphone either side, the operator's chair in front.
    for x in range(13, 20):
        W.set(x, 1, 8, "polished_deepslate_slab[type=top]")
    W.set(16, 1, 8, "simpleradio:receiver[facing=south] @signal:receiver")
    W.set(14, 2, 8, "simpleradio:speaker[facing=south] @signal:speaker")
    W.set(18, 2, 8, "simpleradio:microphone[rotation=0] @signal:microphone")
    W.set(16, 3, 8, wall_sign("south", sign("receiver", "SIGNAL RECEIVER | USE CONSOLE | SURFACE RELAY | ", "light_blue")))
    W.box(13, 4, 8, 19, 4, 8, CUT)
    chair(16, 1, 10, "south")
    # Filing cabinets down the west wall, the records chest among them.
    for z in range(9, 18):
        W.set(12, 1, z, "barrel[facing=east]")
        W.set(12, 2, z, "barrel[facing=east]")
        W.set(12, 3, z, "polished_deepslate_slab[type=bottom]")
    W.set(12, 1, 13, "chest[facing=east] @loot:records")
    W.set(12, 2, 13, AIR)
    W.set(12, 3, 13, AIR)
    W.set(12, 4, 13, wall_sign("east", sign("files", "RECORDS | ODESSA LIST | SUBLEVEL 4 | ")))
    # The planning table in the middle, papers on it.
    for x in range(15, 18):
        for z in (13, 14):
            W.set(x, 1, z, "spruce_slab[type=top]")
            W.set(x, 2, z, "white_carpet" if (x + z) % 2 else AIR)
    chair(16, 1, 12, "north", arms=False)
    chair(16, 1, 15, "south", arms=False)
    # Bennet's corner, south-east: his desk, his lockdown order, his family on the wall.
    for z in (15, 16):
        W.set(20, 1, z, "dark_oak_slab[type=top]")
    W.set(20, 2, 15, "flower_pot")
    chair(19, 1, 16, "west", wood="dark_oak", arms=False)
    W.set(19, 1, 18, strongbox("north", "log:lockdown"))
    ax, ay, az = painting_anchor("west", (20, 17), 2, 2, 2)
    W.set(ax, ay, az, "air @painting:bennets:west")
    W.set(20, 4, 15, wall_sign("west", sign("bennet", "N. BENNET | SITE LIAISON | DO NOT DISTURB | ")))
    # A window into the generator hall, so the operator can see the core.
    for z in range(11, 15):
        W.box(21, 2, z, 21, 3, z, "gray_stained_glass_pane")
    light_box(14, 4, 13, 6)
    light_box(18, 4, 12, 6)
    W.set(12, 5, 10, "heroesevolved:alarm_beacon[facing=east,lit=true] @beacon")


# ======================================================================================================================
# THE EAST CORRIDOR  (x 11..21, z 19..23, y 0..5): records, the generator, and the blast door to 4-R
# ======================================================================================================================
BLAST = (14, 16)           # the blast door's columns in the corridor's south wall


def east_corridor():
    W.shell(11, 0, 19, 21, 5, 23, WALL, FLOOR, CEIL)
    W.box(11, 1, 20, 11, 3, 22, AIR)
    W.box(21, 1, 20, 21, 3, 22, AIR)
    W.box(15, 1, 19, 17, 3, 19, AIR)
    W.box(12, 0, 21, 20, 0, 21, RUNNER)
    for x in range(12, 21):
        W.set(x, 4, 20, CUT)
    for x in (13, 19):
        W.set(x, 5, 21, LAMP)
    # The blast door: hazard-striped jambs, a black lintel, a solid steel leaf with its wheel.
    b0, b1 = BLAST
    for x in (b0 - 1, b1 + 1):
        for y in range(1, 4):
            W.set(x, y, 23, "yellow_concrete" if (x + y) % 2 else "black_concrete")
    W.box(b0 - 1, 4, 23, b1 + 1, 4, 23, "polished_blackstone_bricks")
    W.set((b0 + b1) // 2, 4, 23, "chiseled_polished_blackstone")
    W.box(b0, 1, 23, b1, 3, 23, "iron_block")
    W.set((b0 + b1) // 2, 2, 22, "grindstone[face=wall,facing=north]")
    W.set(b1 + 2, 3, 22, wall_sign("north", sign("helix_door", "SUBLEVEL 4-R | HELIX WARD | RESTRICTED | ", "red")))
    W.set(b1 + 3, 4, 22, "heroesevolved:alarm_beacon[facing=north,lit=true] @beacon")


# ======================================================================================================================
# THE GENERATOR HALL  (x 21..39, z 9..29, y 0..12)
# ======================================================================================================================
CORE = (30, 19)
CELLS = [(x, 10) for x in range(24, 37, 2)] + [(x, 28) for x in range(24, 37, 2)]
LIVE_CELLS = {0, 9, 13}             # the three of fourteen still holding charge in the present
BLOWN_CELL = 3


def generator():
    W.shell(21, 0, 9, 39, 12, 29, WALL, FLOOR, CEIL)
    W.box(21, 1, 20, 21, 3, 22, AIR)
    cx, cz = CORE
    # The core: a column of light behind glass, copper ribs at its corners, a plinth, a cap and feeds to the ceiling.
    for dx in (-2, -1, 0, 1, 2):
        for dz in (-2, -1, 0, 1, 2):
            if abs(dx) + abs(dz) <= 3:
                W.set(cx + dx, 0, cz + dz, CHISEL if abs(dx) + abs(dz) == 3 else FRAME)
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            W.set(cx + dx, 1, cz + dz, FRAME)
            W.set(cx + dx, 9, cz + dz, CUT)
            for y in range(2, 9):
                W.set(cx + dx, y, cz + dz, "sea_lantern" if dx == 0 and dz == 0 else
                      "light_blue_stained_glass" if dx == 0 or dz == 0 else CU)
    for dx, dz in ((-2, 0), (2, 0), (0, -2), (0, 2)):
        W.set(cx + dx, 1, cz + dz, "polished_deepslate_slab[type=bottom]")
    for y in (10, 11):
        W.set(cx, y, cz, CHAIN)
        for dx, dz in ((-1, -1), (1, 1), (-1, 1), (1, -1)):
            W.set(cx + dx, y, cz + dz, "lightning_rod[facing=up]")
    # Feeds from the cap to the four walls, under the ceiling.
    for x in range(22, 39):
        if abs(x - cx) > 1:
            W.set(x, 10, cz, CUT)
    for z in range(10, 29):
        if abs(z - cz) > 1:
            W.set(cx, 10, z, CUT)
    # Fourteen power cells along the north and south walls, each a copper case with a charge lamp.
    for i, (x, z) in enumerate(CELLS):
        W.set(x, 1, z, CU)
        W.set(x, 2, z, BULB)
        W.set(x, 3, z, CUT)
        W.set(x, 4, z, "lightning_rod[facing=up]")
        W.set(x - 1, 1, z, FRAME)
        W.set(x + 1, 1, z, FRAME)
    W.set(31, 3, 11, wall_sign("south", sign("cells", "POWER CELLS | 14 OF 14 | NOMINAL | ", "green",
                                         present="POWER CELLS | 3 OF 14 | REMAINING | ")))
    W.set(31, 2, 10, FRAME)
    W.set(31, 3, 10, FRAME)
    # A catwalk round three walls above the cells, its stair climbing north from the door; the main breaker south of
    # the door.
    for x in range(22, 39):
        for z in range(10, 29):
            if not (x >= 37 or z <= 11 or z >= 27):
                continue
            W.set(x, 6, z, "polished_deepslate_slab[type=top]")
            if (x == 37 and 12 <= z <= 26) or (z == 11 and x <= 37) or (z == 27 and x <= 37):
                W.set(x, 6, z, CUT_TOP)
                W.set(x, 7, z, BARS)
    for k in range(1, 7):
        z = 19 - k
        for x in (22, 23):
            for y in range(1, k):
                W.set(x, y, z, FRAME)
            W.set(x, k, z, stairs("polished_deepslate_stairs", "north"))
            for y in range(k + 1, k + 4):
                W.set(x, y, z, AIR)
        W.set(24, k, z, FRAME)
        W.set(24, k + 1, z, BARS)
    W.box(22, 6, 11, 23, 6, 12, "polished_deepslate_slab[type=top]")
    W.box(22, 7, 11, 23, 7, 12, AIR)
    for z in (24, 25, 26):
        for y in (2, 3):
            W.set(22, y, z, "lever[face=wall,facing=east,powered=false]")
    W.set(22, 4, 25, wall_sign("east", sign("breaker", "MAIN BREAKER | SUBLEVEL 4 | DO NOT | OPERATE", "red")))
    for x, z in ((26, 15), (34, 15), (26, 24), (34, 24)):
        light_box(x, 8, z, 12)
    for z in (11, 27):
        W.set(39, 3, z, LAMP)
    W.set(38, 9, 19, "heroesevolved:alarm_beacon[facing=west,lit=true] @beacon")
    W.set(22, 9, 15, "heroesevolved:alarm_beacon[facing=east,lit=true] @beacon")


# ======================================================================================================================
# SUBLEVEL 4-R, THE HELIX WARD  (x 11..30, z 23..50, y 0..12): one private pod, sealed
# ======================================================================================================================
LAB_POD = (22, 2, 41)       # the private pod's base, on its dais, facing north
LAB_VENT_Y = 7
TANK = (28, 34)


def lab():
    # The passage behind the blast door, and its inner arch.
    W.shell(13, 0, 23, 17, 5, 30, WALL, FLOOR, CEIL)
    W.box(14, 0, 24, 16, 0, 29, RUNNER)
    for z in (25, 28):
        W.box(14, 4, z, 16, 4, z, CUT)
    W.set(15, 5, 26, LAMP)
    # The ward.
    W.shell(13, 0, 30, 30, 12, 50, WALL, FLOOR, CEIL)
    W.box(14, 1, 30, 16, 3, 30, AIR)
    W.box(14, 4, 30, 16, 4, 30, CUT)
    W.set(17, 3, 31, wall_sign("south", sign("helix", "SUBLEVEL 4-R | HELIX WARD | ONE SUBJECT | NO ENTRY", "red")))
    px, py, pz = LAB_POD
    for x in range(14, 30):
        for z in range(31, 50):
            d = max(abs(x - px), abs(z - pz))
            if d <= 5:
                W.set(x, 0, z, "smooth_stone" if d < 5 else CUT)
    # The dais: a step of slabs, a polished octagon, quartz under the pod.
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            r = abs(dx) + abs(dz)
            if abs(dx) <= 2 and abs(dz) <= 2 and r <= 3:
                W.set(px + dx, 1, pz + dz, FRAME)
            elif r <= 4:
                W.set(px + dx, 1, pz + dz, "polished_deepslate_slab[type=bottom]")
    W.set(px, 1, pz, "smooth_quartz")
    W.set(px, py, pz, "heroesevolved:cryochamber[facing=north,half=lower,open=false] @pod")
    W.set(px, py + 1, pz, "heroesevolved:cryochamber[facing=north,half=upper,open=false]")
    W.set(px, py + 2, pz, GRATE)
    W.set(px, py + 2, pz - 1, wall_sign("north", sign("pod15", "SUBJECT 15 | STASIS SEALED | AWAITING | REVIVAL", "red")))
    for y in range(py + 3, 12):
        W.set(px, y, pz, CHAIN)
    # The frosted screen behind the pod, between two posts.
    for x in range(px - 2, px + 3):
        for y in range(2, 5):
            W.set(x, y, pz + 2, FROST)
    for x in (px - 3, px + 3):
        for y in range(2, 5):
            W.set(x, y, pz + 2, "polished_deepslate_wall")
        W.set(x, 1, pz + 2, FRAME)
    # The crown over the dais: a copper ring on four chains, nozzles under it, four end rods pointing at the pod.
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if max(abs(dx), abs(dz)) == 2:
                W.set(px + dx, 7, pz + dz, CUT_TOP)
    for dx, dz in ((-2, -2), (2, 2), (-2, 2), (2, -2)):
        for y in range(8, 12):
            W.set(px + dx, y, pz + dz, CHAIN)
    for dx, dz in ((-2, 0), (2, 0), (0, -2), (0, 2)):
        W.set(px + dx, 6, pz + dz, "end_rod[facing=down]")
    for dx, dz in ((-1, -2), (1, -2), (-1, 2), (1, 2), (-2, -1), (-2, 1), (2, -1), (2, 1)):
        W.set(px + dx, 6, pz + dz, "lightning_rod[facing=down]")
    # Two consoles watching the pod, one either side.
    for x, back in ((18, "west"), (26, "east")):
        W.set(x, 1, pz - 2, FRAME)
        W.set(x, 2, pz - 2, SCREEN)
        W.set(x, 1, pz - 1, FRAME)
        W.set(x, 2, pz - 1, "light_blue_stained_glass")
        W.set(x + (1 if back == "west" else -1), 1, pz - 2, f"spruce_stairs[facing={'east' if back == 'west' else 'west'}]")
    # The bench down the west wall, under the mezzanine: the Formula's instruments, samples, the assay log.
    for z in range(33, 41):
        W.set(14, 1, z, FRAME)
    bench = {33: "heroesevolved:centrifuge[facing=east]", 34: "brewing_stand", 35: "flower_pot",
             36: "heroesevolved:synthesis_station[facing=east]", 37: "amethyst_cluster[facing=up]",
             38: "flower_pot", 39: "brewing_stand", 40: "amethyst_cluster[facing=up]"}
    for z, st in bench.items():
        W.set(14, 2, z, st)
    W.set(14, 1, 35, "water_cauldron[level=3]")
    W.set(14, 2, 35, AIR)
    W.set(16, 1, 37, strongbox("west", "log:helixite"))
    for z in (34, 38, 44, 48):
        W.set(13, 4, z, LAMP)
    # The specimen tank, east: a lit glass column of water with a dead coral in it.
    tx, tz = TANK
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            W.set(tx + dx, 1, tz + dz, FRAME)
            W.set(tx + dx, 4, tz + dz, CUT)
            for y in (2, 3):
                W.set(tx + dx, y, tz + dz, "water" if dx == 0 and dz == 0 else "glass")
    W.set(tx, 1, tz, "dead_brain_coral_block")
    W.set(tx, 4, tz, LAMP)
    # The doctor's office, south-east, behind glass: a desk, his journal, a cot.
    for x in range(25, 30):
        W.set(x, 1, 44, FRAME)
        W.box(x, 2, 44, x, 3, 44, PANE)
        W.set(x, 4, 44, CUT)
    for z in range(44, 50):
        W.set(25, 1, z, FRAME)
        W.box(25, 2, z, 25, 3, z, PANE)
        W.set(25, 4, z, CUT)
    for z in (45, 47):
        W.box(25, 1, z, 25, 4, z, FRAME)
    W.set(25, 3, 46, FRAME)
    W.set(25, 1, 46, "waxed_copper_door[facing=east,half=lower,hinge=left,open=false]")
    W.set(25, 2, 46, "waxed_copper_door[facing=east,half=upper,hinge=left,open=false]")
    for x in (26, 27, 28):
        W.set(x, 1, 49, "dark_oak_slab[type=top]")
    W.set(26, 2, 49, "flower_pot")
    chair(27, 1, 48, "north", wood="dark_oak", arms=False)
    W.set(29, 1, 45, strongbox("west", "log:weir"))
    W.set(29, 1, 47, "white_bed[facing=north,part=head]")
    W.set(29, 1, 48, "white_bed[facing=north,part=foot]")
    W.set(27, 3, 49, wall_sign("north", sign("weir", "DR. A. WEIR | HELIX WARD | ATTENDING | ")))
    W.set(27, 4, 49, BULB)
    # South wall: the subject's files, the lab chest, a whiteboard.
    for x in range(17, 24):
        W.set(x, 1, 49, "barrel[facing=north]" if x != 20 else "chest[facing=north] @loot:lab")
    for x in range(18, 23):
        for y in (3, 4):
            W.set(x, y, 50, "white_concrete")
    for x, y in ((19, 3), (20, 4), (21, 3), (19, 4)):
        W.set(x, y, 50, "gray_concrete")
    W.set(22, 4, 50, "light_blue_concrete")
    # The mezzanine over the west bench and the south wall, reached by a ladder through its floor; the vent at its
    # west end runs through the rock to the stasis hall's catwalk.
    for z in range(31, 50):
        for x in (14, 15, 16):
            W.set(x, 6, z, "polished_deepslate_slab[type=top]")
            W.box(x, 7, z, x, 9, z, AIR)
        W.set(16, 6, z, CUT_TOP)
        W.set(16, 7, z, BARS)
    for z in (47, 48, 49):
        for x in range(17, 23):
            W.set(x, 6, z, "polished_deepslate_slab[type=top]")
    for x in range(17, 23):
        W.set(x, 6, 47, CUT_TOP)
        W.set(x, 7, 47, BARS)
    for z in (48, 49):
        W.set(22, 6, z, CUT_TOP)
        W.set(22, 7, z, BARS)
    W.box(16, 7, 47, 16, 7, 49, AIR)
    W.set(16, 6, 48, "polished_deepslate_slab[type=top]")
    W.set(16, 6, 49, "polished_deepslate_slab[type=top]")
    for y in range(1, 7):
        W.set(14, y, 32, "ladder[facing=east]")
    W.set(14, 4, 33, wall_sign("east", sign("mezzanine", "MEZZANINE | SERVERS | VENT 4-R | ")))
    for z in range(33, 45, 2):
        W.set(14, 7, z, GRATE)
        W.set(14, 8, z, FRAME)
        W.set(14, 7, z + 1, "barrel[facing=east]")
    # The vent: out through the west wall at the mezzanine, through the rock, into the hall.
    # A copper duct a block and a half high inside (a slab floor under a solid top): crouch to get through it.
    for x in (11, 12, 13):
        W.set(x, LAB_VENT_Y - 1, VENT_Z, CUT)
        W.set(x, LAB_VENT_Y, VENT_Z, CUT_BOTTOM)
        W.set(x, LAB_VENT_Y + 1, VENT_Z, AIR)
        W.set(x, LAB_VENT_Y + 2, VENT_Z, CUT)
        for z in (VENT_Z - 1, VENT_Z + 1):
            W.box(x, LAB_VENT_Y - 1, z, x, LAB_VENT_Y + 2, z, CUT if x < 13 else FRAME)
    # Its grille, taken off and leant against the mezzanine rail.
    W.set(15, LAB_VENT_Y, VENT_Z + 1, "waxed_copper_trapdoor[facing=west,half=bottom,open=false]")
    W.set(14, LAB_VENT_Y + 2, VENT_Z + 1, wall_sign("east", sign("vent_lab", "VENT 4-R | TO STASIS HALL | KEEP CLEAR | ")))
    W.set(13, LAB_VENT_Y + 2, VENT_Z + 1, FRAME)
    # Lights: two light boxes over the floor, lamps under and over the mezzanine, the crown's rods over the dais.
    for z in (36, 41):
        W.set(13, 9, z, LAMP)
    W.set(19, 9, 50, LAMP)
    for x, z in ((20, 35), (26, 39)):
        light_box(x, 8, z, 12)
    W.set(px, 11, pz - 5, "heroesevolved:alarm_beacon[facing=down,lit=true] @beacon")


# ======================================================================================================================
# THE WEST WING: corridor (x -23..-11, z 19..23), infirmary (x -35..-23, z 11..31), containment (x -22..-12, z 23..37)
# ======================================================================================================================
def west_corridor():
    W.shell(-23, 0, 19, -11, 5, 23, WALL, FLOOR, CEIL)
    W.box(-11, 1, 20, -11, 3, 22, AIR)
    W.box(-23, 1, 20, -23, 3, 22, AIR)
    W.box(-18, 1, 23, -16, 3, 23, AIR)
    W.box(-22, 0, 21, -12, 0, 21, RUNNER)
    for x in range(-22, -11):
        W.set(x, 4, 22, CUT)
    for x in (-20, -14):
        W.set(x, 5, 21, LAMP)
    W.set(-14, 4, 20, "heroesevolved:alarm_beacon[facing=south,lit=true] @beacon")


SURVIVORS = [(-30, 1, 16), (-30, 1, 25)]
INFIRMARY_BEDS = [(-34, 14), (-34, 18), (-34, 24), (-34, 28)]


def infirmary():
    W.shell(-35, 0, 11, -23, 6, 31, WALL, FLOOR, CEIL)
    W.box(-23, 1, 20, -23, 3, 22, AIR)
    W.box(-23, 1, 19, -23, 4, 19, FRAME)
    W.box(-23, 1, 23, -23, 4, 23, FRAME)
    W.box(-23, 4, 19, -23, 4, 23, CUT)
    W.box(-34, 0, 12, -24, 0, 30, "smooth_stone")
    W.box(-29, 0, 12, -27, 0, 30, RUNNER)
    # Four cots on the west wall, screened from each other, a monitor at each head.
    for bx, bz in INFIRMARY_BEDS:
        W.set(bx, 1, bz, "white_bed[facing=west,part=head]")
        W.set(bx + 1, 1, bz, "white_bed[facing=west,part=foot]")
        W.set(bx, 1, bz - 1, FRAME)
        W.set(bx, 2, bz - 1, SCREEN)
        for z in (bz - 2, bz + 2):
            W.box(bx, 1, z, bx + 2, 2, z, "white_stained_glass_pane")
            W.box(bx, 3, z, bx + 2, 3, z, BARS)
    for x, y, z in SURVIVORS:
        W.set(x, y, z, "air @signal:survivor")
    # Medical cabinets and a sink on the north wall; the operating table under its lamp, south.
    for x in range(-33, -24):
        W.set(x, 1, 12, "barrel[facing=south]" if x % 2 else FRAME)
        W.set(x, 2, 12, "polished_deepslate_slab[type=bottom]")
    W.set(-28, 1, 12, "water_cauldron[level=3]")
    W.set(-28, 2, 12, "lever[face=wall,facing=south]")
    W.set(-26, 2, 12, "brewing_stand")
    W.set(-30, 2, 12, "brewing_stand")
    # The operating table: a white-sheeted slab on a pedestal, an instrument cart and a bin beside it, under its lamp.
    for x in (-28, -27, -26):
        W.set(x, 1, 27, "smooth_stone")
        W.set(x, 2, 27, "white_carpet")
    W.set(-25, 1, 27, "smithing_table")
    W.set(-25, 1, 28, "cauldron")
    light_box(-27, 4, 27, 6, lamp=WARM)
    # A drip stand at the head of every cot.
    for bx, bz in INFIRMARY_BEDS:
        W.set(bx, 1, bz + 1, "end_rod[facing=up]")
        W.set(bx, 2, bz + 1, "end_rod[facing=up]")
    # The nurses' station by the door: a desk, a chair, the charts.
    W.set(-24, 1, 15, "barrel[facing=west]")
    W.set(-24, 1, 16, FRAME)
    W.set(-24, 1, 17, FRAME)
    W.set(-24, 2, 16, "flower_pot")
    W.set(-24, 2, 17, "white_carpet")
    W.set(-25, 1, 16, "spruce_stairs[facing=west]")
    # Cabinets along the south wall, their doors on.
    for x in (-32, -31, -30, -24):
        for y in (1, 2):
            W.set(x, y, 30, "white_concrete")
            W.set(x, y, 29, "spruce_trapdoor[facing=north,open=true]") if x != -24 else None
    W.set(-31, 3, 30, "brewing_stand")
    light_box(-28, 4, 16, 6)
    light_box(-28, 4, 22, 6)
    W.set(-24, 4, 18, wall_sign("west", sign("infirmary", "INFIRMARY | RECOVERY COTS | KEEP CLEAR | ")))
    W.set(-24, 5, 26, "heroesevolved:alarm_beacon[facing=west,lit=true] @beacon")


CAGE = (-14, 1, 26)                # inside Gabriel Gray's cell
CAGE_BARS = [(-15, y, z) for y in (1, 2, 3) for z in (26, 27)]


def containment():
    W.shell(-22, 0, 23, -12, 6, 37, "deepslate_bricks", FLOOR, CEIL)
    W.box(-18, 1, 23, -16, 3, 23, AIR)
    W.box(-18, 0, 24, -16, 0, 36, RUNNER)
    # Six cells, three each side of the aisle, barred onto it and walled from each other.
    for z0 in (25, 29, 33):
        for side, bars, inner in ((-1, -19, (-21, -20)), (1, -15, (-14, -13))):
            lo, hi = min(inner + (bars,)), max(inner + (bars,))
            for x in range(lo, hi + 1):
                W.box(x, 1, z0 - 1, x, 4, z0 - 1, "deepslate_bricks")
                W.box(x, 1, z0 + 3, x, 4, z0 + 3, "deepslate_bricks")
            for z in range(z0, z0 + 3):
                for y in (1, 2, 3):
                    W.set(bars, y, z, BARS)
                W.set(bars, 4, z, "chiseled_deepslate")
            back = inner[0] if side < 0 else inner[1]
            W.set(inner[0] if side > 0 else inner[1], 6, z0 + 1, BULB)
            W.set(back, 1, z0 + 1, "polished_deepslate_slab[type=top]")
            W.set(back, 1, z0 + 2, "polished_deepslate_slab[type=top]")
            W.set(back, 3, z0, CHAIN)
    W.set(-16, 4, 26, wall_sign("west", sign("gray", "CELL 3 | G. GRAY | SUPPRESSION | ALWAYS ON", "red")))
    # Gray's restraint chair, facing his bars, chains on the wall either side of it.
    W.set(-13, 1, 26, "polished_blackstone_brick_stairs[facing=east]")
    for z in (25, 27):
        W.box(-13, 2, z, -13, 3, z, CHAIN)
    # The watch desk at the far end, facing the aisle.
    for x in (-18, -17, -16):
        W.set(x, 1, 36, FRAME)
        W.set(x, 2, 36, SCREEN)
    chair(-17, 1, 35, "north", arms=False)
    for z in (25, 28, 31, 34):
        W.set(-17, 6, z, BULB)
    W.set(-17, 4, 24, wall_sign("south", sign("containment", "CONTAINMENT | SUBLEVEL 4 | ARMED ESCORT | ONLY", "red")))
    W.set(-17, 5, 24, "heroesevolved:alarm_beacon[facing=south,lit=true] @beacon")


def hall_doors():
    """The hall's doors in the commons' south face, cut again after the commons drew its wall."""
    z0 = HALL_Z0
    W.box(-2, 1, z0, 2, 4, z0, AIR)
    W.box(-3, 1, z0, -3, 4, z0, FRAME)
    W.box(3, 1, z0, 3, 4, z0, FRAME)
    for x in range(-3, 4):
        W.set(x, 5, z0, CUT if x else CHISEL)
    W.box(-1, CAT_Y + 1, z0, 1, CAT_Y + 3, z0, AIR)
    W.box(-2, CAT_Y + 1, z0, -2, CAT_Y + 4, z0, FRAME)
    W.box(2, CAT_Y + 1, z0, 2, CAT_Y + 4, z0, FRAME)
    W.box(-1, CAT_Y + 4, z0, 1, CAT_Y + 4, z0, CUT)


# ======================================================================================================================
# A HUNDRED AND THREE YEARS
# ======================================================================================================================
BREACH = (-9, 12)                      # where the cave broke into the commons
COLLAPSE = (-4, 56)                    # where the stasis hall's ceiling came down
CRACKS = {"deepslate_tiles": "cracked_deepslate_tiles", "deepslate_bricks": "cracked_deepslate_bricks",
          "polished_blackstone_bricks": "cracked_polished_blackstone_bricks", "stone_bricks": "cracked_stone_bricks"}
COPPER_PREFIXES = ("waxed_copper_", "waxed_cut_copper")


class Aged:
    """The present: a copy of the lockdown that the years write on. New cells need a room."""

    def __init__(self):
        self.c = dict(W.c)
        self.owner = dict(W.owner)
        self.room = None

    def get(self, x, y, z):
        return self.c.get((x, y, z))

    def set(self, x, y, z, s, force=False):
        if (x, y, z) in W.protected and not force:
            return
        if (x, y, z) not in self.owner:
            assert self.room, f"new cell {x, y, z} with no room"
            self.owner[(x, y, z)] = self.room
        self.c[(x, y, z)] = s

    def air(self, x, y, z):
        return self.c.get((x, y, z)) == AIR

    def solid(self, x, y, z):
        s = self.c.get((x, y, z))
        return s is not None and s != AIR and block(s) in FULL_OR_AGED

    def owned(self, x, y, z):
        return (x, y, z) in self.owner


def stage_at(x, y, z, room):
    """How far the copper has gone: furthest where the water came in, least in the sealed, dry ward."""
    if room == "stasis_lab":
        return "exposed"
    bx, bz = BREACH
    if (x - bx) ** 2 + (z - bz) ** 2 < 15 ** 2 or room in ("stasis_west", "stasis_infirmary", "stasis_cave"):
        return "oxidized"
    return "weathered"


def oxidize(s, stage):
    b = block(s)
    rest = s[len(b):]
    if b == "waxed_copper_block":
        return f"waxed_{stage}_copper" + rest
    for prefix in COPPER_PREFIXES:
        if b.startswith(prefix):
            return f"waxed_{stage}_" + b[len("waxed_"):] + rest
    return s


FULL_OR_AGED = FULL | set(CRACKS.values()) | {"cracked_polished_blackstone_bricks", "waxed_weathered_copper",
                                               "waxed_oxidized_copper", "waxed_exposed_copper"}


def dist(a, b):
    return sum((p - q) ** 2 for p, q in zip(a, b)) ** 0.5


def age():
    A = Aged()
    rooms = dict(W.owner)

    # 1. Copper turns: every waxed copper block takes its zone's stage (the lightning rods have none to take).
    for (x, y, z), s in list(A.c.items()):
        if block(s).startswith(COPPER_PREFIXES) or block(s) == "waxed_copper_block":
            A.set(x, y, z, oxidize(s, stage_at(x, y, z, rooms.get((x, y, z)))), force=True)

    # 2. Cracks: a few everywhere, many where the blast came through the hall's ceiling and round the breach.
    for (x, y, z), s in list(A.c.items()):
        b = block(s)
        if b not in CRACKS:
            continue
        p = 0.04
        if dist((x, z), COLLAPSE) < 7 and y >= 9:
            p = 0.45
        elif rooms.get((x, y, z)) == "stasis_hall" and y >= 11:
            p = 0.12
        elif dist((x, z), BREACH) < 6:
            p = 0.35
        elif rooms.get((x, y, z)) == "stasis_containment":
            p = 0.15
        if chance(p, x, y, z, 1):
            A.set(x, y, z, CRACKS[b] + s[len(b):])

    hall_ruin(A)
    commons_ruin(A)
    airlock_ruin(A)
    east_ruin(A)
    lab_ruin(A)
    west_ruin(A)
    blood_trail(A)
    cobwebs(A)
    return A


def dead(A, x, y, z, s="smooth_quartz"):
    """A light that died: froglights go dull, bulbs go out."""
    cur = A.get(x, y, z)
    if cur and "copper_bulb" in cur:
        A.set(x, y, z, with_props(cur, lit=False))
    else:
        A.set(x, y, z, s)


def fall_light(A, x, y, z, to, top):
    """A light box whose chain broke: chain, lamp and shades gone, the lamp lying dead on the floor below."""
    for cy in range(y, top - 1):
        A.set(x, cy, z, AIR)
    for dx, dz in STEP.values():
        A.set(x + dx, y, z + dz, AIR)
    fx, fy, fz = to
    A.set(fx, fy, fz, "smooth_quartz")
    A.set(fx + 1, fy, fz, "waxed_weathered_copper_trapdoor[facing=north,half=bottom,open=false]")


def rubble(A, cells, seed):
    """Fallen stone: blocks in the middle, slabs and broken steps round the edge."""
    for i, (x, y, z, h) in enumerate(cells):
        for k in range(h):
            A.set(x, y + k, z, ["cobbled_deepslate", "tuff", "cobbled_deepslate", "deepslate_tiles"][noise(x, y + k, z, seed) % 4])
        top = y + h
        n = noise(x, z, seed, 7) % 5
        if n == 0:
            A.set(x, top, z, "cobbled_deepslate_slab[type=bottom]")
        elif n == 1:
            A.set(x, top, z, f"cobbled_deepslate_stairs[facing={['north', 'east', 'south', 'west'][noise(x, z, seed) % 4]}]")


def hall_ruin(A):
    cx, cz = COLLAPSE
    # The ceiling came down: a hole through the bricks to raw rock and roots, a broken rib, stone over the floor.
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            d = dist((x, z), (cx, cz)) + (noise(x, z, 3) % 100) / 120
            if d < 2.2:
                A.set(x, HALL_TOP, z, AIR)
                A.set(x, HALL_TOP - 1, z, AIR)
                A.set(x, HALL_TOP + 1, z, ["rooted_dirt", "tuff", "cobbled_deepslate"][noise(x, z, 4) % 3])
                if noise(x, z, 5) % 3 == 0:
                    A.set(x, HALL_TOP, z, "hanging_roots")
            elif d < 3.2 and A.get(x, HALL_TOP - 1, z) == AIR:
                if noise(x, z, 6) % 3 == 0:
                    A.set(x, HALL_TOP - 1, z, "pointed_dripstone[vertical_direction=down,thickness=tip]")
                    A.set(x, HALL_TOP, z, "dripstone_block")
                    A.set(x, 0, z, "calcite")
    pile = []
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 3, cz + 4):
            d = dist((x, z), (cx, cz))
            if d < 1.5:
                pile.append((x, 1, z, 2))
            elif d < 2.6:
                pile.append((x, 1, z, 1))
            elif d < 3.4 and noise(x, z, 8) % 2 == 0:
                pile.append((x, 1, z, 0))
    rubble(A, pile, 11)
    A.set(cx + 1, 1, cz + 3, "waxed_weathered_cut_copper")                # a length of the duct
    A.set(cx - 1, 2, cz - 1, "polished_deepslate_stairs[facing=east]")    # a piece of the rib
    for z in range(cz - 1, cz + 2):
        A.set(-6, CAT_Y + 1, z, AIR)                                      # railing torn away under it
    # Lights: the box over pod 5 and 12's bay fell; one more box and three niche lamps died.
    fall_light(A, 0, 9, 54, (1, 1, 55), HALL_TOP)
    dead(A, 0, 9, 46)
    for x, z in ((-9, 50), (9, 42), (9, 58)):
        dead(A, x, 4, z)
    # Frost meltwater stained the drains green; moss took the wettest of them.
    for z in range(HALL_Z0 + 1, HALL_Z1):
        for x in (-6, 6):
            if chance(0.25, x, z, 12) and A.air(x, 1, z):
                A.set(x, 1, z, "moss_carpet")
    # Cobwebs under the far balcony and in the niche heads are added with the rest (cobwebs()).


def commons_ruin(A):
    bx, bz = BREACH
    # The breach: the north-west corner of the wall burst inward when the cave behind it gave way.
    breach_walls = [(-8, 10), (-7, 10), (-9, 11), (-10, 12), (-11, 13), (-11, 14), (-11, 15)]
    for i, (x, z) in enumerate(breach_walls):
        h = 5 + noise(x, z, 21) % 4
        for y in range(1, h + 1):
            A.set(x, y, z, AIR)
        A.set(x, 0, z, "moss_block" if i % 2 else "rooted_dirt")
    # The gallery over it fell in: its slabs and rail gone from the north-west quarter, pieces on the floor.
    for x in range(-11, 0):
        for z in range(10, 18):
            r = ring(x, z)
            if 1 <= r <= 3 and x + (z - 10) <= -2:
                A.set(x, 6, z, AIR)
                A.set(x, 7, z, AIR)
    rubble(A, [(-9, 1, 13, 1), (-8, 1, 12, 0), (-10, 1, 14, 0), (-7, 1, 11, 0)], 22)
    # The cave behind.
    cave(A)
    # The azalea in the planter lived on the water and grew into a tree: its roots split the bed and the floor, its
    # crown fills the corner where the gallery was.
    px, pz = PLANTER
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx or dz) and noise(dx, dz, 31) % 3 == 0:
                A.set(px + dx, 1, pz + dz, "rooted_dirt" if noise(dx, dz, 32) % 2 else AIR)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if noise(dx, dz, 33) % 5 < 3:
                A.set(px + dx, 0, pz + dz, "rooted_dirt")
    A.set(px, 1, pz, "rooted_dirt")
    for y in range(2, 10):
        A.set(px, y, pz, "oak_log[axis=y]")
    branches = [(-7, 6, 14), (-8, 7, 13), (-9, 8, 12), (-5, 7, 15), (-4, 8, 16), (-6, 8, 17), (-7, 9, 16)]
    for x, y, z in branches:
        A.set(x, y, z, "oak_wood[axis=y]")
    for x, y, z in ((-7, 1, 16), (-5, 1, 14), (-8, 1, 15), (-4, 1, 16)):
        if A.air(x, y, z):
            A.set(x, y, z, "mangrove_roots")
    crowns = [((-7, 9, 14), 3.2), ((-9, 9, 12), 2.6), ((-4, 9, 16), 2.4), ((-6, 11, 15), 2.6)]
    for (cx, cy, cz), r in crowns:
        for x in range(cx - 4, cx + 5):
            for y in range(cy - 3, cy + 4):
                for z in range(cz - 4, cz + 5):
                    d = dist((x, y, z), (cx, cy, cz)) + (noise(x, y, z, 34) % 100) / 160
                    if d > r or y < 5 or not A.air(x, y, z):
                        continue
                    if A.owner.get((x, y, z)) not in ("stasis_commons", "stasis_cave"):
                        continue
                    A.set(x, y, z, ("flowering_azalea_leaves" if noise(x, y, z, 35) % 3 == 0 else "azalea_leaves")
                          + "[persistent=true]")
    # Moss spreads out across the floor from the breach, thinning with distance; vines hang down the walls near it.
    for x in range(-11, 12):
        for z in range(10, 33):
            if ring(x, z) < 1:
                continue
            d = dist((x, z), (bx, bz))
            if d < 4 and A.solid(x, 0, z):
                A.set(x, 0, z, "moss_block")
            if A.air(x, 1, z) and A.get(x, 0, z) and block(A.get(x, 0, z)) in FULL_OR_AGED | {"moss_block", "rooted_dirt"}:
                if chance(max(0.0, 0.8 - d / 9), x, z, 36):
                    A.set(x, 1, z, "moss_carpet")
    for x, z, face, wall in [(x, 11, "north", (x, 10)) for x in range(-6, 1)] + \
                            [(-10, z, "west", (-11, z)) for z in range(16, 20)]:
        top = 10
        length = 2 + noise(x, z, 37) % 6
        for y in range(top, top - length, -1):
            if A.air(x, y, z) and A.solid(wall[0], y, wall[1]):
                A.set(x, y, z, f"vine[{face}=true]")
            else:
                break
    # Glow berries from the ceiling over the corner; lichen on the stone round the breach.
    for x in range(-9, 0):
        for z in range(11, 20):
            if ring(x, z) < 1 or not chance(0.18, x, z, 38):
                continue
            top = max(y for y in range(1, HUB_TOP + 1) if A.air(x, y, z) or y == 1)
            if not A.solid(x, top + 1, z) or top < 8:
                continue
            length = 1 + noise(x, z, 39) % 3
            for k in range(length):
                y = top - k
                if not A.air(x, y, z):
                    break
                last = k == length - 1 or not A.air(x, y - 1, z)
                A.set(x, y, z, "cave_vines[age=25,berries=true]" if last else f"cave_vines_plant[berries={'true' if k % 2 else 'false'}]")
                if last:
                    break
    # One light box over the corner fell; another died.
    fall_light(A, -3, 9, HUB_Z - 3, (-3, 1, 19), HUB_TOP)
    dead(A, 4, 9, HUB_Z)
    # A bench gone from each table, the lounge's table split.
    for x, z in ((3, 14), (6, 16), (5, 23), (7, 28)):
        A.set(x, 1, z, AIR)
    A.set(-4, 1, 26, AIR)


def cave(A):
    """The pocket of lush cave behind the commons' north-west wall: wet stone, moss, a pool, glow berries."""
    A.room = "stasis_cave"
    cx, cy, cz = -13.5, 3.0, 8.5
    inside = set()
    for x in range(-20, -6):
        for y in range(-2, 12):
            for z in range(1, 18):
                f = ((x - cx) / 6.5) ** 2 + ((y - cy) / 5.0) ** 2 + ((z - cz) / 7.0) ** 2
                f += (noise(x, y, z, 41) % 100) / 400
                if f < 1 and y >= (0 if x >= -10 and z >= 9 else -1) and not A.owned(x, y, z):
                    inside.add((x, y, z))
    # The mouth: from the cave to the breach in the wall.
    for x in range(-11, -6):
        for z in range(9, 16):
            for y in range(1, 6):
                if not A.owned(x, y, z) and ring(x, z) < 0:
                    inside.add((x, y, z))
    for (x, y, z) in inside:
        A.set(x, y, z, AIR)
    for (x, y, z) in list(inside):
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n in inside or A.owned(*n):
                continue
            stone = ["deepslate", "deepslate", "tuff", "cobbled_deepslate", "deepslate", "tuff"][noise(*n, 42) % 6]
            if dy == -1:
                stone = "moss_block" if noise(*n, 43) % 4 else "clay"
            if dy == 1 and noise(*n, 44) % 5 == 0:
                stone = "moss_block"
            A.set(*n, stone)
    # Floor dressing and the pool at the lowest point.
    floor = sorted((p for p in inside if (p[0], p[1] - 1, p[2]) not in inside), key=lambda p: p[1])
    low = floor[0][1]
    pool = [p for p in floor if p[1] == low and dist((p[0], p[2]), (floor[0][0], floor[0][2])) < 2.5]
    for (x, y, z) in pool:
        A.set(x, y - 1, z, "clay")
        A.set(x, y, z, "water")
        for dx, dz in STEP.values():
            n = (x + dx, y, z + dz)
            if n not in inside or (x + dx, y - 1, z + dz) not in inside and n not in pool:
                if A.get(*n) in (None, AIR) or n in inside and n not in pool:
                    pass
    pool_set = set(pool)
    for (x, y, z) in pool:
        for dx, dz in STEP.values():
            n = (x + dx, y, z + dz)
            if n in inside and n not in pool_set:
                # Keep the water in: a lip of moss where the pool meets open floor.
                A.set(*n, "moss_block")
                if (x + dx, y + 1, z + dz) in inside:
                    A.set(x + dx, y + 1, z + dz, "moss_carpet" if noise(*n, 45) % 2 else "small_dripleaf[facing=north,half=lower]")
                    if A.get(x + dx, y + 1, z + dz).startswith("small_dripleaf"):
                        if (x + dx, y + 2, z + dz) in inside:
                            A.set(x + dx, y + 2, z + dz, "small_dripleaf[facing=north,half=upper]")
                        else:
                            A.set(x + dx, y + 1, z + dz, "moss_carpet")
    for (x, y, z) in floor:
        if (x, y, z) in pool_set or not A.air(x, y, z):
            continue
        below = A.get(x, y - 1, z)
        if below in ("moss_block", "clay") or block(below or "") in ("deepslate", "tuff", "cobbled_deepslate"):
            if below != "moss_block":
                A.set(x, y - 1, z, "moss_block")
            n = noise(x, y, z, 46) % 10
            if n < 4:
                A.set(x, y, z, "moss_carpet")
            elif n == 4 and (x, y + 1, z) in inside:
                A.set(x, y, z, "azalea")
            elif n == 5:
                A.set(x, y, z, "big_dripleaf[facing=east]")
            elif n == 6:
                A.set(x, y, z, "short_grass")
    # Ceiling: glow berries, a spore blossom, hanging roots; lichen on the walls.
    ceiling = [p for p in inside if (p[0], p[1] + 1, p[2]) not in inside and A.air(*p)]
    for (x, y, z) in ceiling:
        n = noise(x, y, z, 47) % 12
        if n < 4:
            # As long as the drop allows, leaving a cell of air under the tip: stems, then the berried tip.
            room = 0
            while (x, y - room - 1, z) in inside and A.air(x, y - room - 1, z) and room < 4:
                room += 1
            length = min(1 + noise(x, z, 48) % 4, room)
            for k in range(length):
                A.set(x, y - k, z, "cave_vines[age=25,berries=true]" if k == length - 1 else
                      f"cave_vines_plant[berries={'true' if k % 2 else 'false'}]")
        elif n == 4:
            A.set(x, y, z, "spore_blossom")
        elif n == 5:
            A.set(x, y, z, "hanging_roots")
    for (x, y, z) in inside:
        if not A.air(x, y, z) or noise(x, y, z, 49) % 9:
            continue
        for face, (dx, dz) in STEP.items():
            if A.solid(x + dx, y, z + dz):
                A.set(x, y, z, f"glow_lichen[{face}=true]")
                break
    A.room = None


def airlock_ruin(A):
    # What grew in through the vault: moss along the north wall, vines on it, lichen.
    for x in range(-5, 6):
        for z in (2, 3):
            if chance(0.55 if z == 2 else 0.25, x, z, 51) and A.air(x, 1, z):
                A.set(x, 1, z, "moss_carpet")
    for x in (-5, -4, -3, 3, 4, 5):
        for y in range(5, 5 - (2 + noise(x, 52) % 3), -1):
            A.set(x, y, 2, "vine[north=true]")
    A.set(4, 2, 4, AIR)
    A.set(4, 3, 4, AIR)
    A.set(3, 2, 8, AIR)
    for z in (6, 8):
        A.set(-5, 2, z, "barrel[facing=east,open=true]")
    dead(A, 2, 6, 8)


def east_ruin(A):
    # Records: papers off the table and out of a fallen cabinet.
    A.set(12, 2, 10, AIR)
    A.set(13, 1, 10, "barrel[facing=up,open=true]")
    for x, z in ((13, 11), (14, 12), (18, 11), (15, 16), (14, 17), (17, 17), (13, 15)):
        if A.air(x, 1, z):
            A.set(x, 1, z, "white_carpet")
    dead(A, 18, 4, 12)
    # The generator: eleven cells spent, one blown out and scorched, a crack in the core's glass.
    for i, (x, z) in enumerate(CELLS):
        if i in LIVE_CELLS:
            continue
        A.set(x, 2, z, f"waxed_weathered_copper_bulb[lit=false,powered=false]")
    x, z = CELLS[BLOWN_CELL]
    A.set(x, 1, z, "blackstone")
    A.set(x, 2, z, "coal_block")
    A.set(x, 3, z, "cracked_polished_blackstone_bricks")
    A.set(x, 4, z, AIR)
    for dx in (-1, 0, 1):
        for y in (1, 2, 3, 4, 5):
            if chance(0.7, dx, y, 61):
                A.set(x + dx, y, z - 1, "blackstone" if y < 4 else "smooth_basalt")
        A.set(x + dx, 12, z + 1, "smooth_basalt")
    A.set(CORE[0], 5, CORE[1] - 1, AIR)
    for x2, z2 in ((37, 14), (37, 15), (30, 27)):
        A.set(x2, 7, z2, AIR)
    dead(A, 34, 8, 24)


def lab_ruin(A):
    # The passage behind the blast door came down in the quake: the door's leaf buckled, stone fills the way.
    b0, b1 = BLAST
    A.set(b1, 3, 23, AIR)
    for z, h in ((24, 4), (25, 4), (26, 3), (27, 2), (28, 1)):
        rubble(A, [(x, 1, z, h) for x in range(14, 17)], 70 + z)
    A.set(15, 5, 26, "cobbled_deepslate")
    rubble(A, [(14, 1, 29, 0), (15, 1, 29, 1), (16, 1, 31, 0), (14, 1, 31, 0)], 71)
    # Frost spilled from the pod and never thawed.
    px, py, pz = LAB_POD
    # (Ice, not snow: under the bunker's warm lamps snow reads as sand.)
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if (dx or dz) and abs(dx) + abs(dz) <= 3 and noise(dx, dz, 72) % 3:
                A.set(px + dx, 1, pz + dz, "blue_ice" if noise(dx, dz, 73) % 3 == 0 else "packed_ice")
    # The tank cracked and ran dry.
    tx, tz = TANK
    for y in (2, 3):
        A.set(tx, y, tz, AIR)
    A.set(tx - 1, 3, tz - 1, AIR)
    A.set(tx - 1, 2, tz - 1, AIR)
    # Dr. Weir, at his desk's foot, his coat beside him.
    A.set(27, 1, 48, AIR)
    A.set(26, 1, 47, "skeleton_skull[rotation=6]")
    A.set(27, 1, 47, "bone_block[axis=x]")
    A.set(28, 1, 46, "white_carpet")
    # Papers, a fallen light, dead screens.
    for x, z in ((18, 47), (21, 46), (17, 38), (19, 33), (24, 38)):
        if A.air(x, 1, z):
            A.set(x, 1, z, "white_carpet")
    fall_light(A, 20, 8, 35, (19, 1, 36), 12)
    A.set(26, 2, LAB_POD[2] - 1, "gray_stained_glass")
    # Lichen in the vent, the only light in it.
    for x in (11, 12):
        A.set(x, LAB_VENT_Y + 1, VENT_Z, f"glow_lichen[south=true]")


def west_ruin(A):
    # Gray's cage: the bars he unmade on the third night, never put back; the cell walls clawed.
    for (x, y, z) in CAGE_BARS:
        A.set(x, y, z, AIR)
    A.set(-15, 4, 26, "cracked_deepslate_bricks")
    for z in (30, 31):
        A.set(-19, 2, z, AIR)
    # Damp from the cave reached the corridor's north side and the infirmary's corner: moss, roots, vines.
    for x in range(-22, -11):
        if chance(0.45, x, 81) and A.air(x, 1, 20):
            A.set(x, 1, 20, "moss_carpet")
        if chance(0.3, x, 82):
            A.set(x, 4, 20, "hanging_roots") if A.air(x, 4, 20) else None
    for x in range(-29, -23):
        for z in range(12, 16):
            if chance(0.6 - 0.08 * (z - 12), x, z, 83) and A.air(x, 1, z):
                A.set(x, 1, z, "moss_carpet")
        if A.air(x, 5, 12):
            A.set(x, 5, 12, "hanging_roots")
    # A cot rotted through, screens broken, papers.
    bx, bz = INFIRMARY_BEDS[0]
    A.set(bx, 1, bz, AIR)
    A.set(bx + 1, 1, bz, "white_carpet")
    A.set(bx + 2, 1, bz + 2, AIR)
    A.set(bx + 2, 2, 22, AIR)
    for x, z in ((-29, 19), (-26, 25), (-31, 21)):
        if A.air(x, 1, z):
            A.set(x, 1, z, "white_carpet")
    bx, bz = INFIRMARY_BEDS[2]
    A.set(bx, 2, bz + 1, AIR)
    A.set(bx, 1, bz + 1, AIR)
    A.set(bx + 1, 1, bz + 1, "end_rod[facing=east]")
    A.set(-31, 2, 29, AIR)
    for x, z in ((-27, 26), (-28, 25)):
        if A.air(x, 1, z):
            A.set(x, 1, z, "redstone_wire")



def blood_trail(A):
    """Gray dragged what he took from Pod 14 back to his cell: dark drops, every few steps."""
    path = [(7, 62), (4, 60), (4, 52), (3, 44), (3, 36), (1, 33), (0, 30), (-3, 26), (-8, 22), (-11, 21), (-16, 21),
            (-17, 23), (-17, 25), (-15, 26), (-14, 26)]
    step = 0
    for (x0, z0), (x1, z1) in zip(path, path[1:]):
        n = int(max(abs(x1 - x0), abs(z1 - z0)))
        for i in range(n):
            x = round(x0 + (x1 - x0) * i / n)
            z = round(z0 + (z1 - z0) * i / n)
            step += 1
            if step % 3 and step % 7:
                continue
            below = A.get(x, 0, z)
            if A.air(x, 1, z) and below and block(below) in FULL_OR_AGED:
                A.set(x, 1, z, "redstone_wire")


def cobwebs(A):
    """In the corners nobody cleaned: where a ceiling meets two walls, never on a floor anyone walks."""
    for (x, y, z), s in list(A.c.items()):
        if s != AIR or y < 3 or not A.solid(x, y + 1, z):
            continue
        # Three clear blocks under it, so it hangs above the head of anyone walking or jumping beneath (the vent and the
        # mezzanine have only two).
        if not all(A.air(x, y - k, z) for k in (1, 2, 3)):
            continue
        walls = sum(1 for dx, dz in STEP.values() if A.solid(x + dx, y, z + dz))
        if walls < 2:
            continue
        room = A.owner.get((x, y, z))
        if room == "stasis_cave":
            continue
        p = 0.55 if room == "stasis_lab" else 0.3
        if chance(p, x, y, z, 91):
            A.set(x, y, z, "cobweb")


# ======================================================================================================================
# Writing
# ======================================================================================================================
NOTES = {
    "stasis_airlock": "The airlock: the vault's door, a decontamination arch, the security booth with the maintenance hatch, emergency lockers.",
    "stasis_maintenance": "Maintenance, under the security booth's hatch: a workbench, a map desk, a cot, and Bennet's notes on 4-R.",
    "stasis_commons": "The commons: a two-storey rotunda with a gallery, a chandelier, the mess and the lounge; the planter's azalea.",
    "stasis_cave": "The lush cave that broke into the commons' north-west corner (the present only).",
    "stasis_hall": "The stasis hall: fourteen pods in arched niches under catwalks, the orientation log in the aisle, a grand stair to the balcony under Exhibit 1.",
    "stasis_records": "Records and comms: the Simple Radio console, filing cabinets, the planning table, Bennet's corner.",
    "stasis_east": "The east corridor: records, the generator hall, and the blast door to Sublevel 4-R.",
    "stasis_generator": "The generator hall: the core, fourteen power cells, the catwalk and the main breaker.",
    "stasis_lab": "Sublevel 4-R, the Helix Ward: the private pod on its dais, the Formula bench, the doctor's office; the vent to the hall.",
    "stasis_west": "The west corridor: the infirmary and containment.",
    "stasis_infirmary": "The infirmary: four screened cots, medical cabinets, the operating table; Mara and Ivo recover here.",
    "stasis_containment": "Containment: six barred cells, Gabriel Gray's among them, and the watch desk.",
}
ORDER = ["stasis_airlock", "stasis_maintenance", "stasis_generator", "stasis_lab", "stasis_records", "stasis_east",
         "stasis_infirmary", "stasis_containment", "stasis_west", "stasis_hall", "stasis_commons", "stasis_cave"]


def draw():
    for room, fn in (("stasis_airlock", airlock), ("stasis_maintenance", maintenance), ("stasis_generator", generator),
                     ("stasis_lab", lab), ("stasis_records", records), ("stasis_east", east_corridor),
                     ("stasis_infirmary", infirmary), ("stasis_containment", containment), ("stasis_west", west_corridor),
                     ("stasis_hall", hall), ("stasis_commons", hub), ("stasis_hall", hall_doors)):
        W.room = room
        fn()
    W.room = None


def boxes(*owners):
    out = {}
    for owner in owners:
        for (x, y, z), room in owner.items():
            b = out.setdefault(room, [x, y, z, x, y, z])
            b[0], b[1], b[2] = min(b[0], x), min(b[1], y), min(b[2], z)
            b[3], b[4], b[5] = max(b[3], x), max(b[4], y), max(b[5], z)
    return out


def write(name, box, cells, owner, past):
    x0, y0, z0, x1, y1, z1 = box
    g = StateGrid(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1)
    used = {}
    for (x, y, z), s in cells.items():
        if owner.get((x, y, z)) != name:
            continue
        g.put(x - x0, y - y0, z - z0, s)
        if "@sign:" in s:
            sid = s.split("@sign:")[1].strip()
            used[sid] = SIGNS[sid] if past else PRESENT_SIGNS.get(sid, SIGNS[sid])
    title = name + ("_past" if past else "")
    when = ("As it was on the Last Day and the lockdown nights (PastBunker)." if past
            else "A hundred and three years later: the bunker new players wake in.")
    g.write(os.path.join(OUT, title + ".blueprint"), title, anchor=(0, 0, 0),
            notes=["Generated by tools/generate-bunker.py; change the script, not this file.", NOTES[name], when,
                   f"Placed with its grid corner at bunker {x0} {y0} {z0} (BunkerLayout)."],
            signs=used or None)


LIGHT = {"pearlescent_froglight": 15, "ochre_froglight": 15, "verdant_froglight": 15, "sea_lantern": 15, "glowstone": 15,
         "lantern": 15, "end_rod": 14, "soul_lantern": 10, "glow_lichen": 7, "heroesevolved:alarm_beacon": 6,
         "heroesevolved:cryochamber": 7, "amethyst_cluster": 5, "brewing_stand": 1}
BULB_LIGHT = {"waxed_copper_bulb": 15, "waxed_exposed_copper_bulb": 12, "waxed_weathered_copper_bulb": 8, "waxed_oxidized_copper_bulb": 4}
SEE_THROUGH = {"glass", "white_stained_glass", "black_stained_glass", "gray_stained_glass", "light_blue_stained_glass", GRATE,
               "waxed_exposed_copper_grate", "waxed_weathered_copper_grate", "waxed_oxidized_copper_grate"}


def emits(s):
    b = block(s)
    if b in BULB_LIGHT:
        return BULB_LIGHT[b] if "lit=true" in s else 0
    if b in ("cave_vines", "cave_vines_plant"):
        return 14 if "berries=true" in s else 0
    return LIGHT.get(b, 0)


def flat(s):
    b = block(s or ".")
    return b.endswith("_slab") and "type=double" not in s or b.endswith("_trapdoor") and "open=true" not in s


def dark_floor(cells):
    """Cells a monster could spawn on with the power on: open, two high, over a solid top, and no block light reaching."""
    import heapq
    opaque = lambda s: s is not None and s != AIR and block(s) in FULL_OR_AGED and block(s) not in SEE_THROUGH  # noqa: E731
    light = {}
    heap = []
    for p, s in cells.items():
        e = emits(s)
        if e:
            light[p] = e
            heapq.heappush(heap, (-e, p))
    while heap:
        e, (x, y, z) = heapq.heappop(heap)
        e = -e
        if light.get((x, y, z), 0) > e:
            continue
        here = cells.get((x, y, z))
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            s = cells.get(n)
            if s is None or opaque(s) or block(s).endswith("_stairs"):
                continue
            # Slabs and closed trapdoors have a full face across the cell: light doesn't cross them up or down.
            if dy and any(flat(c) for c in (s, here)):
                continue
            v = e - (2 if "leaves" in (s or "") or block(s) == "water" else 1)
            if v > light.get(n, 0):
                light[n] = v
                heapq.heappush(heap, (-v, n))
    dark = []
    for (x, y, z), s in cells.items():
        below = cells.get((x, y - 1, z))
        if s != AIR and block(s) not in ("moss_carpet", "white_carpet", "redstone_wire", "short_grass"):
            continue
        # Monsters stand on full blocks, top and double slabs, and upside-down stairs (the catwalks are top slabs).
        standable = opaque(below) or below is not None and ("type=top" in below or "type=double" in below
                                                             or block(below).endswith("_stairs") and "half=top" in below)
        if cells.get((x, y + 1, z)) not in (AIR, None) or not standable:
            continue
        if light.get((x, y, z), 0) == 0:
            dark.append((x, y, z))
    return dark


def main():
    draw()
    present = age()
    if "--light" in sys.argv:
        for name, cells in (("present", present.c), ("past", W.c)):
            dark = dark_floor(cells)
            rooms = {}
            for p in dark:
                rooms.setdefault(present.owner.get(p, W.owner.get(p, "?")), []).append(p)
            print(f"dark floor ({name}): {len(dark)}", {k: (len(v), v[:6]) for k, v in rooms.items()})
    box = boxes(W.owner, present.owner)
    for name in ORDER:
        write(name, box[name], present.c, present.owner, False)
        if name != "stasis_cave":
            write(name, box[name], W.c, W.owner, True)
    for name in ORDER:
        b = box[name]
        print(f'{name:22} new Room("{name}", new BlockPos({b[0]}, {b[1]}, {b[2]}))   size {b[3] - b[0] + 1}x{b[4] - b[1] + 1}x{b[5] - b[2] + 1}')


if __name__ == "__main__":
    main()


