# The Company notices

Use a big power where people can see it and word gets around. The Company keeps a file on everyone with a gift, and
the more of yours that ordinary people see, the closer it watches you: first a man in a dark suit asks questions where
you were, then it takes someone with a gift from a village near you, and in the end it comes to your door at night with
a Suppressor. Keep your power out of sight and it loses interest.

Code: `company/CompanyAttention` (the meter, what raises it, what lowers it), `company/CompanyOps` (when the Company acts,
and whom its agents stop), `company/Investigation`, `company/Convoy`, `company/Raid`. Tests: `CompanyNoticeGameTests`.

## Attention

Each player has a Company attention from 0 to 100, shown as a ring under the ability keys once it is 5 or more:

| Attention | The Company | The ring |
| --- | --- | --- |
| 0 to 24 | Has no file on you, or has forgotten it | grey, "COMPANY" |
| 25 to 49 | **Noticed**: sends someone to ask questions where you were seen | steel blue, "NOTICED" |
| 50 to 79 | **Watched**: takes a gifted villager from a village near you | amber, "WATCHED" |
| 80 to 100 | **Hunted**: raids your home at night with a Suppressor | red, "HUNTED" |

While an operation is under way the ring says so ("ASKING", "CONVOY", "RAID"). Crossing into each band, and falling
back below Noticed, gets one line in chat.

### What raises it

| What | Attention |
| --- | --- |
| A move costing 30 stamina or more, seen by a villager | an eighth of what it cost (Critical Mass 15, Tempest 9, Sunfall 8), and more for each further villager who saw it, up to twice that |
| Ending a village's trouble (a meltdown, a renegade, an overload, a great meteor, a convoy), as the one who ended it | +12 |
| Being there when it ended | +5 |
| A Company agent seeing you use a power | +10 |
| An investigator getting away with what he learnt | +10 |
| Killing an investigator | +20 |
| Freeing a convoy's prisoner | +10 (on top of ending the village's trouble) |

**Seen** means a villager who is awake, within 32 blocks and has a clear line to you. Nothing counts if you are hidden
(Invisibility's cloak, or invisible), and nothing counts outside the overworld. Small moves (anything under 30 stamina),
held powers, flying and fighting-style blows never count: it is the big moves people talk about.

### What lowers it

- **Quiet days.** After a whole day in which nobody saw anything, it falls by 10 a day. Days are calendar days: a night
  slept through counts as the night it was.
- **A village that covers for you.** An investigator who asks questions in a village you have saved (one that thanks you
  as its hero) is told nothing: -8.
- **A mind wiped.** A telepath with a thread on the investigator (Focus 30 or more) takes the afternoon out of his head:
  he leaves with nothing, -10.
- **The Company getting what it wanted.** A convoy that gets away: -15. Beating a raid sets it to 35; a raid you don't
  survive, to 50; one that runs until dawn, to 60.

## The investigator (Noticed)

A minute after a big move is seen in a village (or the next morning, if it is night), a man in a dark suit walks into
the village from its edge and makes for the spot where it happened. He has his gun on his hip and a notebook in his
hand. He questions three of the villagers who were there, one at a time (you see what he asks and what they answer over
their heads), makes notes at the spot, and walks away.

- He stops nobody and fights only if he is attacked (then he draws, as any agent does).
- One investigator to a village at a time: two players noticed in the same village don't bring two. He waits while the
  village has trouble of its own.
- If you are near, he speaks to you once: "Afternoon. Quite a mess. You wouldn't know anything about it?"
- **What he takes away depends on you.** He leaves with his notes (+10), with nothing if the village covers for you or a
  telepath wipes him, or not at all if you kill him (+20; agents who don't come back are noticed).
- If he sees you use a power while he's there, he has seen enough: +10, and he leaves at once.

## The convoy (Watched)

On a morning when you are within 160 blocks of a village, the Company takes one of its people: a villager whose gift has
woken (a guardian), or one with a dormant gift, or (if there is neither) one whose gift nobody knew about but the Company
did. Three armed agents and a Suppressor walk them out of the village at a walk, the prisoner beside the Suppressor,
whose quiet keeps the prisoner's gift asleep. The village calls for help on the emergency band (101.10 FM, a
steel-blue flare) and says which way they went. The convoy walks slowly (about a block a second), so there is time to
get there: 160 blocks takes it nearly three minutes.

- **Approach with care.** Within 14 blocks the lead agent warns you once: "Company business. Keep walking." Come within
  6 blocks of the prisoner or an agent, use a power within 20 blocks, or hurt one of them, and all four turn on you.
- **Bring friends, meet more agents.** For each player past the first fighting it, the convoy calls one more agent up
  the road ("Backup's here."), up to six in all.
- **The Suppressor is the key.** Within 9 blocks of him no power answers, yours or the prisoner's. Pick him off from
  outside his quiet (a gun, a bow, a long-range power from 10 blocks or more), and the rest of the fight is yours.
- **Freed** when every agent is down: the prisoner thanks you, walks home as their village's guardian (a gift of Induced
  Radioactivity stays asleep: nobody good can hold it), and the village remembers it. The agents' gear is left at the prisoner's feet: taser cartridges, Dampener rounds and emeralds.
- **Gone** if the convoy walks its 160 blocks out: the prisoner is taken, and the village remembers that too. It walks
  the whole way whether or not anyone is near, so a player coming from far off has the full three minutes.
- If nobody is left to fight (everyone who alarmed it is dead, gone or 32 blocks off for ten seconds), the agents gather
  up and walk on.

## The raid (Hunted)

The first night you spend within 64 blocks of where you sleep (your bed or respawn point; where you stand, if you have
none), the Company comes. Thirty seconds before, a radio crackles somewhere out in the dark, and a calm voice says: "All teams,
the house is lit. Suppressor goes in first." Then a Suppressor and three armed agents (one more for each other player
there, up to five)
come out of the dark 36 blocks away and close in. The Suppressor makes for you; his quiet goes with him.

| How it ends | Attention |
| --- | --- |
| Every agent down: "Out in the dark, an engine starts and drives away." The Suppressor's Dampener Gun is left behind | set to 35 |
| You go down: they check you're out, and pull back into the dark | set to 50 |
| Dawn: they pull back | set to 60 |
| You leave (more than 160 blocks from home): they call it off for tonight | unchanged |

**Lying down brings it.** A Hunted player who goes to bed (when the raid is due) doesn't sleep: "As you lie down, you hear
it: someone outside, moving quietly," and the raid begins, so sleeping through the night never skips it. Nobody sleeps
while a raid is on nearby.

**A shared home has one raid.** Friends whose homes are within 96 blocks of each other share their raid: only one comes
at a time, every player there fights it, and beating it (or holding out to dawn) leaves everyone there who was Hunted at
the same attention as the one it came for.

## Rules

- One operation at a time for each player, and at least a day between one and the next (half a day after an
  investigation).
- An operation is not saved: one under way when the server stops is called off, and its agents are gone when the world
  loads. The attention itself is saved.
- **However far away you are, an operation plays out.** It keeps its place loaded while it runs: the investigator's
  village, the convoy's village and the road under it as it walks, the raid's home. One that would begin somewhere not
  loaded waits a few seconds while it loads, then begins. If an agent or a prisoner is lost with an unloaded chunk all the
  same, the operation is called off with no outcome: nobody is freed, taken or rewarded.
- Agents on an errand are ordinary Company agents ([company-agents.md](company-agents.md)): present-day guards carry a
  Glock and a taser, and the Suppressor's field reaches 9 blocks through walls. Out on an errand they keep track of
  whoever they are after from 32 blocks.
- A Suppressor's quiet switches off gifted mobs' powers too, not only players': a guardian or a renegade near one has
  only its hands.
- Creative and spectating players are never noticed.

## Admin

- `/heroesevolved company attention [player]` says a player's attention, band and what the Company is doing about it.
- `/heroesevolved company attention <player> <value>` sets it.
- `/heroesevolved company investigate|convoy|raid [player]` starts that operation now, near that player.
- `/heroesevolved company calm` calls every operation off.

Config (`company` in the common config): `notices` turns it all off; `attentionScale` multiplies every gain.
