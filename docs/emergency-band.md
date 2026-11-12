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
- **The safe point:** a green column of light about 44 blocks out from the bell, on the side away from him, with a ring
  on the ground.

**The countdown.** One minute (`eclipse.villagerMeltdownFuseTicks`, 1200 ticks).

**Getting everyone out.** Anyone can pick up a villager (sneak + use) and carry them. A villager set down at the safe
point counts as safe, with a chime, and stays put there until it is over.

**Stopping him.**

| How | What it does |
| --- | --- |
| A speedster's containment run (the Cyclone key near him) | Holds the heat in. Five seconds held and he is cool, as for a radioactive player. The run strains the speedster the same way. |
| A radioactive player crouching within 3 blocks | Draws his heat into themselves. Eight seconds of it and he is cool, but they take on 48 Core, enough to run them Hot. |
| Freezing him (Cryokinesis) or stopping his time (Space-Time) | Pauses the countdown for as long as it lasts. |
| Carrying him well away from the village | He still goes off, but where he is, not among his neighbours. |

**Hurting him makes it worse.** Blows don't hurt him: each takes ten seconds off the countdown, at most once a second.
If he dies anyway (by `/kill`, for instance), he goes off at once.

**The end.**

- **Stopped:** everyone nearby gets Hero of the Village for 40 minutes, and he is an ordinary villager again.
- **He goes off:** a nuclear blast of radius 24 where he stands. It breaks blocks only if `mobGriefing` is on. Players
  are told how many of his village got out; if all of them did, they get Hero of the Village for 20 minutes.

A meltdown is stored in the villager's own data, so it carries on through a server restart or an unloaded chunk.

**Admin command.** `/heroesevolved eclipse meltdown [seconds]` wakes it in the nearest grown villager within 64 blocks.
