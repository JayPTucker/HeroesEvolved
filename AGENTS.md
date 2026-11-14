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
  A carried player's own camera is moved each frame to the seat worked out from the carrier's interpolated
  position and facing (`mixin/CarriedCameraMixin`); placed from the passenger's once-a-tick position, it jolted.

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

## Reusable patterns from the rescue awakenings

Natural awakenings answer danger to the owner *or a nearby ally*, and every release saves that person, the
**ward** (`intro/Danger`, `intro/SpeedRescue`, `intro/FlightRescue`, `intro/WardRelease`). What the player sees is in
[docs/power-intros.md](docs/power-intros.md) and [docs/eclipse.md](docs/eclipse.md).

- **Who counts as an ally, and who is a threat.** `Danger.ally(owner, other)` (friendly players and own pets, via
  `StyleOps.isFriend`) and `Danger.threats(owner, ward, radius)` (monsters near the ward, anything targeting either,
  and whoever last hurt either). Use these for anything else that protects someone.
- **"Outside" is a heightmap check.** `Danger.openSky(entity)` compares the eye block with
  `MOTION_BLOCKING_NO_LEAVES`, so tree cover still counts as outside. Sky light would fail under leaves.
- **Damage to anyone can wake someone else's power.** `AwakeningService.interceptAllyDamage` runs on every
  `LivingDamageEvent.Pre` and caps the blow; `recordCombat` now tracks every living thing, pruned past 2048 entries.
- **Hand the ward through the unlock.** `awakenFromAdrenaline` puts the ward in `PENDING_WARD` around
  `awakenAssigned`, and `PowerIntroService.acquired` reads it with `pendingWard`, so no ability's `onAwaken` changed.
- **A release that moves people is a `Rescue`.** The session ticks it and waits for it to finish, and `cancel` calls
  `stop()` so a carry is always set down. Shield the ward with `shieldWard` while it runs.
- **Routes through caves are a bounded breadth-first search** over cells the body fits in and can push off from (a
  floor or a wall), cached per run (`SpeedRescue.search`). It gives up at 16,000 cells and falls back to the best
  cell it reached. Walk the route by teleporting a few blocks a tick; the client can't follow a twisting tunnel by
  velocity alone.
- **Moving a `FakePlayer` in tests.** Its connection ignores teleports and motion payloads, so call `absMoveTo` (or
  `setPos`) as well, as `SpeedRescue.teleport` and `FlightRescue.drive` do. A `FakePlayer` refuses to ride anything,
  so anyone a test carries must be a real `ServerPlayer` on an `EmbeddedChannel` (`RescueGameTests.carriable`).
- **Test arenas have a lid.** A game test's arena is boxed in barrier blocks, ceiling included, unless the test sets
  `skyAccess = true`; anything that checks for open sky (Flight's awakening) needs it. The `rewind_empty` floor is at
  relative y=1, so stand players at y=2.
- **The suite is order-sensitive.** Adding or renaming test batches reshuffles the run order, and about eight older
  tests (conduits, Sylar's escape, rapid punches, Long Winter, Fault, the Sentry Seal) then fail on power-ownership
  leaks with no code change. Check against the untouched code with stand-in tests of the same names before blaming a
  change.
- **Powers are blocked during an intro.** `PowerRules.canAct` is false while `PowerIntroService.isActive`, so
  `FlightDynamicsService` forgets any burst. Intro movement sends `SkyburstMotionPayload` itself instead.

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

## Reusable patterns from firebending (Pyrokinesis melee)

Pyrokinesis gained a fighting style in November 2026 (`pyro/PyroCombat`, numbers in `pyro/PyroGeometry`, input in
`client/PyroCombatClient`, whole-body turns in `render/PyroCombatPoseRenderer`, art in `PyroArt`). What the player sees is
in [docs/pyrokinesis.md](docs/pyrokinesis.md). Reuse these for any power that should fight hand to hand.

- **Moves without keys.** An empty-handed left click (`InputEvent.InteractionKeyMappingTriggered`, cancelled when the
  crosshair isn't on a block) and a double-tapped A, D or S (edges read in `ClientTickEvent`) send `PyroCombatPayload`.
  The server routes it through `PossessionService.actionActor`, picks the move, pays for it and times it. Both can be
  switched off in `ClientVisualSettings` (`firebendingStrikes`, `firebendingDodges`).
- **A click that can't throw fire still punches.** The payload carries the crosshair's entity; when `PyroCombat.strike`
  returns -1 (no stamina, power switched off), the server does a plain `player.attack`. A cancelled click must never
  leave a player swinging at nothing.
- **A combo is three numbers.** The next step, the last strike's time and when the next is allowed (`Fighter`). A click
  within `COMBO_WINDOW` carries on; the client also throttles so mashing doesn't flood the server.
- **Dodges with evasion.** A dodge records an evasion window (`EVADE_TICKS`) that cancels blows with an entity source in
  `LivingIncomingDamageEvent`, before the power's immunities. The first blow evaded loads a reward (Counterflame), the
  cross-move link. Never evade `BYPASSES_INVULNERABILITY`.
- **Ground dashes are re-sent each tick.** One velocity packet on the ground is eaten by friction within two ticks;
  `PyroCombat.push` re-sends the dash for `STEP_TICKS`, working out the hop's fall the client's way
  (`(vy - .08) * .98`), and stops on `horizontalCollision`. During Flame Flight, `PyroFlightService.tick` skips steering
  while `PyroCombat.isDodging`, or cruise overwrites the dash.
- **Turning the whole body.** `render/PyroCombatPoseRenderer` rotates the model in `RenderPlayerEvent.Pre` about the hip,
  in the body's yaw frame (as `CryoRidePoseRenderer` does), keyed off the arm pose and its age: a backflip, a spin, a
  twist into a kick. The camera never turns. Legs are posed in `PowerArmPoses.firebend`; copy the pants afterward.
- **A kicked crescent.** `PyroArt.crescent` lays flame tongues along an arc between two angles in any plane, unrolled
  over time, trailing back the way the foot came, with a three-layer lip. The roundhouse (horizontal), flip kick
  (vertical) and sweep kick (a full turn) are one primitive. The server's `PyroGeometry.inCrescent` uses the same reach
  and angle.
- **Strikes never place blocks.** Fire thrown many times a second would grief; only the kit's casts light real fire.

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

## Reusable patterns from the Electrokinesis rework

Electrokinesis (`electro/`, art in `client/effects/setpiece/ElectroArt`) is Elle Bishop's current: Charge,
Chain Lightning / Live Current, Ball Lightning, Thunderstrike, Ride the Lightning, Storm Surge, Blackout and seven
skills, each with its own arm pose and set piece (November 2026). What the player sees is in
[docs/electrokinesis.md](docs/electrokinesis.md).

- **One art class, a handful of primitives.** Every piece is drawn by `ElectroArt` from `fork` (a jagged channel with
  side branches), `ring`, `corona` (a shell with orbiting rings), `braid` and `pulse` (a ground ring with fingers),
  all through `stroke`'s three layers. Palette: `10172D` shadow, `3D52B8` indigo, `5588FF` blue, `84D5FF` ion,
  `EAF5FF` core. New moves are new arrangements of these, not new primitives (Arc Lance's straight beam, Circuit
  Thread's lit block edges and Arc Lattice's sagging wire are the exceptions, and they still use the palette).
- **Effects leave the palm the player sees.** `ElectroArt.hand(c, owner, reach)` uses `ElectroGeometry.hand` in third
  person and a camera-space point in first person, so a bolt starts at the visible hand in both. Bind the piece to the
  caster (`show(..., player)`) for this to work.
- **Every move has its own pose,** in third (`PowerArmPoses`) and first person (`FirstPersonArmGeometry.electro` and
  `skills`): draw, cast, stream, call, coil, release, ride (arms flung back), cup (palms round the orb), lance (braced,
  straight arm), touch (palm on metal), overcharge (fists clenched at the sides), ground (palm driven down) and point.
- **A generic skill pose overwrites yours.** `SkillActionService.activate` sends `ONE_HAND` / `ARMS_WIDE` /
  `ARMS_RAISED` after every implicit branch skill. A power that poses its own skills must `return` before that line
  (Electrokinesis does). Quake, Regeneration and Sonic still get the generic pose.
- **Held constructs are re-sent short pieces.** Ground Sink's rod, Arc Lattice's anchors and wires go out every ten
  ticks as 12-tick pieces that fade in and out over two (`fade(t, ticks, 2, 2)`), so copies hand over seamlessly. The
  sink's stored charge rides in the piece's `scale` (1 + charge / 16) and brightens the rod's tip.
- **Thin a beam that passes the camera.** Arc Lance dims itself where it runs close by the camera (the front view looks
  straight down it) but not for the caster's own first-person view, where the hand is always beside the camera.
- **Telegraphs share their timing.** `ElectroGeometry.STRIKE_DELAY` and `SURGE_TICKS` drive both the server's delay and
  the warning ring's build, so the marker always fills exactly as the bolt lands.

## Reusable patterns from the Superspeed rework

Superspeed (`speed/`, effects in `client/effects/SpeedEffects`, `SpeedAfterimages`, `SpeedCyclone`,
`setpiece/SpeedArt`) is the guide's reference for "the body is the effect". Its six optional skills were brought up to
the core's standard in November 2026. What the player sees is in [docs/superspeed.md](docs/superspeed.md).

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

### Photographing skills (`tools/skill-anim-preview.init.gradle`)

`uitest/SkillAnimationsPreview` casts every Electrokinesis and Superspeed skill in a flat world and saves front, back
and first-person shots to `build/skill-anim-preview/screenshots/`. Lessons from writing it:

- **Play in survival.** A creative player's hits ignore a mob's invulnerability, so the targets die after a cast or two.
- **Report, don't throw.** An `AssertionError` inside a server task crashes the integrated server and ends the run.
- **Shoot two or three ticks after the cast,** not one: the pose and the piece haven't reached the client yet.
- **Log the pose the client applies** (`ClientArmPoseState.get` in `PowerArmPoses.apply`) when a pose looks wrong: that
  is how the generic skill pose above was found.

## Reusable patterns from the Empathic Mimicry rework

Empathic Mimicry (`mimicry/`, art in `client/effects/setpiece/MimicArt`, the wheel in `gui/screen/MemoryWheelScreen`)
became a three-phase kit in November 2026: the power of whoever you are near (Levels 1 to 5), sets kept by touch
(6 to 14), whole kits remembered (15 to 20), five Echo keys and a memory wheel. What the player sees is in
[docs/empathic-mimicry.md](docs/empathic-mimicry.md).

- **Borrow a power by running its real actions.** An Echo (`MimicryService.echo`) wraps the copied power's own
  `AbilityAction` for the same slot and calls it inside `invoke(...)`, which marks the call as borrowed. Powers whose
  native gates check ownership ask `MimicryService.isBorrowingX(player)` (Flight, Pyrokinesis, Invisibility,
  Cryokinesis, Technopathy, Matter Assembly). A new power with an ownership gate needs one of these, or Echoes of it
  fail. Switching sets, logging out or changing dimension runs `forget`, which ends every held borrowed action.
- **One question: who has a power.** `MimicryService.imprintOf(mimic, entity)` answers for players, gifted villagers,
  the meltdown villager, Sylar and Elle. Attunement, touch, Sense Powers and the stamina passive all use it, so a new
  powered NPC is one more branch there.
- **Keep the old API when the model changes.** The memory became near + kept + channel, but `current()`, `powers()`,
  `remember(player, power, tier)` and `recall(player, step)` still work, so a dozen callers and tests didn't change.
  The codec reads the old `powers`/`active` fields and loads them as kept sets.
- **Menus can show a proxy's target.** `PlayerPowerSyncService` asks `MimicryService.shown(player, action)` and uses
  the copied action's name and path, so the wheel, key list and HUD say "Skyburst", not "Echo II". Resync the loadout
  whenever the channel changes (`MimicryService.sync` does).
- **A second wheel is a small screen and one payload each way.** `MimicMemoryPayload` (server to client, on every
  change and at login) feeds `ClientMimicMemory`; `MimicChoicePayload` (client to server) channels or forgets a slot.
  The key handling copies the ability wheel's hold-to-open, tap-to-act pattern (`ClientKeyEvents.handleMemoryKey`).
- **Two numbers in a piece's scale.** An attunement sends `colourIndex + progress` (whole part and fraction); a touch
  adds 32. Each power's colour lives in `mimicry/MimicColors`, shared by the server and the art.
- **Fade a thread per point near the camera.** `MimicArt.thread` dims each point by its distance to the camera
  (`ribbon` with per-point alphas), so a thread ending at your own chest or passing a front-view camera doesn't fill
  the screen, while the rest of it stays bright. Prefer this to dimming the whole stroke.
- **Throttle touches per target.** One swing can land twice on the same person; two quick touches on different
  people should both count (`LAST_TOUCH` stores the target as well as the time).
- **A saved roll on an entity.** Gifted villagers roll once in `EntityJoinLevelEvent` and save `heroesevolved_gift`
  and `heroesevolved_gift_rolled` in the persistent data, so a reload never re-rolls.
- **Preview gotchas.** A camera behind the player can't see a thread that runs straight ahead (the body hides it):
  stage sources off to the side. A front camera sits on the far side of anyone in front of the player: use a child
  villager (`setBaby(true)`) to see over them.

## Reusable patterns from the Sonic Resonance rework

Sonic Resonance (`sonic/`, art in `client/effects/setpiece/SonicArt`) was reworked in November 2026: every move,
skill and style has its own pose and set piece, three moves gained free follow-ups, and the tree became eighteen
interactions (`sonic/SonicSkills`). What the player sees is in [docs/sonic-resonance.md](docs/sonic-resonance.md).

- **One damage helper makes a status cheap.** Every sonic hit goes through `SonicService.hit(owner, target, damage)`.
  Ringing (`SonicService.ring`) is left by four sources (Focused Shriek, Shatter Note, a braced Cry, Deep Echo), and
  `hit` cashes it in for extra damage and Resonance. Route a power's damage through one helper, and a new mark or
  bonus is one change.
- **Moves that pass through your own constructs.** A Cry or Crescendo checks its line against the singer's Note
  (`onLine`: distance from a segment) and Wall (`throughWall`: a ray crossing the wall's plane inside its box). A tap
  through the Note breaks it harmonic for free; the held scream feeds it; Feedback Loop amplifies through the Wall.
  Cheap geometry against your own things gives combos without new state.
- **One key, several follow-ups.** Note (press: break; Shift: carry it once), Wall (press: shove; Shift: drop),
  Vault (press in the air: dive). `SimpleAction.followUp` runs before cooldown and stamina and sees
  `c.modifierHeld()`, so one predicate picks between two follow-ups. A follow-up that returns false falls through
  to the normal Shift-deactivate (the Wall's drop).
- **A moving construct drawn from one packet.** A shoved Wall moves a fixed `SonicGeometry.WALL_PUSH_SPEED` a tick;
  the server and `SonicArt` both call `SonicGeometry.pushed(start, normal, age)`, so a single `SONIC_WALL_PUSH`
  piece draws the whole roll. The standing wall's re-sent piece is bound to the owner, so `superseded` stops it the
  moment the push starts.
- **An opposite is the same shapes inverted.** Silence (Quiet Zone, Silence Dome) reuses `SonicArt.front` in slate
  colours with rings that close inward. Four primitives (`front`, `trace`, `staff`, `glyph`) and one palette
  (`tone(k)` blends rose, amber, mint, azure and violet) draw all twenty-one pieces.
- **Re-sent poses must be continuous.** The scream, Crescendo's gather, the dive, Resonant Guard's hum and the Quiet
  Zone's hush are refreshed while they last, so they are on `ClientArmPoseState`'s continuous list; otherwise each
  refresh restarts the gesture from frame one.
- **Delegate the whole branch.** `SkillActionService` returns `SonicSkills.skill(c, branch)` directly, which also
  skips the generic skill pose (see Electrokinesis above).
- **Taking a power off the stat-bonus list breaks tests that used its stat nodes.** A stamina-discount test bought
  `sonic_resonance_0_0`; it now uses Empathic Mimicry (see the Quake section). Search the tests for a power's `_0_0`/`_1_0`/`_2_0` nodes when you add it
  to `SkillTreeService.bonus`'s list.
- **A FakePlayer never lands.** A test singer that isn't in the level is still ticked by the service's own server
  tick, but `onGround()` never comes true, so Sonic Dive lands on its `DIVE_MAX` timeout (as Fission Leap's slam
  does). Tests that need skill nodes set `SKILL_TREES` directly (`SonicGameTests.learn`).
- **Photographs:** `tools/sonic-kit-preview.init.gradle` (`uitest/SonicKitPreview`) shoots every move, skill and
  style into `build/sonic-kit-preview/screenshots/`.

## Reusable patterns from the Quake rework

Quake (`quake/`, new art in `client/effects/setpiece/QuakeArt`) was reworked in November 2026: free follow-ups on four
moves, blasts that shake bullets to dust and set off explosives, an eighteen-node interaction tree (`quake/QuakeSkills`)
and 23 poses. What the player sees is in [docs/quake.md](docs/quake.md).

- **One place decides what a wave does to things.** `QuakeKinetics.disrupt(owner, entity, push)` answers for every
  blast, the beam and the Vibration Shell: an incoming TaCZ bullet turns to dust, an explosive goes off where it is
  (credited to whoever fired it), a creeper's fuse is lit, any other shot turns back and becomes hers. Give the next
  deflecting power (a shield, a force field) the same single entry point instead of copying the cases.
- **Fast things need a forward-looking check.** Bullets cross several blocks a tick, so `QuakeKinetics.crossing` tests the
  shot's position *and* where it will be next tick against the blast's line, with a wider reach for bullets
  (`QuakeGeometry.BULLET_CATCH`). Held effects (the beam, the shell) screen shots every tick, even when they only hit
  bodies every few.
- **Reading another mod's private state.** TaCZ keeps a round's explosion settings private; `TaczLink.explosive` reads
  them by reflection inside its try/catch and treats any failure as an ordinary round. `PowerWeapons.testBullet` makes
  a bare bullet for tests (`EntityKineticBullet.TYPE`).
- **A follow-up for each signature move.** Shockwave → Double Tap (a time window kept in `DOUBLE_TAPS`), Resonate →
  Shatter (collapse the rest of a running effect into one blow), Fault Line → Upheaval (finish a travelling effect at
  once), Earthquake → an early aftershock (`followUp` that calls the existing `deactivate`). All are
  `followUp`s, so they are free and ignore the cooldown.
- **Follow-ups change what other tests see.** A second Shockwave press is now Double Tap, so a test that expected the
  second press to hit the cooldown now presses three times (`PowerFeedbackGameTests`).
- **Passives that link two moves, by checking the other's state.** Live Fault (a crack under her own resonance collapses
  it), Pressure Skin (a shot is cancelled while braced, blasting or shelled), Read the Pulse (a felt heartbeat is marked
  and the next hit on it, in `LivingIncomingDamageEvent`, lands harder), Kinetic Sink (each shot broken calls
  `QuakeKit.relieve`). Each is a few lines where the two moves already meet.
- **A telegraph a beat before an aimed hit.** Seismic Lance marks the target's feet and strikes `LANCE_DELAY` ticks later,
  queued in `LANCES`. A test that checks damage at once must wait for it (the all-skills test exempts it).
- **Moving stat-bonus tests again.** Quake joined the no-stat-bonus list, so the discount and attack-bonus tests in
  `SkillTreeGameTests` moved to Empathic Mimicry and Electrokinesis. These are the powers that still have the generic
  nodes.
- **Test players keep three seconds of spawn protection** even when `isInvulnerableTo` is overridden: `ServerPlayer.hurt`
  checks `spawnInvulnerableTime` itself. Zero it by reflection (`QuakeReworkGameTests.pressureSkin...`). Skeleton arrows
  also scale to nothing on the test server's difficulty; use an ownerless shot.
- **Photographs:** `tools/quake-kit-preview.init.gradle` (`uitest/QuakeKitPreview`) shoots every move, follow-up, skill and
  style into `build/quake-kit-preview/screenshots/`.

## Reusable patterns from the Regeneration rework

Regeneration (`regeneration/`, new art in `client/effects/setpiece/RegenArt`) was reworked in November 2026: follow-ups on
three keys, a held Blood Gift, Play Dead, an eighteen-node interaction tree (`regeneration/RegenSkills`) and 18 poses.
What the player sees is in [docs/regeneration.md](docs/regeneration.md).

- **Tap or hold on a plain action.** Blood Gift became tap/hold the same way Canary Cry is: `activate` records the press,
  the server tick turns a press held `STREAM_HOLD` ticks into the held effect, and `release` either gives the tap's effect
  or ends the held one. Move the "is there a target" check into `canUse` (with its own message), so a press with no one
  in reach still costs nothing.
- **Reuse a body state the client already draws.** Play Dead lies the player down by setting the "downed" flag in
  `BodyStatePayload` (`BodyService.state`); `BodyVisuals` forces the sleeping pose on every client. A server-side
  `setForcedPose` alone doesn't reach the player's own client. Check what else the flag drives (here, only the give-up
  key, which the server ignores unless she is really down).
- **Branch skills can have a second press.** `SkillCatalog`'s generic builder takes a `followUp`; Regeneration's Play
  Dead uses it to get up for free, as Cryokinesis's skills do.
- **A follow-up that spends what the first part collected.** Blood Bond keeps a running `taken` total of the wounds
  Claire draws onto herself; Pay It Back turns it into healing for her friends and Regrowth for her. Any held or
  absorbing move can bank a number this way and cash it out on the second press.
- **Watch which damage type a new hit uses.** Freefall's landing first used `playerAttack`, so it also counted as one of
  Indestructible's Reckless punches (which cost her health and gave Regrowth). It now uses an explosion source. Check
  the power's own `LivingIncomingDamageEvent` hooks before picking a source.
- **"Hostile" is not "not a friend".** `ExpansionSkills.hostile` is true for every creature that isn't an ally, cows
  included. A heal that should reach animals and villagers needs a friend rule (`RegenerationService.bonded`), not
  `!hostile`.
- **Blood should be drawn as blood.** `RegenArt` draws blood in the soft (normal-blended) pass so it is red, not light, and
  keeps only a thin wet highlight in the glow pass. `Canvas.bolt` adds a bright core, so dark cracks use plain jagged
  lines (`RegenArt.gash`).
- **Tests.** `RegenerationGameTests`' fixture puts the FakePlayer in the player list, so features ticked through
  `getPlayerList().getPlayer` (the downed timer, Shared Blood's lookup) work; reuse it. A cow's 10 health caps any
  heal you assert on; a zombie's armour shaves a couple of points off a hit; use a pillager for exact numbers.
- **Photographs:** `tools/regen-kit-preview.init.gradle` (`uitest/RegenKitPreview`) shoots every action, follow-up, skill and
  style into `build/regen-kit-preview/screenshots/`. A leap needs its landing shots timed to the fall; a front camera with
  someone standing in front of the player only shows their back.

## Reusable patterns from the Telepathy rework

Telepathy's tree (`telepathy/TelepathySkills`, art in `client/effects/setpiece/TelepathyArt`) was reworked in November
2026: eighteen interaction nodes, three of which carry real Simple Voice Chat voices, and 14 poses. What the player sees
is in [docs/telepathy.md](docs/telepathy.md).

- **Carrying voice chat audio.** `telepathy/MindVoice` is the only class that imports the voice chat API; Simple Voice
  Chat finds it by its `@ForgeVoicechatPlugin` annotation (a public no-argument constructor is enough). For each
  `MicrophonePacketEvent` it looks up routes and re-sends the packet with `packet.staticSoundPacketBuilder()` (inside the
  head) or `locationalSoundPacketBuilder().position(...).distance(...)` (from a point). Like `RadioLink`, every call is
  in try/catch and sets a `broken` flag.
  - **Give relayed audio its own channel** (`channelId`, a UUID made from the speaker's), or it shares a decoder with the
    speaker's ordinary voice and garbles. Give it a volume category (`registerVolumeCategory` on server start) so
    players can turn it down.
  - **Never relay to someone who already hears the speaker**: same group, or within `getVoiceChatDistance()` in the same
    level. Respect closed groups (`Group.Type` not `OPEN`) unless the server opts in.
  - **Direction without position:** a locational packet placed a few blocks from the listener toward the speaker, nearer
    for louder, sounds like a distant voice from the right direction.
  - **Changing the sound.** Decode with `voice.createDecoder()`, change the 960 samples, encode with
    `createEncoder(OpusEncoderMode.VOIP)` and pass the bytes to the builder's `opusEncodedData`. Keep one decoder,
    effect and encoder per speaker (the codecs and the effect's echo carry state from frame to frame), process once per
    packet for every listener, and close them on `PlayerDisconnectedEvent`. The effect itself is a plain class
    (`telepathy/PsychicVoice`) so a game test can feed it a tone.
  - **Speaking so only one mind hears.** `event.cancel()` on a `MicrophonePacketEvent` stops the voice chat's own
    proximity and group broadcast; relay the packet yourself first. That is the inner voice (Shift + Mind Link).
- **Work routes out on the server thread, read them on the voice thread.** `MindVoiceRoutes.compute(...)` builds an
  immutable map each tick and `publish` swaps it into a `volatile` field; the voice thread only reads it and records who
  was heard in concurrent maps (`drain`, `speaking`). The rules have no voice chat imports, so game tests check them
  directly (`TelepathyReworkGameTests.farHearing...`).
- **Ask before you carry someone's voice.** Hive Link and Hive Mind take teammates or players who crouch to volunteer,
  never every nearby player (with PvP off, `StyleOps.isFriend` is true for everyone). A listened-to speaker gets a hint.
- **A talk-down everyone can see.** Persuasion's windup (`TelepathyGeometry.PERSUADE_TICKS`) is a public `TELE_PERSUADE`
  piece whose scale is its length, so the art closes exactly as it lands; breaking line of sight ends it. The ending
  piece (`TELE_WON` or `TELE_FRIGHT`) is bound to the same mind, so `superseded` stops the windup drawing at once.
- **A memory you can walk.** `telepathy/MindMemory` remembers every player, and other creatures near anyone who has
  learned Memory Walk, every half second for a minute (at most 256). A walked path goes to the walker as at most twenty
  private `TELE_MEMORY` segments, each delayed two ticks after the last so the trail unspools backward in time; the age
  and a "hurt here" flag ride in the scale. Memory Walk on the ground finds who passed through a place.
- **Private pieces for one viewer.** `TelepathyGeometry.showTo(viewer, ...)` sends a piece to one player: a heard voice's
  mark, a linked friend's halo, a voice inside your head. Use it for anything only one mind perceives.
- **Hooks into the core, not copies of it.** The tree reads the core where it already decides things:
  `SuggestionService.choose` asks `TelepathySkills.suggestionTicks`, `PossessionService.end` calls `released` (only when
  control had begun), `IllusionService` calls `fright` on contact, and `TelepathyFocus` gained `deepen` (add Focus to a
  mind without reading it) and `threads(p, min)`. The old Implanted Command and Shared Sight duplicated core actions;
  their slots now hold new moves.
- **Links outlast deaths and dimension changes.** `TelepathyService.stopAll` runs on death and dimension change, so a
  link that reaches anywhere is only left on logout (`TelepathySkills.leave`).
- **Test players must be in the player list** when the code looks them up by id: links and routes resolve members with
  `getPlayerList().getPlayer`. The test fixture adds and removes them by reflection, as `RegenerationGameTests` does.
- **Voice tests stay close.** `TelepathyReworkGameTests` shrinks the voice chat range to five blocks (`nearVoices`) so
  "far apart" is twenty blocks; never send test players hundreds of blocks off.
- **Local full-suite runs start from the default config.** A forward speed of 15 left in
  `build/rewind-gametest-world/config` since October made both Skyburst tests fail locally and nowhere else (a tap
  already hit the 10-block cap); `tools/rewind-gametest.init.gradle` now deletes that config with the world, as GitHub's
  fresh checkout does. When a test passes alone but fails in the full run, compare the run's config before blaming code.
- **Tests that wait on a chunk wait for it.** A freshly loaded chunk can take seconds to start entity-ticking while the
  suite runs, and a test FakePlayer moving out of its arena leaves no player to keep chunks ticking. Sylar's boss bar and
  the meltdown crowd now wait for `isPositionEntityTicking` before their clock starts; the flight rescue measures the
  flyer while the ally is carried; the anchored tornado's far cow stands inside the arena's barrier walls; Elle's conduit
  test has 400 ticks for her pocket floor to come alive.
- **A look-don't-touch inventory.** Mind's Eye (`telepathy/MindsEyeMenu`) is a `ChestMenu` over a `SimpleContainer` of
  *copies*, re-mirrored in `broadcastChanges`, with `clicked`, `quickMoveStack` and `canDragTo` all refusing. Use it for
  any read-only view of another inventory. A `FakePlayer` ignores `openMenu`, so tests build the menu directly.
- **Remembered chat is public chat only.** `TelepathySkills.said` records `ServerChatEvent` lines (twenty, thirty minutes,
  forgotten on logout); private messages and commands never reach it.
- **Photographs:** `tools/telepathy-kit-preview.init.gradle` (`uitest/TelepathyKitPreview`) shoots every pose and piece into
  `build/telepathy-kit-preview/screenshots/`; villagers stand in for the second mind.

## Reusable patterns from village distress calls (radio and meltdowns)

Villages call for help over Simple Radio, and the first event that calls is a villager's meltdown (`radio/`,
`village/VillagerMeltdown`, art in `client/effects/setpiece/VillageArt`). What the player sees is in
[docs/emergency-band.md](docs/emergency-band.md). Use these patterns for the next village event (a raid, a boss).

- **Required mods, fetched by Gradle.** Simple Voice Chat comes from `maven.maxhenkel.de` (its API is `compileOnly`).
  Simple Radio and Lexiconfig come from the Modrinth maven (`maven.modrinth:<slug>:<version id>`), all `localRuntime`.
  Pin Modrinth jars by **version ID** (`L8SiUelt`), not version number: a number shared between loaders resolves
  to the Fabric jar. `neoforge.mods.toml` lists `voicechat` and `simpleradio` as required on both sides.
- **One class touches the other mod.** Simple Radio has no published API, so only `radio/RadioLink` imports it.
  - A call is a `Transmitter` on a frequency, added with `frequencies().getOrCreate("101.10", FREQUENCY)`; it takes
    48 kHz mono `short[]` audio through `push`.
  - Every call is wrapped in try/catch and sets a `broken` flag. If the mod changes, calls fall back to subtitles
    for players within 96 blocks, and nothing crashes.
- **A call is one line.** `EmergencyBand.call(level, at, words, ticks)` puts a call on 101.10 FM and returns a
  `Call`. Use `call.say(words)` to update it (a countdown) and `EmergencyBand.end(call)` to stop it.
  - The audio is synthesised each tick: low-passed static that drifts, plus a keyed SOS tone.
  - Listeners get a subtitle every 60 ticks with the bearing and distance to the call.
  - Don't ship sound files for calls: there is no OGG encoder here (see the intro audio notes).
- **State lives on the entity.** A meltdown saves its countdown, bell and safe point in the villager's
  `getPersistentData()` every ten ticks. `EntityJoinLevelEvent` queues the villager, and the next server tick
  resumes the meltdown; don't do the work inside the join event, while the chunk is still loading. An unloaded
  villager pauses: the call and the bar stop, and they resume when he loads again.
- **An event the Eclipse rolls.** `VillagerMeltdown.eclipse(server)` watches `EclipseService.phase` change. On
  `DEPARTURE` it rolls a config chance and schedules the awakening for a random moment later. If no village is near
  a player, it retries every ten seconds, up to twelve times. Give every rare event an admin command too
  (`/heroesevolved eclipse meltdown [seconds]`).
- **Countdown UI.** A `ServerBossEvent` shown to players near the event is the countdown (`Meltdown 0:42  Safe 3/9`);
  the radio carries the words. A world marker that must be found from afar (the safe point) is a re-sent set
  piece: a tall column of light plus a ground ring.
- **Powers that answer an NPC.** The event exposes the same small API as a radioactive player: `critical(entity)`,
  `hold(entity)` (the countdown skips a tick) and `defuse(entity, by)`.
  - `SpeedContainment` targets any `LivingEntity` and picks the player or villager version in three small
    helpers, so the containment run works on both without a second copy.
  - Freeze and time stop are read with `CryoService.isFrozen` / `TimeStopService.isStopped`.
  - Carrying uses the existing sneak + use carry, which works on any villager.
- **A blast from something that isn't a player.** `NuclearFallout.begin(LivingEntity, …)` accepts any owner.
  - With no player, terrain follows `mobGriefing`, like a creeper.
  - It skips `BreakEvent` and `mayInteract`, which need a player.
  - It sends the shockwave with `AbilityEffects.send` instead of `at(player)`.
- **A crowd running from something looks like Odessa's.** Copy `time/intro/VisionCrowd` for any crowd that flees
  (the meltdown's `VillagerMeltdown.flee` does).
  - **Running:** every five ticks, set the brain's `WALK_TARGET` to a point out of reach at speed 0.95, and erase
    `LOOK_TARGET`. Don't call `getNavigation()` directly: the brain overwrites it.
  - **Shouting:** `effects/SpeechBubble.show(...)` puts bold red words in a text display that rides the speaker.
    Have one runner near a player shout every 26 ticks, and keep each bubble up 50 ticks.
  - **Screaming:** the recorded voices on the client (`PanicSounds`) ride the runners: `PanicSounds.village(mc,
    center)` plays them without the city's crowd sound underneath. `client/sound/VillagePanic` finds the center from
    the active set piece with `SetPieceClient.where(piece)`.
  - **Bubbles left behind:** a bubble rides its speaker, so an unloaded chunk saves it with them. Record each one's
    UUID before it is added (`show`'s `beforeAdd`), and turn away any bubble-tagged display that loads without one.
- **Make "just kill it" the wrong answer.** The meltdown villager cancels incoming damage (except
  `BYPASSES_INVULNERABILITY`, so `/kill` still works), and each hit takes ten seconds off the countdown. His death
  sets him off at once.
- **Time fields start at a safe sentinel.** `now - Long.MIN_VALUE` overflows to a negative number, so a "last hit"
  check of `now - hitAt < 20` with `hitAt = Long.MIN_VALUE` is always true. Start such fields at `-1000`.

## Reusable patterns from the Last Day (the playable prologue)

Every new player now lives the evening Odessa ended before waking in the bunker (`story/prologue/PrologueService`,
the light's timing in `PrologueTimeline`, Noah, Claire, Peter and Ted in `story/actor/StoryActorEntity`, the
apartments in `time/intro/city/Apartments`). The Space-Time vision reuses the actors to show Peter and Ted. What the
player sees is in [docs/the-last-day.md](docs/the-last-day.md).

- **One entity for every scripted character.** `StoryActorEntity` is an unsaved, invulnerable `PathfinderMob` with
  no goals. A synced `Role` picks the skin (`StoryActorRenderer`, Ted adds a glow layer) and a synced `Pose` picks
  the arms. The script drives it with `walkTo`, `face`, `place` and `say` (chat as `<Name>` plus a speech bubble).
  Spawn with a scene tag and remove by it. Add a role and a skin in `tools/GenerateStoryActorTextures.java` for the
  next character; don't write another entity.
- **A scene that hands off to the real game.** The prologue ends by calling `BunkerService.handOff`, which runs the
  normal wake-up in the pod the player was sealed in. The prologue pod index *is* `BunkerData.pod(uuid, …)`, so the
  number matches 103 years later. Pod 14 (Claire's) is excluded everywhere.
- **Two scenes, one city.** The prologue and the Space-Time vision share `time_fracture`. `TimeIntroService` only
  starts its next scene while `PrologueService.running()` is false, and `TimeGather` skips players waiting for a
  prologue. The city's protections (no damage, powers, edits or items) check `isMember` and `isWaiting` as well as
  the vision's visitors.
- **Ready-up without a screen.** A clickable chat line runs `/lastday ready`, and using your own door counts too.
  The run starts when everyone present is ready or a timer after the last arrival. Late arrivals wait on a held black
  tint until the run ends.
- **Tension without the payoff.** The light grows on one shared curve (`PrologueTimeline`) read by the server (white
  flashes and tremors on each surge) and the client (the glow's size), but it never detonates on screen. Save the
  real blast for the vision.
- **Stragglers are carried, not killed.** At the deadline an agent darts anyone outside (`Tranquilizer` trail and
  go-under blur), and they skip to their pod. Nobody holds the group up.
- **The bunker as it was.** `story/bunker/PastBunker.build(level, origin)` builds the stasis bunker from the same
  blueprints with every light on and nothing grown in (`past(layer, state)`). Night Three and the prologue both use
  it. Build it once below anything the city rebuilds, and reset only its pods and doors each run.
- **A way in for a crowd under a deadline is a stair, not a ladder.** The safe house is reached by a 3-wide switchback
  (blueprint `lastday_stairwell`); the city draws only its top step and a railed slot in the lobby floor
  (`Landmarks.safeHouse`), so a city rebuild never touches the part underground. A game test walks the route cell by
  cell (`PrologueGameTests.walkable`).
- **A door that shuts behind a group mustn't shut anyone out.** The vault (`PrologueService.vault`, block swaps with a
  grindstone for its wheel) closes only once every sheltered member is past it, or after ten seconds; anyone still on
  the stair side is moved through first. "Safe" starts at the turn of the stair, and anyone who climbs back up is put
  back at the foot.
- **One pod that looks different.** Pod 14's clawed glass is a `cracked` block state on the Cryochamber with four
  `_cracked` models that inherit the normal ones and swap only the glass texture. Use this for any one-off variant of a
  modelled block; don't copy the models.
- **Check where the cast stands, in a test.** Guards and actors are spawned at hand-written offsets inside rooms built
  from blueprints; two of them once stood inside a desk and a lectern. `PrologueGameTests.standing` asserts every
  scene entity is clear of blocks and on a floor once the scene is set. Do the same for any scripted scene.
- **Story text goes to chat, once.** `TimeIntroCaption` writes captions (with their subtitle in grey) and instructions
  (`»`) to chat, skipping repeats, instead of drawing over the scene; story actors speak in chat only. Don't add
  action-bar nags or on-screen captions to a scene: one chat line per beat. Hide corner labels while a black hold tint
  is up (`ClientScreenTintState.blackedOut`).
- **Pace before the payoff.** The evening crowd runs in `VisionCrowd.calm()` (a conversation every thirteen seconds, pauses
  between lines); the light gets fifteen quiet seconds of staring (`PrologueTimeline.SIRENS`) before the sirens, the
  agents' calls and the objective; the surges sit at fractions of the run, so they quicken toward the end.
- **Props on a face are items, not skin pixels.** Glasses painted into an 8-pixel face read as a mask. Noah's are an
  item (`HornRimmedGlassesItem`, `Equipable` in the head slot) whose 3D model the head item layer draws on the face; the
  model is sized for that layer (a full block worn on the head is 1.25 times the head, so the head spans 1.6 to 14.4 in
  model units and the face is the north side), and `neoforge:separate_transforms` gives it a flat icon elsewhere.
  `StoryActorEntity.role` puts the pair on Noah. Use the same for hats, masks or headsets.
- **A blast felt underground.** `PrologueService.quake` collects the hall's lights when the blow lands, kills them,
  flickers them back (a third stay dark) and drops `FallingBlockEntity` rubble down the aisle, all undone by the next
  run's room rebuild. `PrologueGameTests` checks rubble and dark lights after the blast.
- **One path, two ways of seeing it.** The sky fight (`time/intro/SkyFight`) is pure maths: positions and moves at any
  tick for a `Script` (start, pass length, passes). The vision moves real actors along it (`VisionFight`, with the powers'
  own pieces: `FLIGHT_SKYBURST`, `ELLE_BOLT`, `WATCH_HOLD`, `FLIGHT_IMPACT`, `TIME_SNAP`); the Last Day draws it on each
  client from the light's start time (`client/effects/intro/SkyFightArt`, hooked into `SetPieceClient`'s live drawing),
  because a dedicated server only sends entities within its view distance and the fight is 190 blocks away. Use this
  for anything watched from very far off.
- **Probe the city before choreographing over it.** The towers' real faces and heights decided the fight's centre, its
  reach, and where the throw lands; `PrologueGameTests.theSkyFightHasRoomAndAWallToHit` walks every tick of the path for
  open air and checks the raycast finds the wall the far view draws. A real crater is put back with
  `FractureCity.rebuild` when the fireball covers it.
- **Flying actors.** `StoryActorEntity.glide(at, look)` sets position and facing each tick (no gravity, no physics), and
  `StoryActorRenderer.setupRotations` lays a `FLY` actor along its line of flight (as an elytra flyer) or spins a
  `TUMBLE` one. `shouldRenderAtSqrDistance` is raised so actors read from across the city; their update interval is 1.
- **A player's double in a scene.** `Role.TRAVELER` with `StoryActorEntity.doubleOf(uuid, name)` syncs whose double it is;
  `StoryActorRenderer` draws that player's own skin as this client knows it (on the slim model if their arms are slim)
  and shows their name over the head, and the actor speaks in chat under that name. The vision's twist uses it: the
  time traveler whose power the vision is comes back for Peter (`VisionFight.travelerArrives`).
- **Vanilla does the frost and the title.** The pods' creeping frost is `setTicksFrozen` (the powder-snow overlay,
  kept below the damage threshold), and *103 years later...* is a vanilla title packet over a held black tint
  (`ScreenTintPayload.Style.HOLD`). No new overlay was needed.
- **Recovery from a crash.** A player sealed in their pod gets the `SLEPT` tag; logging in with it skips straight to
  the wake-up. Anyone who logged out earlier starts the day over.
- **Returning a group together.** The vision's `together(...)` lands everyone in a ring of open standing spots round
  the traveler's return point, falling back to each player's own ticket if the traveler's is gone. Reuse it for any
  scene that should end with the party side by side.

## Reusable patterns from the Watchmaker (Sylar's boss fight)

Sylar's fight became a boss fight in his own arena in November 2026 (`story/sylar/WatchmakerArena`, `WatchmakerMoves`,
art in `client/effects/setpiece/WatchmakerArt`). What the player sees is in [docs/sylar.md](docs/sylar.md). Use these
for the next boss.

- **An arena is a pocket dimension rebuilt every fight.** `WatchmakerArena.enter` builds the `sylar_shop` blueprint
  in `heroesevolved:watchmaker_shop`, saves each player's way home, moves them in, and spawns a new boss there (a mob
  changing dimension becomes a new entity, so make a fresh one and update whoever holds the old reference).
  `close(server, delay)` sends everyone home. Whatever the fight broke (ripped floor, shattered cases, barred doors)
  is whole again next time.
- **Moving players in and out must not end the encounter.** `PlayerChangedDimensionEvent` normally ends a Sylar
  encounter; `WatchmakerArena.moving(player)` marks the fight's own teleports so the handler skips them.
- **Keep an arena's chunks force-loaded once built.** A freshly loaded chunk in a pocket dimension takes about three
  seconds before its entities tick, so a boss spawned there stands frozen. The shop's chunks stay forced from the first
  build on (the game saves that).
- **The game-test world lists its dimensions by hand.** `src/gametest/resources/data/minecraft/worldgen/world_preset/flat.json`
  defines every dimension the test server has; add a new one there or `server.getLevel(...)` returns null in tests.
- **Phases from health thresholds, each changing the room.** `SylarEntity.PHASE_TWO`/`PHASE_THREE`/`CHASE_AT`. A phase
  changes the arena, not just numbers: the doors bar, the clocks speed up, and at a tenth he runs down an alley.
- **Every telegraph is the same shape.** A dial whose hand reaches twelve when the move lands; each move's windup is
  its marker piece's `ticks`. Store anything a move needs later (a ring's centre) in its own field: his next move's
  `mark()` overwrote the rings' centre once and dragged them across the room.
- **No fists during a windup.** `tickCast` clears the melee target, or `MeleeAttackGoal` keeps punching while the
  telegraph runs.
- **A counterplay window.** He is `exposed()` while winding up or holding someone. A charged hit (a full attack meter,
  read in `AttackEntityEvent` before the swing resets it; a crit arrow; or 8+ damage) then cracks his watch and
  staggers him. Read the swing record on every hit, or one swing carries over to the next.
- **Taking a power for a fight.** `WatchmakerArena.stolen(player)` is checked in `PowerRules.canActWithoutPreservation`;
  `close` clears it, so the power always comes back.
- **Walls rise after the shove.** `TemporaryBlocks.place` skips cells with an entity in them, so the ice wall rises
  two ticks after it pushes people off its line (`SylarEntity.raiseWall`), or it has a hole where the target stood.
- **Test gotchas.** `FakePlayer` ignores `awardStat` (set stats with `getStats().setValue`). Wrap every sequence step in
  a guard that removes the test's players on failure: a test that dies mid-sequence leaves its players owning their
  powers, and every later test that grants those powers fails (34 failures once).

## Reusable patterns from Elle Bishop (the Company's boss)

Elle's fight (`story/elle/`, art in `client/effects/setpiece/ElleArt`) added foreshadowing before a boss and an arena
whose machines are the counterplay. What the player sees is in [docs/elle-bishop.md](docs/elle-bishop.md).

- **Foreshadowing that puts everything back.** `CompanyOmens` changes only block states that it can restore (lamp and
  bulb `LIT`, furnace `LIT`, door and trapdoor `OPEN`) and queues each restoration as a timed step. A restoration runs
  only if the block is still the same kind of block, and `settle()` restores everything on server stop.
- **Never probe with `WorldMutationPolicy.canEdit` for something that breaks nothing.** It posts a `BreakEvent`, which
  records temporal echoes and mirrors breaks into snapshots. The omens use a side-effect-free check
  (`mayBuild`, world border, `mayInteract`) and call `GridDisruption.stall` with a null source.
- **Grid helpers for story events.** `GridDisruption.jam(level, center, radius, ticks)` is a redstone-only field:
  nothing is drained and nothing stalls. A misfire uses it for a second, and the arrival uses it to put out every light
  around the player. `stall(null, ...)` stops a machine with no actor.
- **Poll the arena's switches; don't listen for clicks.** Each tick, `PrimatechArena.pollBanks` reads every breaker
  lever and `GridDisruption.jammed`. So a lever pulled by hand, a broken lever, or a player's Blackout or EMP over a
  bank all count the same. A boss reads the result (a bit mask); it never handles the input itself.
- **One geometry class, checked against the blueprint.** `PrimatechGeometry` holds bank, conduit and mat positions for
  the fight and the drawing. The blueprint is drafted to the same numbers, and a game test compares its `@breaker:N`,
  `@conduit:N` and `@mat` markers with the class.
- **Send state for drawing through synced entity data, not `scale`.** The client clamps a set piece's `scale` to 64.
  The arena piece is bound to Elle and reads her synced `BANKS` mask and phase instead.
- **Cut a travelling piece short with `superseded`.** Ball lightning's `ELLE_ORB` stops drawing when an `ELLE_RING`
  bound to the same entity arrives (the burst), so a ball that hits someone early doesn't roll on.
- **A scene stand-in.** The arrival spawns an `ElleEntity` with `scene(true)` and no AI: no bar, no fight, invulnerable.
  It is discarded when the player is taken.
- **Freshly forced pocket chunks hide their entities.** For a few seconds after the arena is first built, a player
  standing in a chunk that isn't entity-ticking yet is invisible to `getEntitiesOfClass`. Hazards miss them, and so
  do tests. Tests wait for every corner of the room (`ElleGameTests.ready`), not just the boss's chunk.
- **A move's end mustn't shorten a longer hold-off.** `finish()` uses `Math.max` on the global cooldown, so a
  preview's or test's `holdOff` survives the moves it triggers.

## Reusable patterns from the TaCZ weapons

Heroes Evolved requires TaCZ (Timeless and Classics Zero) and adds power-themed guns, an EMP grenade, and TaCZ guns
for the Company's guards (`weapons/`, art in `client/effects/setpiece/WeaponArt`). What the player sees is in
[docs/weapons.md](docs/weapons.md).

- **A gun pack in our own jar.** TaCZ 1.2 reads gun packs through the normal resource and data loaders, so our guns
  are plain files: `data/heroesevolved/index/guns` + `data/guns` (numbers), `index/ammo`, `recipe/` (type
  `tacz:gun_smith_table_crafting`; the 1.21.1 folder is `recipe`, not `recipes`) and `assets/heroesevolved/display`.
  Change a gun's numbers there, not in code. A recipe's `"type": "custom"` result makes any item (the EMP grenade);
  give it a `group` (a table tab such as `explosives`) or it shows in no tab.
- **Borrow TaCZ's shapes, paint our own.** A display file may point `model` and `animation` at TaCZ's own resources
  (`tacz:gun/p90_geo`, `tacz:p90`), so reloads and inspects animate for free. `tools/GenerateWeaponTextures.java`
  reads the borrowed model's geometry from the TaCZ jar and paints every face by part (body, grip, barrel, magazine,
  detail, glow) on its UV layout, and draws the inventory and HUD icons as a side view. No TaCZ texture is copied.
- **One guarded class.** Only `weapons/TaczLink` imports TaCZ, like `radio/RadioLink`: every call is in try/catch and
  sets a `broken` flag, after which the guns fire as plain TaCZ guns and agents fall back to the dart rifle. (The older
  `time/TaczAmmoRecovery` predates it and reaches TaCZ by reflection; move it into `TaczLink` when it next changes.)
- **Effects ride TaCZ's events.** `EntityHurtByGunEvent.Post` (server side) carries the gun id: `PowerWeapons.hit`
  routes it to heat, cold or taser. `GunShootEvent` draws the muzzle effect; `AmmoHitBlockEvent` lets cold bolts skin
  water with frosted ice. The effects reuse the powers' own services (`CryoService.chill`/`freezeSolid`,
  `GridDisruption.emp`) at smaller numbers, so a gun is a weak version of a power, never a second implementation.
- **Any creature can shoot a TaCZ gun.** Every `LivingEntity` is an `IGunOperator`: put the gun in the main hand,
  `PowerWeapons.draw(mob)`, wait for the draw, then `PowerWeapons.fire(mob, point)`. Swapping guns means another draw.
  Keep the gun's magazine topped with `PowerWeapons.reload`.
- **TaCZ times shots in real milliseconds.** Draws, fire rates and cooldowns read a wall clock, not game ticks. In
  play that is the same thing; in game tests the server runs ticks as fast as it can, so every shot comes back
  `COOL_DOWN`. Tests that fire real TaCZ guns hold each tick to 50 ms (`WeaponGameTests.realTime`).
- **An armor stand is a test shooter.** It is a living entity with no AI, so it fires exactly where you point it.

## Reusable patterns from the Ability Dampener

The Ability Dampener (an RF-powered field where no power works) and the Nullifier (a TaCZ pistol charged with RF that
fires dampener rounds) were added in November 2026 (`dampener/`, art in `client/effects/setpiece/DampenerArt`). What the
player sees is in [docs/ability-dampener.md](docs/ability-dampener.md).

- **One question for "is this power switched off".** `Dampening.dampened(entity)` covers a live field and a round's
  `DAMPENED` effect. `PowerRules.canActWithoutPreservation` asks it beside the Suppressor and the Eclipse, so every key,
  sustained power and passive goes quiet with no per-power code. NPCs with powers (Sylar, Elle) and
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

## Reusable patterns from Vibe

Vibe (`vibe/`, art in `client/effects/setpiece/VibeArt`) is Cisco Ramon's power, added in November 2026: Vibe Blast,
two-way Breaches, Read the Vibe, Breach Guard → Return Fire and Frequency Lock. What the player sees is in
[docs/vibe.md](docs/vibe.md).

- **One crossing test for every opening.** `VibeGeometry.crossing(from, to, center, normal, width, height)` checks a
  segment against the same ellipse the viewer sees. Breaches use it on each traveler's last two body centres; the guard
  uses it on each shot's step. Reuse it for any doorway, rift or window.
- **A portal keeps your direction of travel.** A traveler comes out on the side of the far mouth they were heading for
  (`Mouth.arrival(side)`), turned by the angle between the two mouths (`Mouth.yaw()`). Sending everyone out of the front
  of the far mouth bounced anyone walking back through it between the two mouths.
- **A link to another dimension.** `VibeBreaches` holds a region ticket on the exit's chunk while the link is open, moves
  players with `changeDimension(new DimensionTransition(...))`, and adds them to `CROSSING` while they move, so the
  `PlayerChangedDimensionEvent` handler that tidies up a power (`VibeService.forget`) doesn't close the link they're
  using. A saved destination lives in `Player.PERSISTED_NBT_TAG`, so it survives death.
- **A short combat memory.** `VibeSight.record` keeps every hit (`LivingDamageEvent.Post`) in a bounded deque for 25
  seconds; a read replays the matching ones privately (`VibeGeometry.privatePiece`) and marks the attackers. Any
  "what happened here" power can read the same trace.
- **Catching fast shots needs three hooks.** The guard screens each projectile's step in `EntityTickEvent.Pre`
  (lowest priority), again in `ProjectileImpactEvent` (a shot whose whole step fell between ticks), and TaCZ rounds in
  `TaczLink`'s `EntityHurtByGunEvent.Pre`.
- **Switching another power off for a while.** A Frequency Lock puts a caught speedster in `VibeService.DESYNCED`, and
  `AbilityUseService` refuses Superspeed while `VibeService.desynced(player)` is true. `desync` is package-visible so
  `VibeKitGameTests` can test the rule directly (the test server never allows PvP).
- **`SpeedService.swept` returns a position, not a displacement.** Subtract the entity's position before measuring how
  far it got. The awakening's rescue once treated the result as a displacement and never moved anyone.
- **A wave through your own portal.** Through the Seam asks `VibeBreaches.seam(owner, from, to)` for the first open
  mouth a wave crosses; it returns the crossing, the matching point on the other mouth and the turned direction
  (`VibeBreaches.turn`). The first wave stops at the mouth, and a second one leaves the far mouth when the front
  arrives, sharing one strike list. Any projectile that should pass through a portal can use the same lookup.
- **Every passive ties two moves together.** Slingshot (cross a breach → next blast), Return Address (guard → mark),
  Overflow (Return Fire → a small lock queued in `OVERFLOWS`), Residual Sense (damage taken → mark). Marks never shorten
  (`VibeSight.mark(player, target, ticks)` keeps the longer one), so several sources can feed them safely.
- **Photographs:** `tools/vibe-preview.init.gradle` (`uitest/VibePreview`) shoots every core move, active skill, class
  ability, Through the Seam, Overflow and the awakening into `build/vibe-preview/screenshots/`.

## Reusable patterns from the Space-Time rework

Space-Time Manipulation's tree (`time/SpaceTimeSkills`, numbers in `time/TimeGeometry`, art in
`client/effects/setpiece/TimeArt`) was rebuilt in November 2026: Stillness, Wayfinder and Historian, eighteen interaction
nodes, nine poses and ten pieces. What the player sees is in [docs/space-time-manipulation.md](docs/space-time-manipulation.md).

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
- **Moving faster than time (`time/TimeVeil`).** While someone slows or stops time, everyone else is sent a
  `TimeVeilPayload` and `client/effects/TimeVeilClient` hides the caster (model, name, and shadow via
  `mixin/VeiledShadowMixin`) and draws a still stand-in with their skin where they started; the reveal is a blur
  (speedster) or a blink (traveller), or nothing if they are back on the spot. Who sees through it is one rule,
  `TimeVeil.movesInTime`. `AbilityEffects.send` and `SetPiecePayload.send` skip any viewer for whom
  `TimeVeil.masks(viewer, origin)` is true, so a trail drawn beside a hidden body can't give it away; a field's own
  visual goes only to those who see the caster (`TimeVeil.showToSeers`). Begin and end it with a reason
  (`SLOW`, `STOP`, `PERCEPTION`), so overlapping fields keep one stand-in. The chat hints (`hint`, `over`) go out
  with the first reason and the last, and whoever was puzzled (`Veil.puzzled`) is told it is over even if the caster
  logs off.
- **Server-wide fields and parallel tests.** Time fields reach every entity in every dimension when
  `fieldsServerWide` is on (the default). `TimeFieldTestSupport` turns it off on the game-test server, or one test's
  Still World would freeze every other test's mobs; `TimeVeilGameTests` turns it on inside its own one-test batch.
  Photographs: `tools/veil-preview.init.gradle` (`uitest/TimeVeilPreview`, flag `heroes.preview.veil`), with a second
  player staged as in `ContainmentPreview`.
- **Tests.** `SpaceTimeSkillsGameTests` uses a `FakePlayer` whose `teleportTo` also calls `absMoveTo` and whose
  `isInvulnerableTo` is false, and zeroes `spawnInvulnerableTime` by reflection before it is hurt. Package-private entry
  points (`seal`, `throwSeal`, `forwardEnemy`, `rewindPersonal`) let a test drive each skill without aiming.
- **Photographs:** `tools/time-skills-preview.init.gradle` (`uitest/TimeSkillsPreview`) casts every skill into
  `build/time-skills-preview/screenshots/`. It has its own flag, `heroes.preview.timeskills`: the older `TimePreview` driver
  reads `heroes.preview.time`, and two drivers on one flag fight over the camera and the world. A back camera always hides
  the creature you aim at behind your body; use first person or put the target close for a front shot. If the run fails
  with "Failed to locate a primary monitor", the Mac's display is asleep: `caffeinate -u -d -t 600` wakes it.

## Reusable patterns from the Density rework

Density's tree (`phasing/DensitySkills`, numbers in `phasing/DensityGeometry`, art in `client/effects/setpiece/DensityArt`)
was rebuilt in November 2026: Infiltrator, Density and Escape Artist, eighteen interaction nodes, ten poses and fifteen
pieces. What the player sees is in [docs/density.md](docs/density.md).

- **The core asks the tree where the moves meet.** `DensityService.pass` calls `DensitySkills.passed` (Ambush primes the
  hand, Ghost Network opens the wall), Reach Through calls `touched` (the seal, and Inside Knowledge) and
  `throughWall`, every shockwave asks `crater` before it throws anyone, and the surfacing uppercut calls `uprooted`.
  The core keeps its own logic; the passives live in the tree's class.
- **Follow-ups on a core key.** Ambush rides Reach Through's key and Entombed rides Embed's, as `.followUp(...)` on the
  core `SimpleAction`. Each returns false when it doesn't apply (no primed hand, no held body in sight), so the normal
  press still runs and pays its cost. A follow-up that succeeds is free and ignores the cooldown, which is the point.
- **"Your blow" means your fist.** `indirectMagic(p, p)` has the player as its *direct* entity too, so a check of
  `getDirectEntity() == p` alone counts Reach Through and the ground's grip on an Entombed body as punches. Set in
  Stone once extended a burial forever from its own damage ticks. Melee-only passives check
  `source.is(DamageTypes.PLAYER_ATTACK)` as well.
- **Re-embedding deeper.** `DensityService.entomb` takes the existing hold out of the map, stands the body back on the
  ground, embeds it again at full depth, and puts the old hold back if there is no room. Embedded bodies also ignore
  `IN_WALL` damage, so a burial's damage is exactly the skill's.
- **A passage friends can use.** A Ghost Network door is two mouths and a normal, re-sent as a 12-tick piece every ten
  ticks. A player goes through when facing into the wall; a mob when its owner (or the caster) is on the far side.
  Each traveller then waits two seconds, or they bounce straight back. Use this for any portal that follows its maker.
- **A primed state shown only to its owner.** The Ambush hand is a 60-tick private piece (`DensityGeometry.showTo`)
  bound to the player; the strike piece, bound to the same player, ends it at once through `superseded`.
- **Draw at the ground, not the body.** Once a body is embedded, `position()` is under the floor, and a collar or slab
  drawn there is buried. Ask `DensityService.surfaceOf(entity)` for the surface it is held in.
- **Separate aiming from the effect.** `sinkhole` picks the ground and `sinkholeAt` opens the hole, so a game test opens
  one at a known point without pointing a FakePlayer's head at the floor.
- **Stone in a set piece.** `DensityArt.slab` draws `c.box` only in the SOLID pass and shrinks it instead of fading
  (solids have no alpha). Harden's plates hide in your own first-person view, where they would sit inside the camera.
- **Test gotchas.** `rewind_empty` is 12 × 6 × 12 with a barrier lid, so anything above y = 5 needs
  `skyAccess = true`. No-AI mobs never land: set `setOnGround(true)` for anything that checks `onGround()` (Pile Driver,
  Grasp from Below). Fill stone under an arena's floor (y = 0 and −1) before burying anything there.
- **Photographs:** `tools/density-skills-preview.init.gradle` (`uitest/DensitySkillsPreview`, flag
  `heroes.preview.densityskills`) casts every skill into `build/density-skills-preview/screenshots/`.

## Reusable patterns from Shapeshifting

Shapeshifting (`shapeshift/`, stand-ins in `client/MorphClient`, art in `client/effects/setpiece/ShapeArt`,
`ShapeInstinctArt` and `ShapeTrickArt`) was built in November 2026, in phases 1 to 6 of
[docs/shapeshifting.md](docs/shapeshifting.md); phase 7 is polish. It is rollable (not in `IN_DEVELOPMENT`).

- **The kit and where it lives.**
  - **Keys:** Morph (study, the form wheel, revert), Instinct (each form's own attack), Decoy, Reflection and Nightmare.
  - **Classes and their jobs:**
    - `ShapeshiftService`: forms, the body, kin and the shape history.
    - `ShapeshiftForms`: level bands, sizes, kin and the body a form gives.
    - `ShapeshiftTraits` and `ShapeshiftTraitService`: gifts and weaknesses.
    - `ShapeshiftHands`: what a form without hands can't do.
    - `ShapeshiftInstinct` and `InstinctMove`: Instinct.
    - `ShapeshiftTricks` and `ShapeDecoy`: Decoy, Reflection, Nightmare, Borrowed Face, the Doppelganger, cowed and
      turned creatures, and the awakening.
    - `ShapeshiftSuspicion`: Suspicion, exposure and the readers who see through a disguise.
    - `ShapeshiftChat`: speaking as someone.
    - `ShapeshiftSkills`: the 18-node tree, Apex Form and the three classes.
  - **Tests:** `ShapeshiftGameTests`, `ShapeshiftTricksGameTests` and `ShapeshiftSkillsGameTests`.
- **One art language.** Every piece is plates that flip from slate to brass. `ShapeArt` holds the primitives (`hull`,
  `scale`, `face`) and the palette (`SHADOW 1F2A3D`, `SLATE 3E6F86`, `SHEEN 8FCFC6`, `BRASS C29A5E`, `HIGHLIGHT F1ECE2`).
  `ShapeInstinctArt` adds `around` (a ring of plates about any axis), `streak`, `seamRing` and `loose` (a tumbling
  plate), shared with `ShapeTrickArt`. New moves reuse pieces where they can: Hybrid is `SHIFT_TOUCH` on yourself,
  Chimera `SHIFT_STUDY`, Many Hands a string of `SHIFT_LUNGE`s, Apex Form's growth a `SHIFT_DECOY_BURST`.

- **A power still being built.** Put it in `ModAbilities.IN_DEVELOPMENT` (empty now). `ModAbilities.rollable` keeps it
  out of every random pick (the Eclipse, the Formula, gifted villagers, retired-power swaps) while admins can still give
  it, and `ClassAbilityGameTests` and `SkillTreeGameTests` don't expect a tree or classes from it yet. It still needs a
  `power_balance.json` entry for every action, an intro `Spec` in `PowerIntroService`, a `MimicColors` colour and a
  `PowerIcons` entry.
- **Changing a player's hitbox.** `MorphBodies` answers `EntityEvent.Size` on both sides from two maps (server and client
  are kept apart because an integrated server shares entity ids with its client). Call `refreshDimensions()` after a
  change on each side; the client also re-checks every tick in case the form arrived before the body did.
- **Drawing a player as something else.** Cancel `RenderPlayerEvent.Pre` and draw a client-only stand-in entity (never
  added to the level) with the same renderer call, copying facing, head, swing, crouch and hurt flash each frame and
  the walk cycle each tick (`WalkAnimationState.update`). Check `UnseenClient.hidden` first.
- **Mobs that ignore someone.** Clear the target in `LivingChangeTargetEvent` (as Invisibility does). For mobs already
  fighting, erase `ATTACK_TARGET` and `ANGRY_AT` from the brain too, and check `hasMemoryValue` before `getMemory`:
  fetching a memory a brain doesn't register throws.
- **Attributes from a form.** Read them from one cached example per type (`ShapeshiftForms.example`), add transient
  modifiers with the `heroesevolved:shapeshift_*` ids, and keep health as a fraction across every change.
- **Swapping a body under cover (phase 2).** A model can't be clipped mid-render, so the change hides the swap: solid
  plates (drawn in the SOLID pass, which writes depth) close over the old body, the stand-in is swapped while the shell
  is shut, and the plates fall away from the new one. `MorphClient.Transition` holds both ends; `ShapeshiftGeometry`'s
  `COVERED`, `SWAP` and `SHED` are the one timeline the renderer and `ShapeArt` both read. Keep one stand-in per form
  per player during a change, or the old and new bodies fight over one and are re-created every frame.
- **Animate a state change, not a resync.** `MorphStatePayload.animate` is true only when the form really changes; a
  viewer who starts tracking you gets the state without the ripple.
- **A hull for any body.** `ShapeArt.hull` lays rows of hinged scales on ellipses that follow a body's outline (an
  upright profile and a beast's mound, blended by height over width). Each scale is a lit fan drawn with `Canvas.v`
  (package-private, so per-vertex colours work): a slate front and a brass back a hair apart, so a plate flipped past
  90° really shows its underside, darker at the hinge and paler at glancing angles. Studying, touching and the morph
  are three ways of driving the same hull (a scan band, a distance from the hand, two fronts).
- **Effects round your own body in first person.** Hiding them leaves the player seeing nothing; drawing them at body
  size fills the screen. Draw a wider version (a curtain about a block out) and cull it just below the eye. Measure
  from the current eye, which already sits at the new body's height when the hitbox changes at the start.
- **A form's own arm in first person.** NeoForge's `RenderArmEvent` gives the empty-hand pose already transformed: cancel
  it and render the form's `HumanoidModel` arm (`resetPose()` first) with its texture. Cancel `RenderHandEvent` at HIGH
  priority to show nothing for a handless form. `FirstPersonArmEvents` asks `MorphClient.formArm` so power gestures use
  the form's arm too.
- **Models on a GUI wheel.** `InventoryScreen.renderEntityInInventory` draws any cached, unspawned entity; scale it by
  its largest dimension and translate by half its height. The form wheel (`gui/screen/FormWheelScreen`) is opened by the
  server (`FormLibraryPayload`), answered with `MorphChoicePayload`, and the server throttles picks (`CHOICE_GAP`).
- **A form is a type plus an optional profile (phase 3).** `ShapeshiftForm` keys a creature by its type id and a person by
  `player:<uuid>`, saves a person's id, name and skin texture, and goes over the network with `ByteBufCodecs.GAME_PROFILE`.
  Compare forms by `key()`, never by type: two people are both `EntityType.PLAYER`. The library still reads phase 1's
  list of bare type ids.
- **Drawing someone as another player.** `MorphClient.Double` is a client-only `RemotePlayer` with the copied profile.
  It takes the skin from the client's `PlayerInfo` if the person is online, otherwise from
  `SkinManager.getInsecureSkin`, and its customisation byte is set so the hat and sleeves show. It takes its owner's
  entity id, so arm poses keyed by id follow it, and so the `RenderPlayerEvent` handler must skip `Double`s or it
  draws them recursively. Hide your own double's name tag with `RenderNameTagEvent.setCanRender(TriState.FALSE)`.
- **Speaking as someone.** Cancel `ServerChatEvent` and send each viewer a system message built from
  `chat.type.text`, with the name's `SHOW_ENTITY` hover and `/tell` click copied from vanilla, so a bystander can't
  tell it apart. Commands never reach `ServerChatEvent`, so `/msg` stays honest for free.
- **Operators in game tests.** The game-test server's `PlayerList.op` grants level 0. Add a
  `ServerOpListEntry(profile, 4, false)` to `getOps()` yourself, and remove it afterwards.
- **Photograph a change by its progress, not by tick.** The preview's `watch(name, points...)` shoots on the render frame
  where `Transition.progress` passes each point, so the shots land on the same moments whatever the server's timing.
- **Many moves on one key (phase 4).** Instinct is one action whose move comes from the form (`InstinctMove.of`).
  Each move carries its own stamina cost and cooldown, kept per move, and its ordinal rides in the `SHIFT_INSTINCT`
  piece's scale (append new moves at the end). Traits (`ShapeshiftTraits`) and hand rules (`ShapeshiftHands`) are
  read from a living example of the form, cached per side, so a modded mob gets the traits of the vanilla class it
  extends and the client never predicts a use the server refuses.
- **A first-person effect from your arm starts at the hand you see.** A Lunge drawn from the shoulder sat on the
  crosshair. `ShapeInstinctArt.viewHand` puts the start low and to the side of the camera, as `ElectroArt.hand`
  does, and the tip is drawn thinner, with no seam ring, so the target stays visible.
- **A held creature's bands must pinch, not flare.** One row of plates hanging from a seam reads as a skirt from
  any camera above it. The grab draws two rows per band, one hanging and one standing, both tipped in at the
  points (a negative hinge angle in `ShapeArt.scale`), so each band reads as a cuff.
- **Side views of a move.** `TempestCamera.fix(at, lookAt)` holds a still camera for preview drivers (clear it
  with nulls). Keep the game in a third-person mode so the player's body is still drawn.
- **A body for mobs to chase (phase 5).** Decoy is a real, unsaved `PathfinderMob` (`ShapeDecoy`, `.noSave().noSummon()`)
  with no AI, so mobs can target it and hit it. `IEntityWithComplexSpawn` sends its shape in the spawn data, so a viewer
  who arrives late still sees the right body, and its renderer draws the same stand-in a morph uses. It cancels damage
  with no entity behind it (falls, fire) and bursts on the first blow.
- **A set piece's scale is clamped to 0.05–64 on the client.** A flag of 0 or -1 arrives as 0.05. Nightmare's viewer
  flag is 2 (a foe, who sees a warden) or 1 (anyone else); the first version used 0 and -1 and showed everyone the warden.
- **`SimpleAction.builder(path, unlock)` takes a tier (1–5), not a Power Level.** Passing 6, 10 or 15 locks the key
  forever (the HUD shows "[Level 15]"). Instinct had the same mistake (tier 3, Level 6) until phase 5.
- **A status that stops creatures attacking.** `ShapeshiftTricks` keeps one "cowed" map for confused and frightened
  creatures: a `LivingChangeTargetEvent` handler clears their target, a `LivingIncomingDamageEvent` handler at HIGH
  priority cancels their blows, and the tick moves frightened ones away with `DefaultRandomPos.getPosAway`.
- **Seen through by other powers.** `ShapeshiftSuspicion.read(reader, target)` is the one call: Telepathy's Read Mind and
  Vibe's Read the Vibe make it. The next power that should see through a disguise adds one line.
- **Testing what a FakePlayer can't feel.** A game-test FakePlayer is invulnerable, so no damage event ever reaches it.
  The events call small entry points (`ShapeshiftTricks.struck`, `ShapeshiftSuspicion.blow`) that tests call directly.
- **Growing a player (phase 6).** Apex Form puts a transient modifier on `Attributes.SCALE`: it syncs to every client by
  itself and resizes the hitbox. Two things must follow it: `MorphBodies`' size event multiplies the form's own size by
  `getScale()`, and `MorphClient` scales the pose stack before drawing the stand-in. Call `refreshDimensions()` after
  changing it, or the hitbox waits for the next tick (and a test FakePlayer never ticks).
- **Drawing a creature as someone else.** Borrowed Face sends a `MorphStatePayload` for the creature's id;
  `MorphClient.renderCreature` cancels `RenderLivingEvent.Pre` and draws the stand-in (a person's `Double`), and the
  StartTracking hook sends it to late viewers. Creatures keep their own hitbox.
- **Two target rules on one event.** The kin rule (`ShapeshiftService.target`, normal priority) clears a target to
  null; the "turned" rule (`ShapeshiftTricks.target`, low priority) must check its own map before returning early on a
  null target, or the kin rule wins.
- **Leaving the in-development list.** Removing a power from `ModAbilities.IN_DEVELOPMENT` makes it rollable and puts
  it under the catalog tests: add it to `SkillTreeGameTests.POWERS` (and its skip list if it has its own skill tests)
  and to `tools/verify-skill-content.py`'s expected set.
- **Wiring a new power's tree and classes.**
  - **The tree:** the power's entry in `skill_trees.json` (three lanes of six nodes; positions 1 and 4 active, 5 the
    capstone; a signature appended to a lane with `"requires"` set to that lane's mastery, `_1_2`).
  - **Routing:** a line in `SkillCatalog.Node.action` routes the tree to the power's own class. A case in `StyleCasts`
    (`canUse` and `cast`) routes the classes.
  - **Bookkeeping:**
    - an entry in `SkillTreeService.bonus`'s no-stat-bonus list, and a `forget` call where that class handles a respec;
    - an entry in `EvolutionTrials`;
    - a `case` in `PowerIntroService`'s release for the awakening.
  - **Names:** must match `en_us.json`; check with `python tools/verify-skill-content.py`.
- **Every class ability must cast alone.** `ClassAbilityGameTests.everyClassAbilityCasts` casts each one with only a
  pillager near. Masquerade, which needs friends, now includes the caster and falls back to the nearest creature seen.
- **A short history of shapes.** `ShapeshiftService` remembers the last eight shapes worn (`previous`, `history`,
  `lastCreature`) and when each change happened (`lastChange`). Quick Change, Hybrid, Chimera, Masquerade and Ambush
  Shift all read it; record it wherever a form starts or ends (`morph` and `end` both call `remember`).
- **Borrowing gifts without a shape.** Hybrid's traits come back from `ShapeshiftService.traits(player)` when no shape
  is worn, so every damage and fire hook that already asks about traits works for it unchanged. Its own tick calls
  `ShapeshiftTraitService.tick`. Gifts a client draws (wall climbing, the glowing senses) are left out: the client
  only knows worn shapes.
- **A bonus for one move.** Ambush Shift sets `ShapeshiftInstinct.bonus` around `perform` in `use`, and `hit` multiplies
  by it, so the first Instinct after a change hits harder without every move knowing.
- **Suspicion on the HUD.** `SuspicionPayload` (server to the wearer, only when the value moves a point or reaches 0)
  feeds `gui/SuspicionHudOverlay`, an almond eye under the crosshair that fills brass and flashes red past 70.
  Exposure locks Morph (`ShapeshiftSuspicion.locked`, checked in `study` and `morph`) and bursts the shape off with
  `breakForm`, which sends `SHIFT_EXPOSED` and no ripple (`animate` false).
- **One viewer sees something else.** Nightmare sends each player their own `SHIFT_NIGHTMARE` piece bound to the
  shapeshifter. `SetPieceClient.boundTo(piece, entity)` finds it, and `MorphClient` draws a roaring warden stand-in
  (`roarAnimationState`) in that player's place only on a foe's client.
- **A walking shell.** The Doppelganger is a `ShapeDecoy` with a walk vector: it steps along the ground on the server
  (up a block where it must, stopping at a wall). On the client, `ShapeDecoy.tick` advances its stand-in's walk cycle
  from its own movement.
- **Morph flags.** `MorphStatePayload.flags` carries per-wearer drawing rules (`PERFECT_COPY` hides your own gear in a
  person's shape). Add a bit here before adding a payload.
- **Voice chat stays on the voice thread.** Masking a speaker's voice would mean re-broadcasting audio to whoever is
  near, but `MicrophonePacketEvent` runs off the server thread, where the level can't safely be searched (Telepathy only
  reads routes published from the server tick). The planned Familiar Voice became Practised Manner instead.
- **Chat as someone is a server choice.** `HeroesEvolvedConfig.COMMON.shapeshiftChatImpersonation` turns it off. Nobody
  may copy an operator (`ShapeshiftService.operator`), the copied person is told, and operators and telepaths who have
  read the speaker see who really spoke (`ShapeshiftChat.seesThrough`).
- **Photographs:** `tools/shapeshift-preview.init.gradle` (`uitest/ShapeshiftPreview`, flag `heroes.preview.shapeshift`)
  shoots every phase into `build/shapeshift-preview/screenshots/`.
  - To photograph what only a foe sees, the driver sends the foe's private piece to the local player.
  - A front camera behind a standing mob ends up inside it: move the player clear first.
  - Fixed cameras (`side()`) only see what is staged in front of them.
- **Known flaky tests.** `hisneighboursscreamandrun` (the meltdown crowd) and
  `facilitybuildsinbatchesandmanualroutecansavebothcaptives` (waits on chunk loading) each failed once during this
  work and passed on the next run with no change. Re-run before blaming new code.

