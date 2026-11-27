# Superspeed: notes for agents

The reference power for "the body is the effect" (`speed/`, `SpeedEffects`, `SpeedAfterimages`, `SpeedCyclone`, `SpeedArt`). Containment runs are in [power-interactions.md](../power-interactions.md), Blur Boxing in [fighting-styles.md](../fighting-styles.md), Perception and slowed time in [time.md](time.md). What the player sees: [superspeed.md](../../superspeed.md).

Part of the notes for coding agents: start at [AGENTS.md](../../../AGENTS.md).

## Reusable patterns from the Superspeed rework

Superspeed (`speed/`, effects in `client/effects/SpeedEffects`, `SpeedAfterimages`, `SpeedCyclone`,
`setpiece/SpeedArt`) is the guide's reference for "the body is the effect". Its six optional skills were brought up to
the core's standard in November 2026. What the player sees is in [docs/superspeed.md](../../../docs/superspeed.md).

- **Afterimages are the speedster's signature.** `SpeedAfterimages` snapshots the real skin and limb pose and leaves it
  fading for 18 ticks. Use `along(from, to, n)` for a burst, `ring(center, r, n, phase)` for a lap, and
  `at(near, position, yaw)` for one placed anywhere (the Mirage's crowd, a dodge's leftover stance). The runner must
  be within three blocks of `near`/`to`.
- **Quick strokes, not set pieces.** Speed's effects are `AbilityEffects` kinds drawn with `LightningRenderer`:
  gold `FFC23A` ribbons, an amber `FF8A1E` halo and a hot `FFF6D8` core, with `bolt(...)` through them. Staged
  timing uses the stroke's `delay` (Lightning Throw's bolt leaves three ticks after the wind-up).
- **A variant flag in the scale.** A Slipstream passenger and a Charged Throw hop send `scale + 10`, the same trick as
  Pyrokinesis's `BLUE_SCALE`: no new kind or payload field.
- **Held states pulse.** The Mirage re-sends `SPEED_MIRAGE` every five ticks while it lasts; each pulse places a few
  afterimages by the game time (not the random source), so every viewer sees the same crowd.
- **Bursts teleport, swept.** `SpeedService.move` teleports along a `swept` path and draws the trail; build any new
  dash on it (Momentum Strike, Slipstream Rescue and the dodge all do).
- **First-person gestures stay out of the camera.** A raised or cocked arm in `FirstPersonArmGeometry` must sit at the
  edge of the view (Lightning Throw's starts at the top corner and whips down through the centre), and an effect's
  origin must be in front of the eye: the first throw started behind the camera and streaked across the whole screen.

- **Deflecting shots and clearing the air (Wind Arms).** `SpeedWindArms.catchShot(level, shot, from, to)` turns any
  projectile whose step this tick crosses the funnels, sampled every 0.4 blocks so a TaCZ bullet that crosses the whole
  funnel in one tick is still caught. It is asked from three places, like the Vibe guard: `events/SpeedWindEvents`
  (before each projectile's tick, and on impact) and `TaczLink`'s `EntityHurtByGunEvent.Pre`. Turned shots take the
  speedster as owner, so they are never caught twice; a trident keeps its thrower (loyalty) and is remembered instead.
  - An `AreaEffectCloud` never sends its own position (its tracker interval is effectively never): move it with
    `setPos` and broadcast a `ClientboundTeleportEntityPacket`.
  - Particles exist only on clients, so each client blows them itself: `client/effects/WindArmsAir` walks the particle
    engine (`mixin/ParticleEngineAccess`, `mixin/ParticleWindAccess`) for every player in the `WIND_ARMS` pose, using the
    same `SpeedWindArms.inFunnel` test as the server. This is how to make any power move smoke, spores or dust.

- **A visible runner must be hittable there.** Cyclone formerly drew a puppet on the rim while leaving the body
  at the eye. `CycloneGeometry` now supplies its eased opening and laps to the server; the player is swept round
  that path, and `SpeedCyclone` draws on that same path (see below). A blocked lap ends safely. A runner carried round
  the ring in first person sees nothing useful, so `TempestCamera` pulls the view back for every run, not only once
  a tornado has risen ([flight.md](flight.md), "Sound and feel"). Tornado windup is 190 ticks;
  tap duration stays 60, and the longer windup does not add eight seconds of ring damage. Test inflow with horizontal
  distance: measuring 3D distance counted the funnel's successful lift as failed pulling (`SpeedRunGameTests`).
- **A speed you set must be a speed you see.** The Cyclone's scroll setting once changed only the wind's drag and
  heat: the laps ran at a fixed 0.42 radians a tick at every setting, so scrolling up looked like nothing.
  `CycloneGeometry.lapSpeed(setting)` now runs 0.26 to 0.9 (setting 3 keeps the old pace), and the run is a
  `CycloneGeometry.Laps` schedule (from-age, angle, current and target lap speed, changing at `ACCEL`), so a change
  carries on from where the runner is instead of jumping. `SpeedKit` re-plans it with `toward(...)` on every
  scroll and sends it at once in `SpeedCyclonePayload.laps`; the client integrates the same schedule and fades the
  small difference its latency makes over six ticks (`SpeedCyclone.laps`). Keep the run's clock separate from any
  timer you reset: a tornado falling back into a vacuum resets `start`, but the laps keep `born`.
- **Draw a teleported body on its schedule, not where the client has it.** The server carries the runner with
  `teleportTo` every tick, and the client's `handleMovePlayer` sets `xo = x` on an absolute teleport, so the local
  player's body jumps a whole stride a tick with no smoothing (and other players see it every second tick through
  tracking). `SpeedCyclone` hides the body and draws the stand-in at `View.runner(age)` every frame; the afterimages
  are the same schedule `GHOST_TICKS` apart in the past, so they reach further round the faster it runs.
- **Scale the whole effect by one pace number.** `View.pace(age)` (0 at the slowest setting, 1 at the fastest, from
  the eased lap speed) drives everything in `SpeedArt.ring`: trail lengths (as `omega / LAP_SPEED`), the gold wall,
  torn-air streaks, the bow of pressed air, body lightning and the scorched track past 0.62, the lean
  (`SpeedCyclone.lean(angle, pace)`), the leg cycle and the ghost count. Because it reads the eased speed, the
  effects build up and die down with the runner, not with the key. A one-off on a change (a boom ring on a step up,
  a skid on a step down) is stamped when the new schedule arrives (`surgeAt`, `brakeAt`) and placed where the runner
  was then, so it hangs in the air behind them.
- **Scripted movement is not a run for friction.** Once the body really laps the ring, `SpeedFriction` measured the
  laps (a fixed 2.7 blocks a tick at every setting, over `FRICTION_PACE`) and burned even a vacuum in under two
  seconds. While a Cyclone holds the runner, heat comes from `SpeedFriction.cycloneHeat(setting, fastest)` instead
  (none at the vacuum or below the top two settings) and the smoothed pace is zeroed so the laps don't linger after.
  Any other move that carries the body on a set path (a finisher's glide, a rescue) should do the same.
- **Targetable mirages reuse `ShapeDecoy`.** `AdvancedTechniques.Mirage` keeps the owner and three speedster decoys,
  whose `runMirage` moves through swept, terrain-aware steps. They reuse the player's profile and Jade disguise
  question. `ShapeshiftTricks.burst` branches before shapeshifter confusion or damage: a struck speed copy only bursts.
