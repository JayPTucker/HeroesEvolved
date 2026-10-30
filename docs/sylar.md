# Sylar: the watchmaker's hunger

Character sources: [Gabriel Gray](https://heroes.fandom.com/wiki/Gabriel_Gray),
[Intuitive Aptitude](https://heroes.fandom.com/wiki/Intuitive_Aptitude), and
[Telekinesis](https://heroes.fandom.com/wiki/Telekinesis). This adapts the early
antagonist: a patient watchmaker with intuitive aptitude, a hunger for abilities,
and acquired telekinesis. He studies people before attacking. The watch cue,
dialogue, appearance, timers and encounters are original game adaptations.

## The story, one beat at a time

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
| **You get away** (beyond 112 blocks, or survive 150 seconds) | Counts as a hunt survived. Next time he says so. |
| **He flees** | No reprieve. He comes back sooner, and he remembers who did it. |
| **You defeat him** | The whole world gets an **hour** of quiet. |

**His voice is paced.** Everything he says, and every line of narration about him, goes through
one queue per player, at least eight seconds apart; narration gives way to speech when it fills up.
Warnings you must act on right now go straight to the action bar: the hold, the pin, and his escape.

He has a line for every power: "Lightning in your hands. I could feel it from across the field." He
also speaks when the fight changes phase, when he uses your own power against you, when he takes a
power, when he gets back up, when he flees, and when he falls.

## The fight

When he hunts, he is a boss. A **boss bar** shows his health to everyone within 48 blocks. His
health is 80, plus 12 for every power he has taken (up to 240). He fights in three phases:

| Phase | Health | How he fights |
| --- | --- | --- |
| **The Watchmaker** (purple) | above half | Measured: a move every two and a half seconds |
| **Hunger** (red) | below half | Faster, about every second and a half, and a quarter harder |
| **Unfinished** (white) | below a fifth | Faster still and harder again. If he knows an escape power (Invisibility, Phasing, Superspeed, Time or Flight), he says he will run and does, four seconds later, unless you finish him first. |

Each phase change throws everyone back with a telekinetic shove.

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
| Superspeed | Blitz | 0.3 s | Rushes you with a flurry |
| Time Manipulation | Fold | 0.5 s | Appears behind you |
| Time Manipulation | Slow | 0.6 s | Slows you heavily |
| Sonic Resonance | Scream | 0.5 s | A cone that shoves |
| Flight | Dive | 1.0 s | Rises, then slams down on you |
| Telepathy | Dominate | 0.7 s | Darkness and nausea; nearby monsters turn on you |
| Invisibility | Vanish | 2.0 s | Gone, then a strike from behind |
| Phasing | Phase Strike | 0.4 s | A hand through your armour |

He favours what he took from **you**: "Recognise it? Your power. My hands."

### His passive gifts

- **Telekinesis:** shots aimed at him while he is not busy turn aside.
- **Precognition:** he sidesteps a shot, and sometimes a blow, at most once every four seconds.
- **Regeneration:** he heals slowly. The first killing blow in a fight puts him down for five
  seconds instead, then he rises at two-fifths of his health.
- **Phasing:** a wall between you does not stop him for long. He walks out of it near you.
- **Empathic Mimicry:** he borrows your power for the fight, even one he has never taken.
- **Flight:** he rises after you.

### Taking your power

His acquisition hold is unchanged. After four seconds of fighting within seven blocks and in sight,
a six-second channel begins. Hurt him, break his sightline, or get away to stop it. A finished
channel deals 8 and copies (never takes) your power.

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

The original 64x64 skin and mechanical watch audio are reproducible with
`tools/generate-sylar-skin.py` and `tools/generate-sylar-watch.py`. Existing power
and cinematic audio assets are preserved.

## Verification

The server fixtures cover quiet sensing through a real wall, private delivery
and cooldowns, the sixty-four-block boundary, dormant powers, player-role
ownership after a completed copy, damage/line-of-sight interruptions, Cryokinesis
and Time Stop pausing the channel, one global hunter, saved progress and
knowledge, cleanup, natural grace/hint pacing, safe-placement failure and the
actual mortal NPC's defeat cooldown. The existing ability, rewind, intro and
ownership fixtures remain included. A client playtest is still needed for
subjective horror pacing, skin appearance and audio balance.
