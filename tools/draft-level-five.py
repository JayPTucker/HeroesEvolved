"""Drafts level_five.blueprint: Primatech's Level 5, under the bunker's containment block, where Sylar was kept.

Run from the repository root:  py -3 tools/draft-level-five.py
LevelFive builds it once in heroesevolved:level_five and reads its markers; change this script, not the file.

The plan (x west to east, z north to south): the lift from the bunker at the south end, a corridor between eight
cells (their inmates long gone: bars missing, a skull, cobwebs, a cracked stasis pod), the Company's watch list on the
west wall by his cell and the release lever in the guard post opposite, and at the north end his cell behind a glass
front: the stasis pod he slept in, a chair, a workbench of watch parts, one soul lantern.
"""
import sys

sys.path.insert(0, "tools")
from blueprint_draft import StateGrid  # noqa: E402

W, H, D = 15, 8, 44
AX, AY, AZ = 7, 1, 39          # the anchor: the floor under the lift's doors, where you arrive
g = StateGrid(W, H, D)

WALL, CRACK, FLOOR, CEIL = "deepslate_tiles", "cracked_deepslate_tiles", "polished_deepslate", "deepslate_bricks"
TRIM, BARS = "polished_blackstone_bricks", "iron_bars"

# The shell: rock under the floor, walls, a ceiling, and air inside.
g.box(0, 0, 0, W - 1, H - 1, D - 1, WALL)
g.box(1, 1, 1, W - 2, H - 2, D - 2, ".")
g.box(0, 0, 0, W - 1, 0, D - 1, "deepslate")
g.box(1, AY, 1, W - 2, AY, D - 2, FLOOR)
g.box(0, H - 1, 0, W - 1, H - 1, D - 1, CEIL)
g.weather(WALL, CRACK, every=7, seed=3)

# The corridor's runner and the cells either side (x 1..4 west, 10..13 east), z 12..31, four a side.
g.box(6, AY, 10, 8, AY, 41, "deepslate_tiles")
CELLS = [(12, 16), (17, 21), (22, 26), (27, 31)]
for i, (z0, z1) in enumerate(CELLS):
    for x0, x1, bars in ((1, 4, 4), (10, 13, 10)):
        g.box(x0, AY + 1, z0 - 1, x1, AY + 5, z0 - 1, WALL)
        g.box(x0, AY + 1, z1, x1, AY + 5, z1, WALL)
        for z in range(z0, z1):
            for y in range(AY + 1, AY + 4):
                # Bars missing where something got out.
                missing = (i == 1 and x0 == 1 and z == z0 + 1) or (i == 3 and x0 == 10 and z in (z0 + 1, z0 + 2))
                g.put(bars, y, z, "." if missing else BARS)
            g.put(bars, AY + 4, z, TRIM)
            g.put(bars, AY + 5, z, WALL)
    # What was left in them.
    if i == 0:
        g.put(2, AY + 1, z0 + 2, "skeleton_skull[rotation=6]")
    if i == 2:
        g.put(12, AY + 1, z0 + 1, "heroesevolved:cryochamber[facing=west,half=lower,open=true]")
        g.put(12, AY + 2, z0 + 1, "heroesevolved:cryochamber[facing=west,half=upper,open=true]")
    g.put(1 if i % 2 == 0 else 13, AY + 4, z0, "cobweb")
# Exposed copper (light 12): a century old, still on. Weathered copper's 8 left the corridor too dark to read.
for z in (11, 16, 21, 26, 31, 36):
    g.put(7, H - 1, z, "waxed_exposed_copper_bulb[lit=true]")

# The lift at the south end: doors in the south wall, the call button beside them, a sign over them.
g.box(6, AY + 1, D - 1, 8, AY + 3, D - 1, "iron_block")
g.put(7, AY + 1, D - 2, FLOOR)
g.put(9, AY + 2, D - 2, "stone_button[face=wall,facing=north] @exit")
g.put(7, AY + 4, D - 2, "dark_oak_wall_sign[facing=north] @sign:lift")
g.put(AX, AY, AZ, FLOOR + " @spawn:arrival")

# His cell at the north end (z 1..8), its glass front at z 9 with a blackstone frame.
g.box(1, AY + 1, 9, 13, AY + 5, 9, TRIM)
g.box(3, AY + 1, 9, 11, AY + 3, 9, "glass @glass")
g.box(1, AY, 1, 13, AY, 8, "polished_deepslate")
g.put(7, AY + 1, 1, "heroesevolved:cryochamber[facing=south,half=lower,open=true] @pod")
g.put(7, AY + 2, 1, "heroesevolved:cryochamber[facing=south,half=upper,open=true]")
g.put(7, AY, 4, "polished_deepslate @spawn:prisoner")
g.put(4, AY + 1, 3, "polished_blackstone_brick_stairs[facing=east]")
# The workbench of watch parts on the east wall.
g.box(11, AY + 1, 2, 12, AY + 1, 4, "dark_oak_slab[type=top]")
g.put(11, AY + 2, 3, "candle[candles=2,lit=false]")
g.put(12, AY + 2, 2, "chain[axis=y]")
g.put(7, H - 2, 5, "soul_lantern[hanging=true]")
# Two bulbs in his ceiling, so he can be seen through the glass from the corridor.
for x in (4, 10):
    g.put(x, H - 1, 4, "waxed_exposed_copper_bulb[lit=true]")

# The watch list on the west wall by his cell, and the release in the guard post opposite.
g.box(1, AY + 1, 10, 4, AY + 5, 11, WALL)
g.box(10, AY + 1, 10, 13, AY + 5, 11, WALL)
# Read from the corridor facing west, z 11 is the left column: the old names run down it, the new ones down the right.
g.put(5, AY + 4, 11, "dark_oak_wall_sign[facing=east] @sign:list0")
g.put(5, AY + 4, 10, "dark_oak_wall_sign[facing=east] @sign:head")
g.put(5, AY + 3, 11, "dark_oak_wall_sign[facing=east] @sign:list1")
g.put(5, AY + 2, 11, "dark_oak_wall_sign[facing=east] @sign:list2")
g.put(5, AY + 3, 10, "dark_oak_wall_sign[facing=east] @sign:list3")
g.put(5, AY + 2, 10, "dark_oak_wall_sign[facing=east] @sign:list4")
g.put(9, AY + 2, 11, "lever[face=wall,facing=west,powered=false] @lever")
g.put(9, AY + 3, 11, "dark_oak_wall_sign[facing=west] @sign:release")

g.write("src/main/resources/data/heroesevolved/blueprints/level_five.blueprint", name="level_five", anchor=(AX, AY, AZ),
        notes=["Primatech Level 5, under the bunker's containment block: Sylar's cell (story/sylar/LevelFive).",
               "Drafted by tools/draft-level-five.py: change the script, not this file.",
               "@spawn:arrival, @exit (the lift's button), @glass (his cell's front), @pod, @spawn:prisoner, @sign:list0/head (headers), @sign:list1..4 (the list, in reading order), @lever, @sign:release."],
        signs={"lift": ("LIFT | TO SUBLEVEL 4 | CONTAINMENT", "white", False),
               "list0": ("PRIMATECH | LEVEL 5 | WATCH LIST |", "red", True),
               "head": ("EVOLVED | PERSONS | AT LARGE |", "red", True),
               "list1": ("", "white", False), "list2": ("", "white", False),
               "list3": ("", "white", False), "list4": ("", "white", False),
               "release": ("CELL 5-1 | RELEASE | TWO KEYS | REQUIRED", "red", True)},
        layer_notes={0: "rock", AY: "floor", AY + 1: "walls, cells and his cell"})
print("wrote level_five.blueprint")
