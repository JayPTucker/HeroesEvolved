# Power sounds

Every power sounds like itself. Each one has a small palette of original, synthesized cues, and every action it has
(core moves, follow-ups, skill-tree actives, class abilities, fighting-style blows) plays from that palette.

## What you hear

| Cue | When it plays |
| --- | --- |
| **Cast** | Most presses: a core move, a skill-tree active. |
| **Heavy** | An ultimate (the fifth key), a class ability, a signature move, Earthquake, Dive Bomb, Lightspeed. Louder, and heard further away. |
| **Charge** | The press that starts a wind-up: Cinder Spear, Inferno, Skyburst, Critical Mass, Cyclone, Sunfall, Crescendo. Letting go plays the move itself. |
| **Impact** | Where a power's blow lands on someone, and the free second press of a two-stage move (a slam, a crush, a shatter). |
| **End** | Letting go of something held, or dismissing it with Shift. |
| **Strike / Dodge** | Fighting-style blows and dodges, for the ten powers that fight hand to hand. |

Each power has its own material, so you can tell who is fighting by ear:

| Power | Sounds like |
| --- | --- |
| Cryokinesis | frost hiss, crystalline tinkles, cracking and shattering ice, skate carves |
| Electrokinesis | buzzing zaps, crackle, thunder rolls, a current winding down |
| Flight | gusts, pressure booms, swirling wind gathering |
| Hydrokinesis | swishes, bubbles, splashes, a crashing wave, a whip's wet crack |
| Invisibility | a beating glass chord, force-field thuds and booms |
| Matter Assembly | ratchets and clicks locking into a heavy clunk |
| Empathic Mimicry | a sung chord with echoes (an Echo also sounds like the power it borrows) |
| Density | the phasing-era cues (flanged whooshes and very dense thuds) under each move's own stone, gravel and deepslate sounds; to be re-voiced as grinding rock |
| Pyrokinesis | fire whooshes, roars, crackle, flame jets |
| Quake | thumps, rumbling, grinding and cracking rock |
| Induced Radioactivity | a Geiger counter over a reactor hum |
| Regeneration | heartbeats under a warm chord, wet blows |
| Shapeshifting | rippling plates, a brass clank, a growl when the body changes |
| Sonic Resonance | a bowed cello note with glassy partials |
| Superspeed | zips, a sonic crack, blur punches |
| Technopathy | data chirps, modem warbles, glitches, power-ups and power-downs |
| Telekinesis | a low thrum, implosions, kinetic thumps |
| Telepathy | whispers and bells |
| Space-Time | clockwork ticking and a toll |
| Vibe | chirps and grit, like Vibe's existing sounds |

## Rules

- **Most powers layer their cue over a move's existing sounds.** Space-Time, Sonic Resonance and Vibe already had
  hand-made sounds, so for them a cue plays only when a move makes no sound of its own.
- **Stealthy moves are quiet to others.** Fade, Vanishing Touch, Vanishing Squad, Refraction Cloak, Read Mind,
  Suggestion, Mind Link, Far Hearing, Memory Walk, Blind Spot and Read the Vibe play their cue to you alone.
  Surgical Fracture with Surgical Silence plays none.
- **Impacts don't spam.** A target rings at most every half second, an area blow plays once, and damage over time
  (a dose, a burn) makes no impact sound. A plain punch or a weapon sounds like a punch or a weapon, not the power.
- Every cue has a subtitle.

## For developers

- The sounds are made by `tools/generate-power-audio.py` (numpy, scipy and soundfile in `build/audio-tools`); it
  writes `assets/heroesevolved/sounds/power/<power>/<cue>_<n>.ogg` and the `sounds.json` and `en_us.json` entries.
  Re-run it after changing a recipe; it never touches other sounds.
- `sounds/PowerCue` lists the powers and cues; `ModSounds.POWER_CUES` registers them as `heroesevolved:power.<power>.<cue>`.
- `sounds/PowerSounds` picks and plays them. `AbilityUseService` calls it for every press, follow-up, dismissal and
  release; `FightingStyles` for strikes and dodges; a `LivingDamageEvent.Post` hook for blows.
- `mixin/ServerSoundCountMixin` counts every sound the server plays, which is how `PowerSounds` knows whether an
  action already made one.
- To give an action a different cue, add it to the tables at the top of `PowerSounds` (`CHARGES`, `PRESS`,
  `FOLLOW_UP`, `PRIVATE`). A new power needs a recipe in the script and an entry in `PowerCue.POWERS` (and
  `STYLED` if it fights).
