# Gifted villagers at work

A villager whose gift woke good (a guardian: an awakening, a calmed overload, a prisoner freed from the Company) does not
just stand guard. By day they use their power for their village's work, where you can see it, and now and then they ask
a passing player for a favour.

Code: `village/GiftedWork` (each power's job), `village/Favours` (asking and thanking). Tests: `GiftedWorkGameTests`.

## Their work

Each guardian works every minute or so during the day (from morning until dusk), and goes back to ordinary village life
in between. A guardian fighting, asleep, or near a Suppressor or a Dampener doesn't work.

| Gift | Job | What you see |
| --- | --- | --- |
| Pyrokinesis | **Smith** | Walks to the village's furnaces, blast furnaces, smokers and campfires and lights them with a stream of flame. They stay lit through the night: the forge never goes out. A smith with no trade often takes the blast furnace as their work and becomes the village's armorer. |
| Hydrokinesis | **Waterer** | Draws water out of the air over the fields: the farmland is soaked and some crops come on a stage. |
| Cryokinesis | **Ice house** | Freezes ice into the barrel or chest nearest their work (up to a stack and a quarter): ice and packed ice anyone can take. |
| Flight | **Courier** | Flies the post to the nearest other village within 256 blocks and back, high over the roofs. If the way there isn't loaded, they fly off over the horizon and come back from that way a few minutes later. With no other village, they fly the post round their own. |
| Superspeed | **Post runner** | Runs the post from house to house in a blur. |
| Regeneration | **Healer** | Finds hurt villagers, golems and animals and mends them. |
| Electrokinesis, Technopathy | **Golem keeper** | Charges the village's iron golem with an arc, healing it. |
| Telekinesis, Matter Assembly | **Harvester** | Lifts ripe crops out of the fields and replants them. |
| Quake, Density | **Tiller** | Sends a slow tremor through the fields: crops near it come on a stage. |
| Sonic Resonance | **Bell ringer** | Rings the village bell at dawn, and at dusk to call everyone home. |
| Anyone else | **Lookout** | Walks the edge of the village; shouts when monsters come near ("Zombies at the east edge!"), even in the middle of a fight. |

A guardian more than 64 blocks from their bell (a prisoner freed out on the road, a courier who came down somewhere
else) goes home before doing anything else: walking, if a player can see them; otherwise they are simply home the next
time their village is loaded. Guardians only work while their part of the world is loaded and ticking, as every
villager does.

Jobs that change blocks (harvesting) only happen where `mobGriefing` allows, as a farmer's work does. Nothing they do
places a block that wasn't there or takes away anything built.

## Favours

About once a day a guardian may want something for their work, and asks any player who comes within six blocks (a
calm line over their head) and on the radio ([The living radio](living-radio.md)).

| Job | Asks for | Pays |
| --- | --- | --- |
| Smith | 8 coal or 4 iron ingots | 4 to 6 emeralds |
| Waterer | 8 bone meal | 3 to 5 emeralds |
| Ice house | a water bucket | 2 to 4 emeralds |
| Courier | 4 leather | 3 to 5 emeralds |
| Post runner | 8 paper | 2 to 4 emeralds |
| Healer | 2 glistering melon slices | 4 to 6 emeralds |
| Golem keeper | 4 copper ingots | 3 to 5 emeralds |
| Harvester | 8 wheat seeds | 2 to 3 emeralds |
| Tiller | 8 bone meal | 3 to 5 emeralds |
| Bell ringer | 4 amethyst shards | 4 to 6 emeralds |
| Lookout | 16 arrows | 3 to 5 emeralds |

**To do one,** hold what they asked for and use (right-click) them. They take it, pay you, thank you by name, and their
village thinks a little better of you (it is remembered with the rest: [Lasting consequences](lasting-consequences.md)).
Without the items, using them opens their trades as usual. A favour nobody does is forgotten after two days (calendar days: slept nights count).

## Admin

- `/heroesevolved village work` makes the nearest guardian do their job now.
- `/heroesevolved village favour` makes the nearest guardian ask for a favour now.
