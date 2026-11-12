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
