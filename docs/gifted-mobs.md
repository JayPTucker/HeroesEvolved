# Gifted mobs: guardians, renegades and champions

Powers aren't only for players. Any villager can carry one, any power at all, and so can the illagers who raid them
(pillagers, vindicators, evokers and illusioners). **Nothing else does:** no zombies, skeletons, witches, piglins or other mobs; powers belong to people. A mob with a power uses it, for or against you. The strongest are mini-bosses
in the spirit of Apotheosis's bosses and Cataclysm's fights: named, graded, readable, and worth beating.

Code: `gifted/` (`GiftedMobs`, `GiftedMob`, `MobMoves`, `Renegades`, `GiftedRaids`, `Rarity`, `GiftNames`), village
events in `village/` (`VillageIncidents`, `VillageTickets`, `Villages`), art in `client/effects/setpiece/GiftArt` and
`VillageArt`. Tests: `GiftedMobGameTests` (`./gradlew runGameTestServer --init-script tools/gifted-gametest.init.gradle`
runs them with the village and radiation tests).

## Where they come from

| Who | How | Rarity |
| --- | --- | --- |
| A **villager or wandering trader** | 5% are born with a dormant gift (`eclipse.giftedVillagerChance`): any power, Empathic Mimicry included. A stray mote drifts off them now and then; an Empathic Mimic sees them glow and can copy it. | rolled when it wakes |
| A **gift waking** | Village incidents (every 20 minutes, 50%, in a village up to 640 blocks from a player) and the end of each Eclipse wake dormant gifts. See [the emergency band](emergency-band.md#where-they-happen). | Guardian: Uncommon or better. Renegade: Rare or better. |
| A **raid's champion** | Each wave's leader (the one with the banner) has a 30% chance (`villages.giftedRaidChampionChance`), more in later waves. **The last wave's leader always is.** | Rare or better from the second half; the last wave Epic or better |
| A **patrol leader** | 35% of pillager patrol leaders (`villages.giftedPatrolLeaderChance`): a named mini-boss crossing the land, perhaps past your home. | Rare or better |
| A **wild one** | 1% of illagers that spawn on their own, outside a raid or patrol (`villages.giftedWildChance`); vanilla rarely spawns them that way. | any |

## A gift wakes: guardian or renegade

When a villager's gift wakes, it goes one of two ways (50% each, `villages.giftedEvilChance`):

- **A guardian.** They stay a villager and defend their village with their power: they go for monsters, raiders and
  evil gifts near them, never people, villagers, golems or animals. Light spirals up them into a halo of pale gold, and
  nearby players are told who now guards the village. Their name shows when you look at them. Between fights they use
  their power for the village's work and ask for favours ([Gifted villagers at work](gifted-villagers.md)).
- **A renegade.** The gift goes to their head. Crimson cracks climb them, a ring of crimson closes in on them, and they
  turn into an **illager** carrying the same power: a vindicator for the hand-to-hand powers (Superspeed, Density,
  Regeneration, Quake, Shapeshifting), an illusioner for the mind's (Telepathy, Invisibility, Space-Time, Mimicry,
  Vibe), a pillager for the rest. They keep a name ("Hollis the Ashen") and wear it overhead, glow for a minute, and
  wear a crown of crimson thorns. Then they lay siege to their own village.
- A **radioactive** gift that wakes good can't be held by anyone good: that villager melts down
  ([the emergency band](emergency-band.md#a-villagers-meltdown)).
- A **storm's** gift (Pyrokinesis, Flight, Cryokinesis) that wakes good may wake too strong to hold (40%,
  `villages.overloadChance`): an overload, a wildfire, a supercell or a blizzard round them, until someone calms them.
  Calmed, they guard their village as above ([Overloads](overloads.md)).

## A renegade's siege

The neighbours react from the first uncontrolled flash: they shout, scream and scatter. A villager with a roofed home
away from the danger runs there and stays inside. Anyone whose home is too close, destroyed or absent runs away from
the power instead. If the danger moves toward a hiding place, they leave it and escape again. Calm awakenings that
produce a guardian leave the village at peace.

- **The bell rings** and the village hides; it rings again every thirty seconds. Raiders near the bell glow.
- **A distress call** goes out: a line in chat for everyone within 768 blocks, a crimson flare over the village that can
  be seen from hundreds of blocks away, and the words on 101.10 FM. The village stays loaded until it is over.
- **The renegade takes one of its village every 45 seconds** (`villages.renegadeKillSeconds`). Between those, its blows
  and powers don't land on its own village; it walks the streets, fights the golems and anyone who comes. When its turn
  comes it hunts the nearest of them. Its boss bar counts them: `Hollis the Ashen  Rare Pyrokinesis  Village 7/9`.
- **Only players can really hurt it.** A golem, a wolf pack, a lava pit or a fall chips at most 3 health a blow, so the
  village's own golem can't end it before help comes. A player's blows, shots and pets land in full.
- **It stays near home.** With no one to fight it walks back to the bell, and out of a fight it heals.
- **The end.** Kill it and whoever is left is saved: everyone near gets Hero of the Village (stronger if half or more
  lived) and Mastery for each life saved. If nobody is left, the village has fallen and the renegade stays there,
  waiting. After fifteen minutes the calls stop either way.

The siege is saved on the renegade, so it carries on through a restart or an unloaded chunk.

Every renegade, besieging or not, leaves a trail of its power's marks behind it, which fade over a day. A telepath (or
anyone with Vibe, Mimicry or Space-Time Manipulation) notices it ("Vesna Stormcaller passed near here not long ago."),
and crouching by a mark tells anyone which way it went ("Vesna Stormcaller passed this way, heading east, about an hour ago.").
See [Power traces](power-traces.md).

## Rarity

Like Apotheosis's bosses, every gifted mob is graded. Its name is in its rarity's colour, and the beads on its sigil
count its rarity.

| Rarity | Colour | Health | Damage | Casts | Moves | Also |
| --- | --- | --- | --- | --- | --- | --- |
| Uncommon | green | +20 | ×1.0 | normal | one | 30% knockback resistance |
| Rare | aqua | +40 | ×1.2 | 15% faster | two | 40% knockback resistance |
| Epic | purple | +80, +4 armor | ×1.4 | 25% faster | two, signature once unbound | boss bar, one follower |
| Mythic | gold | +130, +10 armor | ×1.7 | 35% faster | two and its signature | boss bar, two followers |

Damage also scales with difficulty (Easy ×0.75, Hard ×1.25). Gifted mobs move faster than their kind (15–30%) and never
despawn.

**More players, more of it.** A hostile gifted mob (a renegade, a raid champion, a patrol leader, a wild one) gains half
its health again for each player past the first fighting it within 32 blocks, up to three times its health with five,
keeping its share of health as it grows, and never shrinking mid-fight. A while after the fight it is itself again.
Guardians don't grow: they fight for you.

## How they fight

Every power has two ordinary moves and a signature, cast from the mob the way the power looks on a player (the same
effects: Pyrokinesis's fire, Electrokinesis's forks, Quake's rings):

| Power | Moves | Signature |
| --- | --- | --- |
| Telekinesis | throw, pin | storm (lifts everyone near, slams them down) |
| Electrokinesis | chain lightning, thunderstrike (marked) | storm surge (three marked strikes) |
| Cryokinesis | ice lance (freezes), cold snap | blizzard |
| Pyrokinesis | fire stream, fire bolt | firestorm (marked ring) |
| Induced Radioactivity | radiant heat, gamma lance | meltdown pulse (a long, loud charge: hit it to break it) |
| Quake | ground pound, shockwave | fault line |
| Superspeed | blitz (a flurry of blows), lightning throw | whirlwind |
| Space-Time | fold behind you, slow time | still moment (you stop; it walks round and strikes) |
| Sonic Resonance | scream, burst | crescendo |
| Flight | dive, gale | skyburst (dashes through you) |
| Telepathy | dominate (monsters turn on you; a guardian turns monsters on each other), mind spike | mind crush |
| Invisibility | vanish and strike from behind, force push | force bubble (held, then crushed) |
| Density | reach through armour, embed in the floor | phase ambush (rises under you) |
| Matter Assembly | crossfire (marked), shield | shard storm (marked) |
| Regeneration | reckless lunge, blood mend (heals it and its side) | sever; and a mini-boss **gets back up once** |
| Hydrokinesis | water whip (pulls), drench | geyser (marked) |
| Vibe | blast, breach step (behind you) | frequency lock: **your power goes quiet for five seconds** |
| Shapeshifting | beast lunge, terrify | Apex Form (grows half again its size) |
| Empathic Mimicry | none of its own: it **copies the power of whoever it fights** (yours, or a guardian's) | |

**Every move can be read before it lands.** A ring fills at the mob's feet in its power's colour while it winds up
(full when the move lands), its hands glow where the move will leave them, an illager raises its arms (both for a cast,
one aimed at you for a pointed move, both overhead for a slam), and each move has its own sound. Marked moves show where
they will land (a ring of fire, a telegraph bolt, a geyser's mark, a frequency ring): step out of it.

**Counterplay.**

- **Break the cast.** A heavy enough blow during a windup (6 damage for an Uncommon, 8 Rare, 10 Epic, 12 Mythic) breaks
  it and staggers the mob for a second and a half, taking a quarter more damage. You're told when you do it.
- **Punish the signature.** After its signature move a mob is spent for three seconds and takes half again as much
  damage; its sigil dims and cracks.
- **Dampen it.** An Ability Dampener's field or a Nullifier round switches a gifted mob's power off, as it does a
  player's.
- **Freeze it or stop its time.** It can't cast while frozen solid or held in stopped time.

**Unbound.** At half health an Epic or Mythic mob stops holding back: rings burst along the ground, a pillar of light
flares, its crown throws bolts, a raid horn sounds and everyone near is told it is unbound. It clears its effects, takes
less damage for three seconds, casts faster and hits harder, an Epic one gains its signature, and its followers (a
vindicator, then a pillager) answer the call.

## What you get

Like Apotheosis's bosses, a beaten gift always drops something worth having:

- **its own weapon,** named for it ("Hollis's Crossbow") in its rarity's colour and enchanted (level 10 for Uncommon up
  to 34 for Mythic);
- **emeralds** (2 to 13 by rarity) and **experience** (30, 60, 140 or 300);
- **Mastery** for whoever struck the last blow (10, 20, 40 or 80), and a line in chat naming what they beat.

Guardians are mourned, not looted.

## What you see

One picture for every gift: **a sigil over the head** in the power's colour (the same colour an Empathic Mimic feels).

- A guardian wears a **halo of pale gold**; an evil gift a **crown of crimson thorns**, tipped white-hot, with a dark
  band under it so it reads in daylight.
- **Beads** on the sigil count its rarity; a mini-boss has a second ring turning the other way.
- Winding up: **a ring fills at its feet** and its hands glow.
- Unbound: **flames climb it**, two strands, its power's colour and crimson.
- Spent: the sigil dims and cracks. Staggered: it wobbles.
- Hostile gifts announce themselves: "**Vesna Stormcaller** (Rare Electrokinesis) leads a patrol nearby." to players
  within 140 blocks (wild ones only from Rare up), and glow for a minute so you can find them.

## Admin commands

- `/heroesevolved gifted give <power> [rarity] [evil|good]`: the nearest mob within 8 blocks gets a power.
- `/heroesevolved gifted summon <power> [rarity] [pillager|vindicator|illusioner|evoker]`. Only villagers and illagers can be given a power; `give` picks the nearest one.
- `/heroesevolved gifted cleanse`: the nearest gifted mob loses its power.
- `/heroesevolved gifted cast <move>`: the nearest gifted mob winds a move up at you.
- `/heroesevolved village turn|guardian|wake|incident|calm`: see [the emergency band](emergency-band.md#admin-commands).
