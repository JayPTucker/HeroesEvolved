# Heroes Evolved: launch trailer storyboard (v1)

About 1:50, filmed in the game itself, cut in Blender. 24 fps with motion blur, letterboxed to 2.39:1 inside 1920×1080.
Every line, card, piece of music and sound is our own. How it is filmed: [docs/agents/previews.md](../agents/previews.md),
"Filming the trailer".

**The idea in one line:** a city ended, a hundred years passed, and the people who woke up are not ordinary any more.
The world notices, and something under the bunker has been waiting for them.

**Shape of the music:** a cold, quiet opening (radio static, one low note), a pulse that builds through the powers, a
hard stop, one beat of silence, the biggest hit on the time stop, then the finale and the title.

Speed is the playback rate the recorder films at (0.4 is slow motion). "Scene" is the existing preview that already
stages it; the trailer driver reuses its staging and adds the moving camera, the light and the terrain.

## Act I: the Last Day (0:00 to 0:22)

| # | Time | What we see | Camera | Speed | Scene |
| --- | --- | --- | --- | --- | --- |
| 1 | 0:00 | Black. Radio static, a weak emergency tone. Card: **ODESSA · 6:02 PM** | | | |
| 2 | 0:03 | An ordinary evening on the street outside the Odessa Arms, lights coming on, Primatech Paper at the end of the road | Low dolly down the middle of the street | 1.0 | `ProloguePreview` |
| 3 | 0:07 | Inside the apartment: the radio on the table, the window glowing | Slow push in on the radio | 1.0 | `ProloguePreview` |
| 4 | 0:10 | The light over the city: white swallows the frame | Locked off, wide, from a rooftop | 0.6 | `ProloguePreview` |
| 5 | 0:12 | Card: **103 YEARS LATER** | | | |
| 6 | 0:14 | A cryo pod's frost clearing; the number on the pod | Overhead, tilting down to the glass | 0.8 | `BunkerPreview` |
| 7 | 0:17 | Odessa's ruins, overgrown; a figure climbing out of the hatch into daylight | Crane up from the hatch to the whole ruined block | 1.0 | `OdessaRuinsPreview` |
| 8 | 0:20 | Card: **SOME OF US WOKE UP DIFFERENT.** | | | |

## Act II: awakening (0:22 to 1:00)

Cuts get shorter as the pulse builds: about 2.5 seconds each at first, about 1.2 at the end. Every one of the twenty
powers gets a shot: by its signature set piece (staged as `SignaturesPreview` stages it, with a slow arc), or, for the
seven powers without one, by its Offense Ultimate through the Ultimate's own camera (staged as `UltimatePreview` stages
it). The shot names are the ones in `trailer/Shots`.

| # | Shot | What we see | Camera | Speed |
| --- | --- | --- | --- | --- |
| 9 | | An awakening: a creeper's fuse burning beside a villager, then the power tearing out of the player to save them (`AwakeningPreview`) | Push in on the face, then pull back hard on the release | 0.4 |
| 10 | `10-ballista` | Matter Assembly's Ballista: the blueprint, parts flying in, built and shooting, folding home | Over the shoulder, down the line of fire | 0.5 on the shots |
| 11 | `11-firebending-sunset` | Firebending: jab, cross, roundhouse into husks, a blue-fire combo, a flip kick | Low follow round the fighter to their face | 0.3 on the kick |
| 12 | `12-lightning-round` | Superspeed's Lightning Round: four foes visited and home again, at dusk | Slow arc | 0.3 throughout |
| 13 | `13-epicenter` | Quake's Epicenter: the faults warn, the plates heave, the ground drops back | High arc | 0.4 on the heave |
| 14 | `14-leviathan` | Hydrokinesis's Leviathan: rising, rearing over a marked spot, the bite, the dive | Arc | 0.4 on the bite |
| 15 | `15-mountain-armor` | Density's Mountain Armor: the plates flying on, worn, blasting off | Close arc | 0.4 on the blast |
| 16 | `16-fission-star` | Radiation's Fission Star: the core grows, flies, and goes off on a foe, at dusk | Arc | 0.35 on the blast |
| 17 | `17-microburst` | Flight's Microburst: the lid over a spot, the column, the outflow | High arc | 0.4 on the column |
| 18 | `18-standing-wave` | Sonic's Standing Wave: the column, a beat, the swell, the break | Arc | 0.4 on the break |
| 19 | `19-puppeteer` | Telepathy's Puppeteer: strings on three foes, then cut, at dusk | Arc | 0.4 on the cut |
| 20 | `20-bastion` | Invisibility's Bastion: the dome rising, arrows glancing off it, the burst | High arc | 0.4 on the burst |
| 21 | `21-chorus` | Empathic Mimicry's Chorus: a ring of borrowed voices, then all together | Close arc | 0.4 on the chorus |
| 22 | `22-overdrive` | Power Amplification's Overdrive: the web over a friend and the amplifier, then the forks, at dusk | Arc | 0.45 on the forks |
| 23 | `23-hailstorm` | Cryokinesis's Hailstorm (Offense Ultimate) | The Ultimate's own | 1.0 |
| 24 | `24-tesla-spire` | Electrokinesis's Tesla Spire, at dusk | The Ultimate's own | 1.0 |
| 25 | `25-implosion` | Telekinesis's Implosion | The Ultimate's own | 1.0 |
| 26 | `26-hour-hand` | Space-Time's Hour Hand | The Ultimate's own | 1.0 |
| 27 | `27-vital-burst` | Regeneration's Vital Burst, at dusk | The Ultimate's own | 1.0 |
| 28 | `28-many-hands` | Shapeshifting's Many Hands | The Ultimate's own | 1.0 |
| 29 | `29-crosswave` | Vibe's Crosswave, at dusk | The Ultimate's own | 1.0 |
| 30 | | Card: **THE WORLD NOTICED.** | | |

## Act III: the hunt (1:00 to 1:25)

| # | What we see | Camera | Speed | Scene |
| --- | --- | --- | --- | --- |
| 31 | A Company convoy on the road at dusk; red aim lines snapping on | Long lens on the road, the convoy walking into frame | 1.0 | `CompanyPreview` |
| 32 | A village overload: a wildfire with a frightened villager at its centre | Crane down through the smoke | 0.8 | `OverloadPreview` |
| 33 | Starfall: meteors streaking down over a village, one marked on the ground, the impact | Wide, then a hard cut to the crater | 1.0 | `MeteorPreview` |
| 34 | Level 5's corridor, a prisoner behind glass | Slow push down the corridor | 1.0 | `LevelFivePreview` |
| 35 | Card: **HE COLLECTS.** | | | |
| 36 | The Collection: powers kept under glass, brass schematics turning | Slow lateral track past the cases | 0.8 | `SylarCollectionPreview` |
| 37 | The Wither's breach tearing open and dragging it through | Low, wide | 0.5 | `WitherPreview` |

## The drop (1:25 to 1:35)

| # | What we see | Camera | Speed | Scene |
| --- | --- | --- | --- | --- |
| 38 | One beat of black and silence | | | |
| 39 | Still World: an explosion stopped mid-blast, a fireball hanging, bullets frozen in the air | The camera glides through the frozen scene | 1.0 (the world is stopped) | `TimePreview`, `TemporalInteractionPreview` |
| 40 | Time starts again: everything lands at once | Hold | 1.0 | |

## Finale (1:35 to 1:50)

| # | What we see | Camera | Speed | Scene |
| --- | --- | --- | --- | --- |
| 41 | The End: the Living Seal rising over the island, its violet rings | Crane up from the island to the Dragon | 0.8 | `EndgamePreview` (dragon) |
| 42 | The rupture: Sylar's brass closing round the Dragon's light | Push in | 0.5 | `EndgamePreview` |
| 43 | Title: **HEROES EVOLVED**, under it *Twenty powers. One world that remembers.* and *Minecraft 1.21.1 · NeoForge* | | | |
| 44 | After the title: an empty glass case in the Collection, a new label being set in front of it | Locked off, close | 1.0 | `SylarCollectionPreview` |

## Progress

- **Shot 11 (firebending at sunset) is filmed** as the test of the whole pipeline: `tools/trailer/test-shot.json`
  cuts it between a card and the title, with the score, into `build/trailer/heroes-evolved-test-shot.mp4` (17.6 s).
  A plains hilltop by a cherry grove on seed 20261127, at (2112, 109, 960). For the trailer itself, cut its best two
  or three seconds (the slow-motion kick through the burning husks, frames 40 to 95).
- **Eight signature shots are filmed** (Act II, shots 10 and 12 to 18), all on the same hilltop in one run. The teaser
  `tools/trailer/teaser.json` cuts them with the firebending shot into `build/trailer/heroes-evolved-teaser.mp4`
  (29 s: a card, nine shots of two to three seconds round their impacts, a beat of black, the title).
- **Every power is filmed** (Act II, shots 10 to 29): twelve signatures, firebending, and seven Offense Ultimates
  through their own cameras. The powers reel `tools/trailer/powers.json` cuts all twenty into
  `build/trailer/heroes-evolved-powers.mp4` (38.5 s: a card, twenty cuts shrinking from about two seconds to one, the
  biggest blast last, a beat of black, the title). Its clip ranges are the frames to use in the full trailer.
- Still to stage: shot 9 (an awakening), and Acts I and III and the finale, which need their own places (Odessa, the
  bunker, Level 5, the Collection, a village, the End) rather than the hilltop. Each scene named in the tables shows
  how it is set up.

## Still to decide

- **The hero's look:** a dev run gets a default skin. A suit from the armor skill, or a skin of the user's choosing,
  would make every shot better.
- **Shaders:** vanilla lighting is what the footage has. A shader mod (Iris on NeoForge) would add real shadows and
  light, if the user wants to install one.
- **Music:** an original score from a generator script, or a licensed track the user supplies.
