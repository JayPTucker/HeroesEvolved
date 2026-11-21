# Previews and photographs: notes for agents

A preview is a real game client that stages scenes, takes screenshots and quits. It is the only way to see what an
effect, pose, build or HUD really looks like, so **photograph every visual change and look at every shot** before
calling it done (the quality bar is in [animation-guide.md](../animation-guide.md)). Show the user the shots: they judge
the look.

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Running one

- `./gradlew runClient --init-script tools/<name>-preview.init.gradle [-P<option>]`. Each script loads its driver from
  `src/uitest/java/com/jayptucker/heroesevolved/uitest/` (never shipped in the jar), sets its own
  `heroes.preview.<flag>` system property, and runs the client in its own folder under `build/`, so it never touches a
  real world. Screenshots land in `build/<folder>/screenshots/`.
- `JAVA_HOME` must be a Java 21 JDK (it already is on this Windows machine).
- **End every driver with `PreviewQuiet.quit()`,** never `Minecraft.stop()`, which saves every chunk Odessa and the
  bunker touched and leaves the window black for minutes. `quit()` prints `PREVIEW_DONE`, writes `preview-result.txt`
  and halts the game without saving its throwaway world.
- **Pass or fail honestly.** On Windows the halt leaves a failing exit code, so a script without the result check ends in
  BUILD FAILED even when every photo was written: look for `PREVIEW_DONE` in its log. **Every new preview script applies
  `tools/preview-result.gradle`** (the amp and finisher previews do), which reads `preview-result.txt` instead; see
  "Previews that pass or fail honestly" below.
- **On a Mac:** if the run fails with "Failed to locate a primary monitor", the display is asleep:
  `caffeinate -u -d -t 600` wakes it.

## Staging a scene

- **Stage in the overworld, 50 or more blocks from spawn.** A new preview player starts in the Last Day's city, which is
  another dimension, and near spawn the bunker's walls trap the player. Call `PrologueService.skip` and then
  `CryoChamberService.release`, teleport into `server.overworld()` explicitly, force-load the staging chunks, and keep
  every scene inside that one small area. `HydroPreview.stage` and `reset` are the pattern to copy: a few force-loaded
  chunks about 150 blocks out, nine spots 12 apart, and per scene a reset that forgets the power's state, clears
  cooldowns and effects, refills stamina, discards the last actors and fixes the time of day.
- **Log the dimension and the block underfoot** before trusting any shot (`HYDRO_PREVIEW staged in ... on ...`). A
  preview that skipped this photographed void.
- **Clear effects before the first shot.** A player just released from the bunker has darkness for a few seconds.
- **Grant powers the normal way, then sync.** Call `PlayerPowerSyncService.sync` after granting a power, or the HUD doesn't
  know it has one. Handing a power over with `setData` skips the power-change path (see the finisher gotcha below).
- **Play in survival** for anything that hits a mob (see "Photographing skills" below), and give actors no AI and a lot
  of health. Actors with no AI ignore shoves, so check knockback in game tests, not previews.
- **Creative flight stops** when a reset stands the player on the ground: start it again after every teleport into the
  air (`GiftedPreview.hover`), or the shots are taken from the ground.
- **Hide chat** (`options.chatVisibility().set(ChatVisiblity.HIDDEN)`), and hide corner labels while a black hold tint
  is up. Leave the GUI on when the effect being photographed is drawn over the HUD (Vibe's vision washes).

## Cameras

- `TempestCamera.fix(at, lookAt)` holds a still camera; `fix(null, null)` clears it. Keep the game in a third-person mode
  so the player's body is drawn.
- **Queue a fixed camera behind the scene's reset on the server thread** (`HydroPreview.side`): the reset clears the
  camera, and with any server lag a clear sent in the same tick, or a few ticks later, lands after the fix.
- **A front camera sits on the far side of anyone in front of the player**, and inside a standing mob: move the player
  clear first, or use a child villager (`setBaby(true)`) to see over them. A back camera hides whatever the player
  aims at behind their body: use first person or put the target close.
- **Time shots to what the client has.** A cast reaches the client two or three ticks later (see "Photographing
  skills"). For a change that plays over time, shoot by its progress, not by tick (`ShapeshiftPreview.watch`).
- To photograph what only one viewer sees (a foe's private piece), have the driver send that piece to the local player.

## One driver, one flag

- **Every driver needs its own `heroes.preview.*` flag.** Two drivers on one flag (`heroes.preview.styles` and
  `heroes.preview.time` were both reused once) start two worlds, fight over the camera and photograph the dark.
- **Print results; never throw** from a server task (see "Photographing skills"). Checks print a `PASS`/`FAIL` line, as
  the fighting preview's `INPUT ... PASS/FAIL` does, and report failures with `PreviewQuiet.report` so the run fails.

<!-- from speedster containment -->
- **Previews with two players.** A preview needs a second player for this: a `FakePlayer` added to the player list,
  announced with `ClientboundPlayerInfoUpdatePacket.createPlayerInitializing`, and given an `EmbeddedChannel` as its
  connection's channel. **Without the channel the server crashes** the first time it syncs the time to every player
  (see `uitest/ContainmentPreview`). Game tests that hurt a `FakePlayer` must also zero its
  `spawnInvulnerableTime`, or it ignores damage for its first three seconds.
<!-- from Empathic Mimicry -->
- **Preview gotchas.** A camera behind the player can't see a thread that runs straight ahead (the body hides it):
  stage sources off to the side.
<!-- from the Odessa rebuild -->
- **Previews are silent.** `uitest/PreviewQuiet` mutes the game whenever a `heroes.preview.*` flag is on; the Last Day's
  sirens and the bunker's alarms otherwise play on the machine running the photos. A preview player is also enrolled in
  the Last Day, which keeps them inside its streets: `TimeCityPreview` calls `PrologueService.skip` before each jump.
  (The awakening preview's `-Psound` is the one exception that keeps the sound on.)
<!-- from finishers -->
- **Preview gotcha.** Handing a power over with `setData` skips the normal power-change path, so the last power's
  drawn state stays. Hydrokinesis's water ring stayed round the waist in every later shot until
  `FinisherPreview.stage` called `HydroService.powerChanged`.

<!-- from Power Amplification's branch -->
## Previews that pass or fail honestly

A preview closes the game with `Runtime.halt`, which on Windows leaves a failing exit code, so every finished preview used
to end in BUILD FAILED and real failures looked the same as success. `tools/preview-result.gradle` fixes that for any
preview whose init script applies it (`apply from: rootProject.file('tools/preview-result.gradle')` inside
`afterEvaluate`; the amp and finisher previews do):

- `PreviewQuiet.quit` writes `preview-result.txt` in the game directory before the halt (the log loses its last lines to
  the halt), and the Gradle check ignores the exit code and reads that file instead. No file means the game stopped
  early (a crash, or the window closed by hand); a line with FAIL in it, written with `PreviewQuiet.report`, fails the
  run and is printed.
- Report, don't throw: wrap each server step in a guard that reports the throw (`AmpPreview.guarded`), so one broken
  scene is named and the rest still run, and end with one result line (`AMP_PREVIEW_RESULT PASS: 34 scenes, 76/76
  photos`).
- The configuration cache is on (`gradle.properties`), so a run task can't be given a custom output stream to read the
  game's output; files and providers read at execution time are fine.
- Start the first scene a few seconds after the Last Day's black hold lifts, or its photos come out dark.

## Photographing skills (`tools/skill-anim-preview.init.gradle`)

`uitest/SkillAnimationsPreview` casts every Electrokinesis and Superspeed skill in a flat world and saves front, back
and first-person shots to `build/skill-anim-preview/screenshots/`. Lessons from writing it:

- **Play in survival.** A creative player's hits ignore a mob's invulnerability, so the targets die after a cast or two.
- **Report, don't throw.** An `AssertionError` inside a server task crashes the integrated server and ends the run.
- **Shoot two or three ticks after the cast,** not one: the pose and the piece haven't reached the client yet.
- **Log the pose the client applies** (`ClientArmPoseState.get` in `PowerArmPoses.apply`) when a pose looks wrong: that
  is how the generic skill pose (see [powers/electrokinesis.md](powers/electrokinesis.md)) was found.

## Every preview script

All are `tools/<script>`, run with `./gradlew runClient --init-script tools/<script>`. Options are `-P` properties.

| Script | What it photographs | Options |
| --- | --- | --- |
| **Powers** | | |
| `amp-preview.init.gradle` | Power Amplification: Supercharge, Overcharges, the tree, the HUD ring | `-Ponly=boost` (the twenty boosted powers) |
| `awakening-preview.init.gradle` | every power's awakening and every danger, live | `-Ponly=cryokinesis,quake`, `-Psound` |
| `cryo-preview.init.gradle`, `cryo-skills-preview.init.gradle` | Cryokinesis ice, skating, the six skills | |
| `terrain-frost-preview.init.gradle` | Ice Blast freezing stone, obsidian, water and lava | |
| `earth-preview.init.gradle` | Density's earthbending: every key, Tiger and Crane, the tree, the classes, Rock Clap, first person, the Root ring | |
| `electro-preview.init.gradle`, `lightning-preview.init.gradle` | Electrokinesis, and its lightning style | |
| `skill-anim-preview.init.gradle` | Electrokinesis and Superspeed skills | |
| `flight-preview.init.gradle`, `rework-preview.init.gradle` | Flight maneuvers; Tempest, a standing tornado, Matter Assembly, Starfall | |
| `hydro-preview.init.gradle` | Hydrokinesis: kit, waterbending, tree, classes | |
| `invisibility-preview.init.gradle` | Fade and the force fields | |
| `mimicry-preview.init.gradle` | attunement, touch, switching, the memory wheel | |
| `pyro-preview.init.gradle`, `firebending-preview.init.gradle` | Pyrokinesis and Flame Flight; every firebending move | |
| `quake-preview.init.gradle`, `quake-kit-preview.init.gradle` | Quake effects; every move, follow-up, skill and style | |
| `radiation-preview.init.gradle`, `radiation-tree-preview.init.gradle`, `radiation-intro-preview.init.gradle` | radiation effects, the tree, the first awakening | |
| `regeneration-preview.init.gradle`, `regen-kit-preview.init.gradle` | Regeneration effects; every move, skill and style | |
| `shapeshift-preview.init.gradle` | every Shapeshifting phase | |
| `sonic-preview.init.gradle`, `sonic-kit-preview.init.gradle` | Sonic effects; every move, skill and style | |
| `speed-preview.init.gradle`, `speedrun-preview.init.gradle`, `windarms-preview.init.gradle`, `afterimage-preview.init.gradle` | Superspeed effects; Cyclone, tornado, stop, Overrun; Wind Arms; afterimages | `-Pstop` (speedrun) |
| `telepathy-preview.init.gradle`, `telepathy-kit-preview.init.gradle` | Telepathy effects; every pose and piece | |
| `time-preview.init.gradle`, `time-skills-preview.init.gradle`, `time-intro-preview.init.gradle` | Space-Time effects, skills, the awakening | |
| `slowtime-preview.init.gradle`, `veil-preview.init.gradle` | bent time; the veil as another player sees it | |
| `temporal-preview.init.gradle`, `temporal-interaction-preview.init.gradle` | shared Perception; frozen bullets and catches | `-Pmobtime` |
| `rewind-travel-preview.init.gradle`, `death-rewind-preview.init.gradle` | rewinding through two worlds; rewinding a death | |
| `vibe-preview.init.gradle`, `vibe-vision-preview.init.gradle` | every Vibe move and the view through a breach; visions and holograms | `-Pbreaches` |
| `construct-preview.init.gradle`, `signature-preview.init.gradle` | particle constructs; signature abilities at night | `-PconstructWeaveOnly` |
| **Combat** | | |
| `fighting-preview.init.gradle` | every shared fighting style | `-Ponly=telekinesis,quake`, `-Pinput` (presses the real keys and prints `INPUT_RESULT`) |
| `style-preview.init.gradle` | every class ability's set piece | |
| `finisher-preview.init.gradle` | the glisten, the prompt and every finisher | `-Ponly=telepathy` or `-Ponly=hearth,phoenix_kick` |
| `projectile-preview.init.gradle` | projectile ranges | |
| `weapons-preview.init.gradle`, `dampener-preview.init.gradle` | the TaCZ guns and EMP grenade; the Ability Dampener and Nullifier | |
| **Bosses, mobs and events** | | |
| `sylar-preview.init.gradle` | Sylar's Collection: the ticket, the hall, every telegraph assembling and landing, each phase, the backfire, Appraisal, the light, Claire's case, his death | |
| `level5-preview.init.gradle` | How you meet Sylar: the hatch in Cell 3, Level 5's corridor, the prisoner behind glass and his talk in chat, the watch list, the release lever, the glass falling out, him behind you at dusk, the Collection | A fixed camera only draws the player in third person: set `THIRD_PERSON_BACK` before `TempestCamera.fix` when the player should be in shot |
| `gifted-preview.init.gradle` | gifted mobs, sigils, poses, a turn, flares, a crater | |
| `company-preview.init.gradle` | The Company notices: an investigator walking in, asking and hearing the answer, making notes; the attention ring at Noticed, Watched and Hunted; a convoy setting out, marching, its flare from afar, freed; a raid at dusk (the warning, the team coming, the Suppressor close, beaten) | Shoot the investigator's beats when they happen (watch `Investigation.asking()` and the bubbles) rather than at fixed ticks; frame the pair side-on from away from the bell. Push the player's `nextOp` far ahead so the Company never starts its own operation mid-preview |
| `consequence-preview.init.gradle` | Lasting consequences: a saved village's memorials, bust and greeting, a fallen village's ruin and its stone for the lost, a crater on the day it fell and healed at days three and five, a burn blooming, a village rebuilding (broken, most of the way, done) | Age a scar with `Scars.age` and run `Scars.heal`; push a rebuild with `Rebuild.hurryTo`. A greeting shows only with the HUD on and the camera back far enough to see over the villager's head |
| `meteor-preview.init.gradle` | Starfall: the night's streaks, a falling star coming down onto its ring and landing, its crater, the ring by day, a great meteor on a village (early, late, a telekinetic pushing it aside, its landing and crater), a falling star cracked apart | Aim a fixed camera at a meteor from its own `start()` and `sky()`: its side of the sky is random. A set piece reaches only players near its origin or target, so one high in the sky needs a target on the ground |
| `overload-preview.init.gradle` | Overloads, each grown out of its own little village: the wildfire (early, wide, the column of fire, someone calming them, the break's warning, the Firestorm), the supercell (its funnel, from the ground, its deck, Touchdown's warning and run), the blizzard (outside, inside with the COLD ring, seen by a telepath, the Deep Freeze) | Stage each storm 200 blocks from the last and hold its chunks: a storm reaches 50 blocks. Set progress and calm with `Overloads.progress` / `calm`, and the break with `hurry` |
| `meltdown-preview.init.gradle`, `containment-preview.init.gradle`, `live-run-preview.init.gradle` | a villager meltdown; a speedster containing a blast | |
| `eclipse-preview.init.gradle` | the Eclipse moon | |
| **Story and places** | | |
| `prologue-preview.init.gradle`, `time-city-preview.init.gradle` | the Last Day beat by beat; Odessa (prints `CITY_AUDIT`) | |
| `bunker-preview.init.gradle`, `night-three-preview.init.gradle` | the bunker tour (prints `BUNKER_AUDIT`, `BUNKER_DARK`); Night Three | |
| `blueprint-preview.init.gradle` | one blueprint, from four corners, the top and inside | `-Pblueprint=<name>`, `-Pcut=<layer>` |
| `ruins-preview.init.gradle`, `signal-site-preview.init.gradle` | the story ruins; the Last Signal site | |
| `unwritten-world-preview.init.gradle` | A Future Unwritten's world fixture | |
| **Interface and gear** | | |
| `stamina-preview.init.gradle`, `gauge-preview.init.gradle` | the stamina bar; the HUD ring gauges | |
| `skill-ui-preview.init.gradle`, `inventory-preview.init.gradle`, `admin-preview.init.gradle` | the skill screen; the inventory button; the admin menu | |
| `compat-preview.init.gradle` | JEI and Jade with our content; checks what Jade targets and that our keys and JEI's are never live together ([compat.md](compat.md)) | `-PnoJei -PnoJade` (the same scenes without them) |
| `suit-preview.init.gradle`, `body-preview.init.gradle`, `carry-preview.init.gradle` | protective suits; severed limbs and the downed body; carrying | |
