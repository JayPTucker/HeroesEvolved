# Matter Assembly: notes for agents

The engineer (`assembly/`, art in `client/effects/setpiece/AssemblyArt`, shapes in `assembly/AssemblyGeometry`):
amber crystal folded out of thin air into constructs that work for you. Reworked 26 November 2026 to stop
overlapping Density (rock torn from the ground) and Invisibility (force walls, bridges, bubbles). What the player
sees: [matter-assembly.md](../../matter-assembly.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## The kit, and the IDs it kept

| Action / node ID (kept) | Now | Notes |
| --- | --- | --- |
| `matter_assembly_barricade` | **Panel** | 3x2 pane where you aim, mid-air allowed; steep look = 3x3 floor at foot level ahead (`AssemblyGeometry.floor`); three kept, a fourth recycles the oldest |
| `matter_assembly_shard_lance` | **Shard Lance** | Formed at the hand; ricochets twice off your own cells (+25% a bounce), x1.5 on airborne foes |
| `matter_assembly_causeway` | **Skywalk** | Unchanged; plates spend Matter and return it as they crumble |
| `matter_assembly_cage` | **Launch Pad** | A zone, not blocks: throws friends along, foes up (airborne 40 ticks); two kept; 4 Matter |
| `matter_assembly_cathedral` | **Sentinel Engine** | Turrets prefer airborne foes; their Shards ricochet too |
| `matter_assembly_faultline` | **Kaleidoscope** | Mechanic `kaleidoscope`; `canonical()` maps both names. A roofless mirror ring; Shards bounce up to eight times off it |
| `matter_assembly_bank_shot` | Bank Shot | Now +2 bounces and +35% a bounce (ricochets are core) |

## Reusable patterns

- **A resource the player can see, that comes home.** `assembly/Matter` holds a pool per assembler (keeping the
  `ServerPlayer`), synced by `MatterStatePayload` to the owner and trackers on change and every five seconds, eased
  one fragment at a time on the client (`ClientMatterState`) and drawn live as an orbit (`AssemblyArt.drawLive`,
  called from `SetPieceClient`). Spending is per cell in `build(...)`; a cell that fails to place is given back.
  Cells taken apart (`collapse`) or expired (`AssemblyTerrain.tick` calls `MatterAssemblyService.expired`, flushed
  once a tick) fly home as one `MATTER_RETURN` piece per owner; a cell someone else breaks is lost. Short of the
  resource, a construct builds partially instead of refusing, and Shard Lance costs none, so the kit never locks
  (Hydrokinesis's "thin from the air" rule).
- **Paced building that matches its drawing.** The server used to place every cell at once, which hid the assembly
  animation behind real blocks. Panels and Kaleidoscopes are now `Job.paced(delay, rate)`: the cast only checks that
  something can start (Matter, one available cell, edit rights), then cells land one or two a tick.
  `AssemblyGeometry.panelArrival(i)` / `kaleidoArrival(i)` give the same schedule to `AssemblyArt`, so each cell's
  fragments arrive and its seam flashes exactly as the block appears. Tests that count cells wait for the schedule.
- **Blueprint, fragments, snap, sheen.** `AssemblyArt.panel` draws the shape's cell outlines first (thin seam lines
  plus a dark soft-pass seam so they read at noon), flies fragments from the visible hand (`AssemblyArt.hand`, camera
  space in first person) along arcs with a hot tail, flares each cell's twelve edges as it lands, then wipes a sheen
  band across the finished pane. Reuse `edges`, `fragment` and `flight` for anything else that assembles.
- **One short piece per projectile step.** A Shard sends `MATTER_SHARD` (4 ticks) each server step: the crystal
  crosses the step in the first tick and its tail fades over the rest, so it never draws twice and never lags the
  server. Ricochets send `MATTER_BOUNCE` with the face normal as target. This replaced per-tick particles.
- **A zone instead of blocks for something you walk over.** A Launch Pad would be a one-block step nobody can walk
  onto, so it is a server zone (`AssemblyGeometry.padZone`) drawn as a held piece (`MATTER_PAD`, re-sent every ten
  ticks with 2/2 fades). Throws use `setDeltaMovement` + `hurtMarked` (reaches players), a per-entity re-arm, and a
  fall cushion for friends in `LivingFallEvent`.
- **Look like your idea, not someone else's.** Assembled matter had vanilla smooth stone's texture and read as
  Density. `tools/GenerateAssemblyTextures.java` paints clear amber glass (bright rim, highlight streak, corner
  glints; the first try, a diagonal lattice, read as wooden crates); the model uses `render_type` translucent and
  the block skips faces between its own cells (`skipRendering`), so a construct reads as one piece of crystal.

- **The old effect kinds draw in the new art.** `MATTER_WEAVE`, `MATTER_SHARD`, `MATTER_COLLAPSE` and `MATTER_SHIELD`
  (still sent by gifted mobs, the awakening rescues and the ward) are forwarded in `client/effects/ConstructPowerEffects`
  to `MATTER_RISE`, `MATTER_SHARD` with a `MATTER_BOUNCE` where it lands, `MATTER_BOUNCE`, and `MATTER_PANEL` with a
  flash on its face. `MatterAssemblyService` itself no longer sends them: the awakening, Pressure Valve and Second
  Foundation send `MATTER_RISE`/`MATTER_BOUNCE` pieces directly.

## Previewing

`tools/assembly-preview.init.gradle` (`uitest/AssemblyPreview`, flag `heroes.preview.assembly`) photographs 25
shots and passes or fails honestly. Lessons it paid for, also in [previews.md](../previews.md):

- `TempestCamera.fix(at, lookAt)` gives a side view; third-person back hides anything you aim at behind the head.
- A `Difficulty.PEACEFUL` world deletes every zombie and pillager the shots need; use EASY with mob spawning off.
- `mc.options.hideGui` also hides the first-person hands; cancel `RenderGuiEvent.Pre` for a clean HUD instead.
- Zombies burn at noon; use a husk. The first frames after loading can show an unrendered sky; shoot later.

## Admin

`/heroesevolved matter status|fill|set <amount>|clear [targets]` (`assembly/AssemblyCommands`).
