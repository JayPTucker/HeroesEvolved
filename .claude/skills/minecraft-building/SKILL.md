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
and recipes for furniture and props. If `references/images/` has pictures, look at them as well.

## The loop

Building well means looking at what you built. Work in this loop and don't skip the looking:

1. **Plan the footprint.** Write down the room's job, its size, where doors connect, and which
   cells other code depends on (spawn points, pods, lecterns tests check). If the structure plugs
   into existing coordinates (like the bunker), work out the anchor and offsets first.
2. **Draft the blueprint.** Small rooms: type the grids directly. Large or symmetric builds:
   draft with `tools/blueprint_draft.py` in a throwaway script in your scratchpad, and commit only the
   `.blueprint` it writes. Prefer `StateGrid`: its cells hold block states directly and letters are
   assigned on write, so two blocks can never collide on one letter.
3. **Validate.** `python3 tools/blueprint.py validate <file>` checks grid sizes, palette letters,
   and every block id against the real game's block list. Fix every error.
4. **Render.** `python3 tools/blueprint.py render <file> --cut <layer> --out <png>` draws an
   isometric view (cut away above the layer so you see inside) and a plan of every layer. Read
   the PNG. Check the silhouette, door alignment, and that nothing floats or is buried.
5. **Screenshot in-game.** The render uses average texture colors; only the game shows real
   lighting and models. Run
   `./gradlew runClient --init-script tools/blueprint-preview.init.gradle -Pblueprint=<name> [-Pcut=<layer>]`
   and read `build/blueprint-preview/screenshots/<name>-{se,sw,ne,nw,top,inside}.png`.
   (Set `JAVA_HOME` to the Java 21 JDK first; on this machine it is
   `$HOME/.jdks/jdk-21.0.13+9/Contents/Home`.) For the bunker in context, use
   `tools/bunker-preview.init.gradle`, which starts a fresh world and tours every room.
6. **Fix what you see, then repeat 3–5.** Typical fixes: rooms too dark, a flat wall that needs
   texture, decorations that read as clutter, a door that lands in a wall.
7. **Wire it up and test.** Place it from code with markers for anything that needs contents, and
   run the gameplay tests: `./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle`.
   If code or tests depend on specific cells, add a test (see `BunkerAndPowersGameTests.blueprintsLoadWithTheirMarkers`).

Show the user the in-game screenshots when you finish: they judge the look, not the grid.

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
- Placement never replaces bedrock or other unbreakable blocks, and uses `UPDATE_CLIENTS`, so
  two-block blocks (doors, Cryochambers) must list both halves explicitly.
- `/heroesevolved blueprint place <name> [rotation]` places one at your feet in a live world.

## Starting from an in-game build

Players can build something by hand, save it with a vanilla **structure block** (Save mode), and
hand over the `.nbt` from `saves/<world>/generated/minecraft/structures/`. Convert it with
`python3 tools/blueprint.py from-nbt <file.nbt> --out <name>.blueprint`, then rename the palette
letters to memorable ones, add markers, and continue the loop from step 3.

## Existing blueprints

| Blueprint | Placed by | Notes |
| --- | --- | --- |
| `bunker_chamber` | `BunkerBuilder` at bunker (0,0,18), anchor 7 0 0 | 14 `@pod` cells and `@log:orientation` must keep their positions |
| `bunker_records` | bunker (0,0,1), anchor 5 0 0 | `@log:lockdown`, `@log:quiet_years`, `@loot:records` |
| `bunker_corridor` | bunker (0,0,11), anchor 2 0 0 | doors at both ends and to the lab and cell |
| `bunker_lab` | bunker (2,0,11) | `@log:helixite`, `@loot:lab` |
| `bunker_cell` | bunker (-9,0,11) | `@log:gabriel`, `@loot:cell`; `BunkerBuilder.inCell` matches its interior |
| `bunker_generator`, `bunker_security`, `bunker_entrance` | bunker (5,0,1), (-14,0,1), surface hatch | `@loot:security`, `@sign:*` |
| `bunker_office` | bunker (-14,-6,3), under security's red rug | `@log:bennet`, `@loot:office`, `@painting:bennets`; skipped on shallow worlds |
| `ruins_*` | world generation (jigsaw structures) | exported to `structure/ruins/<name>.nbt` with `to-nbt`; `@loot:<table>` becomes the chest's loot table. Preview with `tools/ruins-preview.init.gradle` |
