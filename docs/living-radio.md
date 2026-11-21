# The living radio

101.10 FM is not only for emergencies. Between calls for help, the villages talk on it: about the weather, prices,
lost animals, their gifted neighbours' work, favours they need, renegades seen on the road, the man in the dark suit
asking questions, and what you did for them. Carry a radio tuned to 101.10 and the world tells you what is going on in
it.

Code: `radio/BandChatter` (who speaks, what they say, how it sounds), `story/bunker/BunkerRadios` (the radios in the
bunker). Tests: `LivingRadioGameTests`.

## Radios in the bunker

The bunker's supply chest in Records (the one with bread and torches, by the signal receiver) holds walkie-talkies
already tuned to 101.10 FM: **one for every two players who wake in the bunker**, and one in singleplayer.

| Players who have woken | Radios |
| --- | --- |
| 1 or 2 | 1 |
| 3 or 4 | 2 |
| 5 or 6 | 3 |

A radio is added when the first sleeper wakes, and another each time a third, fifth, seventh... player does, so a server
that grows gets more. Players told where the bunker is instead of waking in it count too. If the chest has been broken
or is full, the radio is left on the floor where the chest stood. Without Simple Radio installed there are no radios.

## Hearing the villages

Anyone holding a radio tuned to 101.10 FM (or standing within 8 blocks of a radio block tuned to it) hears the
villages within 768 blocks (`villages.distressRange`) talk, every minute or two:

- a line in chat: `📻 101.10 FM  Ashford: Storm's coming in from the west. Get the sheep in.`
- and on the radio itself, a burst of static and a distant voice saying it (a murmur, never words).

Nobody without a radio hears the chatter. Distress calls work as before ([Emergency band](emergency-band.md)), and while
one is on the air, the other villages talk about it ("Anyone near Ashford? They need help over there!").

## What they talk about

The newest news first: something that happened in the last few days is more likely than small talk.

| Topic | For example |
| --- | --- |
| What happened to them | "Ashford here. Still sweeping up after the wildfire. Thanks again, Alex." |
| A village that fell | "Still nothing from Thornstead. Not since the renegade." |
| Renegades about | "Someone saw Hollis the Ashen by the river, north of Wilbrook. Keep away from him." |
| The Company | "A man in a suit was asking questions here this morning. Polite. Too polite." / "They took Ashford's smith. Nobody's saying where." / "Lights and gunfire out at Alex's place last night." |
| Gifted neighbours at work | "Hollis kept the forge going all night again." / "Courier's left Ashford for Wilbrook with the post." |
| Favours | "Hollis at Ashford is asking for eight coal. Pays in emeralds." (while the favour is open) |
| The weather and the sky | rain, thunder, nightfall, a starfall night, an Eclipse under way |
| Small talk | prices, lost animals, gossip, the harvest |

A village only talks about what it would know: its own news, its neighbours' within the radio's reach, and things that
happened where it could hear. Villages talk whether or not they are loaded (their news and history are saved), but they
only mention their gifted neighbours and favours while the village itself is loaded.
