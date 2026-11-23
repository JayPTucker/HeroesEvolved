# TaCZ weapons and the Ability Dampener: notes for agents

Our gun pack, `weapons/TaczLink`, Forge Energy capabilities and the dampener field. What the player sees: [weapons.md](../weapons.md), [ability-dampener.md](../ability-dampener.md).

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Reusable patterns from the TaCZ weapons

Heroes Evolved requires TaCZ (Timeless and Classics Zero) and adds power-themed guns, an EMP grenade, and TaCZ guns
for the Company's guards (`weapons/`, art in `client/effects/setpiece/WeaponArt`). What the player sees is in
[docs/weapons.md](../../docs/weapons.md).

- **A gun pack in our own jar.** TaCZ 1.2 reads gun packs through the normal resource and data loaders, so our guns
  are plain files: `data/heroesevolved/index/guns` + `data/guns` (numbers), `index/ammo`, `recipe/` (type
  `tacz:gun_smith_table_crafting`; the 1.21.1 folder is `recipe`, not `recipes`) and `assets/heroesevolved/display`.
  Change a gun's numbers there, not in code. A recipe's `"type": "custom"` result makes any item (the EMP grenade);
  give it a `group` (a table tab such as `explosives`) or it shows in no tab.
- **Borrow TaCZ's shapes, paint our own.** A display file may point `model` and `animation` at TaCZ's own resources
  (`tacz:gun/p90_geo`, `tacz:p90`), so reloads and inspects animate for free. `tools/GenerateWeaponTextures.java`
  reads the borrowed model's geometry from the TaCZ jar and paints every face by part (body, grip, barrel, magazine,
  detail, glow) on its UV layout, and draws the inventory and HUD icons as a side view. No TaCZ texture is copied.
  The Freeze Gun is the exception: `GenerateWeaponTextures.freezeGun` generates its original Bedrock geometry and
  paints it through the same texture/icon pipeline, reusing only TaCZ's pistol hand anchors and animations.
- **One guarded class.** Only `weapons/TaczLink` imports TaCZ, like `radio/RadioLink`: every call is in try/catch and
  sets a `broken` flag, after which the guns fire as plain TaCZ guns and agents fall back to the dart rifle. (The older
  `time/TaczAmmoRecovery` predates it and reaches TaCZ by reflection; move it into `TaczLink` when it next changes.)
- **Effects ride TaCZ's events.** `EntityHurtByGunEvent.Post` (server side) carries the gun id: `PowerWeapons.hit`
  routes it to heat, cold or taser. `GunShootEvent` draws the muzzle effect; `AmmoHitBlockEvent` feeds cold bolts
  into `TerrainFrost.freezeGun`, shared with Ice Blast. The Freeze Gun includes water and lava in its
  projectile trace so TaCZ reports the first fluid contact as a hit. The effects reuse the powers' own services
  (`CryoService.chill`/`freezeSolid`, `GridDisruption.emp`) at smaller numbers, so a gun is a weak version of a power.
- **Any creature can shoot a TaCZ gun.** Every `LivingEntity` is an `IGunOperator`: put the gun in the main hand,
  `PowerWeapons.draw(mob)`, wait for the draw, then `PowerWeapons.fire(mob, point)`. Swapping guns means another draw.
  Keep the gun's magazine topped with `PowerWeapons.reload`.
- **TaCZ times shots in real milliseconds.** Draws, fire rates and cooldowns read a wall clock, not game ticks. In
  play that is the same thing; in game tests the server runs ticks as fast as it can, so every shot comes back
  `COOL_DOWN`. Tests that fire real TaCZ guns hold each tick to 50 ms (`WeaponGameTests.realTime`).
- **An armor stand is a test shooter.** It is a living entity with no AI, so it fires exactly where you point it.
- **A held freeze blast needs cold damage, not repeated physical blows.** The former carbine's rounds shattered
  the shell their next pulse hit. `TaczLink.coldBullet` now identifies their damage source for `CryoEvents`' existing
  freezing branch: cold preserves the shell and Cryokinetic immunity, while ordinary blows still shatter it.
  Keep `cold_gun`/`cryo_cell` ids; `TaczLink.prepareCold` migrates an equipped saved gun's semi mode to AUTO without
  replacing its stack or ammunition. New ones use AUTO too. `TerrainFrost.freezeGun` adds two cold per 600-rpm pulse,
  matching Ice Blast's terrain rate. `WeaponArt.frost` stops at the server's first body/block/fluid endpoint and
  supersedes older stream copies; impact pieces stay unbound. The real survival trigger and JEI recipe are checked
  by `tools/freeze-gun-preview.init.gradle` (`FreezeGunPreview`, flag `heroes.preview.freezegun`, eleven photographs).
  TaCZ discards a round after `life - 1` travel ticks: at 80 blocks/second the gun needs a 0.25-second lifetime for
  sixteen blocks, not 0.20. `WeaponGameTests.theFreezeBlastCannotPassThroughCover` checks real hits at fourteen
  blocks and no damage/Frost at eighteen, above the arena lid so its walls cannot hide a range error.

## Reusable patterns from the Ability Dampener

The Ability Dampener (an RF-powered field where no power works) and the Nullifier (a TaCZ pistol charged with RF that
fires dampener rounds) were added in November 2026 (`dampener/`, art in `client/effects/setpiece/DampenerArt`). What the
player sees is in [docs/ability-dampener.md](../../docs/ability-dampener.md).

- **One question for "is this power switched off".** `Dampening.dampened(entity)` covers a live field and a round's
  `DAMPENED` effect. `PowerRules.canActWithoutPreservation` asks it beside the Suppressor and the Eclipse, so every key,
  sustained power and passive goes quiet with no per-power code. NPCs with powers (Sylar) and
  `MimicryService.imprintOf` ask it too. A new way to block powers is one more branch there.
- **A timed status as a mob effect.** The round's hold is a real `MobEffect` (`ModItems.DAMPENED`): it syncs, shows an
  icon, refreshes rather than stacks, and survives milk (`fillEffectCures` left empty). Prefer this to a hand-rolled map
  when a status should be visible to the player.
- **A machine's state is "paid this tick".** The projector's field is live only while `level.getGameTime() - paidAt <= 1`,
  so anything that stops it ticking (an EMP's stall via `BlockEntityStallMixin`, an unloaded chunk) drops the field with
  no extra bookkeeping. Copy this for any powered effect that must die when its block stops.
- **A battery that other mods see.** Register `Capabilities.EnergyStorage.BLOCK` for the block entity and
  `Capabilities.EnergyStorage.ITEM` for the item; every FE mod's cables and chargers then work, and `GridDisruption`'s
  EMPs drain them (they only drain storages that `canExtract`). A capability can be registered on another mod's item
  (`tacz:modern_kinetic_gun`) and answer null for every stack that isn't yours.
- **A TaCZ gun that reloads from itself.** TaCZ's internal reserve ("dummy ammo": `DummyAmmo` and `MaxDummyAmmo` in the
  gun's custom data, read through `TaczLink.reserve`) replaces inventory ammo for reloads, and the HUD counts it. The
  Nullifier's battery *is* this reserve, with part-made rounds kept beside it. A gun from the gun smith table has no
  reserve yet, so `Nullifier.carried` gives it one on the next player tick.
- **Using a block with a gun in hand.** TaCZ takes right-click for aiming; its *Interact With Gun* key (O) calls the normal
  use, so `Block.useItemOn` and `PlayerInteractEvent.RightClickBlock` still see it. Say so in the docs.
- **Keep a block's charge when it is broken.** `collectImplicitComponents` / `applyImplicitComponents` with a data
  component (`STORED_ENERGY`) and a `minecraft:copy_components` loot function (`source: block_entity`).
- **Telegraph a field by its edge.** The dome's set piece is re-sent every ten ticks as a twelve-tick piece with two-tick
  fades, checks the block's `ACTIVE` state so it vanishes the moment power is cut, and draws its foot on the ground: the
  edge a player must cross is what matters. Rings that close inward read as "power swallowed"; every other power's rings
  open outward.
