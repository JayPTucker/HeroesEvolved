# Time Manipulation introduction

The first unlocked Time Manipulation assignment queues a one-time vision of
Odessa in the last half-minute before 6:14 PM on the Last Day, which in this
world happened centuries ago (see [The Past](the-past.md#the-storyline-the-last-day));
the HUD reads PAST · ODESSA, THE LAST DAY for everyone there. Natural awakening,
`/heroes` grants, and admin replacements all enter through `PlayerAbilityService`.
Dormant assignment alone does not trigger it. An existing unlocked time user who
has never seen this introduction gets it on their next login. The seen flag
survives logout, death, and power replacement.

At acquisition, the server captures other players in the same dimension whose
feet are within a spherical ten-block radius (inclusive). The group travels to
`heroesevolved:time_fracture`. Each visitor keeps their own saved dimension,
position, yaw, and pitch for the return. Companions without Time Manipulation
remain eligible for their own introduction if they later acquire the power.

## The vision

You arrive on the avenue near its south end, looking north. You can walk:
the whole southern half of the city is open, from the street in front of city
hall down to the cross street before Primatech Paper, edge to edge (x -92 to
96, z -4 to 96). About forty people are on the sidewalks around you, going about
their evening: some walk their block, some look in shop windows, and small groups
stand talking, a line or two at a time in a bubble over the speaker's head. Listen
and they mention Ted Sprague looking sick, Isaac Mendez's painting, the men in
suits at Primatech: the first hints of what is coming.

| When | What happens |
| --- | --- |
| 0 s | Arrival: the blink, the onboarding music, *This already happened.* |
| 10 s | A light appears at the north end of the avenue: Ted Sprague is glowing at ground zero, a halo and a pillar of light over the rooftops, with a low hum that rises as he brightens. |
| 20 s | The sirens start: three across the city, one after another, each winding up from a low growl into a long wail. Everyone runs south, down whichever street they are on. The crowd's panic rises from every street, and people run past you screaming and away. |
| 29 s | The ground starts to tremble. |
| 32 s | **6:14.** The flash whites out the sky. A fireball swells over the north end, the blast lands half a second later, and a pale dome of condensed air races out with the shock. |
| after | The fireball rises and rolls over into a mushroom cap on a stem of dust, lit from below by its own fire and from the west by the low sun. A wall of dust and debris, a ring around ground zero with billows rolling along its crest, comes down every street at about 23 blocks a second. Ahead of it windows burst, awnings, leaves and street furniture tear away, cars and pieces of facades are flung toward you, and the heat of the flash has already set roofs and trees alight. Its roar grows as it gets closer. People it reaches are gone. |
| 20 blocks away | **Time stops.** The wave, the dust, the debris in the air and the sound hold still for two and a half seconds. Then it all runs backwards: the debris flies home, every window and car goes back where it was, the fires go out, the cloud shrinks into the fireball and the fireball into the man, back to a second before the flash. Then you are home. |

### On screen

There is little to read: the city, the light and the wave tell it. A few one-line **captions** sit
low on the screen on a dark band (*This already happened.* on arrival, *6:14* at the flash, *Time
stops.* at the freeze, *You were there.* at home), each up as long as it takes to read. One
**instruction** sits just above them: *Look around. Find out where you are.* on arrival, *Look north,
up the avenue.* when the glow starts, nothing from the flash on, and *Press J for your Journal* at
home (using whatever key the Journal is bound to).

The other trips into the past get one short instruction each: *Bring people to Bennet's door, behind
you* on the avenue, then *Look at what you're holding*; *Keep Gray from Pod 14 until 3:00* on Night
Three; *Get Mara and Ivo to the yellow exit* in the Hourglass Annex; and *Look around. Find out where
you are.* on waking in the stasis bunker.

How long the wave takes to reach you depends on where you stand when the light
falls: about five seconds from the arrival point, a little over one from the
north edge of the walk, nearly seven from the farthest corner. Time stops for
each visitor separately, 20 blocks before the front reaches them, so a group can
watch each other freeze. The air between you and the wall fills with dust as it comes, and the
haze closes in.

The model of the blast is shared: `IntroBlast` gives the front's radius at any
moment, the server decides from it when time stops for each visitor, and the
client draws the wall exactly there. The destruction happens on each viewer's
own client only. The server's city is never touched, so there is nothing to
rebuild afterwards and the avenue mission always finds the city whole. Every
destroyed block is logged with the moment it broke, which is how the rewind
puts them back. The flight of each piece of debris is a closed formula of time,
so it plays backwards without being simulated twice.

The cloud and the wall are real geometry, not sprites: lumpy spheres whose
bumps are baked once per variant, shaded per vertex by the sun, the sky and
their own heat, with the same distance haze as the city. They are drawn
independently of the player's particle settings and entity distance.

Visitors can walk, look and climb. Damage, death, powers, mining, placement,
item use, and trading are blocked during the visit. Active flight, carried
entities, time fields, Quake, and telekinesis are stopped on entry. The scene
does not change inventories or game modes.

## The city

`FractureCity` draws Odessa from `CityPlan`: 256 by 256 blocks (sixteen chunks
across), eight by eight city blocks of 32 on a grid of eight-wide roads. The
avenue runs north-south along x 0..7 and ends in a T in front of Primatech
Paper at the south edge. Nothing is parked or stands on the avenue: it is the
way to Bennet's door in the [avenue at 6:14](the-past.md#the-avenue-at-614),
which shares this city and its street grid.

- **Districts.** Downtown towers crowd the north end around Kirby Plaza, where
  the light falls: glass curtain walls with setbacks and masts, and art deco
  towers with stone piers, dark spandrels, stepped crowns and spires; the
  tallest rises past y 160. Midtown has ribbon-windowed offices and hotels.
  The south is brick walk-ups and brownstone rows.
- **Buildings have depth.** Piers stand proud of glass set one block in, with a
  sill below and a lintel above every bay. Brick and brownstone buildings carry
  a dark cornice over the sidewalk; brick ones have shops with striped awnings,
  display windows, named signs and doors, and water towers on their roofs.
  Brownstones have stoops with railings. Towers have lobbies with canopies.
  About a third of the windows are lit.
- **Streets.** Raised sidewalks behind a curb, double yellow lines, stop lines
  and crosswalks, tall lamp posts leaning lanterns over the road, street trees
  in fenced pits, hydrants, benches and bins, bus shelters on the avenue, and
  parked cars on the side streets.
- **Landmarks.** City hall faces the avenue with a colonnade and a clock tower
  stopped at 6:14. There is a church with a steeple, a park with a fountain,
  Kirby Plaza at ground zero, a parking lot, a construction site whose tower
  crane stands over the skyline, and Primatech Paper closing the avenue.

The city is built while the server runs, a chunk at a time, never more than
about twelve milliseconds a tick, and reused afterwards. Its version is saved
with it: a city from an older version is rebuilt over, column by column, the
first time the server starts. Any rectangle can be put back exactly as the plan
draws it (`FractureCity.rebuild`), which is how the avenue clears Bennet's door
away. Requests wait for construction to finish, and simultaneous groups queue
one scene at a time.

The client caps effective rendering at twelve chunks only while in this
dimension. Lower graphics settings are respected and saved preferences are
never changed. Warm dusk haze hides the horizon; after the flash it turns
white, then burning orange, and closes in as the dust comes. World rules stay
unchanged.

## Leaving

Return tickets are serialized and saved before crossing dimensions. Logout
returns a visitor immediately; login recovers an interrupted ticket after a
crash; a normal server shutdown returns the remaining group. The crowd and
scene chunk tickets are removed on completion, and startup releases any city
chunk tickets left by an interrupted server. Sound and client scene state clear
on exit or disconnect. On a completed return home the owner gets the second
wind, and nearby monsters lose their target.

## Verification

With JDK 21, run:

```text
./gradlew.bat build
./gradlew.bat runGameTestServer --init-script tools/rewind-gametest.init.gradle
./gradlew.bat runClient --init-script tools/time-intro-preview.init.gradle
./gradlew.bat runClient --init-script tools/time-city-preview.init.gradle
```

`TimeIntroGameTests` checks the actual dimension, waiting for the city, the
ten-block audience boundary, arrival on the avenue, power and damage
protection, the forty-person crowd, time stopping just ahead of the wave,
serialized recovery tickets, logout and reconnect recovery, complete return to
each visitor's own position and orientation, one-time eligibility, crowd
cleanup, chunk-ticket release, that even the closest visitor sees the wave
coming, old-roof removal, a clear avenue end to end, facade windows, city
hall's sign, the park and the tallest tower. The test-only flat world preset
includes the city because Minecraft's GameTest server normally omits custom
dimensions; it is not shipped in the production jar.

The intro preview plays the whole vision in a fresh world and photographs it
beat by beat into `build/time-intro-preview/screenshots/`; the city preview
photographs the city from the street and the air into
`build/time-city-preview/screenshots/`.

## Sound

The sirens are made live on the client (`CitySiren`), the way a mechanical siren
makes its sound: a rotor whose speed is its pitch, its motor switched on and off
for the wail, a horn turning on its mast, distance, wind, and the echoes of the
streets. They never loop, which matters on the avenue, where they sound for
minutes.

The panic is two layers (`PanicSounds`). All around, the crowd: overlapping
pieces of the CC0 crowd recording, each at its own pitch, distance and direction.
Close by, single voices riding the people running past, three at most, each
screaming two to four times with breaths between and a little Doppler as they go.
The voices are lifted out of the same recording by
`tools/prepare-panic-voices.py` (decode the cached MP3 first with
`tools/decode-audio.ps1`) into `assets/heroesevolved/audio/panic/`. Minecraft
only streams about eight sounds at once, so at the flash only the nearest voice
goes on, leaving room for the blast. See
`assets/heroesevolved/sounds/intro/CREDITS.txt` for the recordings. The hum, the
roar of the wave, breaking glass and the sound of time stopping are vanilla
sounds.
