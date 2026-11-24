# Overloads: storms with a person at the centre

Neighbours scream as they escape an uncontrolled gift. They seek their own roofed homes beyond the dangerous area,
or run outward if they have no safe home. In a blizzard, a roofed home can shelter them within the storm. They remain
sheltered while the power is out of control; if their home becomes unsafe, they flee instead.

Sometimes a villager's gift wakes stronger than they can hold, and their power turns into weather around them. A
pyrokinetic becomes the heart of a wildfire, a flyer the eye of a tornado, a cryokinetic the middle of a blizzard. They
don't mean it and can't stop it. The storm grows for four minutes and then breaks over their village, unless someone
reaches them through it and calms them down.

A villager's meltdown ([the emergency band](emergency-band.md#a-villagers-meltdown)) is the same kind of trouble for
Induced Radioactivity. Overloads are the other powers' version.

Code: `village/overload/` (`Overloads`, `Storm`, `OverloadGeometry`, `Wildfire`, `Supercell`, `Blizzard`), art in
`client/effects/setpiece/OverloadArt`, the tornado in the shared `speed/TornadoShape` and `TornadoWind`. Tests:
`OverloadGameTests` (with the gifted tests: `./gradlew runGameTestServer --init-script tools/gifted-gametest.init.gradle`).

## Which storms

| Gift | Storm | In a word |
| --- | --- | --- |
| Pyrokinesis | **Wildfire** | a ring of fire spreading out from them, embers flying ahead of it |
| Flight | **Supercell** | a tornado that lifts them into its eye and wanders the village with them |
| Cryokinesis | **Blizzard** | a whiteout: snow, ice, and a cold that freezes anyone left out in it |

## How one starts

- **A gift waking too strong.** When a villager's gift wakes good (not as a renegade) and it is one of the powers above,
  there is a 40% chance (`villages.overloadChance`) that it overloads instead of making them their village's guardian.
  Gifts wake from village incidents (every 20 minutes, a roll, in a village up to 640 blocks away) and as each Eclipse
  leaves (see [the emergency band](emergency-band.md#where-they-happen)).
- **Admins:** `/heroesevolved village overload [wildfire|supercell|blizzard]` starts one in the nearest grown villager
  within 64 blocks; `/heroesevolved village incident overload` starts one in a village up to 640 blocks away.

## What everyone sees

- **A call for help** on 101.10 FM, a line in chat for everyone within 768 blocks, and a flare over the village in the
  storm's colour: ember orange for a wildfire, slate green for a supercell, ice blue for a blizzard. The village stays
  loaded until it is over.
- **A bar** for everyone within 160 blocks: `Wildfire  3:42   Calm 0%   Village 9/9`. It turns red for the last
  ten seconds.
- **The storm grows** for four minutes (`villages.overloadTicks`, 4800 ticks): wider, stronger and harder to get through.
- **The person at the centre** can't move or be hurt. Hitting them only makes it worse: each blow takes ten seconds off
  the time left, at most once a second.
- **If they die anyway** (by `/kill`, say), the storm goes with them.
- **Their village runs.** Villagers near the storm run away from it, and every few seconds one shouts. Anyone of the
  village still inside the storm glows, so players can find them and carry them out (sneak + use). The bar counts who is
  still alive.

## Calming them

Anyone can do it: **get to them and stay with them.**

| Who | What it does |
| --- | --- |
| Anyone within 4 blocks of them | Calms them: 10 seconds with one person beside them. Each other person beside them adds half again. With nobody there, the calm slips back, a quarter as fast as it came. |
| Someone with the same power | Calms them twice as fast: they know what it is like. The storm can't hurt them either. |
| An Empathic Mimic | Calms them twice as fast: they take some of it on. |
| A telepath | Calms them from up to 16 blocks away, if they can see them, twice as fast. |
| Freezing them solid (Cryokinesis), or stopping their time (Space-Time) | Holds the storm where it is: the time left stops running while it lasts. |
| An Ability Dampener (a strike, or its field) | Ends the storm at once, but switches their gift off for good. |

While someone calms them, a thread of warm light runs from each helper to them, rings of it settle round them, and the
storm visibly weakens. It can still be done in the break's three seconds of warning.

**Calmed:** the storm dies away over a few seconds. They keep their gift, under control now, and guard their village
with it (a guardian, as in [Gifted mobs](gifted-mobs.md)). Everyone near gets Hero of the Village for 40 minutes.

## If nobody gets there: the storm breaks

When the time runs out, the storm breaks: three seconds of warning, then its worst (below). Then it burns itself out over
twenty seconds. The villager at the centre collapses, alive but weak, and their gift is gone. Players are told how many of
the village made it.

## The wildfire (Pyrokinesis)

- **The centre:** they stand in a column of fire, and the ground round them is scorched.
- **The front:** a ring of flame spreads out from them, from 5 blocks to 28 over the four minutes. Inside it the air is
  burning: anyone there catches fire, and standing in the front's flames hurts every half second. Behind the front the
  ground is scorched: grass burns to dirt (it grows back) and plants burn away. This only changes blocks where
  `mobGriefing` is on; nothing else in the world is touched.
- **Embers** arc out past the front every few seconds, more as it grows. Where each will land, a ring glows on the ground
  for a second and a half; then it bursts into a spot fire 2.5 blocks across that burns for five seconds.
- **Smoke** rises over it in a column you can see from far off, ash falls, and the air near it turns hazy.
- **Rain** slows it: in the rain the front spreads half as fast and embers don't catch.
- **The break, Firestorm:** the front flares for three seconds, then a wall of fire rushes out to 36 blocks, setting
  everything in it alight.

| Answer | What it does |
| --- | --- |
| Pyrokinesis | The fire doesn't burn you, and you calm them twice as fast. |
| Fire Resistance (a potion) | Lets anyone walk through the front. |
| Cryokinesis's Ice Blast held on them | Holds the time left for as long as the stream stays on them, as for a meltdown. |
| Water | Standing in water puts you out, as with any fire. |

## The supercell (Flight)

- **The sky:** a wide, slowly turning deck of dark cloud spreads over the village. Then a funnel comes down round the
  villager, lifts them a little off the ground into its calm middle, and **wanders the village with them** at a walking
  pace. A line of dust on the ground shows where it is heading next.
- **The wall:** wind drags things in along the ground (out to 10 blocks at first, 24 at the end), and anything that reaches
  the funnel's wall is whirled round it, carried up, hurt every half second and thrown out. Inside the wall, the eye is
  calm and still.
- **Getting in:** fly down into its open top; run through the wall faster than it can grab you (a speedster going about
  a block a tick); or be too heavy to lift (Density, made dense). A telepath can calm them from outside.
- **Unwinding it:** a Flight Tempest or a Superspeed Cyclone within 24 blocks of it holds it back: the time left stops
  running while the counter-wind turns.
- **The break, Touchdown:** the funnel swells to its full size for three seconds, a ring on the ground showing its
  reach, then runs straight through the village's bell and out the far side before it lifts and thins away.
- Villagers caught in it are carried up and dropped. Get them indoors or far out of its way.

## The blizzard (Cryokinesis)

- **Whiteout:** snow drives sideways and the air closes in to a few blocks near the centre. Snow settles on the ground
  and still water freezes over; both melt away a while after it ends.
- **The cold:** anyone out in the open in the blizzard (with no roof over their head) starts to freeze. A COLD ring on the HUD fills; full, you are
  frozen, slow and hurt, as in powder snow. **A roof over you, or warmth** (within 5 blocks of a fire, a campfire, lava,
  or a pyrokinetic) stops it and thaws you. Leather armor keeps the cold out, as it does in powder snow. Cryokinetics
  don't feel it.
- **The centre:** they stand inside a spire of ice that grows with the storm. In the whiteout it is hard to find: its
  light shows only close by. **Telepaths, Sonic Resonance users and cryokinetics see them** through the snow from
  anywhere in it, under a column of pale light.
- **Their village goes indoors** when it starts, as when the bell rings for a raid. Anyone of the village caught out in
  the open glows: bring them in.
- **The break, Deep Freeze:** a white wave rolls out for three seconds, and everything caught in the open freezes
  through.

| Answer | What it does |
| --- | --- |
| Cryokinesis | The cold doesn't touch you, and you calm them twice as fast. |
| Pyrokinesis | Everyone within 5 blocks of you stays warm, and you calm them twice as fast too (fire against ice). |
| Leather armor | Keeps you from freezing. |
| Shelter | Under a roof, the cold can't reach you. |

## Admin commands

- `/heroesevolved village overload [wildfire|supercell|blizzard]`: the nearest grown villager within 64 blocks overloads.
  Without a storm, one is chosen from their gift (or at random).
- `/heroesevolved village incident overload`: an overload in a village up to 640 blocks away; it begins once the village
  has loaded.
- `/heroesevolved village calm` also ends every overload, with nothing happening.

An overload is saved in the villager's own data, so it carries on through a restart or an unloaded chunk. Its village
remembers how it ended, a wildfire's burn blooms and a supercell's track grows back
([Lasting consequences](lasting-consequences.md)).
