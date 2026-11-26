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

## Reusable patterns from absorbing Technopathy (November 2026)

Technopathy was retired and its best ideas became six Electrokinesis nodes (`electro/ElectroMachines`): Remote Hand,
Kill Switch, Dead Trigger, Return to Sender, Counterweight and Winchline. They replaced the tree's six flat stat nodes
and kept their positional IDs (`electrokinesis_0_0`, `_0_2`, `_1_0`, `_1_2`, `_2_0`, `_2_2`), so saved builds still
match; Electrokinesis is now on `SkillTreeService.bonus`'s no-stat list.

- **Retiring a power is one table.** `ability/registry/RetiredPowers` maps a removed power to its successor (Technopathy
  to Electrokinesis; Precognition to none). Every loader of a saved power id asks it: a player's power
  (`PowerOwnershipEvents.retired` gives the successor when it is unclaimed, else a random unclaimed power, keeping
  awake, Evolved and Power Level), a gifted mob's `Gift.read`, Sylar's watch list (`SylarData.load`) and Mimicry's
  `Imprint` codec (`RetiredPowers.CODEC`). Removing enum constants that go over the network (`Piece`, `Kind`) means a
  protocol bump in `ModPayloads`. Shared colour lists (`MimicColors`) are indexed on both sides from code, so dropping
  an entry needs no migration.
- **Extra meanings on an existing key, not new keys.** Shift + press first runs the action's `deactivate`; when that
  returns false (nothing to stop) the normal activation runs with `modifierHeld`. Chain Lightning records Shift at press
  (`Hold.off`) for the tap's release; Ride the Lightning reads it in `activate` and tries Counterweight first. Both still
  pay their own cost and cooldown.
- **Work a device the way a hand would.** `ElectroMachines.permitted` checks build rights, height, border,
  `mayInteract` and posts a `RightClickBlock` (claim mods cancel it). It never posts a `BreakEvent`, which the old
  Technopathy did through `WorldMutationPolicy.canEdit` (that records echoes and mirrors snapshots). Doors are worked
  from their lower half, both halves are checked, and nothing closes on a living body.
- **Is this gun jammed: one question.** `weapons/Jamming.jammed` reads a real `MobEffect` (`ModItems.JAMMED`), so it
  syncs to the jammed player's own client. `TaczLink` cancels `GunShootEvent`, `GunFireEvent` and `GunReloadEvent` on
  **both** sides (a player's shot starts on their client); TaCZ then reports `FORGE_EVENT_CANCEL`. The Company guard's
  `GunGoal` stops while jammed, so his melee takes over.
- **A screen that returns shots needs the swept step.** `ElectroMachines.screenCatch` tests each projectile's whole
  step against every live Static Screen (`SkillActionService.screens`) in `EntityTickEvent.Pre`, `ProjectileImpactEvent`
  and `TaczLink`'s pre-hurt hook. Check the step to the screen's edge with a plain block clip: TaCZ's
  `bulletObstructed` also counts entities on the path and refused clean catches. A turned round is remembered so it is
  not caught twice, and its new course goes to clients with `TemporalProjectilePayload` (as `SylarProjectiles` does).
- **Game-test arenas are walled.** `rewind_empty` is 12 x 6 x 12 inside a barrier. A test round started outside it is
  rightly refused by the cover check; stage shots inside. Drive catch functions directly (as the Vibe and Quake tests
  do) rather than waiting for a bare TaCZ round to tick.
