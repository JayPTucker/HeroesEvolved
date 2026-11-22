# Density: notes for agents

Earthbending after Hung Gar (`phasing/`: `DensityService`, `EarthGeometry`, `Stones`, `Earthworks`, `EarthStyle`,
`DensitySkills`; art in `client/effects/setpiece/EarthArt` and `DensityArt`). What the player sees: [density.md](../../density.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the earthbending rework

Density was rebuilt as earthbending in November 2026: Horse Stance and Root, Rock Strike, Earth Shackle, Pillar and
Wall, Rising Mountain, the Tiger and Crane style, the Boulder, Mountain and Listener tree, three classes and Rock Clap.
The power id, the action ids and the tree's node ids were all kept, so saved worlds, loadouts and `power_balance.json`
still match (each node slot now holds its earthbending node).

### Earth as a resource

- **Footing is a block tag.** `heroesevolved:earth` (and `earth_heavy`, `earth_metal`) decide what can be bent;
  `EarthGeometry.footing(body, metal)` and `earthUnder(level, at, metal)` are the one question. A modpack adds its own
  stone by adding to the tag.
- **Never let the resource lock the kit.** With no earth underfoot, Rock Strike still throws a pebble at 60% for a
  little more stamina (`Stones.lift`, `PEBBLE_*`), as Hydrokinesis pulls water "thin from the air". Moves that act where
  you aim (pillars, the shackle, the mountain) need earth there and say so.
- **A stance that pays.** Horse Stance is native attribute modifiers plus a per-tick `PlayerEnergyService.drain` of
  `PowerBalance.scaledDrain("phasing_shift", …)` (code that drains on its own uses the scaled drain). Root fills while
  planted (`ROOT_GAIN`, twice in stance) and drains in the air; `EarthGeometry.rooted(root)` is the one multiplier every
  earth hit uses. The HUD reads Root, the stance and Breath from `DensityPayload`.
- **The material decides the look and, with a skill, the hit.** `EarthGeometry.Material.of(state)` sorts a block into
  stone, heavy, sand, gravel, mud or metal. `construct(state)` turns any ground into what may safely be placed or shown:
  never a block that falls (sand becomes sandstone), spreads (grass becomes dirt), hurts (magma becomes netherrack), has
  a block entity, or is worth more than the ground (ores become stone).

### Real rock

- **Flying rock is a block display.** `entity/BlockDebris` (shared with Telekinesis's arsenal) is a centred, never-saved
  `Display.BlockDisplay` with `block(state)` and `resize(size, ticks)` (the transformation is re-read with an
  interpolation, so a stone grows smoothly into a boulder on every client). `Stones` moves each one every server tick and
  sweeps it against bodies (an inflated box clip) and blocks (`ClipContext.Block.COLLIDER`).
- **A rock thrown before it finished rising starts inside the ground.** The lift draws a stone climbing out of its block;
  punched early, its first clip hit that block and it broke at once. `Stones.launch` snaps an unrisen rock to its hover
  point first.
- **Ground searches need a depth that fits the thing.** `EarthGeometry.ground(level, at, depth)`: Avalanche's boulders
  look from a step above their foot down 3.5 blocks; searching from the boulder's centre with the footing depth found
  nothing and broke every boulder on its first roll.
- **Constructs are temporary blocks you take down yourself.** Pillars, walls and the tent go in with
  `TemporaryBlocks.place` (so a crash restores them) with a duration longer than the construct's own, and
  `Earthworks.crumble` puts them back with `TemporaryBlocks.restore` (dust, no ice-melt effects). A `BreakEvent` on a
  construct cell is cancelled and the cell crumbles instead, and `ExplosionEvent.Detonate` strips construct cells from
  the blast: nothing drops, so nothing can be farmed.
- **Bodies in the way are moved before the block goes in.** `TemporaryBlocks.place` skips occupied cells, so a rising
  pillar steps whatever stands in its next cell up (or along) with `Takedowns.move`, then places, and throws it off the
  top when it finishes. A shoved wall moves column by column, each following its own ground (a step up, a drop of two).
- **A lifted plug of ground.** The Rising Mountain tears up the top three layers as displays, each layer a block
  narrower, so it reads as a plug of rock, and moves everyone whose feet were within half a block of the disc's top with
  it each tick. The real ground never moves; `EarthArt.mountain` draws a dark pit under it with rubble falling from its
  underside. Its warning piece is exactly `MOUNTAIN_WARN` long, so the dial fills as it tears free.
- **Tap or hold on one press.** Rock Strike's `activate` lifts a stone that grows every tick while `growing`; the key's
  `release` stops it, and a wheel cast that never releases is capped at `BOULDER_TICKS`. The punch is a `followUp`, so it
  is free and ignores the cooldown, and returns false with nothing held so the next press lifts again.

### Drawing it

- **The block in the seed.** `EarthGeometry.show(..., block)` sends a piece with `Block.getId(state)` as its seed (through
  `Takedowns.show`); `EarthArt.colour(c, seed)` reads it back and takes the construct's map colour, so chips, slabs and
  claws are the ground's own colour. Real blocks carry the textures; the art adds what blocks can't: cracks, dust, chunks
  in flight and the jade seam.
- **One idea, a few primitives.** `EarthArt` is `chunk` (a lit, uneven lump in the SOLID pass that shrinks rather than
  fades), `crack` (a dark jagged split in the SOFT pass, with an optional jade seam in GLOW), `dust` (a `Shades.smoke`
  billow) and `seam` (a three-layer jade ring). Jade appears only where the bender's will is in the ground.

### Ticking and tests

- **Earth rises under the foe you look at.** A player aims at a body, not at its feet, and the ray then hits the ground
  in front of it: the first pillars rose two blocks short. Pillar, Quicksand and the Rising Mountain ask
  `Targeting.livingEntityInSight` first and use the earth under that foe (`Earthworks.underFoe`), and only aim at blocks
  with nobody in sight.
- **Carried mobs are sent at once.** `Earthworks.carry` moves a body and, for a mob, broadcasts a
  `ClientboundTeleportEntityPacket`: otherwise a mob's position reaches clients every few ticks and foes on a slammed
  disc hung in the air after it landed.
- **A block display re-reads its block on every data load.** `BlockDebris.resize` puts the block in the tag with the new
  transformation: without it the display turned to air, and every growing boulder vanished.
- **A held pose re-sent on a timer must not cut a move short, and must keep its age.** Horse Stance re-sent itself
  every ten ticks over whatever move was playing, and wasn't on `ClientArmPoseState`'s continuous list, so every move
  snapped back to the stance's first frame. `ArmPosePayload.playing(player)` says what gesture the server last sent and
  whether it is still running; the stance is sent only when nothing else is, and the earth poses that are held
  (stance, heave, surf, listen) are continuous. The earth poses also keep the legs fully set: ramping them out from
  together made every change of move snap them shut.
- **A toggle's way back must be free.** Tunnel surfaced on a second press, but the press met Tunnel's own cooldown and
  nothing happened. The second press is a free follow-up (`DensitySkills.followUp`, branch 2), and Horse Stance
  surfaces you too.
- **Aim on the server with the current rotation.** `pick(range, 0, …)` uses last tick's head rotation; a real player
  aims a tick late, and a test FakePlayer (whose old rotation never updates after `lookAt`) aims wherever it first faced.
  Pillar, Quicksand and the mountain use `pick(range, 1, …)`.
- **Tick benders from the service's own map.** Stillness (Seismic Sense, Wait and Listen) walks `DensityService.benders()`,
  not the player list, so test players (never in the list) are ticked too.
- **A test player takes no mob damage.** The test server's difficulty scales a mob's blow on a player to nothing, so
  `ServerPlayer.hurt` never reaches `LivingIncomingDamageEvent`. `DensitySkillsGameTests.blow` builds the event
  (`new LivingIncomingDamageEvent(p, new DamageContainer(source, amount))`) and calls the hooks in priority order.
- **Focused run:** `tools/density-gametest.init.gradle` runs the Density tests with the styles, the tree catalog and the
  balance checks in about two minutes.
- **Photographs:** `tools/earth-preview.init.gradle` (`uitest/EarthPreview`, flag `heroes.preview.earth`) stages every
  scene on force-loaded overworld grass and shoots into `build/earth-preview/screenshots/`.

- **A stance a move lends ends with the move.** `setNotch` with free ticks marks the stance `lent` only if the player
  was standing normally. Without that, Stone Skin, Unmoving Mountain and the awakening left the player in a stance that
  began draining stamina when the free time ran out. Pressing the stance key makes it the player's own.
- **Bosses go through `DensityService.immovable`.** The earth hurts them but doesn't grip, pin, carry, lift or throw them.
  Any new move that relocates a body asks it first. A construct cell a boss stands in stays open, because
  `TemporaryBlocks.place` refuses an occupied cell.
- **Earth Surf's speed is its own transient modifier (`earth_surf_speed`).** It used to add and remove the Speed effect,
  which deleted any Speed the player already had. A temporary boost must never remove a shared effect it didn't create.

## Patterns kept from the earlier Density tree

- **The core asks the tree where the moves meet.** Every shockwave asks `DensitySkills.crater` (Pile Driver roots instead
  of throwing), the surfacing uppercut calls `uprooted`, Earth Shackle's second press asks `entomb`. The core keeps its
  own logic; the passives live in the tree's class.
- **Follow-ups on a core key.** Crush and Entombed ride Earth Shackle's key, the shove rides the Pillar's, the punch rides
  Rock Strike's. Each returns false when it doesn't apply, so the normal press still runs and pays its cost.
- **"Your blow" means your fist.** Melee-only passives (Pile Driver, Set in Stone) check
  `source.is(DamageTypes.PLAYER_ATTACK)` and `getDirectEntity() == p`. Thrown rock hurts with `damageSources().thrown(display,
  owner)`, so a rock never counts as a punch.
- **Re-embedding deeper.** `DensityService.entomb` takes the existing hold out of the map, stands the body back on the
  ground, embeds it again at full depth, and puts the old hold back if there is no room. Embedded bodies ignore `IN_WALL`
  damage.
- **Draw at the ground, not the body.** Once a body is embedded, `position()` is under the floor; ask
  `DensityService.surfaceOf(entity)` for the surface it is held in.
- **Separate aiming from the effect.** `sinkhole` picks the ground and `sinkholeAt` opens it; `pillar` aims and
  `pillarAt` builds, so tests and Wait and Listen raise one at a known point.
- **Test gotchas.** `rewind_empty` is 12 × 6 × 12 with a barrier lid, so anything above y = 5 (a pillar's throw, the
  mountain) needs `skyAccess = true`. No-AI mobs never land: set `setOnGround(true)` for anything that checks `onGround()`.
  Fill stone under the arena's floor before burying anything there. The arena's own floor is not earth: tests lay stone.
