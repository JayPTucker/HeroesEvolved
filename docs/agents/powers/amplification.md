# Power Amplification: notes for agents

Ando Masahashi's red lightning, which makes other people's powers stronger (`amplify/`, `AmpArt`, `AmpPoses`, `client/SuperchargeField`). Read this before changing how any power decides its own numbers: an amplified ally changes them. What the player sees: [power-amplification.md](../../power-amplification.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from Power Amplification

Power Amplification (`amplify/`, art in `client/effects/setpiece/AmpArt`, poses in `client/AmpPoses`) is Ando Masahashi's
red lightning: it makes other people's powers stronger, never your own. Built in November 2026. What the player sees is
in [docs/power-amplification.md](../../../docs/power-amplification.md).

- **A power that changes every other power asks one question in one place.** `Amplification.surged(entity)` and
  `overcharged(entity)` are all the other powers call, each in its own code where it already decides the number (Fold
  Space's range, Pyrokinesis's `isBlue`, Hydrokinesis's `draw`, Quake's `strain`, Sonic's `addResonance` and so on).
  The amplifier's state never leaks into them. A new power that should feel amplified adds one line where it decides
  its own number, and a row in the doc's table.
- **Shared bonuses live in the shared paths, not in twenty powers.** The Surge's 25% is three hooks: damage on
  `LivingIncomingDamageEvent` (the attacker is surged), stamina in `AbilityRanks.energy` (`Amplification.cost`), cooldowns
  in `AbilityRanks.cooldown`, plus held drains in `StaminaDrain` (`Amplification.drain`). `AbilityUseService` also asks
  `locked` (a backlash switching a power off), `tierBonus` (Breakthrough) and `misfire` (Overcharge the Enemy), and tells
  `powerUsed` after every press so amplifiers near feel it as Charge; `GiftedMob` does the same for its moves.
- **What a power can't do on its own is done from outside it** (`AmpTuned`): Daphne's run uses Space-Time's history
  (`SpaceTimeService.past`), the storm calls `ElectroService.shock`, the flash freeze `CryoService.freezeSolid`, the dome
  and the ghost party cancel damage in the same incoming hook. Backlashes are effects, a stamina drain or a `lock`.
- **A status that other clients draw needs its own payload.** Vanilla only syncs a player's own effects, so
  `SuperchargedPayload` tells everyone tracking a body (and late arrivals on `StartTracking`), and `ClientAmp` keeps the
  level and end time for `AmpArt`'s live crackle. The `SUPERCHARGED` mob effect is only the icon.
- **Tap or hold on one key, with a fallback.** Supercharge's press records the ally; a release before 40 ticks is a Surge,
  and the server tick turns a hold into an Overcharge (or a Surge without the Charge). A press that never sees its release
  (a wheel cast) still resolves at 40 ticks.
- **Red drawn as red.** Every stroke's red body is in the SOFT pass and only the hot rim and pale core glow, so red
  lightning stays red at noon (the gifted crown's lesson).
- **Tests that search an area need real players in the level.** A FakePlayer isn't in the level's entity lists, so A
  Thousand Times, Hold Hands and Brave New World can't find one; `AmplificationGameTests.findable` adds a real
  `ServerPlayer` with `addNewPlayer` (as `RescueGameTests.carriable` does) and removes it with `removePlayerImmediately`.
- **An aura that follows the body is a render layer, not sparks.** A supercharged body wears a red version of the
  charged creeper's field (`client/SuperchargeField`): `RenderType.energySwirl` with scrolling offsets over a copy of the
  model. Players get an inflated `PlayerModel` (`createMesh(new CubeDeformation(1), slim)`, registered for wide and slim,
  outer skin layer hidden so there is one shell); every other living renderer gets a layer that draws its own model again
  a size larger about the body's middle (`AddLayers.getEntityTypes`, `getRenderer` checked with `instanceof
  LivingEntityRenderer`). The red texture is made on the client from the game's own `creeper_armor.png` (each pixel keeps
  its brightness), registered as a `DynamicTexture` and remade after a resource reload: nothing of Mojang's is shipped.
  The swirl adds light, so keep green and blue low or it reads pink over pale skin. A shapeshifter's stand-in shows its
  wearer's field through `MorphClient.drawingFor()`.
- **A test per power from one table.** `AmplificationGameTests.BOOSTED` lists every power with what a test can see of its
  Surge, Overcharge and backlash; a `@GameTestGenerator` turns each row into its own test (`boosted.<power>`, its own
  batch, since a storm or a flash freeze reaches past the arena next door), so a failure names the power. The template
  must be given in full (`heroesevolved:rewind_empty`) for a generated test. Measure a boost with a plain blow (a
  `GENERIC` source with the ally as its entity): a power's own punch passive (Electrokinesis's shock) adds to a
  `playerAttack`. State kept per listed player (a speedster's friction heat) needs the test player in the player list.
- **Focused tests:** `tools/amp-gametest.init.gradle` runs the amplify, skills and finisher tests in about two minutes.
- **Photographs:** `tools/amp-preview.init.gradle` (`uitest/AmpPreview`, flag `heroes.preview.amp`, `-Ponly=boost` for
  the twenty boosted powers) with stand-in players as allies, into `build/amp-preview/screenshots/`.
