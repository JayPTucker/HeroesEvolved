# The emergency band and village emergencies

Villages in trouble call for help, and the calls carry hundreds of blocks. Three kinds of trouble call:

- **A meltdown:** a villager whose gift is Induced Radioactivity, and who can't hold it (below).
- **A renegade:** a villager whose gift turned them evil; they become an illager and lay siege to their own village (see
  [Gifted mobs](gifted-mobs.md)).
- **A raid:** raiders at the village, for as long as the raid lasts.

## Required mods

The radio comes from two mods, and Heroes Evolved needs both on the client and the server:

| Mod | Mod ID | Version | Why |
| --- | --- | --- | --- |
| [Simple Voice Chat](https://www.curseforge.com/minecraft/mc-mods/simple-voice-chat) | `voicechat` | 1.21.1-2.6.0 or later | carries the radio's sound |
| [Simple Voice Radio](https://www.curseforge.com/minecraft/mc-mods/simple-voice-radio) (Simple Radio) | `simpleradio` | 1.21.1-4.6.7 or later | radios, transceivers, frequencies |
| Lexiconfig | `lexiconfig` | 1.4.x | Simple Radio needs it |

In development, Gradle fetches all three: `runClient` and the test server run with them loaded.

## Hearing a call

**Without a radio.** Everyone within 768 blocks (`villages.distressRange`) of a village that starts calling:

- gets a line in chat: `📻 Distress call: Help! Hollis the Ashen has turned on us! 7 of 9 still alive. (north-east, 412
  blocks). Follow the flare, or tune a radio to 101.10 FM.`, and hears a bell toll faintly from that direction;
- sees a **flare** over the village: a tall column of light, wide enough to pick out from hundreds of blocks, with a
  flare shell climbing it every two seconds and bursting into a ring at the top. Its colour says what the trouble is:
  amber for a meltdown, crimson for a renegade, violet for a raid. It stays visible however far off it is (past about
  320 blocks it is drawn nearer the camera and smaller in proportion, so it covers the same part of the sky and the far
  plane never clips it), and thins out once you are in the village;
- is reminded every thirty seconds, above the hotbar, which way and how far it is.

Players who ride into range after a call started are told then.

**With a radio: 101.10 FM.** Tune any Simple Radio radio or transceiver to **101.10 FM** and keep it on you or nearby. You
hear every call in the same world, however far:

- **On the radio:** static that drifts in and out, with an SOS tone keyed through it.
- **As a subtitle** above the hotbar every three seconds: what the village is saying, plus which way it is and how far,
  for example `101.10 FM  Mayday! … 3:42  (north-east, 212 blocks)`.

The call goes out from the village's bell, through Simple Radio's own transmitters. If Simple Radio can't be reached (a
version it doesn't understand, say), radio listeners fall back to subtitles within 96 blocks; the chat line, reminders
and flare don't need the radio mod.

## Time to get there

A village in trouble is **kept loaded** while the trouble lasts, however far the nearest player is (a region ticket round
the bell, `village/VillageTickets`), so it is still in trouble when help arrives:

- a meltdown counts down **four minutes** (`eclipse.villagerMeltdownFuseTicks`, 4800 ticks);
- a renegade takes one of its village every **45 seconds** (`villages.renegadeKillSeconds`), so a village of ten lasts
  several minutes;
- a raid lasts as long as raids do.

## Where they happen

Not only beside a player. Village incidents ([`VillageIncidents`](../src/main/java/com/jayptucker/heroesevolved/village/VillageIncidents.java))
look for a village up to 640 blocks from a player (`villages.villageIncidentRange`), with the same search `/locate` uses,
from a point a few hundred blocks off in a random direction so it isn't always the nearest. A village that has never
been visited is generated and loaded for it.

- **Every 20 minutes** (`villages.villageIncidentMinutes`) there is a 50% chance (`villages.villageIncidentChance`) that
  a gifted villager somewhere wakes. Half the time they turn on their village.
- **As each Eclipse leaves,** up to three dormant gifts in villages near players wake, and one more somewhere farther off.
- **The Eclipse's meltdown** (20%, `eclipse.villagerMeltdownChance`) happens in a village found the same way.

## A villager's meltdown

**How it starts.** As each Eclipse leaves (the Departure phase), roll once: there is a 20% chance that, 20 to 80 seconds
after the sun comes back, a grown villager in a village within reach of a player starts to glow. A villager whose gift is
Induced Radioactivity and wakes good melts down the same way: nobody good can hold it.

**What everyone sees.**

- **The villager:** a light under his skin that swells and quickens as it builds, cracks of white light over him,
  rings of heat drawing in, and in the last seconds a pillar of light. He can't move.
- **His neighbours:** they panic the way Odessa's crowd did in the vision of the past. Every villager within 20 blocks
  of him sprints away, and anyone asleep wakes up. The same recorded screams ride the runners, and every second or so
  one of them shouts in a red bubble over their head, starting with "It's happening again!" and then lines like
  "The prophecy was true!!", "Not again! Not again!" or "Make for the green light!". They run out of his reach, not to
  the safe point: getting them there is the players' job.
- **The countdown:** a bar for everyone within about 110 blocks: `Meltdown 3:42  Safe 3/9`. It turns red for the last
  ten seconds, and the ground shakes.
- **The call for help:** on 101.10 FM from the bell, in chat for everyone within 768 blocks, and an amber flare.
- **The safe point:** a green column of light with a ring on the ground, well outside the village and the radiation:
  the nearest open, dry, natural ground (not a roof or a treetop) that has no bed, job site or bell within 24 blocks,
  is at least 40 blocks from the bell, and is at least 76 blocks from him (three times the blast's reach), tried first
  on the side away from him.
- **Who is still in danger:** every villager of his village who isn't at the safe point yet glows, so players can find
  the ones hiding in houses or wandering off. A villager stops glowing when they reach safety.
- **What pours off him:** shock waves, a lethal near-field and the ground decaying round him, all growing as the
  countdown runs out. Getting close needs a suit (see [Meltdowns, friction and the suits](protective-suits.md)).

**Getting everyone out.** Anyone can pick up a villager (sneak + use) and carry them. A villager set down at the safe
point counts as safe, with a chime, and stays put there until it is over.

**Who counts as his village.** Every villager within 64 blocks of the bell when it starts, every villager whose home,
job site or meeting place is within 64 blocks of the bell (even one wandering far off), and anyone who comes within the
blast's reach of him while it lasts. The village is gathered again every two seconds, so villagers who wander in, or
are born, count too; nobody is ever taken off the count. It is saved with him, so an unloaded chunk or a restart keeps
it. If he is carried within the blast's reach of the safe point, nobody there counts as safe until he is moved away
again, and players are warned.

**Stopping him.**

| How | What it does |
| --- | --- |
| A speedster's containment run (the Cyclone key near him) | Holds the heat in. Ten seconds held, building to a climax, and he is cool, as for a radioactive player. **Needs the whole velocity suit:** without it the speedster burns up on the ring for three and a half seconds, then burns away, and he still goes off. |
| A radioactive player crouching within 3 blocks | Draws his heat into themselves. Eight seconds of it and he is cool, but they take on 48 Core, enough to run them Hot. They are immune to his heat, and his waves don't throw them. |
| Holding Ice Blast on him (Cryokinesis) | Holds the countdown while the stream stays on him, and never hurts him. Twelve seconds of unbroken contact and he is cool, as for a radioactive player; looking away starts the twelve seconds again. Near the end his near-field reaches as far as Ice Blast, so a cryokinetic needs a suit. |
| Freezing him solid (Cryokinesis) or stopping his time (Space-Time) | Pauses the countdown for as long as it lasts. |
| Carrying him well away from the village | He still goes off, but where he is, not among his neighbours. Whoever carries him needs a suit, or Regeneration. |

**Hurting him makes it worse.** Blows don't hurt him: each takes ten seconds off the countdown, at most once a second. Cold from a cryokinetic is not a blow and takes nothing off.
If he dies anyway (by `/kill`, for instance), he goes off at once.

**The end.**

- **Stopped:** everyone nearby gets Hero of the Village for 40 minutes, and he is an ordinary villager again.
- **He goes off:** a nuclear blast where he stands, exactly the size of a radioactive player's Critical Mass (radius 24,
  not the awakening's 48): the flash and white-out, the boom heard far off, the ground shaking for 300 blocks, the
  mushroom cloud, and far-off players see the sky flash. **The crater is carved in one go**, the moment the shock wave
  reaches its edge, not layer by layer, and it is **twice the blast's reach: radius 48**, about 235,000 blocks of solid
  ground (measured at 136 ms, one tick). The safe point, 76 blocks off, stays outside it. He burns away in it. It breaks blocks only if `mobGriefing` is on. Players are
  told how many of his village got out (anyone of his village not seen dying, including those carried off into chunks
  that have since unloaded); if all of them did, they get Hero of the Village for 20 minutes.

A meltdown is stored in the villager's own data, so it carries on through a server restart or an unloaded chunk. After
a restart its village isn't held loaded again until a player goes there, and its call resumes then.

## Admin commands

- `/heroesevolved eclipse meltdown [seconds]` wakes a meltdown in the nearest grown villager within 64 blocks.
- `/heroesevolved village incident [awakening|renegade|guardian|meltdown]` starts an incident in a village up to 640
  blocks away; it begins once the village has loaded.
- `/heroesevolved village turn|guardian|wake` wakes the nearest grown villager's gift evil, good, or by the usual roll.
- `/heroesevolved village calm` ends every renegade's siege (the renegades stay).
