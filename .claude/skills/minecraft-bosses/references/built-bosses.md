# Built bosses: what they taught

Lessons from the bosses this mod has built. **Sylar's Collection** (`story/sylar/`, November 2026) is the live one and
the example to copy; the Watchmaker (Sylar's old clock-shop fight) and Elle Bishop (the Company's boss on Primatech's test
floor) were retired when the Collection replaced them, and their code is in git history before it. Their lessons still
hold, so they are kept below. What the player sees: [sylar.md](../../../../docs/sylar.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../../AGENTS.md).

## The Living Seal: reusable patterns

- **A flying boss must commit its body, not hover and cast.** The first version locked the Dragon in vanilla's
  HOVERING phase, circled it 54 blocks out and froze it in mid-air for every windup (a null fly target stops vanilla
  flight dead), while thin lines fired from its head. Players called it garbage. `endgame/DragonBrain` now owns the body:
  vanilla steering only for circling close over the party (`flyTarget`, a lead point on an orbit round the fighters),
  and exact cubic `Leg`s for everything committed (line up during the windup, dive through the mark, the breath run,
  the landing drop, the lunge, the take-off climb, the crash). A leg sets position and yaw each tick and leaves the
  multipart neck and tail to trail behind from vanilla's position history, which is what makes it look alive.
- **Land the flier.** Cataclysm's bosses are remembered for their ground windows. The Dragon lands on a fighter after
  two or three air moves (`AIR_MOVES`), fights on foot choosing by where people stand (tail behind, bite ahead, scorch
  in the cone, gust when close; `DragonBrain.next`), and takes off on a timer or when nobody stays near
  (`IDLE_TAKEOFF`). `grounded` doubles as the client's sitting pose through the hover mixin's `isSitting`.
- **Paint the ground, terrain-following, at Cataclysm scale.** `EndBattleArt.strip` and `sector` sample
  `EndGeometry.waveHeight` per vertex, draw a faint footprint of the whole danger, a bright fill that completes at
  `EndGeometry.arrival(move)` (windup plus the dive's approach or the landing's drop), hot edges, and a white flash
  (seed role 1) when it lands. Fill lanes from their start, arcs from the body outward, circles from the centre.
- **A part hit is a design choice.** Vanilla quarters every non-head part. `EndDragonBattleMixin.heroes$partHit` routes
  part hits through `EndBattle.dragonPartHit` (head 1.2, body 0.75) before vanilla's own sitting/take-off logic can run;
  ability hits on the whole entity count at face value.
- **Never fly a committed path through stone.** `DragonBrain.clear` turns a dive or run lane until both halves have a
  clear line past the obsidian towers.
- **Committed legs survive a cancel.** A phase change or a stagger calls `cancelCast`; `DragonBrain.cancel` drops only
  line-up legs, so a dive, drop or crash always finishes instead of leaving the body hanging with its sitting pose.
- **Leave no safe distance.** The landed Dragon's moves reached 16 blocks at most, and nothing counted someone at 16
  to 24 as "gone" (`IDLE_TAKEOFF` watches 24), so standing just out of reach was a free wait. Seal Rush
  (`DragonBrain.lane`, `rush`) charges a fixed lane at anyone 12 to 30 blocks off. Give every grounded boss an answer
  for each ring of distance (close, mid, far, up high), and check the gaps between their ranges.
- **A ground lane walks the real terrain.** `DragonBrain.lane` steps a block at a time and stops at void (an empty
  column, `height <= minBuildHeight`), a step of more than 2.5 blocks, a tower top or anything blocking a line two
  blocks up; the charge must reach past its target or it isn't chosen. Its test forces the End chunks and flattens a
  strip first, because other tests leave altars and markers standing at the island's middle.
- **Pause the ground timer during a move.** `grounded` counted down through windups and charges, so a long move
  chosen late could outlast the landing and leave the body "airborne" while standing, with no take-off ever chosen.
  It now counts only while nothing is cast and no leg runs.
- **A phase must show on the body.** Tether colours alone weren't seen. `DragonBrain.aura` sends forced particles to
  the party from the native parts (`EnderDragonPart.name`: wings stream torn space in phase two, wings, head and
  body shed embers in phase three), and `phaseChange` flashes a ground shockwave.
- **Settle the body before photographing it.** The native model banks with the yaw change in its position history;
  an instant turn in a preview rolls it onto its side for half a second. `EndgamePreview.face()` turns it a few ticks
  before the move, and `focus()` frames shots from the island's middle so the obsidian towers stay behind the action.

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

- **Give a large boss actual motion and a combat rhythm.** The first Wither drifted slowly, blinked its lunge and
  never moved for its slam. `WitherGeometry.chain` provides range-sensitive phase combinations; `WitherMoves.tickMotion`
  moves the body over multiple ticks with collision substeps, fixed endpoints and a once-per-fighter hit set. A blocked
  descent must not damage the unreachable marked floor. `WitherReworkPreview` observes unpaused AI, beyond staged poses.
- **Test whether the counter can physically be used.** The first anchor was enclosed on all six faces; tests removed
  its glass directly and missed it. `anchorGlassHasRealReachableOpenFaces` casts an actual player-height block ray.
  `generate-wither-arena.py` leaves two faces open and supports the cap independently. A seven-second feed remains
  counterable after its initial short cast. The .8-high pulse is also tested against both grounded and jumping feet.
- **Sync the action, aim and exposure, then animate the body.** `RiftWitherEntity` shares action/age, committed aim,
  stance and exposure with `RiftWitherRenderer`. `WitherGeometry.head/mouth` provides server/art emission origins.
  Independent side heads snapshot their target before firing; three spaced heavy hits earn a chain break, preventing
  the old one-hit interruption loop. Effective party health uses damage units above vanilla's 1000-health ceiling.
- **Cancellation must invalidate queued work.** The new console could cancel before the deferred formation actor
  existed. `WitherArena` captures a generation at queue time and rejects stale actors in `swallowed`, so canceled
  formations cannot reopen the encounter after their operator leaves. The real preview clicks each admin control.
- **Replace the construction before the vanilla explosion.** `wither/WitherEvents.formed` intercepts a newly made
  invulnerable native Wither in `EntityJoinLevelEvent`; saved and ordinary command-spawned Withers are untouched. A
  second construction refunds its ingredients. The integration test builds the real soul-sand T and skull pattern.
- **Keep retreat useful without providing a firing nest.** `WitherArena` pairs a persistent source breach with a
  solid, sheltered return alcove; `sheltered` excludes both outgoing damage and boss targeting there. Abandonment
  rebuilds the next attempt. Saved homes and death/prize stashes survive a restart. Release only chunk tickets owned
  by the encounter; an operator's pre-existing tickets survive cleanup.
- **Flight needs a volume and an opponent.** `WitherGeometry` and `generate-wither-arena.py` share checked markers in
  an 81-block floor, 46-block flight space and 48-block ribs. The boss approaches airborne opponents between casts,
  while marked origins stay fixed. Lit rib ledges eliminate upper dark floors without filling the central airspace.
- **A canceled warning must stay canceled.** `WITHER_CANCEL` outlives every Wither warning, so an interrupted marker
  cannot reappear when its shorter backfire animation expires. `WitherArt` reads the same windup and radius as hits.
  A preview must cancel on an earlier server tick than starting its next pose: `superseded` includes equal timestamps,
  so canceling and casting in one callback hid the new warning too. `WitherReworkPreview.serverTick` separates them.
- **A ray with no entity needs an empty collision context.** Passing `(Entity)null` to the 1.21.1 `ClipContext`
  constructor throws in `CollisionContext.of`; use `CollisionContext.empty()` for world-only cover rays.
- **A broad-phase box is not a circular impact.** `RiftSkull.onHit` filters nearby entities by distance from their
  hitbox to the impact, so square corners cannot hit outside the warning. `WitherMoves` fixes both ends of a lunge's
  actual three-dimensional path when it starts; aiming the drawing at a flier and the server at the floor was wrong.
- **Preview after the transfer settles.** A fixed camera installed before an Overworld teleport photographed only
  sky even though source blocks existed on the client. `WitherPreview` reasserts its shot after the transfer and uses
  creative flight plus an ability update for airborne photos, so the player does not fall out of the staged lane.

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
