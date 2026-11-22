---
name: minecraft-bosses
description: Design and build mobs that fight with powers, mini-bosses, story bosses and world events for the Heroes Evolved mod, to the standard of L_Ender's Cataclysm and Apotheosis - arenas, readable telegraphs, phases that change the room, weak windows, answers from other powers, anti-cheese rules (damage caps, range falloff, idle healing, leashes), party scaling, minions, boss music, death scenes, rewards that unlock the next thing, and re-fights. Use this whenever the task involves a boss, mini-boss, gifted or powered mob, villain, enemy with a power, raid champion, arena fight, boss bar, boss music, boss drop, or changing Sylar, renegades or gifted mobs, even if the user just says "make a villain", "add a fight", "make this enemy harder" or "add another boss like Sylar".
---

# Bosses and powered mobs for Heroes Evolved

A boss here is a set piece the player remembers: a place built for the fight, one idea that every attack speaks in, a
telegraph for every hit, phases that change the room, a way to turn the fight around, and a reward that opens the next
thing. The bar is [L_Ender's Cataclysm](references/boss-mods.md) for the fight itself and
[Apotheosis](references/boss-mods.md#apotheosis) for mini-bosses that turn up anywhere. Our own Sylar (his Collection) is the
worked example: read [references/built-bosses.md](references/built-bosses.md) before writing any boss, and
[references/gifted-mobs.md](references/gifted-mobs.md) before any powered mob.

Read these too:

- [docs/animation-guide.md](../../../docs/animation-guide.md) before drawing any telegraph, set piece or pose.
- The building skill ([../minecraft-building/SKILL.md](../minecraft-building/SKILL.md)) before building an arena.
- [docs/agents/testing.md](../../../docs/agents/testing.md) and [docs/agents/previews.md](../../../docs/agents/previews.md)
  before testing or photographing one.
- What the player sees of the existing ones: [docs/sylar.md](../../../docs/sylar.md),
  [docs/gifted-mobs.md](../../../docs/gifted-mobs.md),
  [docs/company-agents.md](../../../docs/company-agents.md).

## Decisions the user has made

These are settled (November 2026). Don't reopen them; ask only about what they leave open.

- **Story first, then a re-fight.** A boss's first fight is a story beat. After it is beaten, an item made from its drop
  lets players fight it again in its arena, and its first-kill drop helps unlock the next boss or area (Cataclysm's
  eyes and altars).
- **Every boss has its own theme music**, original and synthesized, changing at each phase, made from the user's
  reference library (see "Music" below).
- **Every new boss has, by default:** a per-hit and per-second damage cap with falloff for distant attackers, healing
  and a walk home when nobody fights it, health that scales with the number of players, and a death scene before the
  drop. Sylar's Collection (November 2026) has all of them; copy it.

## Two layers

- **Mini-bosses live in the world (Apotheosis).** A small chance turns an ordinary mob into a named, graded one where the
  player already is: a raid's wave leader, a patrol's leader, a natural spawn, a villager whose gift wakes. No structure
  or summoning item. Use `gifted/GiftedMobs.bestow` for these; never write a new entity. Only villagers and illagers can
  carry a power (`GiftedMobs.mayCarry`): powers belong to people.
- **Bosses are set pieces (Cataclysm).** A place built for the fight, a way in the player chooses or the story brings,
  phases that change the room, and a reward that gates something. One entity class per boss
  (`story/sylar/SylarEntity`), one arena class (`CollectionArena`), one moves class (`SylarMoves`), one geometry class
  shared with the art (`SylarGeometry`), one art class (`SylarArt`), one voice class (`SylarVoice`), one saved-data class
  (`SylarData`), one music class (`client/sound/SylarMusic`).
- **Minions sit between them.** Guards cleared before a boss, followers that answer a phase change
  (`GiftedMob.follower`, tagged `GiftedMobs.FOLLOWER` so they never roll gifts of their own).

## Mini-bosses: graded, named, worth beating (Apotheosis)

The layer is built (`gifted/`); what the player sees is in [docs/gifted-mobs.md](../../../docs/gifted-mobs.md) and the
build notes are in [references/gifted-mobs.md](references/gifted-mobs.md). Extend it rather than adding another.

- **A rarity on every powered mob.** `gifted/Rarity`: Uncommon, Rare, Epic, Mythic, each adding health, damage, faster
  casting, knockback resistance, armor, more moves, and from Epic a boss bar, a phase change and followers. The name is
  in the rarity's colour. Roll with a floor for the context (`Rarity.roll(random, floor)`: the last raid wave is Epic+).
- **A generated name that says what it is.** `GiftNames.roll(power, random)`: "Hollis the Ashen", "Vesna Stormcaller".
  Hostile ones wear it overhead (`setCustomNameVisible`); friendly ones show it when looked at.
- **Announce it.** "has awoken nearby!" to players within 140 blocks, and glow for a minute so it can be found
  (`GiftedMobs.announce`). Keep chat quiet: wild ones only from Rare up.
- **Toughen with permanent, named attribute modifiers** (`heroesevolved:gifted_*`, `addOrReplacePermanentModifier`), so
  they save with the mob, re-applying is harmless and a cleanse removes exactly them. Persistence required: a named mob
  must not despawn.
- **A guaranteed prize.** Its own weapon, named for it and enchanted by rarity (set the slot's drop chance to 0 and drop
  the prize yourself), emeralds, experience and Mastery for the killer (`GiftedMobs.reward`). A boss gets the boss
  Mastery reward (at least 120, or four times its toughness reward, before the power bonus) only if
  `MasteryService.boss` knows it: add a story boss to `data/c/tags/entity_type/bosses.json`, or give a gifted one a
  boss-rarity `Gift`. Its minions and summons (`MOB_SUMMONED`, `REINFORCEMENT`, followers) pay nothing, so a boss
  can't be kept alive as a Mastery farm (`MasteryService.rewardable`).
- **Every move is read.** A ring at the feet fills over the windup (`GiftArt.cast`), the hands glow where the move leaves
  them, illagers raise their arms (`mixin/IllagerCastPoseMixin`). Marked moves (thunderstrike, firestorm, geyser,
  frequency lock) fix their spot when the windup starts (`GiftedMob.mark`).
- **Weak windows.** A heavy blow during a windup breaks it and staggers (`GiftedMob.struck`); after a signature the mob
  is spent and takes half again (`GiftedMob.spent`); an Ability Dampener switches its power off (`Dampening.dampened`).
- **A phase at half health.** An Epic or Mythic mob goes unbound: a set piece (`GIFT_UNBOUND`), a raid horn and a roar, a
  line to everyone near, effects cleared, a short resistance, faster casting, its signature and followers.
- **Its own anti-cheese.** Only players really hurt a mini-boss: anything else (golems, wolves, lava, falls, dripstone)
  chips at most 3 a blow (`GiftedMobs.incoming`; `/kill` still works). Renegades walk home to their bell with no one to
  fight, and every gifted mob heals out of combat (`GiftedMob.passives`/`idle`). The full boss rules below are for
  bosses; give a mini-boss only the ones its fight needs.
- **Its own side.** One rule decides whom a powered mob hurts (`MobMoves.foe`): evil hurts players, villagers, golems,
  pets and good gifts, never another monster; good (a guardian) hurts monsters and evil gifts, never people. A new
  faction is a branch there.
- **Powers as moves, not player actions.** Player `AbilityAction`s need a `ServerPlayer`. Mobs use `MobMoves.Move`
  records (windup, cooldown, range, sight, telegraph/pulse/execute) built on the power's own effects (`AbilityEffects`
  kinds, set pieces such as `HYDRO_WHIP`, `VIBE_LOCK`, `SHIFT_LUNGE`) and services that take any `LivingEntity`
  (`CryoService.freezeSolid`, `DensityService.embed`, `HydroService.soak`, `RadiationService.irradiate`). Every power
  has two moves and a signature; Empathic Mimicry borrows its foe's (`GiftedMob.copy`). A boss's own moves are written
  the same way (`SylarMoves.Move`: windup, cooldown, the case it draws on, start, pulse, land, cancel).

## The workflow

Work in this order, and write the design down before the code. [references/boss-brief.md](references/boss-brief.md)
is the template; the finished brief becomes the boss's player doc, `docs/<boss>.md`.

1. **The idea.** Who they are (adapted from the Heroes series, but every line, look and number our own), what they want
   from the player, and **one idea every attack speaks in**: Sylar's is "he sees how it works" (every telegraph is a
   brass schematic whose parts slide together and seat as the move lands; the retired Watchmaker's was a clock running
   down, and Elle's "current crawls before it bites", a thin jittering filament
   that tightens and brightens, then lands as the same shape, thick and white). Pick the boss's palette with it.
2. **How the story brings them.** Foreshadowing first, over days of play if you can (Sylar is a prisoner in Level 5
   while the names on his list are crossed out one a night, until his cell stands open and he comes up behind you),
   then the pull into the arena. Anyone within 16 blocks of the player comes too.
3. **The arena** (below): a pocket dimension rebuilt every fight, whose features are the counterplay.
4. **Moves, phases, weak windows and answers** (below), as tables.
5. **Fight rules, scaling, minions** (below).
6. **Ending, reward, re-fight, music, death scene** (below).
7. **Build**, then **game tests** for every rule and **a preview** that photographs every telegraph and phase.
8. **Docs**: `docs/<boss>.md`, a row in AGENTS.md's tables, and lessons in
   [references/built-bosses.md](references/built-bosses.md).

## The arena

- **A pocket dimension rebuilt from a blueprint before every fight**, so whatever the last fight broke is whole again
  (`CollectionArena.open`). Save each player's way home first (in saved data, so a restart still sends them back),
  move them in, spawn a fresh boss there
  (a mob changing dimension becomes a new entity), and send everyone back exactly where they stood when it ends.
- **Register the dimension** in `data/heroesevolved/dimension` and in the game-test world preset
  (`src/gametest/resources/data/minecraft/worldgen/world_preset/flat.json`), or tests get null.
- **Keep its chunks force-loaded once built.** A freshly loaded pocket chunk takes about three seconds before its
  entities tick, and players standing in it are invisible to `getEntitiesOfClass` meanwhile.
- **Moving players in and out must not end the encounter**: mark the fight's own teleports
  (`CollectionArena.moving(player)`) so dimension-change handlers skip them.
- **The room is the counterplay.** Things to break (Sylar's cases: the one he draws from backfires, the one holding your
  power gives it back), cover to break line of sight (the pillars of his last phase, the rubble of his own throws),
  floor that changes (ice that stays slick), machines to switch off (Elle's breaker banks, retired), places
  that change (the clocks speed up, the lights go out). Design the arena with the moves, not before or after.
- **One geometry class holds every position** the fight and the drawing use, and the blueprint's markers are checked
  against it by a game test (`SylarGeometry` and the `@case:N`, `@cover:N`, `@spawn:*` markers).
- **Poll the arena's switches; don't listen for clicks.** Read the room's state each tick (`SylarEntity.pollCases`),
  so a lever pulled by hand, a broken lever, or a player's EMP over a bank all count the same.
- **Size.** Our arenas are 19 to 25 blocks across and 9 or more high: room to dodge a 3.5-block slam, small enough that
  every move reaches. A boss that flies or charges needs more.

## Moves and telegraphs

Four to six moves in the first phase, one or two more each phase, a signature, and in the last phase one move that
covers the whole arena with a safe spot to find. **Every move is read before it lands**: a pose, a marker on the ground
or in the air in the boss's palette, and a sound when the windup starts, then the move. Never rely on the body's
animation alone: players fight in first person and from behind cover.

| Kind of move | Windup | Ours | Cataclysm |
| --- | --- | --- | --- |
| Quick, at one player | 0.3 to 0.8 s | Sylar's Reel In 0.6 s, Appraisal 0.8 s, Your Own 0.8 s | Remnant's bite (the head drops) |
| Area at a spot | 1.0 to 1.5 s | Sylar's Hurl 1.2 s, Icefall 1.4 s, Resonance 1.0 s | Monstrosity's ground smash (both fists lift) |
| A line or lane | 0.6 to 1.5 s | Sylar's Arc Lattice 1.2 s, Fault Line 1.0 s, Blur 1.2 s, the Second Hand 1.5 s | Maledictus's halberds (marks on the floor) |
| Whole arena, with a safe spot | 1.8 to 4 s | Sylar's Every Hour 3 s (stand in a gold hour), the Light at 6:14 3 s (hide behind a pillar) | Ignis's ultimate (about 4 s), Harbinger's Death Laser (hide behind a wall) |
| Something the player must stop | 3 s | Sylar's Appraisal hold (struggle, or a friend's charged hit); Claire's case while he is down | Leviathan's rush (hit it during the roar) |

- **The telegraph fills as the move arrives.** A hand reaching twelve, a ring tightening: the windup is exactly the
  marker's length, and both read the same constant (`ElectroGeometry.STRIKE_DELAY`, each move's `ticks`).
- **Fix the spot when the windup starts** (`GiftedMob.mark`), so moving beats it. Store anything a move needs later in
  its own field: Sylar's next move's `mark()` once overwrote the rings' centre and dragged them across the room.
- **No fists during a windup.** Clear the melee target while casting, or `MeleeAttackGoal` keeps punching through the
  telegraph.
- **A rotation, not a dice roll.** Never the same move twice in a row; the rest between moves shrinks each phase; a
  global hold-off that a move's end can only lengthen (`Math.max`), so tests' and previews' hold-offs survive.
- **Area moves that aim at "you" aim at each fighter** (Sylar's Icefall rings everyone; Arc Lattice strings a line through each), so a party
  can't stand behind one player.
- **Stop pillaring.** Cataclysm's Ender Guardian pulls down anyone three blocks above it. Have at least one move that
  reaches a player who climbs out of reach (Sylar's Reel In; his Resonance climbs the walls).

## Phases

- **Two or three phases at set health** (constants such as `SylarEntity.PHASE_TWO`), plus an optional end beat (Sylar
  runs at a tenth). Cataclysm uses a half for two phases, thirds for three.
- **The player must see each change.** Every phase changes at least three of: the boss's look (Ignis's flames turn
  blue), the room (the doors bar, the lights go out), the boss bar's colour and name, the music, the move list, and
  the counterplay (Cataclysm's Ender Guardian loses its helmet, so arrows work from then on).
- **A short, readable transition.** A shove that throws everyone back, a set piece, a line of voice, then the new
  phase. Keep it to one to three seconds of invulnerability; Cataclysm's longest (ten seconds) feels like a cutscene.
- **Finishers at phase thresholds:** a boss glistens just above its thresholds instead of at low health, and a
  finisher there forces the phase change with a long stagger, or kills in its last tenth. Implement
  `Takedowns.PhaseFinishable` as Sylar does ([docs/finishers.md](../../../docs/finishers.md), "Bosses").

## Weak windows and answers

Every boss has all four kinds of window. Cataclysm's best fights are remembered for these, not for their health.

| Window | Ours | Elsewhere |
| --- | --- | --- |
| **Skill**: interrupt a windup | Sylar's loupe glints while he winds up; a charged hit breaks his concentration: the move is gone and he reels (`SylarEntity.breakConcentration`) | Mowzie's Ferrous Wroughtnaut can only be hurt from behind after its overhead slam sticks its axe in the floor |
| **Rhythm**: spent after a big move | After a signature a gifted mob is `GiftedMob.spent` and takes half again | Cataclysm's Netherite Monstrosity is open for two seconds while it stands; the Leviathan stuns itself if hit during its rush windup |
| **Room**: use the arena | Break the case Sylar is drawing from: a backfire (a tenth of his health through the caps, a three-second stagger), and that power is gone | Cataclysm's Harbinger is stunned only by the factory's EMPs; Ignis's shield breaks when you return three of its fireballs |
| **Power**: an answer from another power | An Ability Dampener switches a gifted mob's power off (`Dampening.dampened`); Blackout or an EMP over a bank cuts it | Cataclysm's Harbinger and EMPs |

- **A stagger is a reward:** the boss reels, its windup and any hold end, and every hit does more for a few seconds.
- **Read the swing on every hit.** A charged hit is a full attack meter read in `AttackEntityEvent` before the swing
  resets it, a critical arrow, or a single hit of 8 or more. Reset the record on every hit, or one swing carries over.
- **Decide what each power does to this boss**, and write it in the boss's doc as a table. A few powers get a special
  answer; most work at boss strength; none should win the fight alone. What "boss strength" means today, all keyed
  off the common `c:bosses` entity tag (`Tags.EntityTypes.BOSSES`):

| Power | Against a boss |
| --- | --- |
| Cryokinesis | chills and slows, never freezes solid (`CryoService`) |
| Density | can't be embedded (`DensityService`) |
| Carrying | can't be lifted (`EntityCarryService`) |
| Singularity | feels 15% of the pull (`SingularityService`) |
| Vibe's Rift Fall | slammed where it stands, never dropped through a breach (`RiftFall`) |
| Telepathy's Drop It | disarmed for one second, keeps its weapon (`TelepathySkills.boss`) |
| Finishers | can't be finished (`heroesevolved:unfinishable` includes `#c:bosses`) |
| Shapeshifting | its shape needs Evolved, and gives looks, never powers |

- **Put every boss in `c:bosses`** (`data/c/tags/entity_type/bosses.json`) so all of these apply at once, and ask the
  tag, not `instanceof`. Sylar is in it (`data/c/tags/entity_type/bosses.json`); Telepathy's Drop It asks the tag.

## Fight rules (on by default)

Starting values, taken from Cataclysm's eight bosses and scaled to ours. Tune each boss by playing it; write the final
numbers as constants at the top of its entity class and in its doc.

| Rule | Cataclysm | Our default |
| --- | --- | --- |
| **Per-hit cap** | 20 to 25 a hit on 333 to 600 health (about 5% of max) | no hit above 6% of max health (at least 10) |
| **Per-second cap** | 13 to 20 a second (about 3.5% of max) | no more than 4% of max health in any 20 ticks |
| **Range falloff** | full damage to 12 to 38 blocks, none past one and a half times that | full within 16 blocks of the attacker, falling to none at 24 (scale up for open-world fights) |
| **Idle healing** | 25 health a second with no target, paused 4 to 30 s by a melee hit | 5% of max a second once nobody has hurt it for 10 s and it has no target |
| **Leash** | home after 20 s with no target and more than 16 blocks away | the same; walk or blink back to the arena's middle or its spawn |
| **Party scaling** | none | max health × (1 + 0.5 × (fighters − 1)), up to × 3 |
| **Death scene** | the Harbinger overloads and explodes | two to four seconds, then the drop (below) |

- **Where:** in the boss's `hurt` override, after its own reductions, before `super.hurt` (`SylarEntity.hurt`). Keep a short
  ring of recent damage for the per-second cap.
- **Designed counterplay is exempt.** Sylar's backfire, Claire's case and a phase finisher are meant to take a
  big bite; pass them through a flag. `BYPASSES_INVULNERABILITY` (`/kill`) is always exempt.
- **Range falloff measures the attacker,** `source.getEntity()`, not the projectile or beam, at the moment of the hit.
- **Scaling counts fighters when the arena closes** (everyone pulled in), raises max health if someone joins later
  (keep the health fraction, as Shapeshifting keeps it across forms), and never lowers it mid-fight. Damage per hit
  doesn't scale; the caps do, because they are fractions of max health. Add one follower for every two extra fighters.
- **Anti-cheese beyond the caps:** only players' blows hurt it at full strength (`GiftedMobs.incoming` caps anything
  else, golems, wolves, lava, falls and dripstone, at 3 a blow); immune to falling, drowning, suffocation and being stuck
  in a wall; a boss stuck for a second breaks out of the blocks round it (the arena is rebuilt, so it may); ranged
  attacks can be a phase's choice (Cataclysm's Remnant is hit by arrows only for six seconds after each charge) rather
  than always on or always off. Pick what the fight needs; don't stack every rule on a mini-boss.

## Minions

- **Guards before the boss** (Cataclysm's Ignited Revenants drop the item that summons Ignis): a reason to explore the
  place, and a warm-up in the boss's language.
- **Followers on a phase change** (`GiftedMob.follower`): a different job from the boss (a healer, a blocker, a
  flanker), a readable look, a small number, and they never respawn on their own.
- **Hazards are minions too:** Cataclysm's Leviathan fills the water with mines and orbs. Something on the floor to
  avoid is often better than another body to hit.

## How it ends

- **Every boss has a table of outcomes** (see Sylar's doc): beaten, everyone down, time up, and a flight if
  the boss can run. Each sets the story's next step and when the boss can come again.
- **A death scene before the drop.** Two to four seconds: the boss is invulnerable and its AI is off, the room reacts
  in the boss's idea (everything Sylar held streams out of him and his cases shatter), the music ends on its last chord, then the drop
  lands where the boss fell. Record who struck the killing blow first. Nothing (a key release, a logout, a second blow)
  may end the scene early or leave the boss invulnerable; copy "Hold a death back for its animation" in
  [power-interactions.md](../../../docs/agents/power-interactions.md).
- **Close the arena a few seconds later** (`CollectionArena.close(server, delay)`), give back everything the fight took
  (a stolen power, a held player), and send everyone home together.

## Rewards and re-fights

- **The first kill drops something unique** in the world, named for the boss: Sylar's Watch (go back five seconds, once a
  life) and Gray's Ledger (his catalogue, written to the story's outcomes). Make the item use the boss's idea.
- **The first kill also opens the next thing**, the way Cataclysm's boss materials craft the eye that finds the next
  structure: a Journal lead to the next boss or place, a material the next piece of gear needs, a key. A boss that only
  drops loot is a missed beat. Write what it opens in the brief.
- **Re-fights.** From the drop and other materials, craft an item that opens the boss's arena again (Cataclysm: Burning
  Ashes on the Altar of Fire, the Boss Respawner, a thrown eye). A re-fight runs the same arena, phases and rules, but
  not the story: no foreshadowing, no story outcome, no second unique item for the same player. It drops the boss's
  material and a small chance (Cataclysm: 10%) of something rare, such as a record of its theme. Sylar's re-fight is a
  Repair Ticket crafted from his Brass Movement; he doesn't
  have re-fight items yet.

## Music

- **A theme per boss, original and synthesized**, made from the user's reference library in
  `C:\Users\jaypa\OneDrive\Desktop\Gemini Heroes Reborn Audio` (the *Gemini: Heroes Reborn* score and ability sounds;
  what each folder is for is in [docs/agents/audio.md](../../../docs/agents/audio.md#reference-audio-the-users-library)).
  Its `Music/Gameplay Score` pieces each come in Exploration, Combat and Stealth states built from shared stems: the
  model for a boss's phase loops. `Music/Stingers and Transitions` is the model for phase changes, `Music/Short Music
  Cues` for the death scene's last chord. The reference sets mood, instruments, tempo and structure; never copy a melody
  or put any of its audio in the mod. The mod's own tracks set its sound: `assets/heroesevolved/sounds/music/main_menu.ogg`,
  `main_menu_intro.ogg` and `new_beginning.ogg`.
- **One loop per phase**, cut so they share a tempo and key; change on the next bar at a phase change, with a short
  stinger. The death scene ends the music on a last chord, then silence.
- **Play it as music.** Use NeoForge's `SelectMusicEvent` to make the boss theme the current music while the player is in
  the arena, as `client/sound/TitleMusic` does for the title screen: it replaces vanilla music and follows the player's
  music slider. Only fighters in the arena hear it.
- **Make it** with the audio tools in [docs/agents/audio.md](../../../docs/agents/audio.md) (OGG loops through
  soundfile), or as a live `client/sound/PcmStream` (as `CitySiren` and `BunkerAlarm` are) if it must react every beat.
  Check the loops with numbers and a spectrogram before wiring them in, and keep peaks under 0.8.
- **Previews are silent.** Listen to the files themselves; a preview plays them only with its own sound option.

## Voice, bar and feel

- **A boss bar** (`ServerBossEvent`, `NOTCHED_10`) for everyone within 48 blocks, its colour and name changing each
  phase. A second resource (Cataclysm's rage meter, Sylar's standing cases) can ride in the bar's name ("9 cases").
- **A paced voice.** Everything the boss says and every line about them goes through one queue per player, at least eight
  seconds apart, narration giving way to speech (`SylarVoice`). Warnings the player must act on now
  ("She's drinking bank 3. Pull its breaker!") are the one thing that goes to the action bar.
- **A line for every power** the player could have, and for every phase, steal, recovery, flight and fall.
- **Feel it.** `CameraShakePayload.sendAround` on slams and phase changes, a fixed-range sound for anything heard across
  the arena, and the boss's own palette drawn as geometry, not particles ([animation-guide.md](../../../docs/animation-guide.md)).

## Saving and safety

- **State lives on the entity and in saved data**: a mini-boss's gift and siege in its persistent data; a boss's story
  progress in saved data per player (`SylarStoryData`, `CompanyVisitData`). A restart mid-fight closes the arena and
  sends everyone home; it never leaves a player in a pocket dimension or without a power the fight took.
- **Taking a power for a fight** is one check (`CollectionArena.stolen(player)` in `PowerRules.canActWithoutPreservation`)
  that the arena's close clears, so the power always comes back.
- **A scene stand-in** is the same entity, not fighting: Sylar is the story's actor on the avenue, on Night Three, in his
  cell in Level 5 and behind you (`SylarEntity.actor(true)`), with no bar, invulnerable, discarded when the player is
  taken.

## Checklist

- [ ] A brief from [references/boss-brief.md](references/boss-brief.md): the idea, the palette, the story's way in.
- [ ] Graded (`Rarity`) for a mini-boss, or a deliberate one-off boss; named; announced.
- [ ] An arena rebuilt every fight, its dimension registered for tests, its chunks force-loaded, its features the
      counterplay, its positions in one geometry class checked against the blueprint.
- [ ] Every move telegraphed with a pose, a marker and a sound; the marker shares the server's numbers; spots fixed at
      the windup's start; area moves aim at every fighter; something reaches a player who pillars.
- [ ] Phases that change at least three visible things, with a short transition.
- [ ] All four weak windows; a table of what each power does; the boss in `c:bosses`.
- [ ] The default fight rules: per-hit and per-second caps, range falloff, idle healing, a leash, party scaling.
- [ ] Minions with jobs, scaled to the party.
- [ ] A table of outcomes; a death scene; everything taken given back; everyone sent home.
- [ ] A unique first-kill drop that uses the boss's idea and opens the next thing; a re-fight item.
- [ ] A theme in phase loops, made from the user's references.
- [ ] A boss bar, a paced voice, lines for every power and phase.
- [ ] Saved state that survives an unloaded chunk and a restart.
- [ ] Can't be cheesed by the obvious trap (lava, a pillar, a golem farm, a doorway it can't fit through).
- [ ] Game tests for every rule (`SylarCollectionGameTests`, `GiftedMobGameTests` show how), run in their own
      batch, cleaning up their players on failure ([testing.md](../../../docs/agents/testing.md)).
- [ ] A preview that photographs every telegraph and every phase (`tools/sylar-preview.init.gradle`), and the shots
      shown to the user.
- [ ] Right in JEI and Jade ([compat.md](../../../docs/agents/compat.md)'s checklist): a real name, its drops with info
      pages, effect entities hidden from Jade, and any disguise or vanishing answered by the questions Jade asks.
- [ ] `docs/<boss>.md`, the AGENTS.md row, and the lessons added to [references/built-bosses.md](references/built-bosses.md).
