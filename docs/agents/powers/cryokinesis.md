# Cryokinesis: notes for agents

Terrain freezing (`cryo/TerrainFrost`). What the player sees: [cryokinesis.md](../../cryokinesis.md#freezing-terrain).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from terrain freezing

Cold hits freeze the terrain they land on (`cryo/TerrainFrost`, art in `CryoBlastArt.terrainFrost`): Ice Blast, Crystal
Lance, Frost Armor shards and the Freeze Gun. Solids turn to brittle frost that thaws back, water to ice and lava to
obsidian. What the player sees is in [docs/cryokinesis.md](../../../docs/cryokinesis.md#freezing-terrain).

- **One entry per source, one rule for all.** `blast`, `projectile` and `freezeGun` each take the hit and an amount of
  cold; `spread` shares it over the exposed 3 x 3 patch (aimed block full, edges two-thirds, corners half, each checked
  for line of sight from the impact so a covered face is skipped), and no neighbour may finish while the aimed block is
  still cooling. A new cold source is one more entry with its own amount.
- **Hardness decides the time.** `TerrainFrost.required` (20 + 8 * sqrt(hardness)) is the only number; balance by the
  amounts a source adds, not by special cases per block.
- **Show progress on a block without touching it.** The cold is a map, drawn two ways: a rime piece on the struck face
  that the client replaces as it grows (`CRYO_TERRAIN_FROST`; scale 1 clears it) and the vanilla break overlay sent with
  `level.destroyBlockProgress` under a negative id per block (`crackId`), which never matches an entity, so it doesn't
  fight anyone's mining. Clear the overlay (-1) when the block turns or the cold fades. Use this for anything wearing a
  block down (acid, heat, vines).
- **Ask permission once, when the block changes.** Building cold checks only `mayBuild`, `mayInteract` and the world
  border; the break event fires inside `TemporaryBlocks.freezeSolid` / `WorldMutationPolicy.place` at the change. A
  refusal is remembered for the contact timeout, so a protected wall isn't asked again every pulse.
- **Hitting liquids.** Ice Blast and Crystal Lance trace with `ClipContext.Fluid.ANY`; TaCZ rounds get it through
  `mixin/CryoCarbineBulletMixin` (the Freeze Gun's cold pulses only; its saved gun id is still `cold_gun`).
- **Photographs:** `tools/terrain-frost-preview.init.gradle` (`uitest/TerrainFrostPreview`, flag
  `heroes.preview.terrainfrost`) holds the real Ice Blast key on stone, obsidian, water and lava, breaks the ice by hand,
  and logs each patch's blocks and progress (`TERRAIN_SHOT`) into `build/terrain-frost-preview/`.

- **A frozen animation needs a frozen interpolation value.** Cancelling mob ticks still replayed the previous
  walking step every render frame. `TemporalMobAnimationMixin` returns partial tick 1 for `FrozenVisuals.isFrozen`,
  holding the last pose. Glacier Ride keeps its actual `ServerPlayer` and ticks through `CryoRideService.tickAll`
  once per server tick; the action's release hook ends the ride, so a held key has no short lifetime.
