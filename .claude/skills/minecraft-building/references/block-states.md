# Block states: which way things face and what holds them

Every rule here was read from the game, not from a guide: `BlockStateFactsGameTests` prints the shape each stair, door,
trapdoor and gate state fills and which neighbour each wall-mounted block hangs on, and the block classes' own
`canSurvive` code says what each one needs. Several popular guides (and other agents' skills) get some of these
backwards; where they do, the rule here is the right one.

When in doubt, ask the game's facts instead of guessing:

```text
python tools/blueprint.py facts "oak_stairs[facing=east,half=top]" "chain[axis=x]"
```

prints a state's properties, defaults, and what each of its six faces can hold. `validate` uses the same facts to catch
wrong property values, unsupported blocks, mismatched halves, wrong stair corners and hinges that meet in the middle,
and `BlueprintPlacementGameTests` places every blueprint and asks the game itself.

## Directions in a blueprint

Rows run north to south (z grows southward), columns west to east (x grows eastward), layers bottom to top. So:

- the first row is the north edge, the last the south edge;
- a block against a room's **west** wall, sticking out into the room, faces **east**;
- clockwise from north is east, south, west. "Left" in a state name means **counter-clockwise of the facing**
  (north's left is west, east's is north, south's is east, west's is south): it is the left hand of someone looking the
  way the block faces.

## The one rule for things on walls

**`facing` is the way it sticks out: the support is on the opposite side.**

| Block | Example | Held by |
| --- | --- | --- |
| wall torch, soul and redstone wall torch | `wall_torch[facing=north]` | the block **south** of it (a sturdy face) |
| ladder | `ladder[facing=east]` | the block **west** of it (sturdy) |
| wall sign, wall banner | `oak_wall_sign[facing=south]` | the block **north** of it (any block the game counts as solid, which includes fences, bars, chains and glass) |
| button, lever on a wall | `stone_button[face=wall,facing=west]` | the block **east** of it (sturdy) |
| tripwire hook | `tripwire_hook[facing=north]` | south (sturdy) |
| amethyst buds and clusters | `amethyst_cluster[facing=up]` | the block below (sturdy) |
| open trapdoor | `oak_trapdoor[facing=north,open=true]` | nothing; its panel lies against the **south** edge of its cell |
| closed door | `oak_door[facing=north]` | sits on the **south** edge of its cell |

Three exceptions hang on the side they face:

- **Wall bells** (`bell[attachment=single_wall,facing=north]`) hang from the block **north** of them.
- **Cocoa** (`cocoa[facing=east]`) grows on a jungle log to its **east**.
- **Vines and glow lichen** name the side they cling to: `vine[north=true]` clings to the block north of it. A vine face
  needs a **full** face there: not iron bars, panes, fences, walls, chains, or a slab's or stair's side. A vine face
  with nothing behind it is still held if the vine above it has the same face, so a curtain can hang from one held vine
  at the top. `up=true` clings to the block above.

These need nothing at all and can be placed anywhere: wall hanging signs, skulls and heads, end rods, lightning rods,
grindstones, chains, flower pots.

Item frames and paintings are **entities**, not blocks: they can't go in a palette. Paintings use the
`@painting:<variant>:<facing>` marker (see `blueprint-format.md`); there is no item-frame marker yet.

## Floors and ceilings: what holds what

Each block needs a particular kind of face from the block it stands on or hangs from:

| Needs | Blocks |
| --- | --- |
| a **sturdy** top face below | doors, buttons and levers on the floor, redstone dust |
| the **centre** of the face (a post is enough) | standing torches, candles, lanterns (standing below, hanging above), ceiling hanging signs, spore blossoms |
| a **rigid** top face below | rails, pressure plates (or a centre support) |
| a **solid** block below | standing signs, standing banners |
| anything that isn't air below | carpet (including moss carpet) |
| **soil** below: grass, dirt, coarse or rooted dirt, podzol, mycelium, moss block, mud, farmland | grass, ferns, flowers, saplings, azalea (also clay), tall plants. Dead bushes also grow on sand and terracotta. **Not** on dirt paths, gravel, planks or stone |
| a **sturdy** bottom face above | hanging roots, buttons and levers on a ceiling, cave vines (or the cave vine above) |

What common blocks offer (from `blueprint.py facts`):

| Block | Top face holds | Bottom face holds |
| --- | --- | --- |
| full blocks, glass, barrels, bookshelves, copper grates | everything | everything |
| **bottom slab** | nothing: no torch, lantern or carpet-free lamp stands on it | everything |
| **top slab** | everything | nothing: no lantern hangs under it |
| bottom stair | nothing (only half its top is up) | everything |
| upside-down stair | everything | nothing |
| closed bottom trapdoor | nothing | everything |
| closed top trapdoor | everything | nothing |
| fence, wall, iron bars, glass pane, **vertical** chain, end rod, lightning rod, anvil, bell | centre only: lanterns, candles and torches | centre only: hanging lanterns |
| **horizontal chain** (`chain[axis=x]`) | nothing | nothing: a lantern can't hang from it |
| dirt path, farmland, lectern, campfire, 1-layer snow | nothing sturdy (no torch, door or button) | everything |
| leaves | a full face (vines) but nothing sturdy: no torch on leaves | the same |
| hopper | rigid (rails) | centre (a lantern can hang under it) |

So a bracket lamp is a post, an iron-bar arm and a hanging lantern under the arm (`signal_site` does this); a chain
laid sideways is a cable, not a hook.

## Stairs

`facing` is the side with the **tall back**. `half=bottom` sits on the floor; `half=top` is upside down (the full slab
on top, the step hanging below).

```text
facing=north, half=bottom, seen from above       N
   ##  <- the raised half, along the north edge    W   E
   ..  <- the low step                              S
```

- **Roofs: stairs face uphill.** The south slope of a roof uses `facing=north`; the tall back is up the slope.
- **Seats: the sitter faces away from `facing`.** A stair chair with `facing=north` has its back to the north and
  someone sitting in it looks south. (VibeCraft's furniture skill says the opposite; it is wrong.) Put a chair at a
  table's south side with `facing=south`, so its back is away from the table.
- **Sills, lintels and eaves are upside-down stairs** facing the wall: the full top meets the wall line and the step
  hangs out as a lip with a shadow under it.

### Corners

The game picks a stair's shape from the stairs beside it, and it redraws the shape whenever a neighbour changes, so
**write the shape the game would pick**. `validate` works it out exactly and tells you when a blueprint draws something
else. The rules (`StairBlock.getStairsShape`):

- **Outer corner** (one raised quarter): the stair **in front** of it (on its facing side) runs at right angles and has
  the same half. `outer_left` if that front stair faces this one's left, `outer_right` if it faces its right.
- **Inner corner** (three raised quarters): otherwise, the stair **behind** it runs at right angles with the same half.
  `inner_left` / `inner_right` by the same left and right.
- A same-facing, same-half stair on the side it would turn toward blocks the corner (the run just continues).

Raised quarters for each state (the same quarters fill the lower half when `half=top`):

| facing | straight | inner_left | inner_right | outer_left | outer_right |
| --- | --- | --- | --- | --- | --- |
| north | NW NE | NW NE SW | NW NE SE | NW | NE |
| south | SW SE | NE SW SE | NW SW SE | SE | SW |
| west | NW SW | NW SW SE | NW NE SW | SW | NW |
| east | NE SE | NW NE SE | NE SW SE | NE | SE |

Worked examples (each cell is facing:shape; worked out with the game's rule):

```text
A hip roof's course: every stair's tall side points IN (uphill)   A plinth or rim: tall sides point OUT
S:outer_left  S  S  S:outer_right                                  N:inner_left   N  N  N:inner_right
E             .  .  W                                              W              .  .  E
E             .  .  W                                              W              .  .  E
N:outer_right N  N  N:outer_left                                   S:inner_right  S  S  S:inner_left
```

In a hip roof the four corners are **outer** stairs (one raised quarter pointing at the middle); in a ring whose tall
sides face out they are **inner** stairs. A valley (two roofs meeting in an inside corner) uses inner stairs.

## Slabs

`type=bottom` (the default) fills the lower half, `type=top` the upper, `type=double` the whole cell. A run of
alternating bottom and top slabs climbs at half a block per block (a 1-in-2 pitch). Use top slabs for ceilings and
shelves, bottom slabs for steps, seats and table tops. Remember the support table above: nothing stands on a bottom slab
and nothing hangs under a top slab.

## Trapdoors

- **Closed**: a thin plate on the bottom (`half=bottom`) or top (`half=top`) of the cell. `facing` only moves the hinge.
- **Open**: a panel standing against the edge **opposite** its facing, the full height of the cell. `half` doesn't move
  an open panel.
- **Shutters** lie flat on the wall beside the window: on a wall whose outside faces north, the shutter cells are north
  of the wall and the trapdoors are `facing=north,open=true`.
- **Railings** stand on the far edge: a fire escape one block out from a north wall is a floor of closed top trapdoors
  with open trapdoors facing **south** (into the wall) above it, so each panel stands on the outer edge
  (`Building`'s fire escapes do this).
  `validate` notes an open trapdoor whose panel lies against air with a wall on its other side: usually a reversed
  shutter.
- Other uses: a table top or counter (closed, `half=top`, on a post), chair arms (open, either side of a stair seat),
  drawers and cabinet fronts (open, against a block), floor hatches (closed, `half=bottom`, in the floor line).

## Doors

- Two cells, `half=lower` and `half=upper` directly above it, with the **same** `facing`, `hinge` and `open`.
  Blueprints list both halves. The lower half needs a sturdy block under it.
- **`facing` is the way you face walking through from the side it closes on.** A closed door sits on the edge of its
  cell **opposite** its facing and swings open toward its facing. For a front door entered from the street to the south,
  use `facing=north`: the closed door sits on the street side of the cell and opens into the house.
- **`hinge=left` puts the hinge on the facing's counter-clockwise side** (the left hand of someone walking through), and
  `hinge=right` on the clockwise side. An open door lies along its hinge edge.

| facing | closed on the | hinge=left edge | hinge=right edge |
| --- | --- | --- | --- |
| north | south edge | west | east |
| south | north edge | east | west |
| west | east edge | south | north |
| east | west edge | north | south |

- **Double doors: hinges on the outer sides.** Of the two cells, the one on the facing's counter-clockwise side is
  `hinge=left` and the other `hinge=right`. For `facing=north` that is `left` in the west cell and `right` in the east
  cell; for `facing=south`, `right` in the west cell and `left` in the east. `validate` reports a hinge against the door
  beside it. (The Odessa Arms' lobby doors had both hinges in the middle until November 2026.)

## Fence gates, fences, walls, panes and bars

- A gate's `facing` is the way it opens. A closed gate is a bar **across** its facing: `facing=north` or `south` fills a
  gap in a fence that runs **east to west**. `in_wall` (lowered to sit in a stone wall) is set by the game.
- Fences, walls, panes and iron bars join their neighbours by themselves: leave their `north/south/east/west/up`
  properties out. Placement settles them (see below). A pane with nothing on either side is a post, not a window.
- Iron bars and panes join walls; fences join fence gates and sturdy faces.

## Beds and chests

- **Beds**: the head is on the facing side. `red_bed[facing=east,part=foot]` with `red_bed[facing=east,part=head]` in
  the cell east of it. Both halves share the facing.
- **Double chests**: both halves share the facing (the front, where the latch is). `type=left` is on the facing's
  counter-clockwise side: for a chest facing south, `left` is the east cell and `right` the west cell. A chest with
  `type=left/right` and no partner turns single.

## Signs and banners standing up

`rotation` counts sixteenths of a turn clockwise from south: 0 faces south (readable from the south), 4 west, 8 north,
12 east. Skulls standing on the floor count the same way.

## Blocks with power or neighbours in their state

- **Copper bulbs** flip on when their power rises. One drawn `lit=true` beside a powered lever or block must also say
  `powered=true`, or it turns itself off as it is placed. Unpowered bulbs should say `powered=false`.
- **Scaffolding** placed by code keeps `distance=7` and can't stand: write `distance=0` (see the style guide).
- **Leaves** written without `persistent=true` decay unless a log is within six blocks. Write `persistent=true` for
  every hedge, planter or overgrowth leaf.

## How the mod places a blueprint

`Blueprint.place` writes every cell without shape updates (cells go north to south, so a vine or pane would otherwise be
judged before the wall south or east of it exists, and a vine would be deleted), then makes the pass vanilla structures
make: every placed block, and the world's blocks round the edge, works out its shape again from its neighbours, twice.
So stair corners, pane and bar arms and wall joints come out right in any order and any rotation. A block the game
would remove (a torch with nothing behind it, a lantern under a top slab, grass on planks) is **left as drawn**, which
looks fine until the first block update next to it, when it pops off. That is what `validate` and
`BlueprintPlacementGameTests` are for.

Ruins exported with `to-nbt` are placed by vanilla's structure code instead, which does remove such blocks.

## Furniture recipes with the right states

Each is drawn for a room whose door is in the south wall; rotate the blueprint, not the recipe.

- **Chair at a table's south side**: `oak_stairs[facing=south]` (back away from the table), with arms of
  `oak_trapdoor[facing=west,open=true]` in the cell west of it and `oak_trapdoor[facing=east,open=true]` in the cell
  east of it. Each arm faces away from the seat, so its panel lies against the chair.
- **Armchair with sign arms**: the same stair, with `oak_wall_sign[facing=west]` in the cell west of it and
  `oak_wall_sign[facing=east]` in the cell east of it: each sign hangs on the stair beside it (stairs count as solid).
- **Table**: a fence post with `oak_pressure_plate` on top for a small one; for a long one, fence posts under
  `spruce_slab[type=top]` or `spruce_trapdoor[half=top]` tops. A lantern or candle can stand on a fence post, never on a
  bottom slab.
- **Sofa**: a row of stairs facing the wall behind them, ends closed with open trapdoors.
- **Shelf**: `spruce_slab[type=top]` or a closed top trapdoor against the wall, with candles or flower pots on it.
- **Ceiling lamp**: `chain` (vertical by default) from a full block or bottom slab, `lantern[hanging=true]` under it.
- **Window box**: open trapdoors on three sides of a dirt or moss block under the window, flowers in the soil.
- **Counter with a sink**: a cauldron set in a row of barrels or smooth stone, `lever[face=wall]` on the wall behind it as
  a tap, facing out of the wall.

## Common mistakes, caught

| Mistake | Right |
| --- | --- |
| `wall_torch[facing=south]` on a south wall | `facing=north`: it sticks out north, away from the wall |
| a stair seat facing the table | the stair's facing points away from the table |
| the hinges of a double door meeting in the middle | the counter-clockwise door `hinge=left`, the other `hinge=right` |
| only the lower half of a door or bed | list both halves with the same facing (and hinge, open) |
| a lantern under a top slab or a sideways chain | under a full block, bottom slab, vertical chain, fence, wall or bars |
| a torch on a bottom slab, path or leaves | on a full block, top slab, upside-down stair or post |
| a vine on iron bars, a pane or a slab's side | on a full face, or hanging from the vine above |
| grass or flowers on planks, gravel, paths or stone | put dirt, coarse dirt, moss or grass under them, or use moss carpet |
| straight stairs where two runs meet | the corner shape the game would pick (`validate` names it) |
| a property the block doesn't have (`facing=up` on stairs) | `validate` lists the real ones |
