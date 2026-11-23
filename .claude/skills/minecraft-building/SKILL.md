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

**On Windows** (this machine) the commands below that say `python3` are `py -3` (or `python`); `python3` is what
GitHub's build and macOS use. Python 3.12 lives in `%LOCALAPPDATA%\Programs\Python\Python312`; a shell started before
it was installed won't find it on the PATH, so call `python.exe` there by its full path. `blueprint.py` needs only the
standard library. `JAVA_HOME` is already set to a Java 21 JDK. **Set `PYTHONIOENCODING=utf-8` for every `blueprint.py`
command**: `validate` and `stats` print palette letters such as Greek ones, and the Windows console crashes on them
(`UnicodeEncodeError`) before it says PASS.

Related notes: Odessa's and the bunker's build lessons are in
[docs/agents/story-and-scenes.md](../../../docs/agents/story-and-scenes.md); staging and photographing rules for every
preview are in [docs/agents/previews.md](../../../docs/agents/previews.md); a boss arena's rules (rebuilt each fight,
markers checked against a geometry class) are in the bosses skill ([../minecraft-bosses/SKILL.md](../minecraft-bosses/SKILL.md)).

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
   `python3 tools/blueprint.py stats <file>` counts every block state, which is how to check the palette ratios in
   `references/techniques.md`.
4. **Render.** `python3 tools/blueprint.py render <file> --cut <layer> --out <png> [--view se|sw|ne|nw] [--scale 6]` draws an
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
   `tools/bunker-preview.init.gradle`, which starts a fresh world and tours every room. A finished preview prints
   `PREVIEW_DONE`; on Windows Gradle can still report the run as failed after it, and the screenshots are fine.
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
- **Java that places scenery block by block** (Odessa, `BunkerStair`, `PastBunker`, `FacilityService`, an arena's
  extras) follows the same facing and support rules as a blueprint (`references/block-states.md`): put supports down
  before the things they hold, and check the result with `CityAudit`, because the validator only reads blueprints.
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
| `stasis_*` and `stasis_*_past` (twelve rooms: `airlock`, `commons`, `hall`, `records`, `east`, `generator`, `lab`, `west`, `infirmary`, `containment`, `maintenance`, and `cave` with no past) | `BunkerBuilder` (the present) and `PastBunker` (the `_past` twins, for the Last Day and Night Three), each room's grid corner at the bunker cell `BunkerLayout.STASIS` lists | **Generated by `tools/generate-bunker.py`: change the script, never the files.** The script draws the whole bunker once as the Company built it, then ages a copy by 103 years with a cause for every change, and writes each room twice. Cells code reads are markers the aging never touches: 15 `@pod` (the hall's 14 and the Helix Ward's private pod, where `BunkerLayout.podOffset` says; a game test compares them), `@sign:pod01..15`, `@log:orientation/lockdown/helixite/weir/bennet4r`, `@loot:records/security/lab`, `@signal:receiver/display/speaker/microphone/survivor`, `@beacon`, `@painting:explosion:north`, `@painting:bennets:west`. The scene points the Last Day and Night Three use are in `BunkerLayout.Scene`. The bunker stays 13 high: the Last Day's stairwell meets the vault at a fixed depth. A new bunker is built with its lights dark (`BunkerPower.off`) until its first sleeper steps out of a pod, so a light added should be one `BunkerPower.off` knows |
| `bunker_chamber`, `bunker_records`, `bunker_corridor`, `bunker_lab`, `bunker_cell`, `bunker_generator`, `bunker_security`, `bunker_office` | Nothing new: worlds built before the November 2026 rebuild have these rooms (`BunkerLayout.LEGACY`, `BunkerData.design` 1) | Kept for those worlds: their pods, receiver, lights and `BunkerBuilder.upgrade` / `BunkerLayoutUpgrade` read these cells. Don't change them |
| `lastday_stairwell` | `PrologueService.LastDayData.reset` at the Last Day safe house's origin (time_fracture, under Primatech) | Switchback from the lobby to the vault door; fully solid or air (no `_`) so it fills the old ladder shaft. The city draws y 63 up (`Landmarks.safeHouse`); the vault's plug is placed by `PrologueService.vault`. `PrologueGameTests.walkable` walks it |
| *(code)* `story/bunker/BunkerStair` | `BunkerBuilder.build`, after the rooms, when the bunker is at least 12 below the surface | The present-day way out: the vault (stuck open, cracked, overgrown) in the airlock's north wall, skulls of the people it was shut on outside it and on the stair (`remains`, run once per bunker from `BunkerService`, only into air on a solid floor, never on a tread), a switchback stair in the rock north of the bunker (x -3..15, z -9..0; flights along x 3..12, landings at both ends), and Primatech's ruin at spawn with the stair coming up through a railed slot, as in the Last Day. Drawn in Java because its depth depends on the terrain. Shallower worlds keep the ladder and hatch |
| *(code)* `story/bunker/PrimatechRuin` | `BunkerStair.lobby`, new worlds only | Odessa's real Primatech (`Landmarks.primatechPlan`, on a canvas that writes nothing) moved over the stair and collapsed: two wings, a heap between them, the doors and sign kept, the walk from the stair to the doors clear. Change the building in `Landmarks`; change how it fell here. `PrimatechRuinGameTests` audits it. Photographed as `ruin-*` by `tools/bunker-preview.init.gradle` |
| *(code)* `story/bunker/OdessaRuins`, `OdessaDecay` | New bunkers only, built and settled in saved batches from `BunkerService` | Reuses the complete Last Day city with Primatech's translation, keeps native hills and woods, buries lower floors and leaves road fragments on the slopes. `RuinMaterials` prevents resource farming in this city and Primatech; `OdessaCaches` supplies eight civilian journals and modest scavenging. `OdessaRuinsGameTests` checks terrain, supports, books, resource blocks and reloads. Preview with `tools/odessa-ruins-preview.init.gradle` |
| `the_collection` | `CollectionArena.build`, rebuilt before every fight in `heroesevolved:the_collection`; anchor is the dial plate's middle floor block | Sylar's round hall, drafted by `tools/draft-the-collection.py` from `SylarGeometry`'s numbers (change the script, not the file). `@case:N`, `@cover:N`, `@spawn:sylar`, `@spawn:party`, `@lamp`; a game test compares them with `SylarGeometry`. Preview with `tools/sylar-preview.init.gradle` |
| `level_five` | `LevelFive.build`, once per `LevelFive.VERSION`, in `heroesevolved:level_five`; anchor is the floor under the lift's doors | Primatech Level 5, where Sylar was kept: a corridor of ruined cells and his glass-fronted cell. Drafted by `tools/draft-level-five.py` (change the script, not the file). `@spawn:arrival`, `@exit` (the lift's button), `@glass`, `@pod`, `@spawn:prisoner`, `@sign:list0`/`head` (headers), `@sign:list1..4` (the list, in reading order: from the corridor facing west, z 11 is the left column), `@lever`, `@sign:release`, `@sign:lift`. Level 5 is dark (`lockdown` has no ambient light): its list signs glow and its bulbs are exposed copper. Preview with `tools/level5-preview.init.gradle` |
| `signal_site` | `SignalSite` (Primatech Field Relay 07, over a buried stasis wing) | Many `@signal:*` and `@sign:*` cells that code reads with `markerOffsets`; don't move them. `LastSignalGameTests` places it. Preview with `tools/signal-site-preview.init.gradle` |
| `village_town_house_1`, `village_town_house_2` | `village/Projects` and `Growth` (towns build them new and rebuild homes as them), from `structure/village/town_house_N.nbt` | Drawn in oak and cobblestone facing north (door in the north wall); `village/Styles` recolours them per village kind. **Export again with `to-nbt` after any change**: `VillageUpgradeGameTests` compares each export with its blueprint. Photographed by `tools/roads-preview.init.gradle` (the blueprint preview stages next to the bunker at spawn, which hides them) |
| `ruins_*` (`bennet_house`, `burnt_house`, `collapsed_house`, `isaac_studio`) | world generation (jigsaw structures) | Generated by `tools/generate-scattered-ruins.py`, then exported to `structure/ruins/<name>.nbt` with `to-nbt`; `@loot:<table>` preserves story loot. Pools use `ancient_ruins` to preserve native terrain and trees; `OCEAN_FLOOR_WG` plus `RuinsWaterProcessor` floods submerged rooms and floats barrels/loose boards, retaining inventory NBT. Ordinary/story placement regions are 48/96 chunks. Preview with `tools/ruins-preview.init.gradle`, adding `-Pocean` for seabed and surface views |
| *(code)* `story/bunker/PastBunker` | Night Three (`story/night/NightThreeBunker`, in `heroesevolved:night_three`) and the Last Day (under Primatech Paper in Odessa) | The bunker as it was before the hundred years: the `stasis_*_past` rooms. Its rock is poured once, never above the rooms' ceilings (under Odessa the city's cellars and subway steps start there); each run puts the rooms back and resets the pods and doors. Preview with `tools/night-three-preview.init.gradle` |
| *(code)* `expansion/FacilityService` | the Hourglass Annex mission (`heroesevolved:unwritten_annex`) | A bounded mission site that never overwrites a player's settlement; it builds in batches. `facilitybuildsinbatchesandmanualroutecansavebothcaptives` is a known flaky test (it waits on chunk loading) |

**Building an arena for a boss.** A boss's arena is a blueprint rebuilt before every fight in its own pocket dimension,
so it may be broken freely. Put its anchor at the room's middle floor block, mark every cell the fight reads
(`@bank:N`, `@door:front`, `@spawn:*`), keep those positions in one geometry class, and add a game test that compares
the markers with the class (`SylarGeometry`). Register the dimension for game tests too. The rest is in the bosses
skill.

Odessa, the city of the Time vision and the avenue at 6:14, is drawn in Java rather than from blueprints: `time/intro/city/` (`CityPlan` decides what stands where, `Building` draws facades and crowns, `Streets` the street furniture, `Landmarks` city hall, the church, Kirby Plaza and Primatech, `Squares` the Commons, Odessa Square, tower plazas and subway entrances, `Market` the grocery on the avenue the Last Day
sends everyone to, `BlockFont` billboard lettering, `Apartments` the Odessa Arms; `CityGrid` holds the grid's numbers,
`Feature` is anything drawn with the columns it may touch, and `CityDraw` draws the plan in order). Since November 2026 it is laid out like a small Manhattan: the two great towers (the Odessa State Building and the Bishop Building) flank the avenue in the middle of town and the skyline steps down to brownstone rows at the edges. The story pins some of it: the towers west of Kirby Plaza and north of the vision's vantage (blocks 3,1 and 3,2) keep their avenue faces on the lot line (x = -4) up past y 120, because Sylar is thrown into them (`PrologueGameTests.theSkyFightHasRoomAndAWallToHit`); nothing in the plaza rises past y 72; the Arms keeps its door, plate and wake positions (`Apartments`). Bump `FractureCity.VERSION` after any change so built worlds redraw. Every write goes through a clipped `CityCanvas`, so `FractureCity.rebuild` can put back any rectangle exactly. Keep the street grid: the avenue on x 0..7 and the roads every 32 blocks are where `AvenueService` puts its people, door and paths. Photograph it with `tools/time-city-preview.init.gradle`.

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
