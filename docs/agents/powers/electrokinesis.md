# Electrokinesis: notes for agents

Elle Bishop's current as a player power (`electro/`, `ElectroArt`). The lightning fighting style is in [fighting-styles.md](../fighting-styles.md). What the player sees: [electrokinesis.md](../../electrokinesis.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the Electrokinesis rework

Electrokinesis (`electro/`, art in `client/effects/setpiece/ElectroArt`) is Elle Bishop's current: Charge,
Chain Lightning / Live Current, Ball Lightning, Thunderstrike, Ride the Lightning, Storm Surge, Blackout and seven
skills, each with its own arm pose and set piece (November 2026). What the player sees is in
[docs/electrokinesis.md](../../../docs/electrokinesis.md).

- **One art class, a handful of primitives.** Every piece is drawn by `ElectroArt` from `fork` (a jagged channel with
  side branches), `ring`, `corona` (a shell with orbiting rings), `braid` and `pulse` (a ground ring with fingers),
  all through `stroke`'s three layers. Palette: `10172D` shadow, `3D52B8` indigo, `5588FF` blue, `84D5FF` ion,
  `EAF5FF` core. New moves are new arrangements of these, not new primitives (Arc Lance's straight beam, Circuit
  Thread's lit block edges and Arc Lattice's sagging wire are the exceptions, and they still use the palette).
- **Effects leave the palm the player sees.** `ElectroArt.hand(c, owner, reach)` uses `ElectroGeometry.hand` in third
  person and a camera-space point in first person, so a bolt starts at the visible hand in both. Bind the piece to the
  caster (`show(..., player)`) for this to work.
- **Every move has its own pose,** in third (`PowerArmPoses`) and first person (`FirstPersonArmGeometry.electro` and
  `skills`): draw, cast, stream, call, coil, release, ride (arms flung back), cup (palms round the orb), lance (braced,
  straight arm), touch (palm on metal), overcharge (fists clenched at the sides), ground (palm driven down) and point.
- **A generic skill pose overwrites yours.** `SkillActionService.activate` sends `ONE_HAND` / `ARMS_WIDE` /
  `ARMS_RAISED` after every implicit branch skill. A power that poses its own skills must `return` before that line
  (Electrokinesis does). Quake, Regeneration and Sonic still get the generic pose.
- **Held constructs are re-sent short pieces.** Ground Sink's rod, Arc Lattice's anchors and wires go out every ten
  ticks as 12-tick pieces that fade in and out over two (`fade(t, ticks, 2, 2)`), so copies hand over seamlessly. The
  sink's stored charge rides in the piece's `scale` (1 + charge / 16) and brightens the rod's tip.
- **Thin a beam that passes the camera.** Arc Lance dims itself where it runs close by the camera (the front view looks
  straight down it) but not for the caster's own first-person view, where the hand is always beside the camera.
- **Telegraphs share their timing.** `ElectroGeometry.STRIKE_DELAY` and `SURGE_TICKS` drive both the server's delay and
  the warning ring's build, so the marker always fills exactly as the bolt lands.
