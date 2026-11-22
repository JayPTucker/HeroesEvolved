# Villages that grow

A village does well or badly by what happens to it, and it shows. One that is saved, kept safe, guarded and helped
grows: it posts what it wants to build on a notice board by its bell, and once players bring the materials, new houses,
farms and workshops go up in its own style and newcomers arrive. As it grows it goes from a hamlet to a village to a
town, rebuilds its homes bigger, and puts up two-storey town houses. A thriving one becomes a haven. One that is hit and left alone
declines: people leave and the place looks tired.

Code: `village/Growth` (how a village is doing, and what that does to it), `village/Projects` (the notice board and what
it asks for, tiers and rebuilding homes), `village/Styles` (the town houses in each kind's colours). Tests:
`VillageGrowthGameTests`, `VillageProjectGameTests`, `VillageUpgradeGameTests`.

## How a village is doing

Each village keeps a score from what it remembers ([Lasting consequences](lasting-consequences.md)) and what it has now:

| | Counts |
| --- | --- |
| Saved from something (each, in the last 10 days) | +3 |
| A guardian living there (each) | +2 |
| A favour done for one of its people (each, in the last 10 days) | +1 |
| A family of refugees it took in (each) | +2 |
| Every 2 days with no trouble (up to +5) | +1 |
| Hit by something it lived through (each, in the last 10 days) | -4 |
| One of its people taken by the Company (each, in the last 10 days) | -3 |

| Score | The village | What it does |
| --- | --- | --- |
| 8 or more | **Thriving** | Gathers half of what its projects need itself, so its board asks for less. With a guardian, it is a **haven**: refugees head for it first |
| 0 to 7 | **Steady** | Gathers a quarter of what its projects need itself |
| Below 0 | **Struggling** | Gathers nothing itself: its board asks for everything. Every 2 days it looks more worn (a few windows broken, cobwebs, moss), and every 3 days someone leaves, down to 3 people |

## The notice board

Every village puts up a notice board near its bell, facing it: two posts with lanterns, six signs and a chest at its foot
(named "For Ashford's builders"). A village is remembered, and gets its board, as soon as a player comes near it. The board
says what the village is gathering for and how far along it is:

| Sign | Says |
| --- | --- |
| Top left | The village's name, the project ("A new house"), its tier ("- Village -", when the project's name fits on one line), and how much is in ("40% gathered", "Starts at dawn", "Building: 45%", "Finished!") |
| The other five | Each material and how much: "Planks" / "64 / 114", or "Done (114)" once it's all in |

The first time you come within 10 blocks of a board during a project, you're told what it's for: "Ashford's notice board:
the village is gathering for a new house. Leave what it needs in the chest below the signs." The signs are waxed, so
nobody can write over them. A board that's broken is mended once a day, wherever nothing else stands in the way.

### Filling the chest

Put materials in the chest and close it. The village takes what it still needs, leaves anything else in the chest, the
signs count it at once, and you're told: "Ashford's builders take 64 planks and 1 bed. Still needed: 50 planks, 23
terracotta and 2 torches." The chest's own village gets it, even if another bell is nearer. Extend it into a double chest
if you like: either half counts, and so do hoppers (the village checks every ten seconds).

| Material | What counts |
| --- | --- |
| Planks | any planks; a log or stripped log counts as 4 |
| Cobblestone | cobblestone, stone, stone bricks, smooth stone, bricks, deepslate, andesite, diorite, granite |
| Sandstone | any sandstone |
| Terracotta | any terracotta, dyed or not |
| Glass panes | panes; a glass block counts as 3 |
| Wool, Beds, Iron ingots | any wool, any bed, iron ingots (a block of iron counts as 9) |
| Torches | torches or lanterns |
| Hay bales | hay bales; wheat counts as a ninth |

Giving to a project makes the village think better of you; when the building is finished, everyone who gave is thanked
and it thinks better of them again.

### Hamlet, village, town

How big a place is decides what it builds. Its size is its beds plus twice what it has built (or rebuilt) itself:

| Size | Tier | It can build |
| --- | --- | --- |
| under 8 | **Hamlet** | small houses, farms, animal pens |
| 8 to 19 | **Village** | also bigger houses, stables and every workshop (butcher, smiths, armourer, fletcher, shepherd, fisher, tannery, mapmaker, mason), and **rebuilding a small home bigger** |
| 20 or more | **Town** | also big houses, large farms, libraries and temples, **town houses** (two storeys), and **rebuilding a home with a second storey** |

Most ordinary villages start as villages; a new one, or one that lost most of its people, starts as a hamlet. When a
place grows into the next tier, everyone near hears it ("Ashford has grown into a town.") and the radio talks about it.

### Projects

A project is one of the village's own buildings for its kind (plains, desert, savanna, snowy or taiga), and it asks for
what that building is made of, less what the village gathers itself (see the moods above). Soil, plants, crops, snow and
ice it finds itself. No material is asked for more than 160 of.

| Project | How often |
| --- | --- |
| A new house | its first project is always a home; after that, the most likely |
| A bigger home (rebuilding a small house), a two-storey home (rebuilding a small or medium house as a town house) | often, once its tier allows |
| A town house | sometimes, in a town |
| A bigger house, a big house | sometimes |
| A farm, a large farm, an animal pen, a stable | sometimes |
| A library, a temple, a butcher's shop, a toolsmith's, a weaponsmith's, an armourer's, a fletcher's, a shepherd's, a fisher's cottage, a tannery, a mapmaker's, a mason's yard | now and then |

### Rebuilding a home

A village picks one of its own homes still standing (the houses it was founded with, or ones it built), nearest the bell
first, and offers to rebuild it bigger, but only if the bigger house fits there. Rebuilding asks for what the new house
needs, less half of what the old one gives back. When the work starts, the old house comes down from the top (anything
left in its chests is set on the ground), and the new one goes up where it stood. It faces the street the same way, its
front on the old front line, centred, or shifted a block or two if that's where it fits. A rebuilt home that gains beds
gains newcomers. If something has been built in the way by the time everything's in, the village builds the new house on
open ground instead, so nothing anyone gave is wasted.

### Town houses

Two designs of our own, both two storeys with stairs inside, two beds upstairs, a kitchen and table downstairs, and
lanterns:

| | |
| --- | --- |
| The timber house | front gable to the street, a stone ground floor, a jettied upper floor in a log frame with plaster between the beams, flowers on the window sills |
| The brick house | side gable, a brick ground floor, a plank upper floor in a log frame, flowering window boxes, a slate roof and a smoking chimney |

Each is built in its village's colours: oak in the plains, spruce in the taiga and the snow (with stone bricks for
cobblestone), acacia on the savanna (with orange plaster), birch and sandstone in the desert.

A village never posts the same kind twice running. Once everything is in, its builders start in the morning: they pick
open, level ground 20 to 48 blocks from the bell and put the building up over two days of daylight, the way they rebuild
after a meteor ([Rebuilding](lasting-consequences.md#rebuilding)). If there's no room yet, the board says "Finding room"
and it keeps looking. When it's done, a newcomer moves in for each bed (up to two), the radio says so ("Ashford's got a
new house up by the east road. Welcome the Hallorans."), and the next morning the board posts the next project. A village
builds (or rebuilds) up to 16 things in all.

A building never goes where anything was built, on water, or on ground more than a block out of level. Boards and
buildings only appear where `mobGriefing` allows.

## Struggling

A struggling village shows it: a few panes gone from its windows, cobwebs in doorways, moss on its stone (only where
`mobGriefing` allows), and its people leaving one by one (out of sight, never someone with a gift, and never below
three). The radio says so: "Times are hard in Thornstead." Help it (save it from its next trouble, do its people's
favours, give it a guardian) and it recovers: its score climbs, and it stops declining once it is steady.

## Admin

- `/heroesevolved village memory` shows a village's score, mood and project.
- `/heroesevolved village project board` puts up the nearest village's board now; `project fill` brings everything its
  project needs; `project next` posts a new project; `project rebuild` posts rebuilding one of its homes bigger (a village
  or town with a home that has room).
