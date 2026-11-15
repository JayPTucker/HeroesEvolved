# Techniques from other builders

Collected in November 2026 from other agents' Minecraft building skills and human building guides, and adapted to how
this project builds (blueprints and Java drawing, not live commands). Each rule is rewritten in our terms; the sources
are listed at the end. Read this with `style-guide.md` (our principles and lessons) and `inspiration.md` (our quality bar).

## Order of work: big shapes first

Plan the massing and silhouette before any detail. A flat box decorated afterwards still reads as a box.

1. **Shape**: footprint, heights, roofline, towers and setbacks. Check it as an outline (the render's top view, or a
   wireframe of a few marker blocks) before filling walls.
2. **Palette**: pick it and check it (see below).
3. **Depth**: piers, recesses, protrusions, trim courses.
4. **Roof**: pitch, overhang, ridge.
5. **Detail**: windows, doors, lights, props.
6. **Weathering**: last, on top of clean geometry, never instead of it.

If a later check fails on shape (a weak silhouette, a roof that floats), go back to step 1 and change the shape. Don't
paint over it with detail.

## Proportion and scale

- **The player is two blocks tall.** Doors are 2 tall, 3 for a grand entrance. Headroom over any walking cell is at
  least 2 clear, 3 over stairs so nobody bumps their head on each step.
- **Ceilings**: 3 is the minimum, 4 comfortable, 5 to 6 grand, 8 and up a cathedral (and then the ceiling needs its own
  detail: see the style guide's "Sizes that feel right").
- **Rooms** (inside, minimum / comfortable): bedroom 5x6 / 6x7, kitchen 5x7 / 6x8, living or dining 7x9 / 8x10, great
  hall 15x20 / 20x25.
- **Scale tiers** (footprint): shack 5x5, cottage 8x10, house 12x14, estate 20x30, mansion 40 and up, castle 60 and up.
  Walls one block thick up to a cottage, two thick from estate scale up. Corridors two wide or more from estate scale
  (three in the bunker, per the style guide).
- **Windows match their wall.** Giant windows on a small house, or tiny ones scattered on a huge wall, both look wrong.
  Line windows up with the rooms behind them, so the outside matches the inside.

## Palette

- **Three to five blocks per build**, each with a job: frame, walls, roof, trim and accent. Add one only when it has a
  job.
- **Roughly 60 / 30 / 10**: the primary block on the walls, a secondary on the roof and trim, an accent on doors,
  windows and lights.
- **The roof darker than the walls**, so the shape reads from far away.
- **Mix texture, not colour.** One dominant block with a few close variants in patches, never an even speckle:
  - white: quartz, calcite, bone block, white concrete, diorite, a little white wool;
  - stone: stone bricks with stone, cobblestone, andesite, and cracked or mossy blocks near the ground, where water
    and time act first.
- **Check the palette at night as well as by day.** Our cities are seen at dusk and the bunker in emergency light.

## Walls: three depths

- Every wall uses at least three depths: a recessed layer (glass, panels), the main plane, and things that stand out
  (piers, quoins, sills, ledges, beams).
- **Trim courses** at every floor line and at the roofline, of slabs or stairs; quoins at the corners; on tall
  buildings, a stringcourse every few storeys.
- **Contrasting corners**: a log, pillar or darker block up each corner frames the wall.
- **Negative space**: leave plain areas so the detailed ones stand out. Don't cover a whole wall in random stairs,
  slabs and trapdoors.

## Roofs and silhouette

- **Three easy pitches**: full blocks stepping two up per block (about 63°), stairs one up per block (45°), and
  alternating bottom and top slabs, half a block per block (about 27°). In between, repeat a short pattern: slab,
  upside-down stair, stair climbs 5 in 7. The longer the pattern, the bigger the roof must be before it reads.
- Never flat unless the building is modern on purpose, and then give the flat roof a parapet, plant and a stair hut (as
  the city's do).
- Always an overhang of at least one block at the eaves; it gives the wall a shadow line. Put upside-down stairs under
  the overhang as a soffit, and close the gable's rake with upside-down stairs too.
- **Odd widths for one ridge.** A building whose roof should peak over its middle (or over a single door) is an odd
  number of blocks wide; one centred on a double door is even, and its ridge is two wide (slabs or a ridge cap).
- **Keep a roof one to two storeys tall** (a storey is 4 to 5 blocks). A 20-wide building under one 45° gable gets a
  roof 10 to 11 tall, which is too much: split it into two or three sections, add a parapet and a walk, or put a floor in
  the roof.
- **Past about 15 by 15, one simple roof looks like a barn.** Use several sections: cross gables, cross hips, a hip with
  a gable end, on a T, L, H or cross plan. Build the highest section first as a roof on its own, then add the lower ones
  and join them; joins form a V, and the blocks inside the V come out last.
- Hip corners are outer stairs and valleys inner stairs: see `block-states.md` for which shape the game draws where, and
  let `validate` check it.
- Buildings set at an angle to the grid: roof them with slabs or full blocks; stairs on a diagonal rarely line up.
- Break up a long roof: more than one level or angle, chimneys, dormers, towers.
- **Round shapes are octagons.** Round towers and domes read best as stepped octagonal rings of stairs; a spire is a
  tapering stack of stairs with an end rod or fence finial.
- **Arches**: round (stairs either side of a keystone), pointed (stairs meeting at the apex), lancet for tall narrow
  windows.

## Windows, doors and lights

- A window has a frame: trapdoor shutters, a sill and a lintel, or a reveal (glass set back a block).
- **Put a light behind stained glass**, or it reads as a dark patch.
- **Hide light where you can**: behind glass, under carpet, in a ceiling coffer. A bare glowing block is a last resort.
- Wall-mounted blocks (wall torches, ladders, levers, buttons, wall signs) are separate block ids from the floor
  versions, and their `facing` is the way they stick out: a `wall_torch[facing=north]` hangs on a south wall. Item
  frames are entities, not blocks. Every facing, hinge, half and support rule is in `block-states.md`, read from the game.
- Carpet, pressure plates, rails, candles and plants need a block under them, and not just any block: nothing stands on a
  bottom slab, no lantern hangs under a top slab or a sideways chain, grass doesn't grow on planks. Furniture stands on
  the floor block, a carpet sits in the cell above it.

## Interiors

Choose how furnished a building is before drawing it:

- **Shell only** (about 40% of the inside dressed): rooms seen through windows, nobody goes in. Most of the city.
- **Hero rooms** (about 70%): a few rooms that can be seen from outside or are visited are fully dressed, the rest
  plain. The Odessa Arms, shops on the avenue.
- **Fully furnished** (about 95%): every room is a place someone works or sleeps. The bunker.

Every dressed room needs: a reason to exist (the props say what it was for), headroom of 2 clear on its walking cells, a
second way out for any large space, and no dark floor where mobs could spawn (except where darkness is the point, and
then lit by something that grew there: see the bunker stair).

## Landscaping

- Every build meets the land with a transition: a footing that follows the ground down, then a ring of mixed ground
  (gravel, coarse dirt, path, grass) before the wild.
- Lay paths on axes and end each view on something worth seeing (a door, a fountain, a tree).
- Leaves are persistent or sit on a log core; hedges are leaves on logs, not leaves alone.
- No curve without a reason; when a path curves, something made it (a tree, a slope).

## Checking: how to look at what you built

Our loop already validates, renders and photographs. These checks catch what the photos miss:

- **Walk the routes at eye level.** A top-down or high shot hides vertical faces, doors and headroom. Put preview cameras
  at eye height (y + 1.6) at several points along the way a player would walk, not only at the best angle.
- **The distance test.** Step back 20 to 30 blocks (or take a wide shot): the roofline, the entrance, the doors and the
  windows must still read. If they don't, the shape is too weak or the detail too busy.
- **Spot-check the plan.** Compare a few corners and centres of the built thing against the blueprint or the plan; a
  rotated or off-by-one placement shows up there first.
- **Re-check a fault before fixing it.** When an audit or inspection reports a problem, confirm it in the world (or the
  blueprint) before changing anything: some reports are false alarms.
- **Fix the cause, not the symptom.** A failing silhouette means changing the shape, not repainting it.
- **Dark cells.** Any floor cell a mob can stand on with no light nearby will spawn mobs. The bunker stair and its
  lichen are the model for lighting a dark place in-story.

## Writing rules so they stick

What makes the best skills easy to follow, and worth copying here:

- Pair each rule with a WRONG and a RIGHT example (a floor placed on top of the grass, against one that replaces it).
- Give numbers rather than adjectives (60/30/10, 1:1 pitch, ceilings of 4).
- Keep the method (how to work) apart from the reference (block states, schema, tables).
- State the axis convention once and stick to it. Research on language models building in Minecraft found that keeping
  3D coordinates and facings straight is where they go wrong most; our blueprint axes (x east, y up, z south, rows north
  to south) and the render step exist for exactly this.

## Where other skills go wrong

Checked against the game while writing `block-states.md` (November 2026):

- VibeCraft's furniture skill says a stair seat's `facing` is the way the sitter looks. It is the way the **back** is:
  the sitter looks the other way.
- It lists `item_frame[facing=south]` as a block; item frames are entities.
- Its armchair hangs `oak_wall_sign[facing=east]` on the left of the seat, which hangs on the block west of the sign, not
  on the chair. A sign arm faces **away** from the seat.
- It says slabs always need `[type=bottom]`; bottom is the default, so it can be left out.

## Sources

- VibeCraft's `building-structures` and `building-with-schematics` skills:
  [agent-skills.md listing](https://agent-skills.md/skills/amenti-labs/vibecraft/building-structures),
  [repository](https://github.com/amenti-labs/vibecraft)
- The Minecraft Java Fabric Claude plugin's builder agent and design skills:
  [README](https://cdn.jsdelivr.net/gh/chapmanjw/minecraft-java-fabric-claude-plugin@main/README.md)
- VibeCraft's `placing-furniture` skill:
  [SKILL.md](https://github.com/amenti-labs/vibecraft/blob/main/agent/.claude/skills/placing-furniture/SKILL.md)
- The Minecraft Wiki's [roof construction guidelines](https://minecraft.wiki/w/Tutorial:Roof_construction_guidelines)
- [Minecraft building techniques for stunning builds](https://minecraft.how/blog/post/minecraft-building-techniques-stunning-builds-2026)
  (minecraft.how)
- Research on language models building in Minecraft:
  [APT: Architectural Planning and Text-to-Blueprint Construction](https://arxiv.org/abs/2411.17255),
  [A LLM Benchmark based on the Minecraft Builder Dialog Agent Task](https://www.promptlayer.com/research-papers/a-llm-benchmark-based-on-the-minecraft-builder-dialog-agent-task)
