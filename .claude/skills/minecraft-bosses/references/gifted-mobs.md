# Gifted mobs and village incidents

The world's mini-boss layer as built (`gifted/`, `village/VillageIncidents`). What the player sees: [gifted-mobs.md](../../../../docs/gifted-mobs.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../../AGENTS.md).

## Reusable patterns from gifted mobs and village incidents

Only villagers and illagers can carry a power (`GiftedMobs.mayCarry`; `bestow` refuses anything else and a gift saved on
any other mob is taken away when it loads); they use it (`gifted/`), half of villagers whose gift wakes
turn evil and besiege their village (`Renegades`), raids get champions (`GiftedRaids`), and village events can happen
hundreds of blocks from a player (`village/VillageIncidents`). Built in November 2026. What the player sees is in
[docs/gifted-mobs.md](../../../../docs/gifted-mobs.md) and [docs/emergency-band.md](../../../../docs/emergency-band.md).

- **State on the entity, a runtime object while loaded.** `Gift` (power, rarity, side, name, origin, home) is saved in
  the mob's persistent data; `GiftedMobs` keeps a `GiftedMob` for every loaded one (made in `EntityJoinLevelEvent`,
  dropped in `EntityLeaveLevelEvent`) and ticks it from the server tick, only where `isPositionEntityTicking`. A renegade's
  siege (`Renegades.Terror`) is saved beside it and resumed on the next tick after it loads, as the meltdown is.
- **Roll once per mob, by context.** `GiftedMobs.join` marks every mob `ROLLED` and decides by what it is: a raider with
  a current raid (its wave leader, `Raid.joinRaid` sets the raid before the entity is added), a `PatrollingMonster` leader
  with spawn type `PATROL`, an illager with spawn type `NATURAL` (NeoForge's `Mob.getSpawnType()`). Villagers roll a
  dormant gift instead (`GiftedVillagers`). Do heavy work (messages, set pieces) on the next tick (`FRESH`).
- **Turning one mob into another.** `villager.convertTo(type, false)` keeps position, name, no-AI and persistence but not
  persistent data, so write the gift after; fire `EventHooks.onLivingConvert`; call `finalizeSpawn(..., CONVERSION, ...)`
  for the new mob's gear; `setCanJoinRaid(false)` so a raid doesn't recruit a renegade.
- **Pacing a threat so players can arrive.** A renegade's blows on its own village are cancelled
  (`LivingIncomingDamageEvent`) except when its turn comes (`Renegades.mayHurt`, every 45 s), and its powers check the
  same rule through `MobMoves.foe`. Pace by permission, not by slowing the AI.
- **Keep a far village loaded.** `village/VillageTickets.hold(level, bell, key)` adds a region ticket (a custom
  `TicketType`, not saved) that keeps everything within five chunks of the bell entity-ticking; release it when the event
  ends. Meltdowns, sieges and raids all hold one. A restart drops it; the event resumes when a player goes there.
- **Find a village anywhere.** `VillageIncidents.find` uses `ServerLevel.findNearestMapStructure(StructureTags.VILLAGE,
  ...)` from a point a few hundred blocks off in a random direction (so it isn't always the nearest). It returns the
  village's start at y 0: once the chunk is loaded, stand it on the ground (`getHeightmapPos`) before searching for POIs,
  which are found by 3D distance. Then wait a couple of seconds for entities (`Pending.readyAt`). It returns null in a
  world without structures (the game-test world), so tests start events directly.
- **One village question.** `village/Villages` finds the bell and who belongs (near the bell, or a home, job or meeting
  place there). The meltdown predates it and has its own copy; use `Villages` for anything new.
- **A flare that never clips.** A set piece is skipped past 224 blocks, and the far plane is four render distances off,
  so `VillageArt.flares` is drawn live, and past 320 blocks it is drawn nearer the camera and smaller in the same
  proportion: it covers the same part of the sky. Use this for any marker that must be seen from very far.
- **A whole-crater carve in one tick.** `NuclearFallout.carve(level, center, radius, player)` clears the inside of a
  sphere with `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` (no neighbour updates: nothing inside is left) and the rim, and
  anything next to a fluid, with `UPDATE_ALL`. Measured in solid stone: radius 24 is ~30,000 blocks in 21 ms; radius 48
  (`CRATER_RADIUS`, an ordinary blast's crater, twice its damage reach) is ~235,000 blocks in 136 ms. Use it for any big
  instant hole; past radius 48, spread it over ticks.
- **Swap a body under cover.** A villager turning (`Renegades.turn`) stays a villager for `TURN_TICKS` (21) while the
  cracks climb, and `convertTo` runs under the flash `GiftArt.turn` draws at that tick. Converting on frame one showed the
  cracks climbing a pillager. Tests wait `TURN_TICKS` and find the renegade with `Renegades.turnedFrom(villagerId)`.
- **Colour that must read at noon gets a normal-blended body.** Additive crimson washed out to pink against the sky (the
  crown, the flare). Draw the coloured body in the SOFT pass and keep GLOW for rims, beads and tips; keep white cores thin.
- **Photographs:** `tools/gifted-preview.init.gradle` (`uitest/GiftedPreview`, flag `heroes.preview.gifted`) shoots the
  sigils, the three cast poses, unbound, a turn, the flare from 300 and 600 blocks, and a meltdown crater in solid stone
  (it logs the carve time) into `build/gifted-preview/screenshots/`. Creative flight stops when a reset stands the player
  on the ground: start it again after every teleport into the air (`hover`), or the shots are taken from the ground.
- **Arm poses for vanilla models.** `mixin/IllagerCastPoseMixin` adds poses at the tail of `IllagerModel.setupAnim` from
  a client mirror (`client/ClientGifted`, fed by `GiftedMobPayload` on change and `StartTracking`). Uncross the arms
  (`arms.visible = false`) before posing them.
- **Tests.** `GiftedMobGameTests` (run them with the village, radiation and mimicry tests:
  `./gradlew runGameTestServer --init-script tools/gifted-gametest.init.gradle`). `GiftedMob.land(move, target)` runs a
  move from telegraph to landing at once; `Renegades.hurry` brings a renegade's next turn forward. A raid can be built in
  a test (`new Raid(id, level, center)`, `setLeader`, `joinRaid`). Mark test mobs `ROLLED` and give test villagers a null
  gift, or the rolls make the test random. Kill a mini-boss in a test with a player's blow: anything else is capped.
