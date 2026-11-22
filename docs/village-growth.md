# Villages that grow

A village does well or badly by what happens to it, and it shows. One that is saved, kept safe, guarded and helped
grows: it posts what it wants to build on a notice board by its bell, and once players bring the materials, new houses,
farms and workshops go up in its own style and newcomers arrive. As it grows it goes from a hamlet to a village to a
town, rebuilds its homes bigger, and puts up two-storey town houses. Villages and towns hold a market once a week and
will build you a home of your own. You can watch its people at work: carrying what they've gathered to the board,
building, and mending whatever gets broken. A village never touches what you build. A thriving one becomes a haven. One that is hit and left alone
declines: people leave and the place looks tired.

Code: `village/Growth` (how a village is doing, and what that does to it), `village/Projects` (the notice board and what
it asks for, tiers and rebuilding homes), `village/Styles` (the town houses in each kind's colours). Tests:
`village/Homes` (a home of your own), `village/Markets` (market days), `village/PlayerBuilt` (what players put down),
`village/Workers` (gathering and building crews), `village/Repairs` (mending what was broken). Tests:
`VillageGrowthGameTests`, `VillageProjectGameTests`, `VillageUpgradeGameTests`, `VillageLifeGameTests`,
`VillageWorkGameTests`.

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
| under 5 | **Hamlet** | small houses, farms, animal pens |
| 5 to 19 | **Village** | also bigger houses, stables and every workshop (butcher, smiths, armourer, fletcher, shepherd, fisher, tannery, mapmaker, mason), and **rebuilding a small home bigger** |
| 20 or more | **Town** | also big houses, large farms, libraries and temples, **town houses** (two storeys), and **rebuilding a home with a second storey** |

Most ordinary villages start as villages; a new one, or one that lost most of its people, starts as a hamlet. When a
place grows into the next tier, everyone near hears it ("Ashford has grown into a town.") and the radio talks about it.

### The village gathers too

You don't have to bring everything. Each day, in daylight, a village gathers a share of what its project still needs
itself, and one of its people carries it to the board's chest (you'll see them walking over with a log or a stack of
cobblestone in their arms); when they get there the signs count it. How much depends on how it is doing and how many
people it has:

| The village | A day's share (with five or more people) |
| --- | --- |
| Thriving | a fifth of what's still needed |
| Steady | an eighth |
| Struggling | a twentieth |

With fewer people it gathers less. Days nobody was about catch up when someone comes back, up to three. So a village
builds on its own in time; what you bring makes it happen sooner.

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
open ground 20 to 48 blocks from the bell (level if they can find it; on a slope of up to three blocks they dig into
the high side and lay a foundation of cobblestone, or sandstone in the desert, on the low side) and put the building up
over two days of daylight, the way they rebuild
after a meteor ([Rebuilding](lasting-consequences.md#rebuilding)). If there's no room yet, the board says "Finding room"
and it keeps looking. When it's done, a newcomer moves in for each bed (up to two), the radio says so ("Ashford's got a
new house up by the east road. Welcome the Hallorans."), and the next morning the board posts the next project. A village
builds (or rebuilds) up to 16 things in all.

A building never goes where anything was built, on water, or on ground more than three blocks out of level. Boards and
buildings only appear where `mobGriefing` allows.

### Watching them build

Up to three of the village's people crew each building site through the day. They walk to the block going down next,
look at it and hold it in their arms, and it goes down beside them with a puff of dust; now and then one of them calls
out ("Hand me those planks!", "Mind your head!"). A site with nobody left in its village to build it waits, and nobody
works while the village is in trouble.

## Mending what was broken

A village knows its buildings as they were built: its own houses, its meeting point, wells and lamps, and what it built
since. Whatever goes missing from one later (an explosion, a fire, a storm, a power, a renegade's attack), its people
rebuild, a building at a time, the same way they build: the crew, the dust, two days at most. It looks itself over every
thirty seconds or so, and at once after an explosion nearby; it waits for any fire to go out first.

- **Only while anyone is left.** A village with nobody left (one that fell) mends nothing.
- **Three or more blocks gone from a building** before it bothers (eight if it is struggling: a struggling village's
  broken windows stay broken).
- **What was missing when the village was first known is how it was,** not damage.
- **Never your work:** blocks you put down stay, a gap you broke by hand (a window you knocked out, a wall you opened)
  stays open, and your own home is yours to mend.

The radio says so when it starts ("Ashford's patching up what was broken."), and everyone near hears when it's done.


## What you build is yours

Every block a player puts down is remembered, and no village ever builds over it, wears it, tears it down or tidies it
away. That covers new buildings and their sites, rebuilt homes (a home you've put anything in or on is never rebuilt),
its notice board and market stalls, broken windows and cobwebs when it struggles, scars healing, a fallen village's
ruin, memorials, and its guardians at work (a farmer guardian never harvests your crops). A building simply goes round
your blocks, or somewhere else. Take a block away and that spot is open ground again.

## A home of your own

Click any sign on a village's or town's notice board. If the place owes you (you saved it, or did enough for it), it
offers to build you a home as thanks; anywhere else it offers to sell you one. Click again within ten seconds to say yes.

| Place | Your home | Price |
| --- | --- | --- |
| Hamlet | none to spare yet | |
| Village | one of its small houses | 24 emeralds, or free if it owes you |
| Town | a town house (two storeys) | 40 emeralds, or free if it owes you |

It goes on the board ("A home for Alex") after whatever the village is building now, needs nothing gathered, and goes up
over two days like any project. When it's done:

- Every block of it is yours: the village never wears, rebuilds or touches it.
- No villager can claim its beds, and nobody else moves in.
- Its bed is where you wake from now on (if you were away when it was finished, you're told when you're next online).

One home per player in each village. Your home counts as home for anything that comes looking for you there
([The Company notices](company-notices.md)).

## Market days

Once a week (each place keeps its own day), from morning until evening, a village or town holds a market by its bell
while a player is about: two stalls in a village, four in a town. The evening before, the radio says so, and you're told
when you come near ("It's market day in Ashford: four stalls are up by the bell."). Each stall has fence posts, a barrel
counter with a lantern, a striped canopy, and a trader behind it:

| Stall | Canopy | Sells | Buys |
| --- | --- | --- | --- |
| The Builder's Merchant (always there) | orange | logs of the village's wood, cobblestone (sandstone in the desert), glass panes, terracotta, beds, torches, iron: what notice boards ask for | |
| The Grocer | lime | bread, cooked beef, golden carrots, pumpkin pie, cake, honey | wheat, carrots, potatoes |
| The Curio Dealer | purple | two enchanted books, a name tag, a saddle, ender pearls, a golden apple, a spyglass | |
| The Pedlar | red | a Helixite shard, a tuned radio, taser cartridges and Dampener rounds (with TaCZ) | a Helixite meteorite |

Prices follow how the place is doing: 15% cheaper where it thrives, 30% dearer where it struggles; it pays a fifth more
for your goods where it thrives and a quarter less where it struggles. A friend of the place (standing 3 or more) gets
15% off on top. Stalls go only on open, level ground, never on or over anything a player put down, and come down when
the market packs up for the evening, when nobody has been near for two minutes, or in a hurry if trouble comes.

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
- `/heroesevolved village home` has the nearest village build you a home, free.
- `/heroesevolved village market` opens the nearest village's market now (any day); `village market end` packs it up.
