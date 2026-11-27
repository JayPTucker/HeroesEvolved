# Cryokinesis: notes for agents

One art class (`client/effects/setpiece/CryoArt`), terrain freezing (`cryo/TerrainFrost`) and the signature
(`cryo/AbsoluteZero`). What the player sees: [cryokinesis.md](../../cryokinesis.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from terrain freezing

Cold hits freeze the terrain they land on (`cryo/TerrainFrost`, art in `CryoArt.terrainFrost`): Ice Blast, Crystal
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

## One look (November 2026)

The power had four palettes and three ways of drawing: particle streaks and vanilla snowflakes (`CryoEffects`), set
pieces in `CryoBlastArt` and `StyleArt`, and a construct renderer, each in its own blues. It is now:

- **`CryoArt` draws all cold**, in one palette (the table in [animation-guide.md](../../animation-guide.md)): the Ice
  Blast stream and quench, terrain frost, frost-construct cells (`CryoConstructArt` only keeps the client's cell cache),
  the Ice Skater's strip, the three finishers, and Absolute Zero.
- **The old `CRYO_*` effect kinds are shapes now.** `client/effects/CryoEffects` starts one `CRYO_FX` piece with the
  kind's ordinal in the seed's high bits (`kind << 16 | hash`); `CryoArt.fx` draws each from four primitives: `hexRing`
  (ice's own ring), `feather` (veins with barbs on a plane), `chips` (translucent, in the soft pass: opaque solid chips
  read as blue gravel) and `snowfall`. Gifted cryokinetics and the Ice Skater get the new look with no server change.
- **Solid ice keeps the ice texture** (`CryoSkillVisuals`, `CryoMeshVisuals`, `FrozenVisuals`), so a construct and a
  real ice block match. Don't recolour it to the palette.
- **No particle bursts.** The shatter's 50 blue-ice block particles and 25 snowflakes were removed from
  `CryoService.thaw`'s shatter path; a couple of snowflakes on a frozen body remain as seasoning.
- **A warm accent means heat leaving.** `WARM` is used only where heat is drawn out (Last Warmth, a siphoned reactor,
  Absolute Zero's embers), so the player reads it as "the cold is taking something".

## Absolute Zero (the signature)

`cryo/AbsoluteZero`, routed by mechanic `absolute_zero` through `expansion/ExpansionSkills`. Copied from
`electro/BlackoutService`: a channel kept on the server tick with the `ServerPlayer`, then one release. Foes are fixed
when the channel starts (`foes`), slowed and put out while it runs, and frozen with `CryoService.freezeSolid` at the end
(bosses take `coldHit` only). Stamina comes back with `PlayerEnergyService.restore`. Pieces: `CRYO_ZERO` round the
caster, one `CRYO_EMBER` per foe (bound to it, the caster's id in the seed). Tests: `cryo/AbsoluteZeroGameTests`.
Photographs: `tools/cryoart-preview.init.gradle` (`uitest/CryoArtPreview`, flag `heroes.preview.cryoart`).
