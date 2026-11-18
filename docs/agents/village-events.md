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
