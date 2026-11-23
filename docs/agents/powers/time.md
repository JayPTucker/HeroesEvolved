# Space-Time and bent time: notes for agents

Space-Time Manipulation (`time/`, `TimeArt`), the time veil, the mob clock and slowed time as thick air (`time/TimeFlow`, `client/ClientTimeFlow`, `SlowTimeEffects`). Any power that stops, slows or rewinds anything goes through the rules here. What the player sees: [space-time-manipulation.md](../../space-time-manipulation.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the Space-Time rework

Space-Time Manipulation's tree (`time/SpaceTimeSkills`, numbers in `time/TimeGeometry`, art in
`client/effects/setpiece/TimeArt`) was rebuilt in November 2026: Stillness, Wayfinder and Historian, eighteen interaction
nodes, nine poses and ten pieces. What the player sees is in [docs/space-time-manipulation.md](../../../docs/space-time-manipulation.md).

- **Mobs have one temporal clock.** `time/TemporalMobTime.interval` is the strongest field's interval (0 stopped,
  1 normal); vanilla mob ticks and the whole gifted tick, including delayed steps, pulses and flurries, use it.
  `MobTimeSync` sends rate changes and initial tracking state. `client/ClientMobTime` drives body interpolation,
  casting poses, sigils, bound set pieces, short lightning strokes and delayed lightning steps. A gifted tick carries
  its actor through `AbilityEffectPayload`, so short effects retain their source clock. Keep player animations free.
- **A frozen memory is not a tracked player.** `TimeVeil.perceivedTarget` returns an unspawned, unregistered shadow at
  the caster's starting position. Mob goal targets and brain `ATTACK_TARGET` reads resolve through it, and sight and
  acquisition cannot track the actual caster. Weak shadow references resolve back to the owner when time resumes.
  Gifted Superspeed/Space-Time users keep their own clock and perception. Tests are in `TemporalSharingGameTests`;
  `tools/temporal-preview.init.gradle -Pmobtime` stages two ordinary zombies and a gifted pillager beside the shared-time guest.

- **One rule for every kind of stop.** Still World, Selective Stasis, a Stasis Seal and Moment's Edge all answer one
  question, `SpaceTimeService.holder(entity)` (who holds this creature in stopped time), and `TimeStopService.storeImpact`
  stores the holder's blows for any of them. `TimeStopService.release(server, owner, target)` lands one creature's blows;
  Still World's end lands only those its owner no longer holds. A new freeze (a cryo cocoon that stores blows, a boss's
  time trap) is one more branch in `holder`, not a fourth copy of the storing code.
- **Making a core move free for a while.** Still Point overrides Fold Space's `energyCost(context)`, `cooldownTicks(context)`
  *and* `cooldownId(context)`: during a Still World the fold uses a cooldown id of its own that never starts, so a fold
  made just before the Still World doesn't block the free ones. Count the uses where the move succeeds
  (`SpaceTimeSkills.folded`), not in the cost check, which runs on failed presses too.
- **Hooks at the end of a core move.** Fold Space calls `SpaceTimeSkills.folded(player, from, to, free, carried)` once it
  has moved you, and asks `SpaceTimeSkills.behind(player, reach)` for a destination before its own search. Passives that
  change a core move (Behind You, Snap Shut, Still Point) live in the tree's class; the move only asks.
- **Bonuses before storage.** Damage bonuses (Behind You, Foreknowledge's marks) run at `EventPriority.HIGH` on
  `LivingIncomingDamageEvent`, ahead of the normal-priority handler that stores blows, so what is stored already includes
  them. Damage a skill deals for itself (Fast Forward's poison and burning, Era's End's replay) sets a `replaying` flag so
  it isn't recorded as one of your blows and replayed again.
- **Read a holder's skills when the blow is stored, not when it lands.** The holder may be offline (or a test's
  `FakePlayer` outside the player list) by the time blows land, so `StoredImpact` keeps Compound Interest as a flag.
- **Landing blows can kill, and a death lets go of more.** Death runs `SpaceTimeService.forget`, which releases whatever
  the dead player held. Every loop that lands blows (`TimeStopService.release`, the stasis and hold expiries, Moment's
  Edge) first takes its entries out of the map, then lands them, and the "are we landing" guard is a depth counter, so a
  release inside a release never throws `ConcurrentModificationException` or re-stores a blow.
- **Hurt, then pull.** A blow from a player knocks its target away from them. Snap Shut hurts first and then sets the
  pull, or the knockback cancels it.
- **A chain of folds is a ticked sequence.** Moment's Edge holds every target for the whole sequence, then moves you behind
  one every `EDGE_STEP` ticks (`p.teleportTo(level, x, y, z, yaw, pitch)` with the yaw and pitch from
  `TimeGeometry.facing`, so you face each one). The cuts go through the normal hurt path and are stored because the target
  is held; letting them go at the end makes "they feel every cut at once" true with no extra damage code.
- **Everyone has a short history.** `SpaceTimeService.record` now keeps every player's last five seconds (25 samples), not
  only the traveller's, so Shared Hour can take friends back through their own. It is the same bounded deque.
- **Remembering attackers.** Foreknowledge keeps who hurt each traveller in a bounded deque (32 entries, 15 seconds), and
  turns it into marks when time is undone: `SpaceTimeSkills.remember(player, ticks)` is called by `secondChance` and by
  `TimeRewindService.rewind` *before* it clears the traveller's state. Marks are UUIDs, so a mob a rewind puts back is still
  marked.
- **Private pieces for a marker.** Foreknowledge's clock is sent with `TimeGeometry.showTo(viewer, ...)`, re-sent every ten
  ticks as a twelve-tick piece. It faces the camera on the yaw only, so it stays upright.
- **A held piece that stops when something unbound replaces it.** `superseded` matches unbound pieces only on equal origin
  *and* target. The seal's sphere and its throw share an origin but not a target, so `TimeArt.seal` checks
  `SetPieceClient.where(Piece.TIME_SEAL_BREAK)` for a throw at its origin instead.
- **One clock primitive.** `TimeArt.face(c, at, axis, r, hour, minute, colour, alpha)` draws every clock: a three-layer rim,
  twelve marks (every third longer), and two hands with pale cores. Stopped clocks read ten past ten, hurried ones race
  forward, undone ones spin back; colour does the rest (violet and ice stopped, amber and gold hurried). The anchor's hands
  show the real hour, carried in the piece's scale.
- **Moving faster than time (`time/TimeVeil`).** While someone stops time, everyone else is sent a
  `TimeVeilPayload` and `client/effects/TimeVeilClient` hides the caster (model, name, and shadow via
  `mixin/VeiledShadowMixin`) and draws a still stand-in with their skin where they stood; the reveal is a blur
  (speedster) or a blink (traveller), or nothing if they are back on the spot. Slowed time keeps a veil (hints, mob
  targeting, tint) but hides nobody: `Veil.hiding()` is true only for `STOP` or a guest of a stopped field, and the
  stand-in is anchored when hiding starts (`Veil.anchor`), so a stop inside a slow stands where time stopped. Movers
  in slowed time are drawn as a blur instead (see "slowed time" below). Who sees through it is one rule,
  `TimeVeil.movesInTime`. `AbilityEffects.send` and `SetPiecePayload.send` skip any viewer for whom
  `TimeVeil.masks(viewer, origin)` is true, so a trail drawn beside a hidden body can't give it away; a field's own
  visual goes only to those who see the caster (`TimeVeil.showToSeers`). Begin and end it with a reason
  (`SLOW`, `STOP`, `PERCEPTION`), so overlapping fields keep one veil. The chat hints (`hint`, `over`) go out
  with the first reason and the last, and whoever was puzzled (`Veil.puzzled`) is told it is over even if the caster
  logs off.
- **Photographs:** `tools/time-skills-preview.init.gradle` (`uitest/TimeSkillsPreview`) casts every skill into
  `build/time-skills-preview/screenshots/`. It has its own flag, `heroes.preview.timeskills`: the older `TimePreview` driver
  reads `heroes.preview.time`, and two drivers on one flag fight over the camera and the world. A back camera always hides
  the creature you aim at behind your body; use first person or put the target close for a front shot. If the run fails
  with "Failed to locate a primary monitor", the Mac's display is asleep: `caffeinate -u -d -t 600` wakes it.

## Reusable patterns from slowed time (bent time as thick air)

Slowed and stopped time were reworked in November 2026 so the whole world shows it, not only the creatures in it
(`time/TimeFlow`, `client/ClientTimeFlow`, `client/effects/SlowTimeEffects`, rings in `setpiece/SlowTimeArt`). One idea:
**bent time makes the air as thick as water, and everything fast leaves a mark in it.** What the player sees is in
[docs/space-time-manipulation.md](../../../docs/space-time-manipulation.md#bent-time) and the Perception bullets of
[docs/superspeed.md](../../../docs/superspeed.md).

- **One picture of time for the clients.** `TimeFlow.fields` gathers every field (Between Seconds, Still World,
  Perception, each service's `describe`) and `TimeFlow.style` names who moves faster than the world (field owners,
  sharing guests, speedsters at speed). `TimeFlowPayload` carries both, sent on change and once a second; a following
  field's centre is read from its owner on the client, so `sameAs` ignores it and walking doesn't resend. The client
  asks `ClientTimeFlow.rate(x, y, z)` (1, 1/n, or 0 stopped) and `style(entity)`. A new kind of field is one more
  `describe`; anything that should slow on the client asks `rate`.
- **Every particle has a clock.** `mixin/TemporalParticleMixin` gives `Particle` a `TimedParticle` clock and
  `TemporalParticleEngineMixin` skips its tick until the clock is due and draws it with a slowed partial tick (the mob
  clock's trick, `ClientMobTime`). Smoke, splashes and explosions drift; stopped, they hang. `PowerParticle` is exempt:
  power effects keep their own timing.
- **Rain on its own clock.** `mixin/TemporalWeatherMixin` swaps `LevelRenderer.ticks` and the partial tick inside
  `renderSnowAndRain` for `ClientTimeFlow`'s weather clock, which runs at the camera's rate, and skips `tickRain`
  (splashes and patter) as often. The clock always runs (at 1 when nothing is slowed), so rain never jumps.
- **A speedster is dragged, not frozen or freed.** `PlayerTimeSlow.update` uses `TimeFlow.drag` instead of the field's
  1/n while a speedster's `STRIDE` is on: `DRAG_SLOW` 0.55 in slowed time (their own Perception too), `DRAG_STOP` 0.4
  stopped. Their hands keep full attack speed. It rides the same attribute modifier, so flight scaling
  (`SlowedFlightMovementMixin`) and the client follow for free.
- **A floor under fuses.** `TimeFlow.fuse(interval)` is at least `FUSE_INTERVAL` (6) once time is slowed: a swelling
  creeper's mob interval (`TemporalMobTime.interval`, so `MobTimeSync` tells the client) and lit TNT on both sides
  (`TimeSlowEvents`, `ClientTimeFlow.fuse`). A new "about to go off" thing gets the same floor.
- **Blur from distance, not time.** `SlowTimeEffects.blur` leaves an afterimage every `BLUR_SPACING + speed ×
  BLUR_STRETCH` blocks travelled, so the blur thickens with speed (easing toward two a tick) and a mover standing still
  leaves none. A fixed small spacing at a speedster's pace stacked thirty identical ghosts into a solid tube; fewer,
  fainter (`BLUR_STRENGTH`), shorter-lived ones read as a blur. `SpeedAfterimages.blur(player, at, yaw, tint, life,
  strength)` takes those per ghost; the speedster's ordinary running ghosts step aside while `SlowTimeEffects.blurring`.
- **Ground chips are real particles.** `TimeMote` is a `TerrainParticle` (the block's own texture) that grows in and
  shrinks out; spawned into slowed time it inherits the slowdown, so dust hangs and kicked spray arcs slowly. Stopped,
  spawn them at their height already (they will never rise). `settle()` drops them when time runs again.
- **Rings anchored in the world.** Shot wakes, footfall ripples and body rings are `SlowTimeEffects.Ring` records drawn
  live by `SlowTimeArt`. Ripples age by their spot's rate (a little faster, so a trail stays a trail); a shot's rings
  age in real time, or its wake would stretch twenty blocks behind a slowed shot. None age when stopped, and then they
  turn violet. A shot's rings sit `WAKE_SPACING` apart, each turned `WAKE_TWIST` round the line from the last, so the
  wake winds like the loose spring of rings behind a slowed bullet on screen (the first try, rings every 0.2 blocks,
  made solid tubes). Each ring is a faint slate edge in SOFT plus halo, body and core in GLOW, so it reads against a
  bright sky, and fades near the camera. A particle's clock starts due, or a blast in slowed time shows nothing for
  its first slowed tick.
- **Photographs:** `tools/slowtime-preview.init.gradle` (`uitest/SlowTimePreview`, flag `heroes.preview.slowtime`)
  shoots dust and rain, a traveller's blur, a creeper's slowed fuse and blast, shot wakes, Still World, the resume and a
  speedster wading through Perception into `build/slowtime-preview/screenshots/`. It drives the real player with
  `keyUp`/`keySprint.setDown` and runs a side camera alongside with `TempestCamera.fix` each tick. Tests:
  `TimeFlowGameTests` (in `tools/temporal-gametest.init.gradle`).

- **A recorded past is consumed on entry, after its echoes are spawned.** Clearing it earlier loses the cast;
  clearing only on return permits repeat visits. `TemporalSnapshotService.enter` keeps the return trip in visit
  data and empties the recording. `TemporalRevenge` saves eligible deaths, actual death drops and pending returns;
  an echoed killer's defeat claims a death once, invalidates its tagged originals, then delivers in the present.
  Player/item ticks, container opening and machine transfers purge invalidated stacks even after unloading.
