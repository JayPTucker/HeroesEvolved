# Notes for coding agents

- **Animations and power visuals:** read [docs/animation-guide.md](docs/animation-guide.md) before making or
  changing any ability effect, arm pose, set piece or camera. It sets the quality bar (from Quake, Superspeed
  and Space-Time Manipulation), the tools, the body anchor points and how to photograph your work.
- **Structures:** see `.claude/skills/minecraft-building/SKILL.md` (blueprints, renders, in-game screenshots).
- **Gameplay tests:** `./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle`.
- **In-game previews:** `tools/*-preview.init.gradle`; stage scenes at least 50 blocks from spawn, where the
  bunker's walls trap the player.

## Reusable patterns from the Flight rework

Flight's November 2026 rework (Skyburst, Tempest, Skyhook and the six optional skills) solved problems other
abilities will meet. Reuse these instead of starting again. What the player sees is described in
[docs/flight.md](docs/flight.md) and [docs/carrying.md](docs/carrying.md).

### Moving a player fast

- **Server-driven dashes faster than 3.9 blocks a tick need their own motion packet.** Vanilla's
  `ClientboundSetEntityMotionPacket` clamps each component to about 3.9 blocks a tick.
  - Skyburst peaks at 10 blocks a tick, so it sends `network/SkyburstMotionPayload` (full-precision doubles)
    to the dashing player every tick.
  - The dash itself is a `Skyburst(direction, peak, ticks)` record in `FlightDynamicsService`, applied each
    tick by `applySkyburst`. Speed eases from the peak to an exit speed (`peak + (exit − peak) · age²`), so it
    hands back into cruise without a jolt.
- **Every fast move is swept.** Pass each tick's displacement through `SpeedService.swept(entity, displacement,
  clearance)`. It checks the whole body's path in quarter-block steps.
  - The optional `clearance` predicate adds checks of its own: Skyburst passes
    `EntityCarryService.canTravelTo`, so an overhead load must fit too.
  - If the swept move comes back near zero, the dash has hit something: end it there (`applySkyburst` returns
    false and zeroes the push).
- **Fast orbits follow an ideal point.** Tempest's flyer circles at about 3.6 blocks a tick. Aiming straight at a
  target from the position the client reported a tick ago oscillates and flings the player off.
  - Instead, an ideal point runs round the circle. The velocity is that point's step forward plus a gentle
    correction toward it (×0.3, capped at 1.2 blocks), and the result is swept.
  - While the flyer is still far above or below the orbit height, a separate approach phase moves them
    vertically first (`ascent × 0.35`, clamped to −3…1.4), also swept.
- **Steering that tightens as you turn.** Cruise lerps velocity toward the look direction with a factor that
  grows when you aim away from your current path (`0.20 + 0.16 × (1 − alignment)`). It turns crisply without
  snapping. Letting go of Forward cuts velocity to 45% at once, a brake toward hover.
- **Keep state through a maneuver.** `startFlightSession(player, ticks, preserveCruise)` keeps the wheel speed
  and flight state when a maneuver restarts the session, so the player resumes cruising at their speed.
  Maneuvers check each other's flags (`isCharging`, `isBursting`, `isDiving`, `isRolling`, `isChanneling`) so
  one never overwrites another.
- **Client-owned nudges stay on the client.** When something should shift a player without interrupting their
  own momentum (the speedster's ledge drop in `client/SpeedLedge`), do it on their client. A server teleport
  or a vanilla motion packet replaces the client's velocity outright.

### Choosing targets

- **One friend/foe rule for a whole power.** `FlightSkillService.isFriend(player, target)` covers yourself,
  allies and your own tamed pets. `hostile(player, target)` is everything else that is alive, not a spectator,
  not being carried, not a temporal echo, and a player only where PvP is allowed. Skyhook, Tempest, Meteor
  Drop and the skills all use these two, so a rule changes in one place.
- **Respect cover.** `TornadoWind.exposed(owner, center, target)` raycasts from the effect to the target.
  Tempest's wind (`TornadoWind.blowTempest`) only grabs targets that are hostile and exposed.
- **Give a warning before the pull.** Tempest's one-second wind-up no longer pulls anything; it gives targets
  time to escape before the storm takes hold.

### The shared tornado

- **One storm for two powers.** Tempest stands the Superspeed tornado on the ground.
  - `speed/TornadoShape` holds the funnel's shape (`radius(h, height, ring)`, `height(age)`, `lift(age)`).
  - `speed/TornadoWind` holds the physics: `blow(...)` for a speedster and `blowTempest(...)` for Flight.
  - The client learns about it from `SpeedCyclonePayload`, whose style (`SPEED` or `FLIGHT`) picks the look.
- **Variants change the look, not the shape.** `SpeedArt` draws both. A Flight storm uses Flight's own
  condensation colours (`TEMPEST_CLOUD`, `TEMPEST_WIND`), has no lightning, and adds `FlightArt.tempest(...)`
  on top. Use this for any vortex, whirlpool, sandstorm or gravity well: add a style, not a second tornado.
- **Grounded effects cast from the air.** Tempest touches down ahead of you however high you fly.
  - `FlightService.ground(level, at, up, down)` finds the first open cell above a solid block or water. On
    cast it searches all the way down to the world's floor; each tick after that it re-finds the ground within
    a few blocks while you steer it with your look.
  - It is private for now; move it to a shared helper when another power needs it.

### Drawing it

- **One art class, one shape language.** Every Flight set piece is drawn by
  `client/effects/setpiece/FlightArt`. `PieceArt` sends all twelve Flight pieces (`FLIGHT_RELEASE`,
  `FLIGHT_SKYBURST`, `FLIGHT_IMPACT`, `FLIGHT_RESCUE`, `FLIGHT_SHELTER`, `FLIGHT_CURRENT`,
  `FLIGHT_THERMAL_MARK`, `FLIGHT_THERMAL_RISE`, `FLIGHT_INTERCEPT`, `TEMPEST_DRILL`, `GALE_WALL`,
  `FORMATION_FLIGHT`) to one `FlightArt.draw`.
  - They share three primitives: `arc` (a translucent sheet with a turning lip), `crest` (a broken, tapered
    ribbon that ripples along its length) and `spiral`.
  - The palette is condensation, not neon: `SHADOW 535A5A`, `VAPOR D2D4CE`, `CONDENSATION DCDDD5`,
    `CORE F6F4EC`. A new power that wants a coherent look should copy this structure: one art class, a few
    shared primitives, one palette.
- **Stagger the fronts.** Skyburst draws up to three pressure fronts, each 2.5 ticks behind the last. Each opens
  on an ease-out cubic (`1 − (1 − p)³`), so it snaps open then settles, and drifts backward into the wake.
- **Effects that follow a state draw live.** `FlightArt.live()` and `drawLive(c)`, hooked into
  `SetPieceClient`, draw the wind round every flyer who is charging, gathering a Vacuum Clap or diving, with no
  packet per frame. Copy this for anything that lasts as long as a state does.
- **Scale with the particle setting.** `FlightArt.minimal()` / `detail()` read the player's particle option:
  Minimal keeps one front and drops the spirals; All adds mist crests and finer ribbons.
- **Stop fast movers vanishing at the frame edge.** `mixin/FlightRenderBoundsMixin` widens culling for a
  dashing player (and their cargo) along the last tick's travel. Without it, a 10-blocks-a-tick dash leaves
  the model culled before its drawn position catches up.
- **Face where you're going, not where you're looking.** `FlightVisualGeometry.travel(player)` gives a real
  travel vector even for other players, whose velocity isn't known on your client: it falls back to the
  position change since last tick. The pose renderer, vapor trails and banking all use it.

### Bodies and state

- **Animate from phase flags, not timers sent each tick.** `FlightVisualStatePayload` carries bit flags
  (`TAKEOFF`, `BURST`, `DIVE_RELEASED`, `VACUUM`, `WIDE_VACUUM`, and others). When an animated flag changes,
  `ClientFlightVisualState` stamps the game time, and `phaseAge(id, now, partial)` tells any animation how far
  into its phase it is. One small sync per change drives smooth poses and effects on every client.
- **Give each maneuver its own pose,** in third and first person: `FLIGHT_COIL` (hands draw in while
  charging), `FLIGHT_BRACE` (arms forward for thrust or a plunge), `FLIGHT_ROLL` (tuck through a roll),
  `FLIGHT_CLAP` (hands sweep together) and `FLIGHT_CAST` (one arm sweeps out).
  - Each eases with `smooth(age / n)` or a sine over its length. `FirstPersonArmGeometry.flight(...)` mirrors
    them in first person.
  - Ambient poses read state too: a hover holds the arms apart with a gentle sway and staggered feet; a fast
    flight leads with a fist; banking adds the heading error and locks at −32° while circling a Tempest.
- **Carrying survives packet order.** `CarryAnchorEntity` syncs both endpoint IDs (`CARRIER_ID`, `CARGO_ID`).
  The client re-links the passenger chain once both entities exist, because the passenger packet can arrive
  before either entity has spawned. Seats come from `anchor.seatPosition(carrier)`; fast moves check them with
  `EntityCarryService.canTravelTo`. Carried loads skip the dash pose and keep the upright carry frame.

### Sound and feel

- **A sound with its own range.** `ModSounds.SKYBURST_BOOM` is a fixed-range event (192 blocks), and its
  `attenuation_distance` in `sounds.json` matches. Keep the two equal.
- **Feel it as well as see it.** `CameraShakePayload.sendAround(level, at, 64, strength, ticks)` gives a
  tremor that weakens with distance. Skyburst and impacts scale it with charge and Momentum.
- **The camera for watching a big effect** (`TempestCamera` + `mixin/TornadoCameraMixin`) pulls back and holds
  still: 26 back and 12 up for a speedster's tornado; 34 back and 15 up for Tempest, raycast in front of walls.
  It moves only the camera at the end of `Camera.setup`. **Never use `Minecraft.setCameraEntity(otherEntity)`**:
  it stops the local player moving and reporting their position.
- **Speed tuning lives in one place.** `FlightSpeedControl.speed(step, max)` maps wheel steps to cruise speed;
  Skyburst's numbers are constants at the top of `FlightDynamicsService`.

## Reusable patterns from the Induced Radioactivity rework

The radiation power (`radiation/`, art in `client/effects/setpiece/RadiationArt`) was reworked in November 2026
to be more fun: a mobility move, punchier hits, faster blasts. What the player sees is in
[docs/induced-radioactivity.md](docs/induced-radioactivity.md).

- **A press-again follow-up that is free.** Fission Leap launches on the first press and slams on the second.
  The second press uses `SimpleAction.builder(...).followUp(...)`, which the use service checks *before*
  cooldown and stamina, so the slam costs nothing and isn't blocked by the launch's cooldown. Use this for any
  two-stage move: a dash then a strike, a throw then a detonation.
- **Short server-driven hops use the vanilla motion packet.** Under 3.9 blocks a tick (the leap is 1.2 up,
  the slam 2.6), `setDeltaMovement` plus `ClientboundSetEntityMotionPacket` is enough. Re-send the slam
  velocity every tick so drag doesn't slow it. Over 3.9, see Skyburst's payload above.
- **Land detection needs a fallback.** The slam lands on `onGround()`, `verticalCollision`, water or lava,
  *or* when its time runs out (`SLAM_MAX`). Without the timeout, a test player (or a player whose packets
  lag) would hang in the slam forever.
- **Cushion falls for a window, not an instant.** `RadiationLeap.cushioned` stays true for ten ticks after
  landing, and a `LivingFallEvent` hook zeroes the fall. The server can process the landing packet a tick
  after it ticks the move.
- **One blast helper for every size.** `RadiationLeap.blast(player, at, radius, damage, stages, throwSpeed)`
  hurts, doses and throws everything in range, harder near the middle, with knockback resistance respected.
  The launch, slam and other blasts all call it with different numbers. The art does the same:
  `RadiationArt.blast(...)` draws the launch and the slam as one shape at two sizes.
- **Keep action IDs; change names.** Fission Leap kept the old `radiation_pulse` ID, and Critical Mass kept
  `radiation_exploding_man`, so saved loadouts, `power_balance.json`, config keys and `ArmPoseActions` all
  still match. Only `en_us.json` names changed.
- **Long charges must be stoppable.** Critical Mass charges for three seconds on purpose: other players need
  time to stop it. Shift cancels it, and damage taken during a voluntary charge adds up until it breaks
  (`RadiationService.CHARGE_BREAK_DAMAGE`). An involuntary meltdown can't be stopped. Give any big ultimate a
  wind-up that opponents can see and answer.
- **Fast blast, slow cloud.** The explosion's flash, fireball and shock now arrive within a few ticks
  (`MiniBlast`: fireball time constant 3, dust and condensation 2.2), while the mushroom cloud still rises and
  rolls over at its own pace. Impact should be instant; the aftermath can linger.
- **Centre ground effects on the ground.** A caster who rose while charging still blows a crater at their
  feet's ground: `NuclearFallout.groundUnder(owner)` raycasts down a few blocks.
- **Replace the old effect when you add a new one.** The first EMP rework drew its new set piece *and* the
  old `AbilityEffects` rings, which showed up as thick grey tubes. When a set piece takes over a visual,
  remove the old `AbilityEffects.send` for it.
- **A held effect re-sent as short pieces.** The Meltdown Beam sends a four-tick `RAD_BEAM` piece every two
  ticks; each copy draws at about half strength so the two overlapping copies add up to one. Contaminated
  ground does the same with `RAD_ZONE` (twelve ticks, re-sent every ten, fading in and out over two).
- **A skill tree of interactions, not stats.** Its eighteen nodes (`radiation/RadiationSkills`) each change a move
  or tie two together: Chain Vent bursts the sick, Fallout Boots and Catastrophe turn the leap's ground into a
  combo, Breeder Reactor trades a meltdown for Overdrive. To do this for another power:
  - add the power to the no-stat-bonus list in `SkillTreeService.bonus`, or the positional nodes (`_0_0`,
    `_0_2`, …) keep granting generic cost, damage and cooldown bonuses on top of their new meaning;
  - route the three implicit branch actives (`SkillActionService.canUse/activate`) and the three named
    techniques (`AdvancedTechniques` or `ExpansionSkills`) to the power's own class;
  - keep the node IDs so purchases and prerequisites carry over, and check `name`/`description` against
    `en_us.json` with `python tools/verify-skill-content.py`.
- **Tests that start a detonation must clean up on failure.** If an early `runAfterDelay` check throws before
  the fixture's `finally`, the test player stays registered and *really detonates* later, wrecking every test
  that runs near it (15 unrelated failures once). Wrap early checks in `try { ... } catch (RuntimeException e)
  { remove(player); throw e; }`.

## Reusable patterns from powers working together (speedster containment)

A speedster can run rings round a radioactive player to cool them or hold in their blast
(`speed/SpeedContainment`; docs in [docs/superspeed.md](docs/superspeed.md)). It is the first power that acts
on another, and these are the patterns to reuse for the next one.

- **One key, two meanings by context.** The Cyclone key starts a containment run when a hot radioactive player is
  in reach (`SuperspeedAbility.CycloneOrArms.activate` tries `SpeedContainment.start` first), and a Cyclone
  otherwise. Its release goes to whichever is running.
- **The other power exposes a small API, not its internals.** `RadiationService.critical(player)`,
  `hold(player)` (push the warning and the charge back a tick) and `defuse(player, core)` are all the speedster
  needs; it never touches the radiation maps directly.
- **Reuse the drawing.** The run is a Cyclone view with a new style (`SpeedCyclonePayload.CONTAIN`): the client's
  runner, trail and wind come for free, a faster lap is one multiplier in `SpeedCyclone.View.angle`, and
  `SpeedArt.containment` draws only what is new on top.
- **Cost that can kill, by tier.** Strain is paid in stamina first and health after
  (`staminaPerHeat` / `healthPerHeat`, kinder at tier 5), with fractional costs carried over in a debt.
  The killing blow is checked *before* it lands, so the death can be special.
- **A special death.** A custom damage type (`data/heroesevolved/damage_type/disintegrated.json`, its death
  messages in `en_us.json`, and the `bypasses_*` damage tags so armor and Resistance don't blunt it) gives the death
  its own message. The `SPEED_DISINTEGRATE` set piece draws the body breaking into cubes, and
  `SpeedArt.disintegrating(id)` hides the real body and corpse while it plays (checked in `SpeedCyclone.hide`).
- **A defence another power provides.** Shield-making powers survive a nuclear blast at tier 4+
  (`radiation/NuclearShields`): the blast asks `NuclearShields.brace(target, center, damage)` before it hurts
  anyone, and the fallout skips anyone still `shielded`. Keep the list of which powers count (a map of power ID to
  shield look) in one place, so adding a power is one line.
- **Hold a death back for its animation.** A beaten speedster isn't killed on the spot: the run enters a failing
  phase (`SpeedContainment.disintegrate` → `burning`), the runner is made invulnerable, the client is told with a
  new view style (`SpeedCyclonePayload.CONTAIN_FAIL`) so the stand-in keeps running but slows to a stop over the burn, the
  disintegration piece follows the runner's view on the ring (cubes break off where the runner was when they let go;
  the body is hidden once `SpeedArt.consumed`), and the kill lands when the animation ends. Make sure nothing (a key release) can end the phase early and leave the player invulnerable.
- **PvP gates player-caused damage.** A blast, beam or anything else a player causes can't hurt other players
  where PvP is off, and the game-test server always has PvP off (`setPvpAllowed(true)` is ignored there). Test
  player-on-player rules by calling the rule directly (as the shield test calls `NuclearShields.brace`).
- **Previews with two players.** A preview needs a second player for this: a `FakePlayer` added to the player list,
  announced with `ClientboundPlayerInfoUpdatePacket.createPlayerInitializing`, and given an `EmbeddedChannel` as its
  connection's channel. **Without the channel the server crashes** the first time it syncs the time to every player
  (see `uitest/ContainmentPreview`). Game tests that hurt a `FakePlayer` must also zero its
  `spawnInvulnerableTime`, or it ignores damage for its first three seconds.

## Reusable patterns from the Pyrokinesis rework

Pyrokinesis (`pyro/`, art in `client/effects/setpiece/PyroArt`) was reworked alongside Flight. What the player
sees is in [docs/pyrokinesis.md](docs/pyrokinesis.md).

- **One geometry class for gameplay and drawing.** `pyro/PyroGeometry` holds the shared numbers (the breath
  cone's slope, warning lengths, the sun's flight time) and the body anchors: `castingHand(player, partial)`
  (shoulder 1.375 up, or 1.17 crouching, 0.3125 out from the body's yaw, then 0.64 along the look) and
  `breathOrigin(player, partial)`. The server aims with them and `PyroArt` draws from them, so fire leaves the
  palm the player sees.
- **A variant flag packed into the piece's scale.** Blue fire adds `PyroGeometry.BLUE_SCALE` (32) to the set
  piece's `scale`; `PyroArt.draw` checks `scale >= BLUE_SCALE` and subtracts it. That gives a second colour
  without a new piece or payload field. Use it for any two-colour variant whose real sizes stay well under 32.
- **One `Fire` palette record, two palettes.** `PyroArt` keeps `Fire(edge, body, gold)` records (`EMBER`
  red-orange-gold, `BLUE` indigo-blue-cyan) and passes one to every primitive (`billows`, `flame`, `orb`,
  `jet`, `curtain`, `marker`), so every move switches colour together.
- **Mark the ground before the hit.** Firestorm marks its footprint for 0.6 s (`STORM_WARNING`) before the
  wall rises, Collapse shrinks the wall over `COLLAPSE_WARNING`, Sunfall shows its impact ring while it
  charges, and Ember Mines give a public `MINE_WARNING`. A readable telegraph makes area damage fair.
- **Private warnings for hidden things.** `PyroGeometry.showPrivate(player, ...)` sends a set piece to the
  owner only: an armed Ember Mine is visible to whoever laid it and to no one else until it triggers.
- **Abilities that feed each other.** Dragon's Breath aimed at your own Firestorm *fans* it (wider and
  faster-burning); a fully charged Cinder Spear or a Blue Lance primes armed Ember Mines along its path;
  Fire Shepherd turns fires back into Heat. Cross-ability interactions like these are what make a kit fun
  to master. Design new kits with at least one.
- **Blue fire leaves soul fire.** Blue flames place `Blocks.SOUL_FIRE` where regular fire could stay: twice
  the contact damage and no spreading, so the hotter variant is stronger but safer for builds.
- **Live drawing for flight jets.** `PyroArt.live()` / `drawLive(c)` draw the flame streams on palms and
  boots for every pyro flyer each frame, following the bank, the same way `FlightArt` draws wind.

## Reusable patterns from the Invisibility rework

Invisibility (`invisibility/`, art in `client/effects/setpiece/ForceArt`) became a force-field kit in November
2026. What the player sees is in [docs/invisibility.md](docs/invisibility.md).

- **The kit.** Fade (shared with friends and pets within 3 blocks), Lens Shield → Force Push, Force Bridge,
  Bubble → Crush (or a shelter for friends) and Force Nova. The two-press moves use `SimpleAction.followUp`, like
  Fission Leap. The tree is Veil, Aegis and Pressure, routed through `invisibility/InvisibilitySkills`.
  - The moves feed each other: blocked hits store Pressure for the Push, a Push sends a held bubble rolling, the Nova
    hits held foes double and breaks your own bridges and barriers into shards.
  - Action and node IDs are unchanged from the older kits.
- **One shape language.** `ForceArt` draws every field from four primitives:
  - `lens`: a bowed round sheet with a facet lattice and a three-layer rim.
  - `pane`: a flat sheet with facets and lit edges.
  - `cage`: a shell wrapped in six great circles.
  - `facet`: a tumbling triangle shard.

  Palette: `9CC8FF` field, `E8F4FF` edge, `3F6FD8` deep, `B6A2FF` sheen, `1E2A44` stress cracks (normal blending).
- **Held fields that end at once.** Re-sent pieces (shield, bubble, disc, bridge, wall) can linger up to ten ticks
  after the server ends them. `SetPieceClient.superseded(active, pieces, now)` lets a piece stop drawing as soon as
  a later piece replaces it on the same entity (or, if unbound, at the same origin and target). For example, the
  bubble stops when its `FORCE_CRUSH` or `FORCE_SHATTER` arrives, and the shield stops when its `FORCE_PUSH` does.
  Bind the ending piece to the same entity so the match works.
- **Stairs without jumping.** Players standing on a Force Bridge get a transient `Attributes.STEP_HEIGHT`
  modifier (+0.5), removed when they leave or the bridge ends, so one-block steps walk like a ramp.
- **Solid constructs that are drawn, not textured.** Force Bridges and Barriers are temporary `minecraft:barrier`
  blocks placed with `cryo/TemporaryBlocks.place` (so they restore after a crash) and removed with
  `TemporaryBlocks.restore`, which skips the ice-melt particles. The client draws the panes from the same
  `ForceGeometry.bridgeCell` the server placed, so the picture is exactly the solid cells.
- **Constructs only friends can see.** `ForceFieldService.show` sends a set piece to each friendly viewer instead of
  `SetPiecePayload.send` when the caster has Unseen Constructs. Use this for any private or stealthy object.
- **Holding a creature.** A bubble re-anchors its target every tick (`teleportTo`, or `connection.teleport` for a
  player) and cancels damage whose source is the held creature. Rolling a held creature is the same anchor
  moved by `SpeedService.swept` each tick.
- **Catching projectiles.** Each field scans nearby `Projectile`s every tick with its own shape test
  (`ForceGeometry.onShield`, a radius, a wall's box) and either discards them or, with Rebound, aims them back at
  their shooter and takes ownership, which stops them being caught twice.
- **Hiding things that aren't players.** `InvisibilityService.hide` sends `UnseenPayload` for any entity;
  creatures are cancelled by `UnseenClient`'s render hook and everything else by `mixin/UnseenObjectMixin`.
- **Test gotchas.** No-AI mobs ignore velocity (assert on `getDeltaMovement`, or use a mob with AI such as a pig),
  and the `rewind_empty` arena is 12 blocks across: entities placed beyond it sit in unloaded or solid ground.
