# Invisibility: notes for agents

Fade and the force-field kit (`invisibility/`, `ForceArt`). What the player sees: [invisibility.md](../../invisibility.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the Invisibility rework

Invisibility (`invisibility/`, art in `client/effects/setpiece/ForceArt`) became a force-field kit in November
2026. What the player sees is in [docs/invisibility.md](../../../docs/invisibility.md).

- **The kit.** Fade (shared with friends and pets within 3 blocks), Lens Shield → Force Push, Force Bridge,
  Bubble → Crush (or a shelter for friends) and Force Nova. The two-press moves use `SimpleAction.followUp`, like
  Fission Leap. The tree is Veil, Aegis and Pressure, routed through `invisibility/InvisibilitySkills`.
  - The moves feed each other: blocked hits store Pressure for the Push, a Push sends a held bubble rolling, the Nova
    hits held foes double and breaks your own bridges and barriers into shards.
  - Action and node IDs are unchanged from the older kits.
- **One shape language.** `ForceArt` draws every field from four primitives:
  - `lens`: a bowed round sheet with a facet lattice and a three-layer rim.
  - `pane`: a flat sheet with facets and lit edges.
  - `cage`: a shell wrapped in six great circles.
  - `facet`: a tumbling triangle shard.

  Palette: `9CC8FF` field, `E8F4FF` edge, `3F6FD8` deep, `B6A2FF` sheen, `1E2A44` stress cracks (normal blending).
- **One palette for the whole power, in one place: `client/effects/setpiece/ForcePalette`.** A review found three looks
  (blue fields in `ForceArt`, silver/cyan/violet optics in `InvisibilityEffects`, a cyan cloaked body in
  `OpticalClient`). All three read `ForcePalette` now (the Fade's strokes and the cloaked body's tint, contours, hands and
  HUD ring included), the Fade's broken loops are drawn in three layers like every field, and the awakening's colour is
  the field blue. A new Invisibility visual takes its colours from `ForcePalette`, never its own constants.
- **Held fields that end at once.** Re-sent pieces (shield, bubble, disc, bridge, wall) can linger up to ten ticks
  after the server ends them. `SetPieceClient.superseded(active, pieces, now)` lets a piece stop drawing as soon as
  a later piece replaces it on the same entity (or, if unbound, at the same origin and target). For example, the
  bubble stops when its `FORCE_CRUSH` or `FORCE_SHATTER` arrives, and the shield stops when its `FORCE_PUSH` does.
  Bind the ending piece to the same entity so the match works.
- **Stairs without jumping.** Players standing on a Force Bridge get a transient `Attributes.STEP_HEIGHT`
  modifier (+0.5), removed when they leave or the bridge ends, so one-block steps walk like a ramp.
- **Solid constructs that are drawn, not textured.** Force Bridges and Barriers are temporary `minecraft:barrier`
  blocks placed with `cryo/TemporaryBlocks.place` (so they restore after a crash) and removed with
  `TemporaryBlocks.restore`, which skips the ice-melt particles. The client draws the panes from the same
  `ForceGeometry.bridgeCell` the server placed, so the picture is exactly the solid cells.
- **Constructs only friends can see.** `ForceFieldService.show` sends a set piece to each friendly viewer instead of
  `SetPiecePayload.send` when the caster has Unseen Constructs. Use this for any private or stealthy object.
- **Holding a creature.** A bubble re-anchors its target every tick (`teleportTo`, or `connection.teleport` for a
  player) and cancels damage whose source is the held creature. Rolling a held creature is the same anchor
  moved by `SpeedService.swept` each tick.
- **Catching projectiles.** Each field scans nearby `Projectile`s every tick with its own shape test
  (`ForceGeometry.onShield`, a radius, a wall's box) and either discards them or, with Rebound, aims them back at
  their shooter and takes ownership, which stops them being caught twice.
- **Hiding things that aren't players.** `InvisibilityService.hide` sends `UnseenPayload` for any entity;
  creatures are cancelled by `UnseenClient`'s render hook and everything else by `mixin/UnseenObjectMixin`.
