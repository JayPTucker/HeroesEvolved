# Style guide

Heroes Evolved scenery tells a story: Company facilities that were sealed, a world that ended and
grew back, a killer who waited. Every build should answer "what happened here?" at a glance.

## Principles

1. **Nothing is flat.** A wall of one block reads as a placeholder. Mix the main block with one
   or two close variants (tiles + cracked tiles, bricks + chiseled), add pillars between bays,
   and break long runs with a recess, a pipe, a light or a shelf. `Grid.weather()` sprinkles
   variants deterministically.
2. **Light tells the mood.** Light every room so a player can read it, then choose what kind:
   - Company facility, working: sea lanterns flush in the ceiling.
   - Facility in emergency: red stained glass in the ceiling with glowstone behind it.
   - Abandoned: one weak source (soul lantern, a single lit copper bulb), deep shadow elsewhere.
   - Labs: end rods and lit copper bulbs over benches.
   Check darkness in the in-game screenshot, not the render; the render ignores light.
3. **Props say what the room was for.** A console needs a desk and controls; a records room
   needs shelves, desks and paper (white carpet); a lab needs a bench, samples and glassware.
   Place props where people would have used them, not scattered evenly.
4. **Age it on purpose.** Time passed: cobwebs in upper corners, a stalactite (pointed dripstone
   hanging from the ceiling), moss carpet on the floor, a cracked block near a stress point,
   a skull or a torn-out bar where something went wrong. Two or three touches per room;
   more becomes noise.
5. **Keep paths clear.** Leave a two-block-high walkway through every room and keep doorways three
   wide where the room is important. Decorations go along walls and in corners.
6. **Readable from the door.** The first view from the entrance should show the room's point (the
   pods, the lectern, the cell bars). Put the focal object on the line of sight from the door.

## Palettes

| Theme | Structure | Accents | Light |
| --- | --- | --- | --- |
| Primatech bunker | deepslate tiles, polished deepslate floor, deepslate brick ceiling | iron block, iron bars, copper pipes (`lightning_rod[facing=north]` laid horizontally), chains, waxed weathered copper | sea lantern; red glass + glowstone for alarms |
| Containment / Sylar | cracked deepslate bricks and tiles, chiseled deepslate | iron bars (some missing), chains, skeleton skull, cobweb | one soul lantern |
| Formula lab | polished deepslate, smooth stone | brewing stand, cauldron, amethyst clusters as samples, glass panes | lit waxed copper bulbs, end rods |
| Ruined city (future) | blackstone, cracked stone bricks, tuff, concrete | basalt, coal blocks, scorched wood, fire-scarred iron | almost none; magma under rubble |
| Reclaimed surface | the facility block set | moss blocks, moss carpet, vines, roots, leaf litter, saplings through cracks | daylight through holes |

## Sizes that feel right

- Corridors: 3 wide, 3 high inside.
- Ordinary rooms: 6–9 wide, 3–4 high inside.
- Halls (the stasis chamber): 13 wide, 5 high inside, with bays every two blocks.
- Ceilings above 5 need something in the upper space (chains, pipes, hanging lights) or they feel empty.

## Quality checklist

Run through this on the in-game screenshots before calling a build done. The user's reference builds
(`inspiration.md`) all pass it.

- [ ] **Silhouette:** from outside, is there a clear shape with height contrast (a tower, a roofline, a mast)?
- [ ] **Depth:** do walls have at least two depths (pilasters, recesses, ledges, frames), not one flat plane?
- [ ] **Ceiling:** in rooms taller than 4, is the ceiling detailed (beams, ducts, hanging light boxes)?
- [ ] **Lights as objects:** are light sources built into fixtures rather than bare glowing blocks?
- [ ] **One accent color** repeated through trims, frames and furniture?
- [ ] **Furniture from several blocks** (see the micro-builds) instead of single props?
- [ ] **The floor leads somewhere:** a pattern or path toward the focal point?
- [ ] **Story in one glance:** what happened here is readable from the doorway?
- [ ] **Weathering with logic:** stains under ledges, moss on horizontal surfaces, vines falling from the top,
      damage concentrated where force hit. Not random noise.
- [ ] **Sits in the land:** foundations follow terrain, vegetation at its feet, nothing floating.
- [ ] **Palette:** three to five blocks, roughly 60/30/10, the roof darker than the walls; it holds up at night too.
- [ ] **Proportion:** doors 2 to 3 tall, headroom 2 clear on every walking cell, windows sized to their wall.
- [ ] **Seen from the path and from afar:** an eye-level shot along the way in, and a shot 20 to 30 blocks back.
- [ ] **No dark floor where mobs would spawn**, unless the darkness is the point and something in the story lights it.
- [ ] **Audit clean:** `CityAudit` reports nothing for the build (see SKILL.md).

## Lessons from builds so far

- **Palette letters must be unique.** A second `N` in a Python palette dict silently replaces the
  first; the roof that used it vanished. The validator only sees the written file, so check the
  in-game shot.
- **Stairs face uphill.** A roof's south slope uses `facing=north` stairs (the tall back is up the slope).
- **Carve before you roof.** A `box(..., ".")` after the roof rows erases them.
- **Hide one secret per build.** The bunker's best moments are found, not shown: a painting up on the
  gallery, a trapdoor under a rug that a guard's notes warned about. Put the hint somewhere the player
  will read before they find the secret.
- **A tour shot can lie.** Check each preview camera actually faces its room; one pointed into the
  wrong door and another into a forest canopy.
- **Weathering follows a cause.** Char by distance from the fire origin, crumble walls toward where
  the blast came from. `Grid.weather()` with a small `every` makes a checkerboard; use it only for
  light sprinkles on intact walls.
- **Audit, then look.** `CityAudit` (see SKILL.md) found about 4,000 faults in a city that looked fine in photos:
  awnings and railings hanging in air, doors opening into solid huts, plants on stone, lanterns with nothing above.
  Run it on anything big before judging the screenshots.
- **A pane needs something to hold.** A glass pane between two solid blocks (or other panes) is a window; a pane on its
  own is a stub. Set glass back between piers two blocks deep, or put it in the wall plane beside a corner or pier.
- **A sill is an upside-down stair, not a slab in the reveal.** A slab sitting inside a window recess reads as a shelf;
  an upside-down stair in the course below the window gives a lip with a shadow under it.
- **Light the dark places with what grew there.** In a ruin, glow lichen and glow berries light a stairwell well enough to
  walk and keep mobs from spawning, and they tell the story better than a working lamp would.
- **Blocks placed without updates keep their defaults.** Scaffolding placed by code stays at distance 7 and can't stand
  until something updates it: set `distance=0` yourself. The same goes for any block whose state depends on neighbours.
- **Placement order kills vines.** Blueprints were placed north to south with shape updates on, so a vine clinging to
  the wall south of it was judged before that wall existed and deleted. Placement now writes without updates and settles
  shapes afterwards (as vanilla structures do); the lesson stands for any code that places blocks one by one with
  `UPDATE_ALL`: put supports down before the things they hold.
- **"It looks fine" isn't "it stays".** A torch, plant or lantern with nothing to hold it is drawn happily until the
  first block update beside it, often when a player walks in. The Isaac studio's vines hung on window bars, the signal
  site's bracket lanterns on sideways chains, the ruins' grass on planks; all looked right in screenshots.
- **A structure template placed from Java must turn its blocks too.** `processBlockInfos` turns positions only; every
  turned village house had stairs facing the wrong way and unjoined panes until `Growth.blocks` rotated each state and
  `Rebuild` settled the shapes (see `docs/agents/village-events.md`). Rotate states and settle whenever code places a
  template block by block.
- **Clearing a footprint cuts trees.** Remove the loose leaves in a ring round anything that clears ground, or they hang
  in the air until they decay.
