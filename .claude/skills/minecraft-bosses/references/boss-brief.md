# Boss brief template

Fill this in before writing code, show it to the user, and turn it into the boss's player doc (`docs/<boss>.md`) when
the boss is built. Every heading needs an answer; "none" is an answer only where the skill allows it. Sylar's and
doc ([sylar.md](../../../../docs/sylar.md)) is a filled-in
examples.

## Who

- **Character source** (the Heroes series page it adapts) and what is ours: lines, look, numbers, moves.
- **What they want from the player**, in one sentence.
- **The one idea every attack speaks in** (Sylar: he sees how it works, so every telegraph is a brass schematic
  that assembles as the move lands).
- **Palette**: three to five colours with hex codes, and which are the boss's and which the room's.
- **Voice**: a line for meeting them, each phase, each kind of power, a steal or grab, a stagger, a flight, a defeat.

## How the story brings them

- **Foreshadowing**: what happens before the fight, for how long, and how it is undone afterwards.
- **The arrival**: where, what the player sees, who else is pulled in (anyone within 16 blocks).
- **Requirements**: Power Level, story beats, cooldowns, config keys.

## The arena

- **Dimension and blueprint names**, size, and what is in it.
- **Features that are the counterplay**: cover, switches, safe floor, exits, things that change by phase.
- **Markers** the code reads, and the geometry class that holds them.

## Moves

| Move | Phase | Range | Opening pose and signal (ticks) | Strike, end pose, window (ticks) | Sound and mark | What it does | How to answer it |
| --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | |

Plus: the signature, the whole-arena move and its safe spot, the move that reaches a player who pillars, touch damage.

## Phases

| Phase | Health | Bar colour and name | What changes (look, room, music, moves, counterplay) |
| --- | --- | --- | --- |
| | | | |

## Weak windows

- **Skill** (interrupting a windup):
- **Rhythm** (spent after a big move):
- **Room** (using the arena):
- **Power** (an answer from another power):

## Locks (what it asks of the player)

Two or three per phase ([fight-design.md](fight-design.md), section 2). Each needs at least three power answers in
different roles, and one slow answer anyone has.

| Phase | Lock (what it looks like) | Power answers, and how well | The slow answer | What answering it pays |
| --- | --- | --- | --- | --- |
| | | | | |

Then every power against every phase: no power sits a phase out.

| Power | Phase 1 | Phase 2 | Phase 3 |
| --- | --- | --- | --- |
| (all 20, one row each) | its strong answer | | |

## What each power does to them

| Power | Effect on this boss |
| --- | --- |
| (every power) | special answer / works at boss strength / no effect |

## Fight rules

- Max health for one player, and the party scaling (default × (1 + 0.5 × (fighters − 1)), up to × 3).
- Per-hit and per-second caps (default 6% and 4% of max health), and what is exempt.
- Range falloff (default full within 16 blocks, none past 24).
- Idle healing and leash (default 5% a second after 10 quiet seconds; home after 20 seconds without a target).
- Immunities, block breaking, minions and how they scale.

## Endings

| Outcome | What happens | When they can come again |
| --- | --- | --- |
| Beaten | | |
| Everyone down | | |
| Time up | | |
| They flee (if they can) | | |

- **Death scene**: what the boss and the room do in two to four seconds.

## Rewards

- **First-kill drop**: the unique item, what it does, and how it uses the boss's idea.
- **What it opens**: the next boss, place, lead or gear.
- **Re-fight item**: its recipe, where it is used, what a re-fight drops.

## Music

- **Reference audio** used (from the user's files) and what is taken from it: mood, instruments, tempo.
- **Loops**: one per phase, tempo and key, the stinger, the last chord.

## Tests and photographs

- The rules each game test checks.
- The preview's scenes: every telegraph, every phase, the death scene.
