# Built bosses: what they taught

Lessons from the bosses this mod has built. **Sylar's Collection** (`story/sylar/`, November 2026) is the live one and
the example to copy; the Watchmaker (Sylar's old clock-shop fight) and Elle Bishop (the Company's boss on Primatech's test
floor) were retired when the Collection replaced them, and their code is in git history before it. Their lessons still
hold, so they are kept below. What the player sees: [sylar.md](../../../../docs/sylar.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../../AGENTS.md).

## The Living Seal: reusable patterns

- **Keep the native progression owner.** `endgame/EndBattle` controls the native multipart Dragon, redirects parent-body
  ability hits to its head path, captures death XP, and defers `EndDragonFight.setDragonKilled` until Inheritance ends.
  Freezing the native fight tick at a saved Sylar checkpoint prevents its missing-Dragon watchdog from respawning one.
  Release the original completion once; never imitate its egg, gateway or exit with block placement.
- **Effective health must survive the attribute ceiling.** `EndDamage` scales damage units when a party's pool exceeds
  vanilla's 1000-health limit. Preserve the fraction on join; otherwise a late fighter silently heals the boss.
- **Force-loaded does not mean entity-ticking yet.** `Encounter.discoverCrystals` polls the native spike bounding boxes
  during startup. A one-time constructor query missed crystals in waking chunks. Tests use explicit fixture references
  until those chunks are ticking; ordinary player builds' decorative crystals outside the pillars remain unrelated.
- **Snapshot only owned scenery.** `EndScenery` refuses occupied footprints and block entities, records before/after
  states durably, restores only matching after-states, and releases only its own force-load tickets. Regenerate both
  altar/refuge blueprints with `generate-end-arena.py`; the real preview checks lighting with `CityAudit`.
- **A floor wave needs a jumpable height and the actual floor.** A fixed y=64 wave floated over End slopes, and a
  1.8-block hit height exceeded a normal jump. `EndGeometry.waveHeight` follows the terrain on server and client;
  `WAVE_HEIGHT` is .8. `EndBattleGameTests.ordinaryJumpClearsTerrainFollowingPressureWave` checks both outcomes.
- **Airborne ultimates need reachable shelter.** `EndMoves.warn` fixes a safe orbit seven blocks from each fighter,
  besides the room sector and real cover. Put the sector away from the primary target's initial location, or a solo
  player standing still automatically evades the ultimate. Sampled radiation/amplification use the same 70-tick warning.
- **Separate the character's worldline from his earlier story.** `SylarEntity.inheritance` reuses the entity, poses and
  renderer while keeping Collection state, Claire/Ted outcomes and earlier wins intact. A broken echo prism cancels
  the sampled pattern; it never removes the player's real gift.
- **Make the body crack before hiding it.** `SylarRenderer` draws generated emissive cracks on both base and outer
  PlayerModel UVs (base-only cracks were occluded by the coat). `BodyCopies` hides the real model precisely at
  `EndGeometry.RUPTURE_TICK`; `EndRuptureArt` uses the prologue's `CloudMesh` through `Canvas.cloud` for the ability wave.
  The server waits the complete eight seconds before progression and rewards; its blast is visual and leaves builds intact.
- **Keep victory art on the encounter clock.** The release reads `EndClientState.age`, so a slowed boss animation does
  not desynchronize the detonation, the 3.6-second score impact and the server's reward beat. `EndBattleMusic` uses
  six original 100 BPM arrangements and 48-tick bar transitions; restart silent stems after volume or sound reload.
- **Unloaded is not defeated.** The encounter checks `getRemovalReason().shouldDestroy()` before treating a removed
  Inheritance actor as dead. Unloading abandons the attempt and retains its checkpoint, without rewards. The focused
  tests also complete native progression and use the crafted Worldline Sigil against the real four-crystal respawn.
- **End a boss's hazards when its scene begins.** `Encounter.clearHazards` discards bolts and waves on Dragon death,
  absorption and victory; `EndMoves.hit` also refuses cinematic stages. Reflected `EndBolt` damage retains its player
  source and uses the existing `FlightSkillService.hostile` rule, so disabled PvP and allies remain protected.
- **Return beside a saved source portal.** The home saved on End entry can be inside its portal block. `returnHome`
  finds adjacent walkable ground when needed and sets portal cooldown, instead of immediately sending the player back.

## Containment Failure: reusable patterns

The Wither (`wither/`: `RiftWitherEntity`, `WitherMoves`, `WitherGeometry`, `WitherArena`, `WitherEvents`; art in
`client/effects/setpiece/WitherArt` and `WitherFloorWave`; body in `entity/client/RiftWitherModel`; score in
`tools/generate-wither-music.py`). Rebuilt in November 2026 after the user called the first version "low quality and
nothing like Cataclysm": a scaled vanilla Wither in an empty 81-block disc with hairline effects and a muddy score.

- **A boss is a body first.** The rebuild's biggest win was a real creature: `RiftWitherModel` is a hierarchical model
  (yoke, stretching IK necks, hinged ribs, clawed arms, a tail with follow-through) whose bones end exactly at
  `WitherGeometry`'s anchors, posed by keyframe curves (`k(t, ...)`) on synced action age, and drawn with two emissive
  layers (eyes and star; fractures that brighten each phase). Scaling a vanilla model up never reads as a boss.
- **Give a large boss actual motion and a combat rhythm.** `WitherGeometry.chain` picks range-sensitive phase chains;
  `WitherMoves.tickMotion` moves the body over several ticks with collision substeps, fixed endpoints and a
  once-per-fighter hit set. A blocked descent must not damage the unreachable marked floor.
- **Make the room's counterplay visible in the warning.** Ribbreaker sweeps the real body along its lane when the windup
  starts (`WitherMoves.warn`); if stone is in the way the warning draws a burst glyph there, so baiting it into a pillar
  is something a player can plan. A hovering boss is snagged by any 1-high block, so the generator keeps rubble against
  pillar plinths and remains at the wall's foot, never loose on the fighting floor.
- **Real floor, no entities.** The rolling pulse lifts the actual floor blocks in a ring (`WitherFloorWave`, a
  `RenderLevelStageEvent` renderer reading the running `WITHER_PULSE` pieces), each by `WitherGeometry.lift`, the same
  height the server's hit uses, so "jump it" is honest. One-off chunks (slams, crashes, the death's ribs) are
  `QuakeDebrisEntity`, which never removes or places a block.
- **A body passing through a breach.** The breach's near-black fill is drawn after entities, so whatever is behind its
  plane is hidden from the front; the renderer also shrinks the body by how far its feet are behind the plane
  (`RiftWitherEntity.gateDepth`, synced gate position and yaw; `RiftWitherRenderer.size`). The same pair does the
  summoning-site swallow and the arrival out of a tear over the dais. The tunnel behind a breach recedes away from the
  camera, so it has depth from either side.
- **"Too easy" is a numbers problem first.** The first rebuild looked right and the user still called it too easy: 440
  health (25 s at the damage cap), 6 to 14 a hit, 11 attacks in 37 s, a 2.6 s slump after every chain. The harder pass
  (`WitherGeometry.castWindup`, `restScale`, `spentTicks`, `damage`) raised health to 720, roughly doubled damage with
  +15% a phase and Wither II, cut windups 14% and 26% in later phases, made every third bite, rake, slam and charge a
  feint that holds its coil (synced as `ACTION_WINDUP`, so the warning and the pose still fill exactly to the hit),
  added combos through the chain (`quickFollow`: tether into bite, a missed charge turning back), and reached 18 attacks
  in the same 37 s. Measure attacks a minute and damage a hit before and after, and test the feints and combos.
- **A boss bar built from the boss** (Bosses'Rise): `client/WitherBossBar` cancels `CustomizeGuiOverlayEvent.BossEventProgress`
  for the Wither's bar names only and draws a generated bone frame and skull emblem, tinting the fill and the eyes
  with the phase colour and keeping a trail of health just lost.
- **Minions a boss throws should land where the warning said.** Bone Brood flies each wither skeleton on the shared
  `WitherGeometry.spewArc` with its AI off and invulnerable, sets it down exactly on its mark, then turns its AI on
  (`WitherMoves.Spew`); the art draws the same arc. Every summoned body is tagged (`WitherMoves.BROOD`): drops and
  experience are cancelled, its own skulls pass it by, it crumbles after 30 s, and a cap per party plus a per-phase
  cooldown (`RiftWitherEntity.broodReady`, skipped in the chain when not ready) keep the floor from flooding.
- **A dragged actor needs its chunks.** The summoning-site Wither is pulled several blocks; force the 3x3 chunks round
  the breach while the encounter runs, and give the intro a watchdog on the arena clock so a stalled actor still
  leaves the breach usable (`WitherArena.tick`).
- **Fake players are not in the level's list.** The first detonation looped `level.players()` and missed every game-test
  fighter. Area effects that must reach the whole fight ask the boss's party too (`RiftWitherEntity.everyone`).
- **Time an encounter on its own clock.** `WitherArena.clock` advances once per `tick`, so the exit's opening, the
  knock-downs and the close can be stepped in a test by calling `tick`, which game time can't be.
- **Server reads don't wait for sync.** `exposed()` read synced state that only updates on the next tick, so a crash on a
  move's last tick looked unexposed; on the server it reads its own fields.
- **A death scene can end the fight with a set piece.** Thrash, ribs burst, rise and swell, drink the room in, detonate
  (`WitherMoves.deathTick`): `WitherArena.blast` knocks every unshielded player flat with the Last Day's `DazePayload`
  (blackout, ringing, blinking awake) and holds them down; a raised shield facing it or a `NuclearShields` power holds.
  The exit tears open where it died while they are still down, and the prizes are thrown out of it.
- **Test whether the counter can physically be used.** `anchorGlassHasRealReachableOpenFaces` casts an actual
  player-height block ray at each pylon's glass through its open faces.
- **Cancellation must invalidate queued work.** `WitherArena` captures a generation when it queues the formation actor
  and rejects stale actors in `swallowed`.
- **Replace the construction before the vanilla explosion.** `WitherEvents.formed` intercepts a newly made invulnerable
  native Wither in `EntityJoinLevelEvent`; a second construction refunds its ingredients.
- **Keep retreat useful without providing a firing nest.** A sheltered return alcove behind a baffle: `sheltered`
  excludes both outgoing damage and targeting there. Release only chunk tickets the encounter took.
- **A canceled warning must stay canceled.** `WITHER_CANCEL` outlives every Wither warning; cancel on an earlier server
  tick than starting the next pose (`superseded` includes equal timestamps).
- **A ray with no entity needs an empty collision context** (`CollisionContext.empty()`), and **a broad-phase box is not
  a circular impact** (`RiftSkull.onHit` measures from each hitbox to the impact).
- **Preview after the transfer settles,** and stage tears and bodies with a fixed `TempestCamera` shot per beat
  (`WitherPreview` for the story beats, `WitherReworkPreview` for every move and the live AI).

## Sylar's Collection: the classes

| Class | Job |
| --- | --- |
| `story/sylar/SylarEntity` | The boss and the story's actor in one: a fight after `begin(party, refight)`, an actor that scenes move by hand otherwise (`scenePose`, `actor(true)`, `foldAway`). The rules (caps, falloff, idle heal, leash, party scaling), the phases, the cases, Claire's gift and the death scene |
| `SylarMoves` | Every move as a `Move` record (windup, cooldown, the case it draws on, `start`, `pulse`, `land`, `cancel`), the moves still landing after their windup (the ring, the hand, the dashes, the reel, the slab in flight) and the Appraisal hold |
| `SylarGeometry` | Every position, reach and timing the server and the art share; each windup is its telegraph piece's length |
| `CollectionArena` | The pocket dimension: building it, the party in (`open(holder, story)`) and everyone home, the lights, the pillars, outcomes, prizes |
| `SylarStory` | How you meet him: waking at the configured Power Level, the watch list (old names, three gifted villagers named lazily, the listed players), a name crossed out each night unless guarded, the prisoner's paced talk, the stiff release lever, and him coming up behind a listed player once his cell is open |
| `LevelFive` | Where he was kept: a pocket dimension built once from `level_five`, the hatch in Cell 3, the lift, the glass, the list's signs, the prisoner, protection |
| `SylarData` | Saved: the story's stage, the list and who has heard what, the hatch, visits, wins, each fighter's way home, what they dropped in the hall; and `holds(power)`, which asks the past's own records whether a story case is full |
| `SylarEvents`, `SylarCommands`, `SylarVoice`, `RepairTicketItem`, `SylarsWatchItem` | Hooks (charged swings, the story's clock, login/respawn safety), operator commands (`next` moves the story a step), the paced voice (and `sayNow` for scenes), the re-fight's way in, the first-kill reward |
| `client/effects/setpiece/SylarArt`, `client/sound/SylarMusic`, `entity/client/SylarRenderer` | The schematic telegraphs and the room, the phase loops, the poses |
| `tools/draft-the-collection.py`, `tools/draft-level-five.py`, `tools/generate-sylar-music.py`, `tools/generate-sylar-items.py` | The blueprints, the theme, the item icons |

## Reusable patterns from the Collection

- **Bring the boss with a mystery the player solves, not an item that arrives.** The first version left a ticket in
  the player's pocket at Power Level 8; the user didn't like meeting him that way. Now he is a prisoner who is helpful
  through his glass while the names on a list by his cell are crossed out one a night, and the player works out who is
  doing it before he comes for them. It gives the foreshadowing days of play, reasons to travel (guarding a listed
  villager spares them a night), a choice (pull the lever early, sparing the rest), and the reveal its weight. Keep an
  item (the Repair Ticket) for re-fights only. The story's lessons are in
  [story-and-scenes.md](../../../../docs/agents/story-and-scenes.md) ("the prisoner and the list").

- **Make the room's machines the boss's move list.** Each case holds one power and powers one move; while a move winds up,
  its case is open and breaking it backfires. Players who learn the room take the boss apart, and the fight gets easier
  as they play well. A case he isn't drawing on mends at once (`pollCases` restores the glass), so nobody can strip the
  room in the first ten seconds. Poll the blocks each tick, as Elle's banks were polled: a fist, an explosion or a
  power's block-breaking all count the same.
- **Put what the boss takes from a player somewhere the party can reach.** Appraisal's stolen power goes into an empty
  case that stays open; breaking it gives the power back. A theft becomes a room objective, not a timer.
- **Let the story's outcomes show in the room.** Claire's and Sprague's cases are full or dark by asking
  `NightThreeData.podKept()` and `AvenueData.grayNeverSaw()` directly (`SylarData.holds`): one question, one place, no
  copy of the past to keep in step. A dark case changes the fight (no second life; a light that fails and leaves him
  spent) and the reward (the ledger's pages).
- **One telegraph primitive per shape, in one art language.** `SylarArt.assembleRing` and `assembleLine` (parts flung
  out and up, sliding in, ticks appearing at half built, a fill growing, a click of light as the last part seats) cover
  most moves; the rest are a hand, a wedge and rays built from the same parts. Every new move costs a few lines.
- **Put a carried number in the piece's seed, not its scale.** The client clamps scale to 0.05..64; a colour index, a
  progress-and-struggle pair or a 12-bit case mask rides in the seed (`SetPiecePayload.sendSeeded`).
- **Draw cover from the client's own world.** The light's rays are clipped against blocks on the client
  (`SylarArt.clipped`), so the shadows behind the pillars and the rubble are exactly where the server's
  `seenFrom` check spares people.
- **Thrown terrain with vanilla display entities.** Hurl lifts nine `Display.BlockDisplay`s (never saved) out of the
  floor and flies them on a fixed arc; landing turns them into rubble that becomes cover. Cancelling puts them back in
  the pit. Copy `SylarMoves.Chunk`.
- **The per-second cap must not undercut the per-hit cap.** With both as "remaining allowance", a 4% per-second cap
  made every hit 4%. The first hit in an empty window may take the per-hit cap; after it the window holds the total.
- **A charged hit frees a held player too.** The Appraisal hold isn't a windup, so `breakConcentration` is called when
  he is casting *or* holding someone.
- **Clear the target's hurt cooldown before a boss move lands** (`hurtFoe`): two of his moves landing a tick apart are
  two blows, not one swallowed by invulnerability frames.
- **Keep a hook that freezes the boss in place for tests and previews** (`still(true)`, `holdOff`): his moves still run
  when started, and nothing walks him off his mark between scenes.
- **Fill the boss bar at once.** A bar refreshed every ten ticks starts empty; `begin` adds the party immediately.
- **Hand back what fighters drop.** The hall is rebuilt every fight, so drops would be lost: `LivingDropsEvent` stashes
  them in saved data and they come back on respawn or login.
- **Music as the game's music.** `SelectMusicEvent` picks the phase's loop while the local player is in the hall
  (`SylarMusic`); a stinger plays on a phase change and the last chord on his death, then silence. The three loops share
  tempo, key and length (24 bars at 68 bpm), made by importing the story and Last Day generators' synthesis.
- **Followers from the gifted system, bound to the room.** Echoes are vindicators given a case's power with
  `GiftedMobs.bestow` (UNCOMMON, evil, tagged `FOLLOWER` and `ROLLED` so nothing re-rolls them), so their moves,
  telegraphs and sigils are the gifted mobs' own. Each is bound to its case: while it walks the case counts as open
  (`SylarEntity.open`), and breaking it fades the echo and takes the power, with no backfire. Count them by the party
  (`SylarEntity.echoes(phase, fighters)`), keep them on the floor and on a fighter (`tickEchoes`), and discard them with
  the boss (`remove`).
- **Sync what the room lets you break.** The `OPEN` mask (drawn on, stolen, or with an echo out) is synced so the art
  edges exactly the breakable cases in gilt; a player should never have to guess which glass will hold.
- **Finishers at phase thresholds are one engine hook.** `Takedowns.PhaseFinishable`: the boss reports a synced
  window (`FINISHABLE`) that `looksFinishable` and `finishable` read, and `strike` (and `land`, for a finisher that hurts
  some other way) hands the blow to `phaseFinished` instead of killing. The finisher plays in full; the boss takes a
  slice, jumps to its next phase and reels after the transition (`pendingStagger`), or dies in its last tenth.
- **Test fixtures in the boss's package.** `SylarCollectionGameTests` reads package-private state (`safeHour`, the moves'
  weights) and runs with `tools/sylar-gametest.init.gradle`, which includes the root test helpers that
  `structure-gametest`'s package filter leaves out.

## From the Watchmaker (retired)

- **An arena is a pocket dimension rebuilt every fight.** Build the blueprint, save each player's way home, move them in,
  spawn a new boss there (a mob changing dimension becomes a new entity), and send everyone home on close.
- **Moving players in and out must not end the encounter**: mark the fight's own teleports (`moving(player)`).
- **Keep an arena's chunks force-loaded once built**, or a boss spawned there stands frozen for three seconds.
- **The game-test world lists its dimensions by hand** (`src/gametest/resources/data/minecraft/worldgen/world_preset/flat.json`).
- **Store anything a move needs later in its own field**: his next move's `mark()` once overwrote the rings' centre and
  dragged them across the room.
- **No fists during a windup**: clear the melee target while casting.
- **Read the swing record on every hit**, or one charged swing carries over to the next.
- **Taking a power for a fight is one check** in `PowerRules.canActWithoutPreservation` that the arena's close clears.
- **Walls rise after the shove**: `TemporaryBlocks.place` skips occupied cells, so raise a wall two ticks after pushing
  people off its line.

## From Elle Bishop (retired)

- **Foreshadowing that puts everything back**: change only block states you can restore, queue each restoration, and
  restore everything on server stop.
- **Never probe with `WorldMutationPolicy.canEdit`** for something that breaks nothing: it posts a `BreakEvent`.
- **Poll the arena's switches; don't listen for clicks.** A lever by hand, a broken lever and a player's EMP all count.
- **One geometry class, checked against the blueprint's markers** by a game test.
- **Send state for drawing through synced entity data, not `scale`.**
- **Cut a travelling piece short with `superseded`** when a later piece bound to the same entity arrives.
- **Freshly forced pocket chunks hide their entities** for a few seconds: tests wait for every corner of the room.
- **A move's end mustn't shorten a longer hold-off** (`Math.max`), so tests' and previews' hold-offs survive.

## Where the Collection stands against the skill's defaults (November 2026)

| Default | The Collection |
| --- | --- |
| `c:bosses` | yes |
| Per-hit and per-second caps, falloff, non-player blows | 6% / 4% per 20 ticks; full to 24 blocks, none past 36; others 3 a blow |
| Idle healing and leash | 5% a second after ten quiet seconds with nobody in sight; back to the dais if thrown out |
| Party scaling | +50% per extra fighter, up to x3 |
| Death scene | yes, three seconds |
| Theme music | three phase loops, a stinger, a last chord |
| A drop that opens the next thing | Sylar's Watch and Gray's Ledger (whose last line points onward; the next boss isn't built) |
| Re-fight item | the Repair Ticket, crafted from a Brass Movement |
| Minions | echoes (one at phase two, one more per two extra fighters; more at phase three in a party), bound to their cases; and his hazards (rubble, blue ice, the cracks) |
| Finishers at phase thresholds | yes: 6% windows above two thirds and one third, and his last tenth |
| A rare re-fight drop | the record of his theme, one in ten per fighter |
