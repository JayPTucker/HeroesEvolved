# The emergency band and village meltdowns

Villages in trouble call for help on the radio. The first trouble that calls is a **meltdown**: an Eclipse wakes a
power in a villager that he can't control.

## Required mods

The radio comes from two mods, and Heroes Evolved needs both on the client and the server:

| Mod | Mod ID | Version | Why |
| --- | --- | --- | --- |
| [Simple Voice Chat](https://www.curseforge.com/minecraft/mc-mods/simple-voice-chat) | `voicechat` | 1.21.1-2.6.0 or later | carries the radio's sound |
| [Simple Voice Radio](https://www.curseforge.com/minecraft/mc-mods/simple-voice-radio) (Simple Radio) | `simpleradio` | 1.21.1-4.6.7 or later | radios, transceivers, frequencies |
| Lexiconfig | `lexiconfig` | 1.4.x | Simple Radio needs it |

In development, Gradle fetches all three: `runClient` and the test server run with them loaded.

## The emergency band: 101.10 FM

Tune any Simple Radio radio or transceiver to **101.10 FM** and keep it on you or nearby. When a village calls:

- **You hear it** on the radio: static that drifts in and out, with an SOS tone keyed through it.
- **You read it** as a subtitle above the hotbar every three seconds: what the village is saying, plus which way it
  is and how far, for example `101.10 FM  Mayday! … 0:42  (north-east, 212 blocks)`.

The call goes out from the village's bell, through Simple Radio's own transmitters, so radio range and interference
work as they do for players' radios. If Simple Radio can't be reached (a version it doesn't understand, say), calls
still go out as subtitles to everyone within 96 blocks.

## A villager's meltdown

**How it starts.** It is rare. As each Eclipse leaves (the Departure phase), roll once: there is a 20% chance
(`eclipse.villagerMeltdownChance`) that, 20 to 80 seconds after the sun comes back, a grown villager near a village
bell and near a player starts to glow. The Eclipse gave him Induced Radioactivity, and he can do nothing about it.

**What everyone sees.**

- **The villager:** a light under his skin that swells and quickens as it builds, cracks of white light over him,
  rings of heat drawing in, and in the last seconds a pillar of light. He can't move.
- **His neighbours:** they panic the way Odessa's crowd did in the vision of the past. Every villager within 20 blocks
  of him sprints away, and anyone asleep wakes up. The same recorded screams ride the runners, and every second or so
  one of them shouts in a red bubble over their head, starting with "It's happening again!" and then lines like
  "The prophecy was true!!", "Not again! Not again!" or "Make for the green light!". They run out of his reach, not to
  the safe point: getting them there is the players' job.
- **The countdown:** a bar for everyone within about 110 blocks: `Meltdown 0:42  Safe 3/9`. It turns red for the last
  ten seconds, and the ground shakes.
- **The call for help:** on 101.10 FM from the bell.
- **The safe point:** a green column of light with a ring on the ground, well outside the village and the radiation:
  the nearest open, dry, natural ground (not a roof or a treetop) that has no bed, job site or bell within 24 blocks,
  is at least 40 blocks from the bell, and is at least 76 blocks from him (three times the blast's reach), tried first
  on the side away from him.
- **Who is still in danger:** every villager of his village who isn't at the safe point yet glows, so players can find
  the ones hiding in houses or wandering off. A villager stops glowing when they reach safety.
- **What pours off him:** shock waves, a lethal near-field and the ground decaying round him, all growing as the
  countdown runs out. Getting close needs a suit (see [Meltdowns, friction and the suits](protective-suits.md)).

**The countdown.** One minute (`eclipse.villagerMeltdownFuseTicks`, 1200 ticks).

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
| Freezing him (Cryokinesis) or stopping his time (Space-Time) | Pauses the countdown for as long as it lasts. Near the end his near-field reaches as far as Ice Blast, so a cryokinetic needs a suit. |
| Carrying him well away from the village | He still goes off, but where he is, not among his neighbours. Whoever carries him needs a suit, or Regeneration. |

**Hurting him makes it worse.** Blows don't hurt him: each takes ten seconds off the countdown, at most once a second.
If he dies anyway (by `/kill`, for instance), he goes off at once.

**The end.**

- **Stopped:** everyone nearby gets Hero of the Village for 40 minutes, and he is an ordinary villager again.
- **He goes off:** a nuclear blast where he stands, the same as a radioactive player's Critical Mass: radius 24, the
  flash and white-out, the boom heard far off, the ground shaking for 300 blocks, the mushroom cloud, and far-off
  players see the sky flash. He burns away in it. It breaks blocks only if `mobGriefing` is on. Players are told how
  many of his village got out (anyone of his village not seen dying, including those carried off into chunks that have
  since unloaded); if all of them did, they get Hero of the Village for 20 minutes.

A meltdown is stored in the villager's own data, so it carries on through a server restart or an unloaded chunk.

**Admin command.** `/heroesevolved eclipse meltdown [seconds]` wakes it in the nearest grown villager within 64 blocks.
