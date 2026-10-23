# Sylar: the watchmaker's hunger

Character sources: [Gabriel Gray](https://heroes.fandom.com/wiki/Gabriel_Gray),
[Intuitive Aptitude](https://heroes.fandom.com/wiki/Intuitive_Aptitude), and
[Telekinesis](https://heroes.fandom.com/wiki/Telekinesis). This adapts the early
antagonist: a patient watchmaker with intuitive aptitude, a hunger for abilities,
and acquired telekinesis. He studies people before attacking. The watch cue,
dialogue, appearance, timers and encounters are original game adaptations.

## Encounter progression

An awakened Survival or Adventure player in the Overworld becomes eligible.
The first hint has at least five minutes of grace plus up to two minutes of
random delay. It is private chat and a faint mechanical ticking sound. It
does not immediately create an attacker. Early sightings are separated by at
least three minutes plus up to two minutes of jitter.

The next encounters reveal a dark-coated human watching from roughly forty to
sixty-four blocks away. He watches the player's movements, faces them, and
withdraws when they approach or stare back for three seconds. The director keeps
only twelve recent route points per online player and can place him near a
previous part of that trail. A quiet footstep can come from a recently passed
position. Successful power use raises his interest in that player. Repeated
failed inputs and cooldown presses do not raise it.

After the introductory warning and two sightings, an encounter can become a
hunt. He watches for six seconds and gives another chat warning before attacking.
An encounter ends when the quarry escapes beyond 112 blocks or survives the
150-second hunt. Escaping gives at least ten minutes before another encounter.
Characters who have remained still for over a minute do not start new natural
encounters. Creative, Spectator, unassigned, dormant, sleeping, dead and protected
intro players are not quarry. Peaceful difficulty, Eclipse suppression, the
future-city sequence and Temporal Snapshot dimensions prevent hunting.

There is one active hunter globally, not one per player. Surface placement uses
at most twelve candidate columns, checks existing chunk availability, safe
footing, fluids, world borders, body collisions and the initial sightline.
Candidates in the player's central view are rejected so he can be discovered
when the player turns, rather than materializing directly under the crosshair.
It postpones a sighting if no safe spot exists, including underground or enclosed
areas. It does not force chunks or break the player's base to reach them.

## Combat and acquisition

Sylar has sixty health, light armor and telekinesis. He is mortal from the first
sighting. His acquisition hold starts only after four seconds of combat, within
seven blocks and with clear line of sight. A raised hand, a psychic tether,
ticking, mild Slowness and a private warning telegraph the six-second channel.

Hitting him interrupts the channel and gives three seconds of recovery. Moving
out of range, putting a wall between you, or a telepath's successful suggestion
can also interrupt it. It never locks player controls or teleports the victim.
Cryokinesis and Time Stop pause his actual entity ticks and channel progress;
thawing or resuming time does not cause an instant catch-up hit. Allies can
damage him to rescue someone.

A completed channel must actually deal damage before it can grant knowledge.
It deals eight health before ordinary reductions. Intuitive aptitude copies
rather than transferring a player's assignment: a surviving victim keeps their
ability, progress, and exclusive saved ownership. Sylar's learned knowledge lives
in a separate NPC story record and persists between encounters and restarts.

Learned abilities produce NPC combat variants: electric arcs, ice attacks,
shockwaves, searing heat, brief temporal slowing, psychic pressure, regeneration
and short ascending pursuit. These are adaptations of this mod's roster, not a
claim that canon Sylar possessed every one of these powers. He cannot trigger
a player awakening, city-wide nuclear detonation, or rewrite player roles.
Learning Radiation warns players who know the visions: another person now
understands the glowing man's heat. The future can still change.

Defeating the actual NPC grants a saved thirty-minute world-wide reprieve.
The encounter entity itself is temporary and is not saved into unloaded chunks;
quarry logout, death, loss of eligibility, dimension changes and server shutdown
clean it up. Saved chapters and acquired knowledge remain.

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
`canAcquirePowers`, `firstEncounterSeconds` and `encounterIntervalSeconds`.
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
