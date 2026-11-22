# Travellers on the road

The roads between villages aren't empty. Now and then, near players, someone is travelling from one village to another,
and what they want from you depends on who they are.

Code: `travel/Travellers` (who sets out, where, and when), `travel/Party` (a group on the road), `company/Pursuit` (the
Company's hunt for refugees). Tests: `TravellerGameTests`.

## When they come

About every ten minutes (`villages.travellerMinutes`), each group of players (players within 256 blocks of one another,
as for village incidents) has a 40% chance (`villages.travellerChance`) of meeting a party on the road: it appears 40 to
70 blocks away, out of sight, heading for a village within 256 blocks. Only where there is a village to head for. The
first time you come within 24 blocks, you hear who they are: "Ines the Pedlar is on the road with a pack of wares,
heading for Ashford."

| Who | How often | |
| --- | --- | --- |
| A pedlar | 35% | trades what villagers don't |
| A travelling healer | 25% | mends anyone hurt they pass |
| A caravan | 25% | may be ambushed; protect it for a reward |
| Refugees | 15% | the Company is after them |

They walk the road to their village at a walking pace. Once there, a pedlar and a healer stay about half a day, then go
on their way; a caravan unloads and leaves; refugees settle.

Travellers only travel where players are. A party that nobody is within 160 blocks of for two minutes reaches its village
unseen (it is gone). A party isn't saved: one on the road when the server stops is gone when the world loads.
If any member's chunk unloads, the journey ends quietly, without a delivery reward or lost cargo. A refugee family's
pursuit ends with it, and its agents leave.

## The pedlar

A wandering merchant (a wandering trader, named for themselves) who trades things villagers don't:

| You give | You get |
| --- | --- |
| 8 emeralds | a Helixite shard |
| 6 emeralds | a walkie-talkie tuned to 101.10 FM |
| 10 emeralds | 6 taser cartridges (with TaCZ) |
| 12 emeralds | 4 Dampener rounds (with TaCZ) |
| a Helixite meteorite | 10 emeralds |
| 4 Helixite shards | 9 emeralds |

## The travelling healer

A villager whose gift is Regeneration, travelling to whoever needs them. Every few seconds they mend anyone hurt within
8 blocks (players, villagers, golems, animals: 3 health each, with the healer's light), on the road and wherever they
stop. They won't fight.
Their healing stops while their gift is dampened, including inside an Ability Dampener or a Suppressor's field.

## The caravan

Two drovers and two pack llamas on leads, carrying goods between villages. If a player walks with it a while (within 32
blocks for 30 seconds), renegades may hit it (60%): a gifted renegade and two pillagers come at it from the road ahead,
going for the drovers and their llamas. An ambush only ever comes when someone is there to answer it.

- **Delivered**, with at least one drover alive: everyone within 32 blocks is paid (6 to 10 emeralds, and a share of the
  goods: bread, iron, leather), and the village it reaches thinks well of them.
- **Lost**, every drover dead: its goods lie where it fell, and the radio talks about it.

## Refugees

A family (two or three villagers, one with a gift) running from the Company. When a player comes near they ask for help
("Please, they're right behind us!"), and soon after, two Company agents come up the road behind them.

- **Walk them in.** If they reach their village (within 24 blocks of its bell), they settle there: they belong to it now,
  and the one with a gift becomes its guardian. The village thinks well of whoever brought them.
- **Stand between them and the agents.** The agents go for anyone within 10 blocks of the family, or anyone who hurts
  them. Put both down and the family goes on safely: fighting the Company gets you noticed (+8 attention).
- **If the agents reach them,** they take them, one at a time (each agent needs a few seconds between them). Anyone
  within 16 blocks when one is taken was caught with them: +10 Company attention, once a pursuit.
  Once the last refugee is taken, the agents leave with them.

## Admin

- `/heroesevolved travel pedlar|healer|caravan|refugees` sends that party onto the road near you.
- `/heroesevolved travel clear` sends every party on its way and calls off their refugee pursuits.
