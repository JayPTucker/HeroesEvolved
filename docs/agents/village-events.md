# Village events, radio and meltdowns: notes for agents

Distress calls over Simple Radio, the villager meltdown and keeping far villages loaded (`radio/`, `village/`, `VillageArt`). Gifted mobs, renegades and raids are in the bosses skill: [gifted-mobs.md](../../.claude/skills/minecraft-bosses/references/gifted-mobs.md). What the player sees: [emergency-band.md](../emergency-band.md).

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Reusable patterns from village distress calls (radio and meltdowns)

Villages call for help over Simple Radio, and the first event that calls is a villager's meltdown (`radio/`,
`village/VillagerMeltdown`, art in `client/effects/setpiece/VillageArt`). What the player sees is in
[docs/emergency-band.md](../../docs/emergency-band.md). Use these patterns for the next village event (a raid, a boss).

- **Required mods, fetched by Gradle.** Simple Voice Chat comes from `maven.maxhenkel.de` (its API is `compileOnly`).
  Simple Radio and Lexiconfig come from the Modrinth maven (`maven.modrinth:<slug>:<version id>`), all `localRuntime`.
  Pin Modrinth jars by **version ID** (`L8SiUelt`), not version number: a number shared between loaders resolves
  to the Fabric jar. `neoforge.mods.toml` lists `voicechat` and `simpleradio` as required on both sides.
- **Calls carry hundreds of blocks, and their villages stay loaded.** (Updated with the gifted mobs: see [gifted-mobs.md](../../.claude/skills/minecraft-bosses/references/gifted-mobs.md).) A call tells
  everyone within `distressRange` (768) when it starts, reminds them every thirty seconds, and lights a flare
  (`DistressPayload` → `client/ClientDistress` → `VillageArt.flares`) coloured by its `EmergencyBand.Trouble`. Whatever
  starts a call should also hold its village with `VillageTickets.hold(level, bell, key)` and release it when it ends,
  or a call from 500 blocks away is over (or frozen) before anyone arrives.
- **One class touches the other mod.** Simple Radio has no published API, so only `radio/RadioLink` imports it.
  - A call is a `Transmitter` on a frequency, added with `frequencies().getOrCreate("101.10", FREQUENCY)`; it takes
    48 kHz mono `short[]` audio through `push`.
  - Every call is wrapped in try/catch and sets a `broken` flag. If the mod changes, calls fall back to subtitles
    for players within 96 blocks, and nothing crashes.
- **A call is one line.** `EmergencyBand.call(level, at, trouble, words, ticks)` puts a call on 101.10 FM and returns
  a `Call`. Use `call.say(words)` to update it (a countdown) and `EmergencyBand.end(call)` to stop it. A new kind of
  trouble is a new `Trouble` (append it) and a colour in `VillageArt.FLARE`.
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

## Reusable patterns from overloads (storms round a villager)

A villager whose gift wakes too strong to hold, at the centre of their own storm (`village/overload/`: `Overloads` runs
them, a `Weather` per `Storm` says what each does, `OverloadGeometry` holds every number the server and the drawing
share; art in `client/effects/setpiece/OverloadArt`). Built November 2026. What the player sees: [overloads.md](../overloads.md).

- **A new storm is a style, not a system.** Add a `Storm` (append it: its order goes over the network), a `Weather`
  class, a `Trouble` and flare colour on the emergency band, and a branch in `OverloadArt`. The countdown, calm, the
  break, saving and the village's panic are the engine's. The meltdown predates it and stays its own class; both now
  share `village/Shouts` (bubbles over runners, `runFrom`), so a third village panic shouldn't copy either.
- **Anyone can win; powers make it easier.** The core answer is to reach them and stay (`Overloads.calm`: nearest
  counts in full, others half, a telepath at range, the same power or a mimic twice as fast), so a storm never waits on
  who is online. The Dampener is the item answer and costs the gift (`Dampening.dampened` on the villager).
- **A storm that is nobody's uses the shared tornado.** `TornadoWind.storm` takes no owner: a `spared` predicate and a
  hazard `DamageSource` (`HazardDamage.OVERLOAD`, no knockback), a calm eye inside a fraction of the wall, and a throw
  high in the wall so what it carries falls. It draws as `SpeedCyclonePayload.STORM`, whose radius is the ring times the
  storm's scale; the server and `SpeedArt` both size the funnel through `OverloadGeometry.wall`, so the drawing never
  lies about the wall. A mob that has no owner sends the payload with the villager as its entity.
- **Under a sky-sized set piece, hide the sky's clouds.** Vanilla clouds are drawn after `AFTER_PARTICLES`, and soft
  geometry writes no depth, so they showed through the supercell's deck as blocks of blue. `mixin/StormCloudsMixin`
  cancels `renderClouds` while a deck is near (`ClientOverloads.hidesClouds`).
- **"In the open" is the heightmap, not sky light.** `canSeeSky` reads the sky light, which lags the light engine; a
  test's fresh roof let the cold through. `Blizzard.exposed` compares the `MOTION_BLOCKING` height with the head.
- **Freeze with vanilla's own frozen state.** `setTicksFrozen` gives powder snow's overlay, slowing and damage, and
  `canFreeze` already spares leather wearers and the freeze-immune. Vanilla thaws two a tick out of powder snow, so add
  two plus the chill each tick after the entity has ticked. The HUD's COLD ring reads the synced frozen ticks.
- **Temporary blocks a storm keeps.** Snow and ice go through `TemporaryBlocks.place(..., null)` with a short life, and
  the storm refreshes what it placed every ten seconds, so it all melts a while after the storm stops however it ends.
  Lay snow in uneven drifts, not single blocks: scattered layers read as tiles.
- **Send a village indoors** by setting `HEARD_BELL_TIME` on its villagers (a raid bell's hide), again every ten
  seconds while it lasts.
- **A whiteout is a branch of `BlizzardFog`** (`whiteout`), sized as how far you can see, not added to the
  cryokinetic's own storms (other drawing reads those). A wildfire's smoke haze is `ClientOverloads.fog`.
- **Testing storms.** The test world's build limit is under y 440: blocks placed there are void air, so stage
  block-placing tests at y 300. A mob with no AI never moves however its velocity is set (no `travel`): give AI to
  anything the wind must push. Storms reach past a test's plot: one batch per test, `mobGriefing` off while blocks
  would land on other plots, and hold a far chunk loaded with `setChunkForced`. `Overloads.progress`, `calm` and `hurry`
  stage a storm anywhere in its life; `-PtestPackages=village/overload` runs only these.

## Reusable patterns from Starfall (the Helixite meteor shower)

The night before every Eclipse, meteors fall (`meteor/`: `Starfall` schedules the night and the falling stars near
players, `Meteors` runs every meteor and its answers, `MeteorGeometry` holds every number the drawing shares; art in
`client/effects/setpiece/MeteorArt`). Built November 2026. What the player sees: [meteor-shower.md](../meteor-shower.md).

- **One path, read by both sides.** A meteor's place in the sky is `MeteorGeometry.position(start, land, fallen)`: the
  server's "looking at it" test and the client's drawn rock use it, and its warning ring fills by the same `fallen`. The
  client runs `fallen` forward itself between packets (`ClientMeteors`), so a five-tick sync is smooth.
- **Answers by gesture, not by key.** Crouching while looking at it (within `LOOK_DEGREES`) is the one input; what it
  does depends on the power (`FightingStyles.hasPower`, the one question): push, hold, crack, brace. A new power's answer
  is one branch in `Meteors.answers`. Fake players can't tick a pose, so read `isShiftKeyDown`, not `isCrouching`.
- **A great event reuses the village machinery.** `VillageIncidents.Kind.METEOR` finds and loads a far village, and the
  great meteor holds it (`VillageTickets`), calls on the band (`Trouble.METEOR`), panics it (`village/Shouts`) and tallies
  its dead, as meltdowns and overloads do.
- **Draw far things nearer.** Past 300 blocks a meteor is drawn toward the camera, smaller in proportion (as distress
  flares are), and never smaller than a share of its distance, so it reads as a bright point however far.
- **A set piece reaches players near its origin or its target** (`SetPiecePayload`, 128 blocks plus its scale). A
  meteor cracked high in the sky was sent to nobody until its target was the ground below it.
- **Find a crater's floor by looking down from the impact,** never from the heightmap (`Meteors.floorUnder`): under a
  tree or a roof the heightmap's top is the overhang. Let the search reach the world's floor (`>= getMinBuildHeight`),
  and never replace an unbreakable block; set the meteorite on top of bedrock instead. A lip of rubble set on the ground
  round the rim makes a sphere carve read as an impact, not a dug pit.
- **Emissive blocks for things that must read at night:** `emissiveRendering` draws the block full bright; light level
  alone only lights its neighbours.
- **Shared scorching.** `hazard/Scorch.column` burns grass to dirt and plants away for anything that isn't a player's
  fire move (an overload's wildfire, a crater's rim).
- **Testing.** The `rewind_empty` arena's stone floor is at `absolutePos` y 1 with air under it: stand things at y 2. A
  test can stage a meteor anywhere in its fall (`Meteors.hurry`, `fallen`) and set its `crack` to the edge of breaking.

## Reusable patterns from lasting consequences

What villages remember (`village/VillageMemory`), ground that heals (`village/Scars`) and rebuilding after a great meteor
(`village/Rebuild`), each a per-level `SavedData`. Built November 2026. What the player sees:
[lasting-consequences.md](../lasting-consequences.md).

- **Every event ends in one call.** `VillageMemory.record(level, bell, what, outcome, hero, present, alive, total)` is how a
  meltdown, a siege, an overload and a great meteor tell their village how it went; gratitude, memorials, busts and ruins
  all follow from it. A new village event adds one call at its ending, never its own memory.
- **Use vanilla's gossip for reputation.** `GossipType.MAJOR_POSITIVE` (lasting, not shared) for whoever ended it and
  `MINOR_POSITIVE` (spreads between villagers) for the others give real trade discounts and golem friendliness, the same
  way curing a zombie villager does, with no trade code of our own.
- **Names from the bell.** A village's name is a hash of its bell's position into two syllable lists
  (`VillageMemory.nameFor`), so it is the same every time, even before anything is saved; the emergency band asks
  `VillageMemory.name` to say who is calling.
- **Scenery placed by code goes on open, sturdy ground and never replaces anything built** (`VillageMemory.spot`): a ring
  search near the bell, room for the whole piece, not touching the last one. Standing signs turn with
  `RotationSegment.convertToSegment(yaw toward the bell)`; a skull faces the other way, so add 180. Wrap sign lines at
  fifteen characters and spill onto the back (`wrap`, `back`).
- **Ruin a place in one pass over its box,** not by sampling: 2,400 random picks in a 73-by-19-by-73 box touched about
  two blocks in a hundred, and the first fallen village looked untouched. Removing one half of a door or bed takes the
  other with it, with no drops.
- **Healing catches up.** A scar stores the game time it was made; each pass works out the days since and brings a few
  random spots that far along (`Scars.heal`), so a crater left unloaded for a week is healed on the first passes after it
  loads. Look for the ground by scanning down from the scar, never from the heightmap: a test arena's ceiling (or a tree)
  is the heightmap's top.
- **Rebuild from a note taken before the damage,** lowest first, into empty space and the damage's own leftovers only
  (`Rebuild.scorched`), so a player's later building is kept; clear the rubble round the rim when done, twice over for
  rubble on rubble. Store the note as a palette and indices (`NbtUtils.writeBlockState`).
- **Speech bubbles go through `village/Shouts`** (`say` for a shout, `talk` for calm words): it registers each display
  before it joins the level. A greeting's own bubble, added to a list only after `SpeechBubble.show` returned, was
  cancelled the moment it joined by the leftover-bubble check.

## Reusable patterns from the Company noticing players

An attention meter per player (`company/CompanyAttention`, overworld `SavedData`) and three operations it sets off
(`company/CompanyOps`: `Investigation`, `Convoy`, `Raid`). Built November 2026. What the player sees:
[company-notices.md](../company-notices.md).

- **Feed a meter from the two places everything already passes through.** Every successful activation ends in
  `AbilityUseService`, which now calls `CompanyAttention.seen(player, action, cost)`, and every village event ends in
  `VillageMemory.record`, which calls `CompanyAttention.deed`. "Big" is the stamina the move actually cost (30 or more),
  so the balance table decides what is loud; no list of loud actions to keep up to date. A new source of attention is a
  call to `raise`, never a second meter.
- **Seen means seen.** Witnesses are villagers awake, within 32 blocks, with `hasLineOfSight`; a hidden player
  (`CompanyAttention.hidden`: invisible, or Invisibility's cloak) has none. Only the overworld counts.
- **Decay from the last time it rose, by game time,** so offline players' files fade too and a test can age one
  (`CompanyAttention.quiet`). `set` doesn't count as being seen: a test that sets a value and waits 30 seconds sees it
  drift down, so long tests `raise` instead.
- **Agents on an errand are the same entity, with one tag.** `CompanyOps.AGENT_TAG` turns on `CompanyAgentEntity`'s
  `ErrandGoal` (walk to `walkTo`, or stand still facing `face`, with MOVE and LOOK flags so stroll and look-around goals
  can't pull him off mid-question) and sends `wouldStop` to `CompanyOps.stops`, the operation's own rule. Any fight comes
  first (the goal yields while he has a target). Errand agents get 32 blocks of follow range, or targets past 16 are
  dropped. `GunGoal.start` draws from the hip, so a holstered investigator who is attacked doesn't show two guns.
- **Operations aren't saved; their people carry tags.** An agent tagged but not in a live operation is turned away when
  it joins (a chunk, a restart); a prisoner tagged but not live is let go. Everyone an operation spawns is added to
  `CompanyOps.LIVE` before `addFreshEntity`, or the join check cancels them.
- **Operations keep their `ServerPlayer`**, not a UUID, and pending investigations too, so game-test players work.
  Scheduling that walks the player list (`consider`) takes the time of day as an argument (`consider(p, timeOfDay)`), so
  a test never has to change the world's clock.
- **A convoy is one point on a line.** Its middle moves at a fixed pace along a straight heading and waits for the
  Suppressor and the prisoner; every member walks to a slot round it (`Convoy.slot`). The prisoner is walked through
  their brain's `WALK_TARGET`, set every five ticks so their own schedule can't win. Ground for a slot comes from
  `CompanyOps.ground`: from two blocks above, down to a floor with headroom, or up out of solid ground (the heightmap
  answers a test arena's ceiling).
- **A Suppressor is a dampening field that walks.** `Dampening.dampened` asks `SuppressorField.suppresses`, so a convoy's
  prisoner (or any gifted mob near a Suppressor) has no power, with no new check in `GiftedMob`.
- **Reward from what is there:** agents' gear (`PowerWeapons.ammo` for taser cartridges and Dampener rounds,
  `PowerWeapons.gun(DAMPENER_GUN)`) is empty without TaCZ, so `CompanyOps.drop` skips empty stacks; no new items, nothing
  for JEI or Jade.
