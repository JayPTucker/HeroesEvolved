# Starfall: the Helixite meteor shower

On the night before every Eclipse the sky fills with falling stars. Most burn up overhead. Some come down near you, each
marked on the ground well before it lands, and leave a crater with a lump of **Helixite meteorite** in it: the
sky-fallen form of the crystal the Formula lab and the Ability Dampener are built from. Now and then a great one comes down
on a village, and players have two minutes to push it aside, hold it back, break it up or get everyone out.

Nobody can sleep while stars are falling ("the sky's too bright to sleep"): a night slept through would skip the
shower, for one player alone or for a whole server. Players within 96 blocks of one another share their falling stars,
so a group sees as many as someone alone; players far apart each get their own. The great meteor is one a night for the
whole server (on 35% of starfall nights), near a random player.

Code: `meteor/` (`Starfall` schedules the night, `Meteors` runs every meteor, `MeteorGeometry` holds the numbers the
drawing shares), art in `client/effects/setpiece/MeteorArt`. Tests: `MeteorGameTests`
(`./gradlew runGameTestServer --init-script tools/structure-gametest.init.gradle -PtestPackages=meteor`).

## When

- **Starfall night** is the night before each Eclipse day (the new moon): from dusk to dawn of the day before it. With a
  ten-day Eclipse cycle that is once every ten nights (`meteors.showers` turns it off).
- **The sky:** streaks of teal-white light cross the sky all night, everywhere in the Overworld.
- **Falling stars near you:** about once a minute (`meteors.meteorSeconds`, 60 on average), one comes down somewhere
  between 24 and 96 blocks from each player.
- **A great one:** on one starfall night in three (`meteors.greatChance`, 35%), a great meteor comes down on a village
  up to 640 blocks from a player.

## A falling star

- **You see it coming.** It appears high in the sky far off and comes down at a slant, burning teal and white, faster as
  it nears. Where it will land, a ring on the ground fills for the **thirty seconds** it takes to fall, the size of its
  blast. A whistle rises in the last seconds.
- **It lands:** a flash, a shock ring and a burst of dust. Anything within 4 blocks is hurt (worst at the middle) and
  thrown, and catches fire.
- **What it leaves:** a small crater with a block of **Helixite meteorite** at the bottom, glowing teal, its floor
  scorched to blackstone and basalt with a little magma, a lip of thrown-out rubble round its rim and the grass there
  burnt. Where `mobGriefing` is off it leaves no crater; it drops its Helixite
  Shards on the ground instead.

## Helixite meteorite

| Block | Mined with | Drops |
| --- | --- | --- |
| Helixite Meteorite | an iron pickaxe or better | 2 to 4 Helixite Shards (Fortune adds more); Silk Touch keeps the block |

It glows teal and gives light (9), so craters are easy to find at night. Where a crater reaches bedrock, the
meteorite sits on it. A falling star leaves one block; a great meteor leaves a
heap of seven.

## A great meteor

- **The warning:** a distress call on 101.10 FM, a line in chat for everyone within 768 blocks, and a teal flare over the
  village. A bar for everyone within 200 blocks: `Great meteor 1:42   Village 8/8`. It takes **two minutes** to fall
  (`meteors.greatTicks`), growing from a point to a burning rock with a long trail, and the ring on the village fills
  as it comes: **20 blocks** of blast.
- **The village runs:** villagers inside the ring flee it, shout and glow, so players can find and carry them out.
- **If it lands on them:** everything within 20 blocks is hurt, badly at the middle, a crater 9 blocks deep is torn
  out of the village (where `mobGriefing` allows), and a heap of meteorite is left in it. Players are told how many of the
  village lived.

## Stopping it

Each answer works the same way: **crouch while looking at the meteor**, within 64 blocks of where it will land
(within 15 degrees of it counts). A thread
of light runs from you to it while you work on it, and the bar says what is happening.

| Who | What it does |
| --- | --- |
| Telekinesis | Pushes it off course: the ring slides away from the village, a block a second. Two telekinetics push twice as fast. |
| Flight | Pushes it off course with the wind, two thirds as fast. |
| Space-Time | Holds it in the sky: the time left stops running while you hold it. |
| Sonic Resonance | Cracks it with its own resonance. Twenty seconds of it and it breaks into five falling stars, which land round the ring: five small blasts and five meteorite blocks, instead of one great blast. |
| Quake | Not looking up but down: crouch within 8 blocks of the middle of the ring to brace the ground. Five seconds of bracing, in all, and the ground holds: no crater, and half the blast. |
| Anyone | Carry the village out of the ring (sneak + use). |

Pushed clear of the village, it still lands, but where it can do no harm, and its meteorite is yours. Where any meteor
will land stays loaded until it has landed, so one you walk away from still leaves its crater and meteorite to come back
to. Craters heal over
the next days, and a village a great meteor hit rebuilds ([Lasting consequences](lasting-consequences.md)). Anything you do
to a great meteor also works on an ordinary falling star.

## Admin commands

- `/heroesevolved meteor drop`: a falling star comes down where you are looking (or near you), with the usual
  thirty seconds' warning.
- `/heroesevolved meteor great`: a great meteor comes down on the nearest village bell within 64 blocks (or where you
  stand), with two minutes' warning.
- `/heroesevolved meteor shower on|off`: starts or stops a starfall night now.
- `/heroesevolved village incident meteor`: a great meteor on a village up to 640 blocks away.
- `/heroesevolved meteor clear`: every meteor in the sky burns up harmlessly.

Meteors aren't saved: a falling star or a great meteor in the sky when the server stops is gone when it starts again.
