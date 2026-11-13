# Sylar: the watchmaker's hunger

Character sources: [Gabriel Gray](https://heroes.fandom.com/wiki/Gabriel_Gray),
[Intuitive Aptitude](https://heroes.fandom.com/wiki/Intuitive_Aptitude), and
[Telekinesis](https://heroes.fandom.com/wiki/Telekinesis). This adapts the early
antagonist: a patient watchmaker with intuitive aptitude, a hunger for abilities,
and acquired telekinesis. He studies people before attacking. The watch cue,
dialogue, appearance, timers and encounters are original game adaptations.

## The story, one beat at a time

[The Last Signal](the-past.md) brings Gray to the relay as Mara and Ivo wake. After the rescue, his stalking and hunts in the Overworld follow their own progression.

Sylar is a slow ladder per player. Each beat waits for the last, so the story never piles up:

| Chapter | Beat | Next beat after |
| --- | --- | --- |
| 0 → 1 | **The ticking.** A private line and a faint watch, five minutes or more after you awaken. | 10 to 14 minutes |
| 1 → 2 | **A man watching** from 40 to 64 blocks away. He withdraws if you approach or stare back. | 10 to 14 minutes |
| 2 → 3 | **He speaks.** The same distant sighting, but this time he says something. The Journal names him: Gabriel Gray, who calls himself Sylar. | 25 to 30 minutes |
| 3 → 4 | **The hunt.** He watches for six seconds, then attacks. | 25 to 30 minutes |
| 4 → 5 | **Nemesis.** After three fights he opens by remembering how you fight. | 25 to 30 minutes |

A player who keeps using their powers draws him up to a fifth sooner. The pauses come from
`sightingIntervalSeconds` (default 600) and `huntIntervalSeconds` (default 1500) in the `sylar`
config section, plus up to four or five minutes of random delay.

How a hunt ends sets what happens next:

| Outcome | What happens |
| --- | --- |
| **You get away** (beyond 112 blocks, or survive 150 seconds; in his shop, walk out the open front door or outlast ten minutes) | Counts as a hunt survived. Next time he says so. |
| **He flees** (in his shop: he reaches the end of the alley) | No reprieve. He comes back sooner, and he remembers who did it. |
| **You defeat him** | The whole world gets an **hour** of quiet, and you get his watch. |

**His voice is paced.** Everything he says, and every line of narration about him, goes through
one queue per player, at least eight seconds apart; narration gives way to speech when it fills up.
Warnings you must act on right now go straight to the action bar: the hold, the pin, and his escape.

He has a line for every power: "Lightning in your hands. I could feel it from across the field." He
also speaks when the fight changes phase, when he uses your own power against you, when he takes a
power, when he gets back up, when he flees, and when he falls.

## The fight: the Watchmaker

When a hunt turns into a fight, he takes it home. His hand closes on the air, a clock face opens behind
you, and you are standing in **Gray & Sons**, his clock shop. Anyone fighting beside you (within 16 blocks)
comes too. When the fight ends, the shop folds away and everyone is put back exactly where they stood. Any power he
took comes back at the same moment.

### The shop

The shop is a pocket dimension of its own (`heroesevolved:watchmaker_shop`), rebuilt from the `sylar_shop` blueprint
before every fight, so whatever the last fight broke is whole again:

- **The floor is a clock face**: a pale dial nineteen blocks across, ringed in copper, with twelve hour marks.
- **Two copper gear rails** cross the floor east to west, each between two geared posts with a lightning rod on top.
- **Glass display cases** line both side walls, with a clock over every other one. His slam shatters them.
- **The great clock** hangs over the north wall, its pendulum swinging in a case beneath. Its hands run faster with
  every phase.
- **Four columns** hold up the ceiling. They are the only cover in the room.
- **The front door** opens onto a dark street. The **boarded back door** opens onto an alley.

A **boss bar** shows his health to everyone within 48 blocks: 80, plus 12 for every power he has taken (up to 240).

### Three phases

| Phase | Health | What changes |
| --- | --- | --- |
| **Taking Inventory** (purple) | 100–60% | Stolen-power attacks from people he has hunted, each telegraphed. **The front door stands open**: walk out into the street and you get away (it counts as escaping the hunt). |
| **The Hunger** (red) | 60–30% | **The doors slam and bar.** Every clock in the shop runs four times fast, he moves a fifth faster and rests less between moves. He starts **lunging to grab you**. |
| **Clockwork** (white) | 30–0% | The clock face lights violet: **rings of stopped time** close in, and **only the gap in the clock face is safe**. The clocks run a dozen times fast. |
| **Running** | 10% | He runs: the back door bursts and he makes for the end of the alley. **Catch him** before he reaches it, or he gets away (he "flees": no reprieve, and he remembers you). |

Each phase change throws everyone near him back with a telekinetic shove.

Outside his shop (a fight that never got there), the old rules hold: the same phase thresholds, and at Clockwork
he tries to run if he knows an escape power (Invisibility, Density, Superspeed, Time or Flight).

### Every telegraph is a clock running down

Each of his shop moves draws a **brass dial** before it lands. A hand sweeps round from twelve, the part it has
passed fills with the stolen power's colour, and the move lands **when the hand reaches twelve**. The windup is
exactly the dial's length. While he winds up he stands still and throws no punches.

| Move | Phase | Telegraph | What it does | How to answer it |
| --- | --- | --- | --- | --- |
| **Floor slam** (telekinesis) | all | 1.5 s: a lavender dial round where you stood, cracks spreading from its middle | Within 3.5 blocks: 7 damage and you're thrown up. The boards tear out of the floor and nearby glass cases shatter | Step out of the dial |
| **Ice wall** (cryokinesis) | all | 1.5 s: a frost line from wall to wall through you, square to him, its ticks lighting from both ends to the middle | Anyone on the line: 6 damage, slowed, frozen and shoved to the far side. A wall of packed ice three high stands for eight seconds, cutting the shop in two | Step off the line, and pick your side |
| **Rail lightning** (electrokinesis) | all | 1.2 s: light creeps along the gear rail nearest you from both posts; little dials over the posts count down | Everyone within 1.8 blocks of that rail: 8 damage and slowed | Get off the rail |
| **The grab** (his hunger) | Hunger on | 0.8 s: a red lane on the floor from his feet toward you, its chevrons lighting | He lunges down the lane (up to 10 blocks). Whoever he reaches first is **held** | Leave the lane. A miss makes him stumble for a second |
| **Rings of stopped time** | Clockwork | 1.8 s: the clock face lights violet, except a two-hour **gap** blazing gilt; the ring gathers beyond the walls | A fence of stopped time sweeps in from the walls to the middle over 1.6 s, open only at the gap. Caught: 7 damage and **held still for two seconds** | Stand in the gap |

He still uses everything he has taken from players (the table below), mixed in with these.

### The grab and breaking free

Held in his grip, you have **three seconds**. Red bands close round you, and a gilt arc round your chest fills as you
struggle. **Sneak again and again**, or **hit him** (your hits and your friends' count half their damage). Six points
tears you free, and he reels for a second and a half.

Too slow, and **he takes your power until the fight ends**. You can't use it, and he can: its moves join his. He
also learns it, the way his old acquisition channel did, which counts toward his health in later hunts. It is a copy:
your power is still yours, and comes back the moment the fight ends, however it ends. (In his shop the grab replaces
the slow six-second channel.)

### The counterplay: crack his watch

His **watch shows on his left wrist** whenever he winds up a move or holds someone. It is a small brass dial with a
pulsing gilt glint you can see across the shop. A **charged hit** while it shows **cracks it**:

- a charged hit is a fully charged melee swing (the attack meter full), a critical arrow, or any single hit of 8 or more;
- the glass bursts, gears and a spring tumble out, and **he staggers for three seconds**;
- whatever he was winding up is gone, anyone he held is free, and **every hit on him does a quarter more** while he
  reels.

An ordinary hit of 5 or more during a windup still breaks it, without the stagger.

### What he leaves: Sylar's Watch

Whoever finishes him (or, failing that, the one he hunted) gets **Sylar's Watch**. Its glass is cracked and it is
still ticking. Use it to **go back five seconds**: you are carried back along your own path, as Rewrite the Moment
shows it, and arrive with the health you had then (and no fire). It works **once a life**: it winds again only after
you die. While you carry it, it remembers the last five seconds of you.

### What he does with what he takes

Every move has a **windup you can read**: a pose (one hand, both hands, arms raised, crouched), a
telegraph in that power's colours, and a sound. A hit of 5 or more during the windup breaks it.

| Power | Move | Windup | What it does |
| --- | --- | --- | --- |
| Telekinesis (always) | Throw | 0.7 s | Lifts and flings you |
| Telekinesis (always) | **Pin** | 0.5 s | Holds you just off the ground for 2.5 s while he walks up. Hit him to break it. |
| Electrokinesis | Chain Lightning | 0.6 s | Bolt from his hand, leaping to two more |
| Electrokinesis | Thunderstrike | 1.5 s | Lands where you stood when he raised his hand; step out of the mark |
| Cryokinesis | Ice Lance | 0.5 s | Freezes you briefly |
| Cryokinesis | Cold Snap | 0.8 s | Everything around him slowed and frosted |
| Pyrokinesis | Fire Stream | 0.5 s | A cone of flame |
| Radiation | Radiant Heat | 0.9 s | A cone of heat that withers and burns |
| Quake | Ground Pound | 1.0 s | A shockwave ring that throws everyone up |
| Quake | Shockwave | 0.5 s | Daisy's palm blast: rings of pressure, and you are thrown back hard |
| Superspeed | Blitz | 0.3 s | Rushes you with a flurry |
| Time Manipulation | Fold | 0.5 s | Appears behind you |
| Time Manipulation | Slow | 0.6 s | Slows you heavily |
| Sonic Resonance | Scream | 0.5 s | A cone that shoves |
| Flight | Dive | 1.0 s | Rises, then slams down on you |
| Telepathy | Dominate | 0.7 s | Darkness and nausea; nearby monsters turn on you |
| Invisibility | Vanish | 2.0 s | Gone, then a strike from behind |
| Density (Phasing) | Reach Through | 0.4 s | A hand through your armour; you can't heal for 4 seconds |
| Density (Phasing) | Embed | 0.8 s | The floor swallows you to the waist for 2.5 seconds. Moving against it frees you sooner |

He favours what he took from **you**: "Recognise it? Your power. My hands."

### His passive gifts

- **Telekinesis:** shots aimed at him while he is not busy turn aside.
- **Regeneration:** he heals slowly. The first killing blow in a fight puts him down for five
  seconds instead, then he rises at two-fifths of his health.
- **Density:** a wall between you does not stop him for long. It ripples like water as he walks out of it near you.
- **Empathic Mimicry:** he borrows your power for the fight, even one he has never taken.
- **Flight:** he rises after you.

### Taking your power

Outside his shop, his acquisition hold is unchanged. After four seconds of fighting within seven blocks and in sight,
a six-second channel begins. Hurt him, break his sightline, or get away to stop it. A finished
channel deals 8 and copies (never takes) your power.

Worlds that already kept Gray from the light on the Avenue or protected Pod 14 on Night Three
retain those outcomes. The affected power, Radiation or Regeneration, stays out of reach of his
copying channel. His borrowed power from Empathic Mimicry still follows its own rules.

## Telepathy and the existing visions

Every nearby awakened telepath can sense Sylar within sixty-four blocks,
including the exact boundary and through solid walls. He need not be targeting
or attacking them. A private message gives a cardinal direction and a brief
two-second outline reveals the presence. The warning has a fifteen-second
cooldown per telepath, and Eclipse suppression disables it. Other players do
not receive the private warning or outline.

Reading his mind reveals a calculating hunger and a clue to breaking his
concentration. Four new villager memories extend the existing twelve fragments;
existing saved story progress continues without a reset. They introduce Gabriel
Gray, his watchmaker identity, and why he must not learn the glowing man's heat.

## Configuration and previews

The common configuration's `sylar` section provides `enabled`,
`canAcquirePowers`, `firstEncounterSeconds`, `sightingIntervalSeconds` and `huntIntervalSeconds`.
The old `encounterIntervalSeconds` key is ignored.
Disabling acquisition leaves the stalking and combat story in place.

Operators can use these commands with an eligible awakened player:

```
/heroesevolved sylar stalk <player>
/heroesevolved sylar hunt <player>
/heroesevolved sylar dismiss
/heroesevolved sylar status <player>
```

Stalk previews a distant sighting. Hunt previews the six-second warning before
the ambush. Previews respect the singleton, eligibility, safe loaded placement
and the enabled setting. Try an open surface area if there is no safe sightline.
The hunt preview intentionally bypasses the saved chapter and cooldown waits.

`./gradlew runClient --init-script tools/watchmaker-preview.init.gradle` photographs the Watchmaker phase by phase
(a real hunt 300 blocks from spawn, then every move in his shop) into `build/watchmaker-preview/screenshots/`.
`tools/generate-sylars-watch-icon.py` draws the watch's item icon.

The original 64x64 skin and mechanical watch audio are reproducible with
`tools/generate-sylar-skin.py` and `tools/generate-sylar-watch.py`. Existing power
and cinematic audio assets are preserved.

## Verification

`WatchmakerGameTests` covers the shop:

- he takes the quarry and an ally into it, and walking out of the open door is getting away and sends everyone home;
- the phases turn at three-fifths and three-tenths, and the doors bar; at a tenth he runs, the back door bursts, and
  reaching the alley's end counts as fleeing;
- his grab holds you until you struggle free, or takes your power until the fight ends (and it comes back);
- a charged hit cracks his watch only while it shows, and staggers him;
- the rings of stopped time catch you outside the gap and pass you by inside it;
- the rail lightning and the floor slam land only after their telegraphs, and the slam rips up the floor;
- defeat hands over his watch, which rewinds once a life.

The server fixtures cover quiet sensing through a real wall, private delivery
and cooldowns, the sixty-four-block boundary, dormant powers, player-role
ownership after a completed copy, damage/line-of-sight interruptions, Cryokinesis
and Time Stop pausing the channel, one global hunter, saved progress and
knowledge, cleanup, natural grace/hint pacing, safe-placement failure and the
actual mortal NPC's defeat cooldown. The existing ability, rewind, intro and
ownership fixtures remain included. A client playtest is still needed for
subjective horror pacing, skin appearance and audio balance.
