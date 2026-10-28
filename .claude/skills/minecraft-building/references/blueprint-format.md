# Blueprint format

```text
# Comments start with # (outside layers, and on their own line inside layers).
name: bunker_lab                 # optional; defaults to the file name
anchor: 7 0 0                    # grid cell (x y z) that lands on the placement position
palette:
  D = deepslate_tiles            # "minecraft:" is implied when there is no namespace
  L = lectern[facing=north] @log:helixite
  P = heroesevolved:cryochamber[facing=east,half=lower,open=false] @pod
  p = heroesevolved:cryochamber[facing=east,half=upper,open=false]

# floor
layer 0:
DDDDD
DFFFD
DDDDD

# walls: a range repeats the same grid on several layers
layer 1-3:
DD.DD
D...D
DDDDD
```

## Axes

- **Layers** go bottom to top (`y`). Layer numbers must start at 0 with no gaps.
- **Rows** in a layer go north to south (`z`, first row is the north edge).
- **Columns** go west to east (`x`, first column is the west edge).

So a block that should face the inside of a room's west wall faces `east`.

## Palette

- One character per entry, any non-space character. Choose memorable letters (`D` deepslate,
  `G` glass, `L` lectern) and lowercase for a variant or upper half (`P`/`p`).
- States use the game's own syntax: `block_id[property=value,...]`. Unlisted properties keep
  their defaults. The validator rejects unknown block ids.
- Two characters are predefined: `.` is **air** (carves space) and `_` is **keep** (leave whatever
  is already there). Use `_` around irregular shapes and for "behind the wall" cells.
- `@marker` tags every cell using that character. Code receives the world positions by marker name.
- Built-in markers: `@loot:<table>` on a chest or barrel (its loot table in structure exports), `@sign:<id>` on a
  sign (text from the `signs:` section), and `@painting:<variant>:<facing>` on an **air** cell, which hangs the
  `heroesevolved:<variant>` painting with that cell as its anchor. Paintings grow from the anchor toward the
  facing's counter-clockwise side and upward; use `painting_anchor()` in `blueprint_draft.py` to find the
  anchor from the bottom-left cell. The wall behind every covered cell must be solid.
  Conventions: `@log:<id>` for lecterns that get a written book, `@loot:<id>` for chests to fill,
  `@pod` for Cryochambers, `@spawn:<id>` for entity spawn points.

## Rules the validator enforces

- Every row in every layer has the same width; every layer has the same number of rows.
- Every character in a grid is in the palette.
- A palette character is defined once.
- Unused palette entries are reported (not an error), which usually means a typo in a grid.

## Rotation

`place(level, pos, Rotation.CLOCKWISE_90)` rotates the whole blueprint about its anchor and
rotates directional block states with it, so draw blueprints facing one way and rotate at
placement time instead of keeping rotated copies.

## Tools

| Command | What it does |
| --- | --- |
| `python3 tools/blueprint.py validate <files...>` | Format and block-id checks |
| `python3 tools/blueprint.py render <file> [--cut Y] [--scale N] [--out png]` | Isometric view + per-layer plans |
| `python3 tools/blueprint.py stats <file>` | Size, anchor, block counts, markers |
| `python3 tools/blueprint.py from-nbt <file.nbt> [--out file]` | Convert a structure-block save |
| `./gradlew runClient --init-script tools/blueprint-preview.init.gradle -Pblueprint=<name> [-Pcut=Y]` | In-game screenshots from four corners, above, and inside |
