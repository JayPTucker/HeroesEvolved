# Game tests: notes for agents

How to run the gameplay tests, and every gotcha past work ran into. The gotchas were found one power at a time and used
to sit in that power's section; they are gathered here, each marked with where it came from.

Part of the notes for coding agents: start at [AGENTS.md](../../AGENTS.md).

## Running them

- **Where tests live.** `src/gametest/java` (NeoForge game tests) and `src/gametest/resources` (test structures such as
  `rewind_empty`, and `data/minecraft/worldgen/world_preset/flat.json`, which lists every dimension the test server has).
  They are loaded only by an init script, so they never ship in the mod's jar. The only plain check outside the game is
  the effect geometry check below.
- **The whole suite** is what GitHub runs on every push (`.github/workflows/build.yml`):
  `./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle`. Every run starts from a fresh world and
  the mod's default config. Failures are in `build/rewind-gametest-world/logs/gametest`.
- **The full suite also checks dedicated-server safety.** `rewind-gametest.init.gradle` applies
  `tools/client-class-tracer.gradle`, which fails the run if any test path loads a client-only class (a real server has
  none; the dev server has them all). Its log is `build/rewind-gametest-world/client-class-loads.txt`, with the stack
  of each load. Game tests never encode packets: use the multiplayer check for payloads
  ([servers.md](servers.md)).
- **One or a few packages.** `./gradlew runGameTestServer --init-script tools/structure-gametest.init.gradle
  -PtestPackages=vibe,time` runs only those packages (the default is `structure`, about a minute), in a world of its own.
- **Focused suites** with their own worlds:

| Init script | Runs |
| --- | --- |
| `tools/admin-gametest.init.gradle` | admin protection, permissions and dormant assignments |
| `tools/amp-gametest.init.gradle` | Power Amplification, every power's skill-tree, class and balance checks, and the finisher tests (about two minutes) |
| `tools/combat-target-gametest.init.gradle` | who powers may target |
| `tools/elements-gametest.init.gradle` | Hydrokinesis in the Nether, Pyrokinesis in the rain |
| `tools/fighting-gametest.init.gradle` | `FightingStylesGameTests` |
| `tools/structure-gametest.init.gradle -PtestPackages=radio/LivingRadioGameTests.java,village/GiftedWorkGameTests.java` | The living radio (bunker radios, chatter, the voice) and gifted villagers at work (every job, the courier, favours) |
| `tools/structure-gametest.init.gradle -PtestPackages=trace,travel,village/VillageGrowthGameTests.java,village/VillageProjectGameTests.java,village/VillageUpgradeGameTests.java,village/VillageLifeGameTests.java,village/VillageWorkGameTests.java` | Power traces, travellers on the road (and the Company's pursuit), villages that grow, notice boards and projects, tiers, rebuilt homes and town houses, player blocks left alone, homes of your own, market days, gathering, building crews, mending, slopes |
| `tools/structure-gametest.init.gradle -PtestPackages=village/WorldEventBalanceGameTests.java` | World events in multiplayer and singleplayer: player groups, one trouble per village, party scaling, starfall and sleep, calendar days |
| `tools/structure-gametest.init.gradle "-PtestPackages=compat,travel/TravellerBodiesGameTests.java" -Pmca` | Villagers with the real Minecraft Comes Alive ([mca.md](mca.md)); without `-Pmca`, the same tests on vanilla spawning |
| `tools/structure-gametest.init.gradle -PtestPackages=company` | The Company: agents, attention, investigators, convoys, raids (`CompanyAgentGameTests`, `CompanyNoticeGameTests`) |
| `tools/structure-gametest.init.gradle -PtestPackages=village/ConsequenceGameTests.java` | What villages remember, scars, rebuilding (a single file works as a package) |
| `tools/structure-gametest.init.gradle -PtestPackages=meteor` | Starfall's meteors (`meteor/MeteorGameTests`), in a world of their own |
| `tools/gifted-gametest.init.gradle` | gifted mobs, village events (overloads too: `-PtestPackages=village/overload` with the structure script runs only those), nuclear blasts, mimicry |
| `tools/perception-projectile-gametest.init.gradle` | Perception and projectiles, with real TaCZ entity ticks |
| `tools/projectile-gametest.init.gradle` | long range, obstruction and projectile lifetimes |
| `tools/radiation-gametest.init.gradle` | radiation, the first awakening and crater recovery |
| `tools/sylar-gametest.init.gradle` | Sylar's Collection and how you meet him (`story/sylar`: the fight, Level 5, the list), with the root test helpers |
| `tools/structure-gametest.init.gradle` | `structure` by default, or `-PtestPackages=a,b` |
| `tools/telepathy-gametest.init.gradle` | Telepathy |
| `tools/temporal-gametest.init.gradle` | voice and shared-time rules, `TimeFlowGameTests` |
| `tools/vibe-gametest.init.gradle` | Vibe and the grid |

- **The other checks GitHub runs:** `./gradlew build`, `python3 tools/blueprint.py validate` over every blueprint, and
  `python3 tools/verify-skill-content.py`. Run `py -3 tools/balance.py --check` too whenever costs change.
- **Game tests call the server directly.** They never press a key, so a broken click, double-tap or key binding passes
  every test. The fighting preview's `-Pinput` run and `flight-roll-preview` are the checks of real input (see
  [previews.md](previews.md)).
- **The effect geometry check** (`tools/verify-effect-geometry.ps1`) compiles the mod and runs
  `tools/EffectGeometryCheck.java` against its classes with no game open: meshes, poses, particles and payload
  round-trips, one `PASS:` line each. GitHub doesn't run it, so it rots: by November 2026 it was broken two ways at
  once and nobody had noticed. Run it after changing anything it calls, and when you delete a class, delete its block
  too.
  - **There is no client in it.** `Minecraft.getInstance()` is null, so client code it reaches that reads the client's
    world throws. Wrap that block in `withClientOutsideAWorld`, which puts in a client with no world for the block's
    length. Don't add a null-client guard to the game's code for it: a client always exists in game
    (`ClientFlightVisualState.update` crashed it this way).
  - **It must run in Windows PowerShell 5.1,** the one Windows ships (`pwsh`, version 7, may be missing). 5.1 has no
    `-Encoding utf8NoBOM` (and its `utf8` writes a BOM, which java reads as part of the first argument): write files
    with `[IO.File]::WriteAllText` and `UTF8Encoding($false)`. When output is redirected, 5.1 turns a native tool's
    stderr (javac's warnings) into errors, so run tools through `Invoke-EffectTool` and judge them by exit code.

## Before you blame your change

<!-- from the Living Seal rework, November 2026 -->
- **The endgame package's server can hang after passing.** On this Windows machine,
  `-PtestPackages=endgame` logs "All N required tests passed", then "Saving worlds", and never exits (the commit before
  the Dragon rework, `18b8775`, hangs the same way). Run it with a `timeout`, read the log for "required tests passed"
  rather than trusting the exit code, and stop the leftover `java.exe` (match `*<your build folder>*` in its command
  line, never the shared Gradle daemon) before the next run.
<!-- from rescue awakenings -->
- **The suite is order-sensitive.** Adding or renaming test batches reshuffles the run order, and about eight older
  tests (conduits, Sylar's escape, rapid punches, Long Winter, Fault, the Sentry Seal) then fail on power-ownership
  leaks with no code change. Check against the untouched code with stand-in tests of the same names before blaming a
  change.
<!-- from Shapeshifting -->
- **Known flaky tests.** `facilitybuildsinbatchesandmanualroutecansavebothcaptives` (waits on chunk loading) failed once
  during this work and passed on the next run with no change. Re-run before blaming new code.
  `hisneighboursscreamandrun` (the meltdown crowd) was flaky too, and is fixed: it read a runner's walk target at one
  tick, but the meltdown re-sets it every five and the brain drops a target it can't path to in a walled arena, so it
  now looks across a window. Check a re-sent brain memory over a window, never at one tick.
  `thelastdayrunsfromapartmenttopod` failed four full runs in a row once another batch was added ("The guards, Noah and
  Claire are all placed: 8", "Rubble comes down...: 2") and passed alone every time; it is fixed. The guards' chunks and
  the stasis hall's, forced when the day starts, were still waking under the suite's load: the test now waits for all ten
  of the cast and for every hall chunk to be entity-ticking before the blast, and counts the deepslate tiles the blast
  drops as rubble too.
- **The speedster tornado's tests race chunk loading** (as the retired Sylar hunt tests did). They have failed with no
  code change, sometimes twice running. Re-run them alone before blaming a change.
<!-- from Telepathy -->
- **Local full-suite runs start from the default config.** A forward speed of 15 left in
  `build/rewind-gametest-world/config` since October made both Skyburst tests fail locally and nowhere else (a tap
  already hit the 10-block cap); `tools/rewind-gametest.init.gradle` now deletes that config with the world, as GitHub's
  fresh checkout does. When a test passes alone but fails in the full run, compare the run's config before blaming code.
<!-- from Telepathy -->
- **Tests that wait on a chunk wait for it.** A freshly loaded chunk can take seconds to start entity-ticking while the
  suite runs, and a test FakePlayer moving out of its arena leaves no player to keep chunks ticking. Sylar's boss bar and
  the meltdown crowd now wait for `isPositionEntityTicking` before their clock starts; the flight rescue measures the
  flyer while the ally is carried; the anchored tornado's far cow stands inside the arena's barrier walls; Sylar's tests wait
  for every corner of his hall to come alive.
- **Pocket dimensions hide players at first.** For a few seconds after an arena dimension is built, a player in a chunk
  that isn't entity-ticking yet is invisible to `getEntitiesOfClass`. Wait for every corner of the room
  (`SylarCollectionGameTests.ready`), not just the boss's chunk.
- **Wait for every distant target, including a later replacement.** The frozen-villager story test could miss its
  first mind at 30 blocks or its second at 35, depending on chunk alignment. `CryoGameTests.frozenVillagersKeepTelepathyStoryProgress`
  holds the path's chunks and waits for both villagers in the real entity query before reading. It releases only its
  own tickets and cleans up on timeout too. A direct entity reference does not prove that targeting can see it.

## Clean up, or break every later test

<!-- from the Watchmaker (retired) -->
- **Test gotchas.** `FakePlayer` ignores `awardStat` (set stats with `getStats().setValue`). Wrap every sequence step in
  a guard that removes the test's players on failure: a test that dies mid-sequence leaves its players owning their
  powers, and every later test that grants those powers fails (34 failures once).
<!-- from Induced Radioactivity -->
- **Tests that start a detonation must clean up on failure.** If an early `runAfterDelay` check throws before
  the fixture's `finally`, the test player stays registered and *really detonates* later, wrecking every test
  that runs near it (15 unrelated failures once). Wrap early checks in `try { ... } catch (RuntimeException e)
  { remove(player); throw e; }`.
<!-- from Space-Time -->
- **Server-wide fields and parallel tests.** Time fields reach every entity in every dimension when
  `fieldsServerWide` is on (the default). `TimeFieldTestSupport` turns it off on the game-test server, or one test's
  Still World would freeze every other test's mobs; `TimeVeilGameTests` turns it on inside its own one-test batch.
  Photographs: `tools/veil-preview.init.gradle` (`uitest/TimeVeilPreview`, flag `heroes.preview.veil`), with a second
  player staged as in `ContainmentPreview`.

## Test players

<!-- from rescue awakenings -->
- **Moving a `FakePlayer` in tests.** Its connection ignores teleports and motion payloads, so call `absMoveTo` (or
  `setPos`) as well, as `SpeedRescue.teleport` and `FlightRescue.drive` do. A `FakePlayer` refuses to ride anything,
  so anyone a test carries must be a real `ServerPlayer` on an `EmbeddedChannel` (`RescueGameTests.carriable`).
<!-- from Quake -->
- **Test players keep three seconds of spawn protection** even when `isInvulnerableTo` is overridden: `ServerPlayer.hurt`
  checks `spawnInvulnerableTime` itself. Zero it by reflection (`QuakeReworkGameTests.pressureSkin...`). Skeleton arrows
  also scale to nothing on the test server's difficulty; use an ownerless shot.
<!-- from Shapeshifting -->
- **Testing what a FakePlayer can't feel.** A game-test FakePlayer is invulnerable, so no damage event ever reaches it.
  The events call small entry points (`ShapeshiftTricks.struck`, `ShapeshiftSuspicion.blow`) that tests call directly.
<!-- from Space-Time -->
- **Tests.** `SpaceTimeSkillsGameTests` uses a `FakePlayer` whose `teleportTo` also calls `absMoveTo` and whose
  `isInvulnerableTo` is false, and zeroes `spawnInvulnerableTime` by reflection before it is hurt. Package-private entry
  points (`seal`, `throwSeal`, `forwardEnemy`, `rewindPersonal`) let a test drive each skill without aiming.
<!-- from Telepathy -->
- **Test players must be in the player list** when the code looks them up by id: links and routes resolve members with
  `getPlayerList().getPlayer`. The test fixture adds and removes them by reflection, as `RegenerationGameTests` does.
- **A listed FakePlayer crashes the server at the next time sync** unless its connection answers `hasChannel`. Every 20
  ticks NeoForge's `synchronizeTime` asks each listed player's connection whether it has the custom time channel, and a
  `FakePlayer`'s connection has no netty channel (a `NullPointerException` in `ChannelAttributes`). A test that keeps a
  listed player past a 20-tick boundary gives it a connection of its own that answers: `CompanyAgentGameTests`' aim test
  makes a `ServerGamePacketListenerImpl` with a bare `Connection` and overrides `hasChannel(ResourceLocation)` (false)
  and `send` (nothing). It used to pass only when the guard fired before the boundary.
- **Above the build limit nothing is ticking.** The meltdown tests float villagers at y 500; `isPositionEntityTicking`
  is false there whatever the chunk. Teleporting into a freshly forced chunk before its entities have loaded only passed
  when it happened to be ready (it failed every time in the smaller gifted suite): wait for `hasChunk` and
  `areEntitiesLoaded(chunk)` first (`VillagerMeltdownGameTests.villagersCarriedToTheSafePointCountAndStay`).
- **Player marks outlive a test.** `PlayerBuilt` marks sit on the chunk, and later batches reuse test positions: a test
  that lays ground clears the marks in its box first (`PlayerBuilt.clear`), or a stall or house refuses ground an
  earlier test's player "built" (it flaked one run in three).
- **Room for a village house in a test:** the arena is too small and has a ceiling; lay an open grass platform at y 300
  (nothing above it) and put the bell on it (`VillageGrowthGameTests.aThrivingVillageBuildsOneOfItsOwnHouses`), then
  clear the box afterwards.
- **Sleeping in a test:** post `new CanPlayerSleepEvent(player, bedPos, null)` on `NeoForge.EVENT_BUS` and read
  `getProblem()` (`WorldEventBalanceGameTests`, `CompanyNoticeGameTests.lyingDownWhileHuntedBringsTheRaid`).
- **Unloading in a test:** `entity.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK)` stands in for a chunk unloading
  under it (`CompanyNoticeGameTests`' unloaded escort and prisoner). A position 90,000 blocks out is never ticking, for a
  "not loaded yet" case; release any ticket it starts (`CompanyOps.callOffAll` clears prep tickets).
- **Never clean up in a `finally` inside `succeedWhen`.** It retries until the checks pass, so the first failure's cleanup
  runs and the retry then fails on the cleaned-up state, hiding the real cause (an attention reading 0.0 instead of a
  missed step). Assert, then clean up at the end of the lambda.
<!-- from Regeneration -->
- **Tests.** `RegenerationGameTests`' fixture puts the FakePlayer in the player list, so features ticked through
  `getPlayerList().getPlayer` (the downed timer, Shared Blood's lookup) work; reuse it. A cow's 10 health caps any
  heal you assert on; a zombie's armour shaves a couple of points off a hit; use a pillager for exact numbers.
<!-- from Sonic Resonance -->
- **A FakePlayer never lands.** A test singer that isn't in the level is still ticked by the service's own server
  tick, but `onGround()` never comes true, so Sonic Dive lands on its `DIVE_MAX` timeout (as Fission Leap's slam
  does). Tests that need skill nodes set `SKILL_TREES` directly (`SonicGameTests.learn`).
<!-- from Shapeshifting -->
- **Operators in game tests.** The game-test server's `PlayerList.op` grants level 0. Add a
  `ServerOpListEntry(profile, 4, false)` to `getOps()` yourself, and remove it afterwards.
- **Held moves must tick on the server tick.** The level never ticks a game-test player, so anything driven from the
  player tick never runs in a test. Keep a reference to the `ServerPlayer` and tick from the service's server tick
  (Octopus Form, Wave Rider, Maelstrom, `StaminaDrain`). Keep the owner, not its UUID: test players aren't in the
  player list unless the fixture adds them.
- **A `FakePlayer` ignores `openMenu`,** so tests build a menu directly (Mind's Eye).
- **PvP is always off on the game-test server** (`setPvpAllowed(true)` is ignored), so player-on-player damage never
  lands. Test the rule itself, as the shield test calls `NuclearShields.brace` and Vibe's tests call `desync`.
- **Tests that check a charged cost** must work it out with `AbilityRanks.energy`, not read the balance table
  ([AGENTS.md](../../AGENTS.md#stamina-and-balance)). `PlayerProgressionService.getLevel` is the power's tier (1 to 5);
  the unlock check uses `getPowerLevel`, and tests set it with `setPowerLevel`.
<!-- from Between Heartbeats and the Amplification passives, November 2026 -->
- **A mob's blow on a player scales with difficulty.** `mobAttack` is changed by `Player.hurt` before any of our hooks
  see it, so an exact number from it depends on the test world. Use `generic()` (no attacker) for an exact check, or
  compare two equal blows (Grounded's test: a powered foe's 10 lands at four fifths of a plain foe's 10).
- **Time anything in a run by the run's own clock.** A spawned helper entity's `tickCount` did not advance in the
  test world (Between Heartbeats' illusions), so a window measured on it never opened.

## Arenas and mobs

<!-- from rescue awakenings -->
- **Test arenas have a lid.** A game test's arena is boxed in barrier blocks, ceiling included, unless the test sets
  `skyAccess = true`; anything that checks for open sky (Flight's awakening) needs it. The `rewind_empty` floor is at
  relative y=1, so stand players at y=2.
<!-- from Density -->
- **Test gotchas.** `rewind_empty` is 12 × 6 × 12 with a barrier lid, so anything above y = 5 needs
  `skyAccess = true`. No-AI mobs never land: set `setOnGround(true)` for anything that checks `onGround()` (Pile Driver,
  Grasp from Below). Fill stone under an arena's floor (y = 0 and −1) before burying anything there.
<!-- from Invisibility -->
- **Test gotchas.** No-AI mobs ignore velocity (assert on `getDeltaMovement`, or use a mob with AI such as a pig),
  and the `rewind_empty` arena is 12 blocks across: entities placed beyond it sit in unloaded or solid ground.
- **A mob's own attack is scaled by the test server's difficulty.** Use generic damage when a test needs exact numbers
  (`TakedownGameTests`).
- **Mark test mobs as already rolled.** Gifted rolls make a test random: mark test mobs `ROLLED` and give test villagers a
  null gift ([gifted-mobs.md](../../.claude/skills/minecraft-bosses/references/gifted-mobs.md)).
- **A mini-boss only takes a player's blows.** Anything else is capped at 3 a hit, so kill one in a test with a player's
  blow.
- **A new dimension must be listed for tests** in `src/gametest/resources/data/minecraft/worldgen/world_preset/flat.json`,
  or `server.getLevel(...)` returns null there.

## TaCZ guns in tests

- **TaCZ times shots in real milliseconds.** The game-test server runs ticks as fast as it can, so every shot comes back
  `COOL_DOWN`. Tests that fire real guns hold each tick to 50 ms (`WeaponGameTests.realTime`). An armor stand is a good
  test shooter: living, no AI, fires exactly where it is pointed ([weapons-and-dampener.md](weapons-and-dampener.md)).

## Tests to read before writing your own

| Need | Copy from |
| --- | --- |
| A player in the player list | `RegenerationGameTests`, `TelepathyReworkGameTests` |
| Two players and a carry | `RescueGameTests.carriable` |
| A hurtable test player | `SpaceTimeSkillsGameTests`, `QuakeReworkGameTests` |
| Voice-chat routes without audio | `TelepathyReworkGameTests` (`nearVoices` shrinks the range to five blocks) |
| A boss arena in a pocket dimension | `SylarCollectionGameTests` (`ready`, `open`, `guard`) |
| Every scripted actor standing on a floor | `PrologueGameTests.standing`, `PrologueGameTests.walkable` |
| Every blueprint placing cleanly | `BlueprintPlacementGameTests`, `BunkerAndPowersGameTests.blueprintsLoadWithTheirMarkers` |
| Every balance entry being a real action | `PowerBalanceGameTests`, `BalanceDumpGameTests` |
| Gifted mobs, raids and moves | `GiftedMobGameTests` (`GiftedMob.land`, `Renegades.hurry`) |
| Finishers | `TakedownGameTests` |
| Every class Ultimate, cast for real | `UltimateGameTests` (a `@GameTestGenerator`, one batch per Ultimate) |
| One generated test per power from a table | `AmplificationGameTests.BOOSTED` (a `@GameTestGenerator`; see [powers/amplification.md](powers/amplification.md)) |
| A real player that area searches can find | `AmplificationGameTests.findable` (`addNewPlayer`, then `removePlayerImmediately`) |
| Something that needs a custom dimension | the game-test server loads only the vanilla dimensions, so `TemporalSnapshotService.beginSnapshot` refuses there. Plant a map entry instead (`WorldCloseGameTests`) |
