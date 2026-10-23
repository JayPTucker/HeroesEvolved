# Time Manipulation introduction

The first unlocked Time Manipulation assignment queues a one-time, 17.5-second
vision. Natural awakening, `/heroes` grants, and admin replacements all enter
through `PlayerAbilityService`. Dormant assignment alone does not trigger it.
An existing unlocked time user who has never seen this introduction gets it on
their next login. The seen flag survives logout, death, and power replacement.

At acquisition, the server captures other players in the same dimension whose
feet are within a spherical ten-block radius (inclusive). The group travels to
`heroesevolved:time_fracture`. Each visitor keeps their own saved dimension,
position, yaw, and pitch for the return. Companions without Time Manipulation
remain eligible for their own introduction if they later acquire the power.

The city is 192 by 192 blocks (twelve chunks across). Six building/lot styles
mix glass office towers with wider podiums, brick apartments, attached houses
with pitched roofs and chimneys, low shops, terraced masonry offices, and parks.
Shopfronts have readable signs and usable entrances; windows have recessed
interior lighting. Crossings, stop lines, kerbs, manholes, street signs, traffic
lights, bus shelters, bins, fountains, trees, and parked cars complete the streets.
Roofs include access huts, ventilation housings, and parapets. Its
construction runs in batches of at most 256 columns per server tick. It is
prepared during the first several seconds of the server's lifetime and reused
afterward, with a version marker saved in the dimension's data storage. Version
two rebuilds existing saved cities in place, clearing each old column completely
before drawing its replacement so old windows and roofs cannot remain floating.
Requests wait for construction to finish. Simultaneous groups queue one scene
at a time so their clocks cannot overlap.

The client caps effective rendering at twelve chunks only while in this
dimension. Lower graphics settings are respected and saved preferences are
never changed. Warm haze hides the horizon. World rules stay unchanged.

## Sequence

* Arrival: replay the existing `new_beginning` sound at its onboarding volume
  and play the existing blink cue. An original eight-second civil-defence siren
  loops locally throughout the visit.
* Twenty-four persistent, invulnerable villagers populate the streets. Hurt
  memories activate panic behaviour, navigation sends them down the avenues,
  and staggered human panic recordings replace synthesized voices and villager
  hurt sounds. Three variations play near natural pitch, roughly every two to
  three seconds. The CC0 source is [Crowd in panic by IENBA](https://freesound.org/people/IENBA/sounds/491405/).
* At eleven seconds, a white flash reveals the detonation 148 blocks down the
  avenue. Client-rendered geometry builds an incandescent core, a rising smoke
  column, a rolling mushroom cap, a ground ring, and advancing dust. This is
  independent of the user's particle and entity-distance settings. A delayed
  six-second explosion follows half a second after the flash. It layers a sharp
  pressure crack, deep bass, a turbulent roar, and secondary city echoes. It plays
  relative to the listener so movement and distance cannot make it inaudible;
  the siren drops from 55% to 18% volume underneath it. Ambient/master sound
  settings still apply. Camera rumble accompanies the blast.
* Immediately before escape, the existing time slow/stop cue and rewind
  ribbons play around the group.
* At 17.5 seconds, everyone returns before the dust front reaches the nearest
  allowed position. A final temporal burst and caption complete the sequence.

The intro messages use normal HUD-sized main text and 85%-sized secondary text
at 72% of the screen height, with wrapping and fades. They replace the old 4x/2x
vanilla title display only for this scene; other messages keep their existing size.

Visitors can look and move along the viewing avenue. Damage, death, powers,
mining, placement, item use, and trading are blocked during the visit. Active
flight, carried entities, time fields, Quake, and telekinesis are stopped on
entry. The scene does not change inventories or game modes. The detonation is
visual and cannot damage blocks or players.

Return tickets are serialized and saved before crossing dimensions. Logout
returns a visitor immediately; login recovers an interrupted ticket after a
crash; a normal server shutdown returns the remaining group. The crowd and
scene chunk tickets are removed on completion, and startup releases any city
chunk tickets left by an interrupted server. Siren playback and client scene
state clear on exit or disconnect.

## Verification

With JDK 21, run:

```text
./gradlew.bat build
./gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle
```

`TimeIntroGameTests` checks the actual dimension, city preparation and windows,
the ten-block audience boundary, actual avenue arrival, power/damage protection, the 24-person crowd,
serialized recovery tickets, logout/reconnect recovery, complete scene return,
orientation restoration, one-time eligibility, crowd cleanup, chunk-ticket
release, the blast's escape timing, old-roof removal, usable entrances, readable
shop signs, parks and tower setbacks. The same run includes eight existing
rewind-block tests. The test-only flat world preset includes the city because
Minecraft's GameTest server normally omits custom dimensions; it is not shipped
in the production jar.

The OGG assets can be regenerated with
`python tools/generate-intro-audio.py` (requires NumPy and SoundFile). The script
caches the public HQ rendition of the CC0 crowd recording under `build/` and
produces three trimmed, faded, mono clips plus the original procedural siren and
blast. Source credits and processing details are shipped beside the sounds in
`assets/heroesevolved/sounds/intro/CREDITS.txt`. The blast leaves encoder headroom
to avoid clipped decoded samples. Existing
flight, time, and onboarding sound files are unchanged. Server tests cannot
assess the visual composition or the perceived audio mix; those still need an
in-game viewing/listening pass.
