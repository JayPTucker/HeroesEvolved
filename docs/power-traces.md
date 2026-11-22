# Power traces

Powers leave marks. Where someone used a power hard, the place remembers it for a few days: a faint scorch, a rime of
frost, a crack of static, a hum in the air. Anyone can come across one; a telepath, a viber, an empath or a time-bender
can read it (what was used, how long ago, and whose it felt like). The Company finds them too, even where nobody saw a
thing. And a renegade on the road leaves a trail you can follow.

Code: `trace/Traces` (what is left, how it fades, who can read it). Tests: `PowerTraceGameTests`.

## What leaves a trace

| What | Trace | Lasts |
| --- | --- | --- |
| A move costing 30 stamina or more | strong | 3 days |
| A move costing 15 to 29 | faint | 1 day |
| A hostile gifted mob on the move (a renegade, a champion) | a trail, a mark every few blocks, with the way it went | 1 day |

Every power leaves its own kind of mark:

| Power | Mark | What you see |
| --- | --- | --- |
| Pyrokinesis | scorch | embers and smoke |
| Cryokinesis | frost | snowflakes |
| Electrokinesis, Technopathy | static | crackling sparks |
| Hydrokinesis | damp | drips |
| Quake, Density | crack | dust |
| Induced Radioactivity | glow | a faint green |
| Flight, Superspeed | wind | swirling cloud |
| Anyone else | hum | soft violet motes |

A trace fades as it ages: fewer motes, fainter, until it is gone. Rain washes it away twice as fast. Using a power in the
same place again (within a few blocks) freshens it rather than piling up marks.
Whether that fresh use was witnessed is checked again; witnesses to an earlier use don't count as seeing the new one.

## Seeing and reading them

- **Anyone** within 24 blocks of a trace sees its motes. Crouch within 3 blocks of one and you learn what it is and
  roughly how old: "The ground here is scorched in a ring. A day old, maybe."
- **A reader** (Telepathy, Vibe, Empathic Mimicry, Space-Time Manipulation) sees traces from 48 blocks, brighter, and
  reads them fully: "Fire was used here about 4 hours ago. It felt like Alex." or "Hollis the Ashen passed this way,
  heading north-east, about an hour ago." A reader walking within 16 blocks of a fresh one notices it without stopping:
  "Someone used fire near here, not long ago."

Each mark is reported once per kind of reading. If you gain a reading power after examining one, crouch by it again to
learn whose it was. Reading or noticing a mark in one dimension doesn't silence marks in another.

## The Company reads them too

- **Unseen isn't unknown.** A strong trace of yours within 64 blocks of a village, left where no villager saw, is found
  the next day: +2 Company attention each, up to +6 a day ([The Company notices](company-notices.md)). The radio talks
  about it: "Scorch marks out past the mill at Ashford again. Nobody saw who."
  Faint traces don't add Company attention.
- **The ground tells.** An investigator asking in a village that covers for you still files his notes if your strong
  traces are fresh within 32 blocks of the spot: "The villagers said nothing. The ground said plenty."

## Admin

- `/heroesevolved trace list` lists the traces within 64 blocks.
- `/heroesevolved trace clear` clears every trace within 64 blocks.
