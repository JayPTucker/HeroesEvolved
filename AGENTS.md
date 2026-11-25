# Notes for coding agents

Heroes Evolved is a Minecraft 1.21.1 NeoForge mod (Java 21, mod id `heroesevolved`, package
`com.jayptucker.heroesevolved`): twenty-one player powers with skill trees, classes, fighting styles and finishers, story
bosses, gifted mobs, village emergencies and a playable prologue. It needs TaCZ, Simple Voice Chat and Simple Radio
(with Lexiconfig) on both sides.

## How these notes are organised (read this first)

These notes are one body of knowledge split across files, because one file was too big to read (and Codex reads only
the first 32 KiB of AGENTS.md). This file holds what applies to every task. Everything else is in:

- **`docs/agents/`**: how things are built and the patterns to reuse, by topic, with one file per power in
  `docs/agents/powers/`.
- **`.claude/skills/`**: step-by-step skills for bosses and mobs, building, and armor. Claude loads them by itself;
  other agents read the `SKILL.md` named in the table below.
- **`docs/*.md`**: what the player sees, one page per power or system. These are the specifications.

**Before you start a task, find its rows in the table below and read those files.** They hold the rules that past work
paid for (most of them a failure first), and code that ignores them gets redone. If a task touches several areas, read
each one. If nothing matches, search `docs/agents/` and `.claude/skills/` for the system's class names before writing
anything new.

**When you review, reorganise or add to these notes, read every file in `docs/agents/` and `.claude/skills/` first**,
so nothing is duplicated, contradicted or lost, and keep the index below complete.

## Read before you work

| When the task involves... | Read |
| --- | --- |
| Any ability effect, arm pose, set piece or camera | [docs/animation-guide.md](docs/animation-guide.md) (the quality bar, tools, body anchors, how to photograph), then "Drawing it" in [kit-design.md](docs/agents/kit-design.md) |
| Designing, adding or reworking a power, a skill tree or a class | [kit-design.md](docs/agents/kit-design.md) (the checklist and every reusable pattern), then the power's own notes |
| One power | its row in "Powers" below: its agent notes and its player doc |
| A power that fights hand to hand | [fighting-styles.md](docs/agents/fighting-styles.md). **Pyrokinesis is the bar** (see below) |
| Finishers | [finishers.md](docs/agents/finishers.md) |
| Awakenings, intros, saving someone in danger | [awakenings.md](docs/agents/awakenings.md) |
| Two powers acting on each other | [power-interactions.md](docs/agents/power-interactions.md) |
| Opening the End portal, shared channeling or a solo offering | [power-interactions.md](docs/agents/power-interactions.md), "End portal offerings", and [end-portal.md](docs/end-portal.md) |
| The native End Dragon, Inheritance Sylar, grounding altars, End checkpoints or the rupture finale | The bosses skill, its [built-bosses.md](.claude/skills/minecraft-bosses/references/built-bosses.md), "The Living Seal", and [the-living-seal.md](docs/the-living-seal.md) |
| Any mob that fights with a power, a mini-boss, a boss or a world event | **the bosses skill**: [.claude/skills/minecraft-bosses/SKILL.md](.claude/skills/minecraft-bosses/SKILL.md). **First its [fight-design.md](.claude/skills/minecraft-bosses/references/fight-design.md)**: how to make the best fights (powers must be needed: locks every power can open; five-phase animation; Souls-like numbers), from Cataclysm, Bosses'Rise and five more boss mods ([boss-mods.md](.claude/skills/minecraft-bosses/references/boss-mods.md)) |
| Any structure, room, city block, arena, or Java that places scenery block by block | **the building skill**: [.claude/skills/minecraft-building/SKILL.md](.claude/skills/minecraft-building/SKILL.md) |
| Scattered Overworld ruins (burnt/collapsed houses, Bennet house, Isaac's studio), their rarity, terrain blending or submerged salvage | The building skill, then [story-and-scenes.md](docs/agents/story-and-scenes.md), "Scattered ruins". Edit `tools/generate-scattered-ruins.py`, regenerate and re-export all four NBT templates; keep loot and painting markers. Rarity lives in `worldgen/structure_set/ruins.json` and `story_ruins.json`; structures use `OCEAN_FLOOR_WG` and `RuinsWaterProcessor` for flooded rooms and floating salvage |
| Anything worn (armor, suits, helmets, costumes) | **the armor skill**: [.claude/skills/minecraft-armor/SKILL.md](.claude/skills/minecraft-armor/SKILL.md) |
| The Last Day, Odessa, the bunker, story actors, scripted scenes | [story-and-scenes.md](docs/agents/story-and-scenes.md) |
| Village distress calls, the radio, villager meltdowns, overloads, Starfall (meteors), what villages remember, scars, rebuilding, the Company noticing players (attention, investigators, convoys, raids), the living radio, gifted villagers' work and favours, power traces, travellers on the road, villages that grow (notice boards, tiers, homes of your own, market days, leaving players' blocks alone) | [village-events.md](docs/agents/village-events.md) |
| TaCZ guns, Forge Energy machines, the Ability Dampener | [weapons-and-dampener.md](docs/agents/weapons-and-dampener.md) |
| Meltdown hazards, protective suits, HUD ring gauges | [hazards-and-suits.md](docs/agents/hazards-and-suits.md) |
| Sounds, music, OGG files, voice chat | [audio.md](docs/agents/audio.md): start from the user's reference library it describes (`C:\Users\jaypa\OneDrive\Desktop\Gemini Heroes Reborn Audio`), never ship any of it; [powers/telepathy.md](docs/agents/powers/telepathy.md) for voice chat |
| Costs, cooldowns, drains, unlock levels | "Stamina and balance" below, and [docs/stamina-balance.md](docs/stamina-balance.md) |
| Mastery, Power Levels, max stamina, anything that rewards play (and stopping farms) | [progression.md](docs/agents/progression.md) |
| Player guidance, the Guide tab, contextual tips, civilian journals, admin controls or debug commands | [guidance-and-admin.md](docs/agents/guidance-and-admin.md), [player-guidance.md](docs/player-guidance.md) |
| Adding or changing any item, block, entity, mob, machine, recipe or disguise; JEI or Jade themselves | [compat.md](docs/agents/compat.md) (its checklist) |
| Spawning, replacing or special-casing a villager; Minecraft Comes Alive | [mca.md](docs/agents/mca.md). **Never `EntityType.VILLAGER.create` plus `addFreshEntity`:** use `Villagers.actor` or `Villagers.enter` |
| Writing, running or fixing game tests | [testing.md](docs/agents/testing.md) |
| Screenshots of anything in game | [previews.md](docs/agents/previews.md) |

### Powers

| Power | Agent notes | What the player sees |
| --- | --- | --- |
| Cryokinesis | [powers/cryokinesis.md](docs/agents/powers/cryokinesis.md) (terrain freezing) | [cryokinesis.md](docs/cryokinesis.md) |
| Density (earthbending; id `phasing`) | [powers/density.md](docs/agents/powers/density.md) | [density.md](docs/density.md) |
| Electrokinesis | [powers/electrokinesis.md](docs/agents/powers/electrokinesis.md) | [electrokinesis.md](docs/electrokinesis.md) |
| Empathic Mimicry | [powers/mimicry.md](docs/agents/powers/mimicry.md) | [empathic-mimicry.md](docs/empathic-mimicry.md) |
| Flight | [powers/flight.md](docs/agents/powers/flight.md) | [flight.md](docs/flight.md), [carrying.md](docs/carrying.md) |
| Hydrokinesis | [powers/hydrokinesis.md](docs/agents/powers/hydrokinesis.md) | [hydrokinesis.md](docs/hydrokinesis.md) |
| Induced Radioactivity | [powers/radiation.md](docs/agents/powers/radiation.md) | [induced-radioactivity.md](docs/induced-radioactivity.md) |
| Invisibility (force fields) | [powers/invisibility.md](docs/agents/powers/invisibility.md) | [invisibility.md](docs/invisibility.md) |
| Power Amplification | [powers/amplification.md](docs/agents/powers/amplification.md) (it changes every other power's numbers) | [power-amplification.md](docs/power-amplification.md) |
| Pyrokinesis | [powers/pyrokinesis.md](docs/agents/powers/pyrokinesis.md) | [pyrokinesis.md](docs/pyrokinesis.md) |
| Quake | [powers/quake.md](docs/agents/powers/quake.md) | [quake.md](docs/quake.md) |
| Regeneration | [powers/regeneration.md](docs/agents/powers/regeneration.md) | [regeneration.md](docs/regeneration.md) |
| Shapeshifting | [powers/shapeshifting.md](docs/agents/powers/shapeshifting.md) | [shapeshifting.md](docs/shapeshifting.md) |
| Sonic Resonance | [powers/sonic.md](docs/agents/powers/sonic.md) | [sonic-resonance.md](docs/sonic-resonance.md) |
| Space-Time Manipulation, slowed time | [powers/time.md](docs/agents/powers/time.md) | [space-time-manipulation.md](docs/space-time-manipulation.md) |
| Superspeed | [powers/superspeed.md](docs/agents/powers/superspeed.md) | [superspeed.md](docs/superspeed.md) |
| Telepathy | [powers/telepathy.md](docs/agents/powers/telepathy.md) | [telepathy.md](docs/telepathy.md) |
| Vibe | [powers/vibe.md](docs/agents/powers/vibe.md) | [vibe.md](docs/vibe.md) |
| Telekinesis, Technopathy, Matter Assembly | none yet: read [kit-design.md](docs/agents/kit-design.md) | [telekinesis.md](docs/telekinesis.md), [technopathy.md](docs/technopathy.md), [matter-assembly.md](docs/matter-assembly.md) |

Shared systems with player docs: [fighting-styles.md](docs/fighting-styles.md), [finishers.md](docs/finishers.md),
[classes.md](docs/classes.md), [skill-trees.md](docs/skill-trees.md), [progression.md](docs/progression.md),
[power-intros.md](docs/power-intros.md), [power-sounds.md](docs/power-sounds.md), [gifted-mobs.md](docs/gifted-mobs.md),
[sylar.md](docs/sylar.md), [company-agents.md](docs/company-agents.md),
[wither.md](docs/wither.md) (Containment Failure: the breach it is dragged through, the Ossuary, its death blast and exit),
[the-living-seal.md](docs/the-living-seal.md) (the native End Dragon and Sylar's Inheritance finale),
[emergency-band.md](docs/emergency-band.md), [overloads.md](docs/overloads.md), [meteor-shower.md](docs/meteor-shower.md),
[lasting-consequences.md](docs/lasting-consequences.md), [company-notices.md](docs/company-notices.md),
[living-radio.md](docs/living-radio.md), [gifted-villagers.md](docs/gifted-villagers.md), [power-traces.md](docs/power-traces.md),
[travellers.md](docs/travellers.md), [village-growth.md](docs/village-growth.md),
[the-last-day.md](docs/the-last-day.md), [end-portal.md](docs/end-portal.md),
[the-past.md](docs/the-past.md), [jei-and-jade.md](docs/jei-and-jade.md),
[minecraft-comes-alive.md](docs/minecraft-comes-alive.md).

## Pyrokinesis is the bar for movement and fighting

Pyrokinesis has the best movement in the mod: firebending's combo of fists and kicks, jet-step dodges with an evasion
window, a backflip kick, a counter that rewards a dodge, whole-body turns, and Flame Flight's jets on palms and boots.
Many other abilities should move like it, though not all of them (a telepath or a healer has no business doing
backflips). When a power should feel physical, start from firebending: the shared `combat/FightingStyles` engine gives
any power the same input, dodges, evasion and counters (Electrokinesis's lightning style and Hydrokinesis's
waterbending are each one class on it, plus art and poses), and `render/FightingStylePoseRenderer` turns the whole
body. See [fighting-styles.md](docs/agents/fighting-styles.md) and [docs/pyrokinesis.md](docs/pyrokinesis.md).

## Where things are

- `src/main/java/com/jayptucker/heroesevolved/`: a package per power (`pyro`, `cryo`, `electro`, `hydro`, `speed`,
  `flight`, `quake`, `sonic`, `telepathy`, `telekinesis`, `time`, `radiation`, `regeneration`, `phasing` for Density,
  `invisibility`, `mimicry`, `shapeshift`, `technopathy`, `assembly` for Matter Assembly, `vibe`, `amplify` for Power
  Amplification) and shared ones:
  `ability` (actions, use, balance), `combat` (fighting styles, finishers), `skills` and `styles` (trees, classes),
  `energy` (stamina), `progression`, `intro` (awakenings), `effects` and `client/effects` (set pieces; each power's art
  is `client/effects/setpiece/<Power>Art`), `network`, `story` (prologue, bunker, Sylar and his Collection, actors), `gifted`,
  `village` (with `village/overload`, the storms), `meteor` (Starfall), `company` (Company agents, attention and its operations), `trace` (power traces), `travel` (travellers on the road), `radio`, `weapons`, `dampener`, `hazard`, `structure` (blueprints),
  `mixin`, `compat` (the optional JEI and Jade plugins).
- `src/main/resources/data/heroesevolved/`: `blueprints/`, `power_balance.json`, `skill_trees.json`, tags, damage types.
- `src/gametest/` (game tests) and `src/uitest/` (preview drivers) load only through init scripts in `tools/`, so they
  never ship in the jar.
- `tools/`: init scripts for tests and previews, `balance.py`, `blueprint.py`, and the generators for every texture
  and sound (`generate-*.py`, `Generate*.java`). **Generated files are changed by changing their generator**, never by
  hand.

## Commands

- **Build:** `./gradlew build`.
- **Every gameplay test** (what GitHub runs): `./gradlew runGameTestServer --init-script tools/rewind-gametest.init.gradle`.
  Focused suites, `-PtestPackages=...` and every gotcha: [testing.md](docs/agents/testing.md).
- **Previews:** `./gradlew runClient --init-script tools/<name>-preview.init.gradle`; screenshots land in
  `build/<name>-preview/screenshots/`. A new preview script applies `tools/preview-result.gradle` so it passes or fails
  honestly. Rules and the list: [previews.md](docs/agents/previews.md).
- **Checks GitHub also runs:** `python3 tools/blueprint.py validate <blueprints>`, `python3 tools/verify-skill-content.py`.
  After changing any cost: `py -3 tools/balance.py --check`.
- **On this Windows machine** Python is `py -3` (or `python`, not `python3`), `JAVA_HOME` is already a Java 21 JDK, and
  `PYTHONIOENCODING=utf-8` is needed for scripts that print Greek letters. Write scripts to a file and run them; Python
  pasted into a shell heredoc has had its `\n` escapes turned into real newlines.

## Rules that apply everywhere

Each rule cost a bug once. The file in brackets has the story and the code to copy.

- **One question, one place.** Who is a friend (`isFriend` / `hostile`), who holds a creature in stopped time
  (`SpaceTimeService.holder`), who has a power (`MimicryService.imprintOf`), is this power switched off
  (`Dampening.dampened`), does a suit protect (`Suits`), what can a power answer (`Danger.answers`). Add a branch to
  the existing question; never write a second copy ([kit-design.md](docs/agents/kit-design.md)).
- **Add a style, not a second system.** A new vortex is a style of the shared tornado; a new fighting style is one
  class on `FightingStyles`; a finisher is one object on `Takedowns`; a gifted mob uses `GiftedMobs.bestow`.
- **The server and the drawing share their numbers.** Positions, reaches and telegraph timings live in one geometry
  class per power (`PyroGeometry`, `ElectroGeometry`, `SylarGeometry`) that both the server and the art read, so a
  warning ring fills exactly as the hit lands and fire leaves the hand the player sees.
- **Every area hit is telegraphed** before it lands, and its spot is fixed when the windup starts, so moving beats it.
- **Held things tick on the server tick and keep their `ServerPlayer`,** not a UUID: game-test players and other mods'
  fake players are not ticked by the level and are not in the player list.
- **A held power pays with `drain`,** never `tryConsume`, or recovery stops for good (see below).
- **Clear synced state after a change, not before:** a sync sent while the power is still owned re-sends the old state
  ([awakenings.md](docs/agents/awakenings.md)).
- **Never `Minecraft.setCameraEntity(otherEntity)`:** the local player stops moving and reporting. Move the camera at the
  end of `Camera.setup` (`TempestCamera`) ([powers/flight.md](docs/agents/powers/flight.md)).
- **Fast moves are swept** (`SpeedService.swept`, which returns a position), and anything over 3.9 blocks a tick needs
  its own motion payload ([powers/flight.md](docs/agents/powers/flight.md)).
- **Keep action and node IDs when reworking; change only names** in `en_us.json`, so loadouts, balance, configs and
  poses still match ([powers/radiation.md](docs/agents/powers/radiation.md)).
- **When a new effect replaces an old one, remove the old `AbilityEffects.send`,** or both draw.
- **A set piece's `scale` is clamped to 0.05 to 64 on the client.** Flags packed into it must stay inside that; send
  anything bigger through synced entity data.
- **Fighting strikes never place blocks;** only a kit's casts light real fire or freeze terrain.
- **Posting a `BreakEvent` is not a free probe.** `WorldMutationPolicy.canEdit` posts one, and that records temporal
  echoes and mirrors breaks into snapshots. Post only at the real break; probe with `mayBuild`, `mayInteract` and the
  world border.
- **Player-caused damage obeys PvP.** A hazard (meltdown heat, friction) is nobody's blow, so it hurts friends too. Pick
  a hit's damage type on purpose: a landing that used `playerAttack` once counted as a punch for another power's
  passive ([powers/regeneration.md](docs/agents/powers/regeneration.md)).
- **Another mod is touched by one guarded class** (`TaczLink`, `RadioLink`, `MindVoice`; an optional mod gets one
  plugin package, `compat/jei`, `compat/jade`): every call in try/catch, a `broken` flag, and a fallback, so an update
  to that mod never crashes ours.
- **An entity removed by unloading is not dead.** `isRemoved()` is true for an entity whose chunk unloaded; ask
  `getRemovalReason().shouldDestroy()` before treating it as killed, keep an event's place loaded with
  `VillageTickets.hold` while it runs, and never read or write blocks where `hasChunkAt` is false
  ([village-events.md](docs/agents/village-events.md), "unloaded chunks").
- **Small Java traps that bit:** start "last time" fields at `-1000`, not `Long.MIN_VALUE` (the subtraction overflows);
  never change a set while walking it (walk `List.copyOf`); check `brain.hasMemoryValue` before `getMemory`; a helper
  named like a base-class method can silently call the base one (`pay` in `ComboStyle`).
- **Story text goes to chat, one line per beat.** No action-bar nags or on-screen captions; actors speak in chat.
- **Original work only.** Characters adapt the Heroes series, but lines, looks, numbers, emblems, music and sound are our
  own; never copy a real costume, song or texture.

## Stamina and balance

Every cost, cooldown, per-second drain and unlock tier is set in one place. The rules and the full table are in
[docs/stamina-balance.md](docs/stamina-balance.md).

- **Change numbers in `tools/balance.py`, never by hand.** It holds the rules (about 2 stamina per point of damage at
  the unlock tier, +40% per extra target, 3 per second of control, 0.35 per block, a band per key) and every core
  action's inputs. It writes `data/heroesevolved/power_balance.json`, the skill and class numbers in `skill_trees.json`
  (designed values kept as `design_energy`/`design_cooldown`), and the doc. `py -3 tools/balance.py --check` fails if
  any is stale. It reads each action's own cost from `build/rewind-gametest-world/build/balance/actions.json`, which
  `BalanceDumpGameTests` writes on every game-test run.
- **The table is absolute, scaled from each action's own base.** `PowerBalance.energy(id, configured)` returns the
  table's stamina times `configured / base_energy`, so another tier, mode or server config scales in proportion, and
  zero stays zero (free follow-ups, free returns). An entry with `energy: -1` passes the code's value through (Mimicry's
  Echoes already pay the copied move's balanced cost). The table can also set `unlock` (read by
  `AbilityActionDefinition.unlockLevel()`), `drain` and `per_block`, and fighting-style moves as
  `style_<power>_<move>` (`PowerBalance.styleEnergy`). A new core action must be added there, or
  `PowerBalanceGameTests` fails.
- **Stamina is fractional.** `PlayerEnergyService.getStamina` is exact; `getEnergy` rounds down for code that thinks in
  whole points. Recovery runs every tick (`PlayerEnergyService.tick`, from the player tick) and a small
  `StaminaPayload` carries the bar; the full power sync no longer goes out on every change.
- **Two ways to spend.** `tryConsume` / `consumeUpTo` are for moves: they pause recovery for
  `staminaRecoveryDelayTicks` (1.5 s). `drain(player, amount)` is for anything held (flights, toggles, beams, streams,
  a morph's upkeep): fractions are fine and it never restarts the pause. A held power must use `drain`, or flying
  would stop recovery for good.
- **Held for as long as it is paid for.** `energy/StaminaDrain.start(player, id, perSecond, holdsRecovery, onEmpty)`
  takes stamina every server tick and calls `onEmpty` when the bar can't pay; `stop` ends it quietly. Still World
  (`TimeStopService.hold`) and Between Seconds (`TimeSlowService.hold`) use it with `holdsRecovery` true, and end on a
  second press (`deactivateOnPress`). `start` still gives the old fixed-length field to scenes, intros and tests.
  Drains tick on the server tick and keep their `ServerPlayer`, so they run for game-test players.
- **Costs that depend on what a use will do.** `AbilityAction.travelCost(context)` adds stamina after the table (Fold
  Space: 0.3 a block to the destination its own `destination(...)` search finds, the same one the fold then uses). It
  is not scaled by the table again; skill and rank discounts still apply.
- **The HUD reads `ClientStamina`,** which runs the bar forward on the server's rates between packets and keeps a
  trailing "just spent" value. `PlayerEnergyService.refused(player)` flashes and shakes the bar; call it wherever a move
  is refused for lack of stamina (`AbilityUseService` and `ComboStyle.pay` do).
- **Practice discount.** `AbilityRanks.energy` takes 4% off per Power Level since an action unlocked (its
  `definition().unlockLevel()`, so skills and class abilities count from their node's level), capped at 30%, then
  Rank III's 10%. Activation and every HUD path go through it; held drains don't. Tests that check a charged cost
  must compute it with `AbilityRanks.energy`, not read the table.
- **Menus describe costs with `PowerBalance.describe(path, energy)`** ("20 + 12/s stamina"), so held and distance
  costs read the same on the wheel and the Powers page.
- **Photographs:** `tools/stamina-preview.init.gradle` (`uitest/StaminaHudPreview`, flag `heroes.preview.stamina`)
  shoots the bar full, just spent, recovering, during Still World, low and refused into `build/stamina-preview/`.
  A preview must call `PlayerPowerSyncService.sync` after granting a power, or the HUD doesn't know it has one.

## Finishing a piece of work

- **Keep player guidance current in the same change.** Every new or changed player-facing feature needs an entry or
  updated explanation in the in-game Guide/tip system: update the relevant Guide topic, ability card or item help
  and make sure the Guide points players to it. Explain hidden inputs, prerequisites and recovery from refusal.
  Add a one-time contextual note only when timing helps; use original civilian journals or dialogue for discoveries
  and boss counterplay without spoiling the story. Read [guidance-and-admin.md](docs/agents/guidance-and-admin.md).
- **Keep the admin console current in the same change.** New debug commands belong under `/heroesevolved` so the
  live Commands tab discovers them automatically. A new command root must also enter `AdminCommands` and its tests.
  New events, bosses, state or debug operations need appropriate admin controls, live status, target/scope labels,
  permission checks and cancellation/reset paths. Extend `AdminEvents` / `AdminService` / `AdminScreen`, rather than
  leaving a command-only feature behind. Test the server checks and click/photograph the actual menu controls.
- **Photograph it** if anyone will see it ([previews.md](docs/agents/previews.md)), look at every shot, and show the
  user. Run the focused tests, then the whole suite before calling it done.
- **Make it show right in JEI and Jade.** Anything new a player can hold, place, meet or craft (an item, block,
  entity, mob, machine, recipe) or that changes how something looks (a disguise, hiding, a stand-in body) goes through
  the checklist at the top of [compat.md](docs/agents/compat.md) in the same change: names, the creative tab, hiding
  what can't be had, info pages, recipe categories, Jade's hidden types, and the disguise questions Jade asks.
- **Update the player doc** (`docs/<topic>.md`): what the player sees, in plain words, with tables for moves.
- **Add what you learned to the agent notes for that area,** not to this file: a "Reusable patterns" bullet that says
  what failed, why, and the class to copy. Put a rule here only if it applies to every task, and keep this file under
  30 KB, because Codex stops reading at 32 KiB. A new notes file gets a row in the tables above.
