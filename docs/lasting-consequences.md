# Lasting consequences: a world that remembers

What happens in the world stays happened. Villages remember who stood by them and who didn't come; they put up
memorials, greet their heroes by name and trade with them cheaply; a village that falls stays a ruin. The ground
remembers too: a meteor's crater, a wildfire's burn and a tornado's track heal over the following days, into landmarks
rather than back to nothing. A village hit by a great meteor rebuilds what it lost.

Code: `village/VillageMemory` (what each village remembers, its name, gratitude, memorials, ruins), `village/Scars`
(ground that heals), `village/Rebuild` (putting back what a great meteor broke). Tests: `ConsequenceGameTests`.

## Every village has a name

The first time anything happens to a village (a call for help, an event, a hero), it is given a name, its own and
always the same: Ashford, Wilbrook, Thornstead. Its calls on 101.10 FM carry the name, and so do its memorials.

## What a village remembers

Each village keeps a history: what happened, on which day, how it ended, how many of its people lived, and who was there.
These count:

| Event | Saved when | Fallen when |
| --- | --- | --- |
| A villager's meltdown | he is stopped, or everyone gets out | nobody of the village is left |
| A renegade's siege | the renegade dies with anyone left | the renegade takes everyone |
| An overload | they are calmed, or the storm breaks and everyone lives | nobody of the village is left |
| A great meteor | it is pushed clear or broken up, or lands with everyone out | nobody of the village is left |

**Who it thanks:** the one who ended it (stopped the meltdown, killed the renegade, calmed the overload, pushed or broke
the meteor) most, and everyone else who was there (within 160 blocks) too.

## Gratitude

- **Better trades.** The village's people think well of you, as they do of someone who cured a zombie villager: the one who
  ended it gets a lasting discount, and everyone who was there a smaller one that spreads as villagers gossip.
- **They know you.** Walking through a village that you saved, its people greet you by name now and then: "It's Alex!",
  "Ashford won't forget the wildfire, Alex.", "Thank you, Alex."
- **A memorial.** Each time a village is saved, it puts up a stone by its bell with a sign: what came, on which day, and
  the name of who ended it (on the back, if the front is full). A village keeps up to four.
- **A bust.** Save the same village twice, ending it yourself both times, and it carves your likeness: your head on a
  pedestal by the bell, with a plaque. One for each hero.

Memorials stand on open ground near the bell and never replace anything built.

## A village that falls

- **It stays fallen.** Its doors are torn off, its windows broken, its beds and torches gone and its fires out, holes in
  its walls and roofs, moss on its stone and cobwebs in its corners (only where `mobGriefing` allows). Its bell still
  hangs.
- **A stone for the lost** stands by its bell: its name, the day it fell, and what took it.
- **It is remembered.** The first time you come near it again, you are told what it was: "This was Ashford. It fell to a
  renegade on day 12."

## Ground that heals

| Scar | Left by | How it heals |
| --- | --- | --- |
| A crater | a meteor | The magma cools to blackstone on the first day; over the next few the scorched floor turns to coarse dirt and then soil, grass creeps back in and, by the fifth day, wildflowers grow in the bowl. The rubble round its rim grows moss. The meteorite stays: it's a landmark (and yours to mine). |
| A burn | a wildfire | Grass grows back over the scorched dirt on its own; from the second day, the burn blooms, thicker with flowers than the land round it. |
| A track | a supercell | Where the funnel went, the grass and flowers are torn up. They come back over three days. |

Healing goes on only while the place is loaded, and catches up when it is: a crater left alone for a week is healed when
you return. Days are calendar days, so nights slept through count, and a rebuild that starts "the next morning" starts
when you wake.

## Rebuilding

A great meteor that lands on a village that survives it breaks what stood there. From the next morning, the village
**rebuilds**: its people walk to the site during the day, and over two days what stood there goes back up from the
ground, as it was, and the rubble round the crater's rim is cleared away. The crater's own scorched floor and rubble are
built over; anything else (anything players built there in the meantime) is kept. Mine the meteorite before the ground
closes over it.

A meltdown's crater is too big to rebuild: it is there for good.

A village also grows or declines with how it is treated: saved, guarded and helped, it needs less from players for the projects
on its notice board, builds and takes in newcomers; hit and left alone, it wears and loses people ([Villages that grow](village-growth.md)).

## Admin

- `/heroesevolved village memory` describes the nearest village: its name, its history, who it thanks.
- `/heroesevolved village forget` makes the nearest village forget everything (its memorials stay).
