---
name: minecraft-building
description: Design and build Minecraft structures for the Heroes Evolved mod as text blueprints that the mod places in the world, with an isometric render and in-game screenshots to check the result. Use this whenever the task involves building, designing, decorating, redesigning or fixing any structure, room, facility, bunker, lab, base, ruin, city block, arena, mission site or interior for the mod, even if the user just says "make it look better", "add detail", "build a lair for Sylar" or shares a structure-block .nbt file. Also use it before changing BunkerBuilder or any code that places blocks for scenery.
---

# Building structures for Heroes Evolved

Structures are **blueprints**: plain-text files in `src/main/resources/data/heroesevolved/blueprints/`
with a palette of single characters and horizontal layers drawn as grids. The mod places them
with `Blueprint.load(name).place(level, pos, rotation)`; markers let code finish special blocks
(books on lecterns, chest contents). Write blueprints rather than `setBlock` loops: a grid is
something you can actually see and reason about, which is why the results are better.

Read `references/blueprint-format.md` for the exact file format before writing your first
blueprint, `references/style-guide.md` before designing anything players will see, and
`references/inspiration.md` for the quality bar the user set: what their favourite builds do well,
and recipes for furniture and props, and `references/techniques.md` for rules adapted from other builders and agents
(order of work, proportions, palette ratios, wall depth, roofs, interiors, and how to inspect a finished build).
**Read `references/block-states.md` before placing any stair, slab, trapdoor, door, gate, bed, chest, torch, lantern,
sign, vine or plant**: which way each faces, which corner a stair fills, which hinge goes where, and what holds what,
all read from the game itself. Then **look at the pictures in `references/images/`** that match
the job (its `README.md` says which is which): the words leave out proportion and density, the
pictures don't. Compare your in-game screenshots with them before calling a build done.

**On Windows** (this machine) the commands below are `python`, not `python3`. Python 3.12 lives in
`%LOCALAPPDATA%\Programs\Python\Python312`; a shell started before it was installed won't find it on
the PATH, so call `python.exe` there by its full path. `JAVA_HOME` is already set to a Java 21 JDK. Set
`PYTHONIOENCODING=utf-8` for `blueprint.py stats`: some palettes use Greek letters, which the Windows console can't print.

## The loop

Building well means looking at what you built. Work in this loop and don't skip the looking:

1. **Plan the footprint and the shape.** Write down the room's job, its size, where doors connect, and which
   cells other code depends on (spawn points, pods, lecterns tests check). Settle the massing and roofline before
   any detail, then the palette (three to five blocks), then depth, roof, detail and weathering, in that order. If the structure plugs
   into existing coordinates (like the bunker), work out the anchor and offsets first.
2. **Draft the blueprint.** Small rooms: type the grids directly. Large or symmetric builds:
   draft with `tools/blueprint_draft.py` in a throwaway script in your scratchpad, and commit only the
   `.blueprint` it writes. Prefer `StateGrid`: its cells hold block states directly and letters are
   assigned on write, so two blocks can never collide on one letter.
3. **Validate.** `python3 tools/blueprint.py validate <file>` checks grid sizes, palette letters, every block id and
   property value against the real game, and placement: halves that don't match (doors, beds, chests, tall plants),
   double doors hinged in the middle, stairs drawn straight where the game will draw a corner, and anything with nothing
   to hold it (a torch on a slab's side, a lantern under a top slab or a sideways chain, a vine on iron bars, grass on
   planks). The support rules come from `tools/block-facts.json.gz`, which the game wrote. Fix every error; read every
   `note:`. `python3 tools/blueprint.py facts "<state>"` says what any state's faces hold.
4. **Render.** `python3 tools/blueprint.py render <file> --cut <layer> --out <png> [--view se|sw|ne|nw]` draws an
   isometric view (cut away above the layer so you see inside) and a plan of every layer. Slabs, stair corners, doors
   and trapdoors (open or shut), fences, panes and lamps are drawn in their real shapes, and the plans darken each
   stair's low side and mark where each door and open trapdoor stands, so facings can be checked here. Look from the
   default south-east and from the opposite corner (`--view nw`), which shows the north and west faces. Check the
   silhouette, door alignment, that every stair rises the way you meant, and that nothing floats or is buried.
5. **Screenshot in-game.** The render uses average texture colors; only the game shows real
   lighting and models. Run
   `./gradlew runClient --init-script tools/blueprint-preview.init.gradle -Pblueprint=<name> [-Pcut=<layer>]`
   and read `build/blueprint-preview/screenshots/<name>-{se,sw,ne,nw,top,inside}.png`.
   (`JAVA_HOME` must point at a Java 21 JDK; on Windows it already does, on the Mac it is
   `$HOME/.jdks/jdk-21.0.13+9/Contents/Home`.) For the bunker in context, use
   `tools/bunker-preview.init.gradle`, which starts a fresh world and tours every room.
   Put at least one camera at eye height on the way a player walks in, and one 20 to 30 blocks back: the first
   shows doors, headroom and faces a high shot hides, the second whether the shape still reads. For anything big,
   run `CityAudit` on it (the city and bunker previews print it) and confirm each fault before fixing it.
6. **Fix what you see, then repeat 3–5.** Typical fixes: rooms too dark, a flat wall that needs
   texture, decorations that read as clutter, a door that lands in a wall.
7. **Wire it up and test.** Place it from code with markers for anything that needs contents, and
   run the gameplay tests: `./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle`.
   `BlueprintPlacementGameTests` places every blueprint and fails on any block the game would remove or redraw; for a
   quick run of only that and the block facts, use `tools/structure-gametest.init.gradle` (about a minute).
   If code or tests depend on specific cells, add a test (see `BunkerAndPowersGameTests.blueprintsLoadWithTheirMarkers`).

Show the user the in-game screenshots when you finish: they judge the look, not the grid.

**Changing a pinned room.** Most bunker rooms have cells that code, tests or `BunkerLayoutUpgrade` read by grid position.
Grow a room into free rock instead of moving things: keep the old cells where they are, and if it grows west or north,
move the anchor by the same amount so every old cell keeps its world position (the infirmary's anchor went from 0 to 5).
For a small fix in a big blueprint, change single cells with `python tools/blueprint.py set <file> LAYER ROW COL VALUE ...`
(a palette letter or a block state; it adds the palette entry and splits a `layer a-b:` range so only that layer
changes), then validate again. It gives new states plain letters with no marker; a cell that needs a marker needs its
own palette line. To give a plain room structure without moving anything, only touch cells that are plain wall from floor
to ceiling (pilasters) and rows that are clear from wall to wall (ceiling beams).

Aim for the reference builds, not for "correct". Before calling a build done, check it against
the list in the style guide's "Quality checklist".

## Placing from code

```java
var marked = Blueprint.load("bunker_lab").place(level, origin.offset(2, 0, 11), Rotation.NONE);
marked.getOrDefault("loot:lab", List.of()).forEach(pos -> fillChest(pos));
```

- `place(level, pos, rotation, topLayer)` places only up to a layer (useful for ruins and previews).
- `markerOffsets("pod")` returns marked cells relative to the anchor, for code that needs
  positions without placing (the bunker's pod assignments use this kind of data).
- Placement never replaces bedrock or other unbreakable blocks, and writes cells without updates, so two-block blocks
  (doors, beds, Cryochambers) must list both halves explicitly. Afterwards it makes vanilla's settling pass: every block
  works out its shape from its neighbours, so stair corners, pane and fence arms and wall joints come out right in any
  order and rotation. A block the game would remove is left as drawn and pops off at the first update beside it; the
  validator and `BlueprintPlacementGameTests` catch those.
- Ruins exported with `to-nbt` must be exported again after any change to their blueprint.
- `/heroesevolved blueprint place <name> [rotation]` places one at your feet in a live world.

## Starting from an in-game build

Players can build something by hand, save it with a vanilla **structure block** (Save mode), and
hand over the `.nbt` from `saves/<world>/generated/minecraft/structures/`. Convert it with
`python3 tools/blueprint.py from-nbt <file.nbt> --out <name>.blueprint`, then rename the palette
letters to memorable ones, add markers, and continue the loop from step 3.

## Existing blueprints

| Blueprint | Placed by | Notes |
| --- | --- | --- |
| `bunker_chamber` | `BunkerBuilder` at bunker (0,0,18), anchor 7 0 0 | 14 `@pod` cells and `@log:orientation` must keep their positions. The bunker is built once per world: anything added later reaches old worlds only if `BunkerBuilder.upgrade` adds it (bump `BunkerData.LAYOUT`; layout 2 added Exhibit 1 with `Blueprint.addMissing`, layout 4 the `@beacon` alarm beacons). A new bunker is built with its lights dark (`BunkerPower.off`) until its first sleeper steps out of a pod, so a light added to a room should be one `BunkerPower.off` knows |
| `bunker_records` | bunker (0,0,1), anchor 5 0 0 | `@log:lockdown`, `@loot:records`, `@sign:receiver`, `@signal:receiver/display/indicator/antenna`. Its north wall opens three wide onto the vault (`BunkerStair`) |
| `bunker_corridor` | bunker (0,0,11), anchor 2 0 0 | doors at both ends and to the lab and infirmary; `@sign:lab`, `@sign:cell`, a `@beacon` |
| `bunker_lab` | bunker (2,0,11) | 13 x 7 x 8 since November 2026 (it grew east and up into the rock). `@log:helixite` lectern at (3,1,5) and `@loot:lab` chest at (6,1,3) keep their cells: `BunkerLayoutUpgrade` and the tests read them. The door at x 0, z 3..4 meets the corridor |
| `bunker_cell` | bunker (-9,0,11), anchor 5 0 0 | The infirmary, 13 wide since November 2026: the old room keeps every cell (the gray cot at world (-6,1,14), `@signal:survivor` spots, chest at (6,1,6) old grid) and opens through an arch into a west ward. `@sign:infirmary` |
| `bunker_generator`, `bunker_security` | bunker (5,0,1), (-14,0,1) | `@sign:cells`; `@loot:security`, `@sign:checkpoint` (hung on a chained block), `@sign:maintenance_hatch` over the hatch the tests check |
| `bunker_office` | bunker (-14,-6,3), under security's maintenance hatch | `@sign:office`; skipped on shallow worlds |
| `lastday_stairwell` | `PrologueService.LastDayData.reset` at the Last Day safe house's origin (time_fracture, under Primatech) | Switchback from the lobby to the vault door; fully solid or air (no `_`) so it fills the old ladder shaft. The city draws y 63 up (`Landmarks.safeHouse`); the vault's plug is placed by `PrologueService.vault`. `PrologueGameTests.walkable` walks it |
| *(code)* `story/bunker/BunkerStair` | `BunkerBuilder.build`, after the rooms, when the bunker is at least 12 below the surface | The present-day way out: the vault (stuck open, cracked, overgrown) in records' north wall, a switchback stair in the rock north of the bunker (x -3..15, z -9..0; flights along x 3..12, landings at both ends), and a ruined lobby at spawn with the stair coming up through a railed slot, as in the Last Day. Drawn in Java because its depth depends on the terrain. Shallower worlds keep the ladder and hatch |
| `sylar_shop` | `WatchmakerArena.enter`, rebuilt before every fight in `heroesevolved:watchmaker_shop`; anchor is the clock face's middle | `@door:front` locks in phase two, `@door:back` bursts, `@alley:end`, `@rail:n/s`, `@spawn:*`. Preview with `tools/watchmaker-preview.init.gradle` |
| `primatech_substation` | `PrimatechArena`, rebuilt before every fight in `heroesevolved:primatech_substation`; anchor is the room's middle floor block | Elle's arena, drawn by a draft script. `@bank:N`, `@breaker:N`, `@bulb:N`, `@rod:N`, `@conduit:N` must match `PrimatechGeometry` (a game test checks). Preview with `tools/elle-preview.init.gradle` |
| `signal_site` | `SignalSite` (Primatech Field Relay 07, over a buried stasis wing) | Many `@signal:*` and `@sign:*` cells that code reads with `markerOffsets`; don't move them. `LastSignalGameTests` places it. Preview with `tools/signal-site-preview.init.gradle` |
| `ruins_*` | world generation (jigsaw structures) | exported to `structure/ruins/<name>.nbt` with `to-nbt`; `@loot:<table>` becomes the chest's loot table. Preview with `tools/ruins-preview.init.gradle` |

Odessa, the city of the Time vision and the avenue at 6:14, is drawn in Java rather than from blueprints: `time/intro/city/` (`CityPlan` decides what stands where, `Building` draws facades and crowns, `Streets` the street furniture, `Landmarks` city hall, the church, Kirby Plaza and Primatech, `Squares` the Commons, Odessa Square, tower plazas and subway entrances, `BlockFont` billboard lettering, `Apartments` the Odessa Arms). Since November 2026 it is laid out like a small Manhattan: the two great towers (the Odessa State Building and the Bishop Building) flank the avenue in the middle of town and the skyline steps down to brownstone rows at the edges. The story pins some of it: the towers west of Kirby Plaza and north of the vision's vantage (blocks 3,1 and 3,2) keep their avenue faces on the lot line (x = -4) up past y 120, because Sylar is thrown into them (`PrologueGameTests.theSkyFightHasRoomAndAWallToHit`); nothing in the plaza rises past y 72; the Arms keeps its door, plate and wake positions (`Apartments`). Bump `FractureCity.VERSION` after any change so built worlds redraw. Every write goes through a clipped `CityCanvas`, so `FractureCity.rebuild` can put back any rectangle exactly. Keep the street grid: the avenue on x 0..7 and the roads every 32 blocks are where `AvenueService` puts its people, door and paths. Photograph it with `tools/time-city-preview.init.gradle`.

- **The outskirts** (`city/Outskirts`) ring the city 96 blocks deep (`CityGrid.OUTER_MIN..OUTER_MAX`) on the same 32-block grid:
  suburbs in ring 1, houses and oil leases in ring 2, prairie in ring 3, arterial highways every fourth line, a rail
  line south of town. Past the ring, the dimension's own flat ground is grass. `FractureCity` builds the city's chunks
  first (`ready()`), then the outskirts nearest-first (`complete()`).
- **Every write that leaves a wall hangs from it**, and only on street faces (`Building`'s `Face` helper: depth 0 is the
  wall plane, negative depths are over the sidewalk). Window panes sit between two-deep piers so both ends are held.
- **Check it block by block.** `city/CityAudit.run(level, x0, z0, x1, z1)` reports floating blocks and pieces, blocks
  that can't stay (`canSurvive`), glass-pane stubs, and doors that open into a wall or onto a drop. The city preview
  prints it as `CITY_AUDIT` once the city is complete, and `CityAuditGameTests` fails on any fault. The bunker preview
  prints `BUNKER_AUDIT` for the bunker's box. Keep both clean.
- **Dark floor.** `CityAudit.run(..., true)` also reports cells a monster could stand on with no block light and no sky
  light (they spawn monsters day and night). Run it only once the light has settled, never right after building, and
  only over what you built: the caves round a build are dark too. The bunker preview prints it as `BUNKER_DARK` for the
  rooms and the stairwell. Odessa doesn't need it: no monster spawns there on its own (`time/intro/CityMonsters`).
- Paving over ground in the outskirts clears the scrub on top (`Outskirts.floor`), or the plants are left standing on concrete.
