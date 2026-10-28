# The Past: waking in the Odessa bunker

The future-city vision is no longer a warning. In this world it already happened.

## What happened

The Company (Primatech) kept powered people in **Cryochambers** in a stasis wing deep under its
Odessa site. Then the painting came true: Ted Sprague lost control downtown, the city burned, and
Noah Bennet sealed the bunker. Above it, centuries passed and nature took the surface back. Below,
the pods kept their sleepers until the power cells began to fail. Now each pod opens when someone
arrives to wake in it.

Gabriel Gray, Sylar, was held in the same wing. During the lockdown he took the regeneration
sleeper's ability, unmade his cell's bars, and walked out. He does not age. He has been waiting for
the sleepers to wake, which is why the ticking follows new players.

A note in unfamiliar handwriting, from Hiro, suggests the past can still be changed. That is where
the time-travel content goes next.

## The first five minutes

1. **STASIS FAILURE.** A new player joins sealed inside a Cryochamber under red emergency lights.
   An alarm pulses, the screen washes red, and the old city siren sounds faintly.
2. **How long were you asleep?** Frost on the glass. The pod holds you while the revival protocol runs.
3. **You are awake.** The pod hisses open (about 5.5 seconds in) and you earn **Wake Up**.

A lectern in the middle of the chamber holds the orientation log. From there:

| Room | What is in it |
| --- | --- |
| **Stasis hall** | A 12-high hall: 14 Cryochambers under copper galleries, red emergency lights, hanging lamps, the orientation log on the console. A lush cave has broken through the south-east ceiling, and its glow berries light that end. A blood trail runs from Claire Bennet's pod (14) to Sylar's cell |
| **The west gallery** | Climb the stairs at the south end. Exhibit 9 hangs there: Isaac Mendez's painting of someone waking in a pod |
| **Records room** | Noah Bennet's lockdown order, the last researcher's "Day 2,190" log, filing cabinets, Exhibit 4 (Isaac's *Black Sun*), and a chest with Hiro's note, bread, apples and torches |
| **Lab** (east of the corridor) | A working Centrifuge and Synthesis Station, a cracked specimen tube, the Helixite assay log, and a chest with 1 Helixite Shard, 2 Empty Syringes and 3 glass bottles |
| **The cell** (west of the corridor) | Sylar's containment file, the cage whose bars he unmade, his watchmaker's bench, and the end of the blood trail. You'll hear ticking the first time you step inside |
| **Generator hall** (east of records) | A cold reactor, 14 power cells (3 still lit), a catwalk |
| **Security** (west of records) | A barrier, the guard post, lockers, Company gear, and a guard's *Shift Notes* about an ugly red rug |
| **Sublevel 4-B** (secret) | Under the red rug in security is a trapdoor. Noah Bennet's hidden office is at the bottom of the ladder: the family photo, his letter *For Claire*, and a chest with Claire's donated blood (a Formula catalyst), 2 Helixite Shards, a syringe and a spyglass |
| **Shaft** | A ladder up to a spruce trapdoor on the surface, directly at world spawn |

The bunker is built once, about 32 blocks below world spawn, the first time any player joins. It
has six recovered logs in total; read in order, they tell the whole story.

**Existing worlds:** players who have already played more than two minutes, or who already have an
awakened power, are not put in a pod. They get a chat message saying where the bunker is instead.
Turn the whole feature off with `bunker.enabled = false` in the common config.

## The Cryochamber

A two-block stasis pod. The bunker's pods are real Cryochambers, and players can craft their own.

- **Recipe:** iron ingots around glass (top), blue ice (middle) and one Helixite Shard (bottom).
- **Use an open pod** to step in. The door seals and the pod becomes **your respawn point**, like a
  bed that works at any time of day. If the pod is broken, you lose the respawn, just like a bed.
- **While sealed:** no ordinary damage, no hunger, and you heal half a heart every two seconds. You
  can't attack, use items or use powers. **Sneak** to step out.
- **Cryokinesis:** a Cryokinetic who sneak-uses a pod supercharges it for five minutes. It then
  heals four times faster and clears harmful effects and fire.
- **The Formula:** injecting yourself while sealed in a pod adds **+10%** to the success chance.
  The vial is the one item you can use inside.
- Only one sleeper per pod. It drops as an item when the bottom half is broken.

Regenerate the bunker tour screenshots with:

```bash
./gradlew runClient --init-script tools/bunker-preview.init.gradle
```

## Paintings

Four custom paintings tie the places together: *The Man Who Was the Bomb* and *The Black Sun* in Isaac's
studio, Exhibits 4 and 9 that the Company took to the bunker, and the Bennet family photo in Sublevel 4-B.
They are data-driven painting variants (`data/heroesevolved/painting_variant`), drawn by
`tools/generate-paintings.py`, and hung from blueprints with `@painting:<variant>:<facing>` markers.

## Ruins on the surface

Up top, the world is scattered with what is left of the houses. They generate naturally in plains,
forests, savannas, taigas, snowy plains and deserts, and each has a chest to scavenge.

| Ruin | How common | What happened | What's inside |
| --- | --- | --- | --- |
| **Burnt house** | common | A timber-frame home that burned from the kitchen outward. Only the charred frame stands | Scavenged supplies, sometimes a survivor's note |
| **Collapsed house** | common | A brick house the blast wave pushed over. The west wall and chimney still stand | Scavenged supplies, sometimes a survivor's note |
| **The Bennet house** | rare | The fire took the east wing; a young oak grows there now. Claire's room survived upstairs, and the burnt-out car is still in the driveway | Claire's note for her dad, and his spare glasses |
| **Isaac Mendez's studio** | rare | A brick loft with a collapsed corner. His painting of the man who was the bomb hangs on the wall, with a study of the black sun opposite | Isaac's note about the painting that came true |

The survivor notes and the two story notes point back toward Odessa and the bunker. Find one with
`/locate structure heroesevolved:ruins_bennet_house` (or `ruins_isaac_studio`, `ruins_burnt_house`,
`ruins_collapsed_house`).

Ruins are drawn as blueprints (`blueprints/ruins_*.blueprint`) and exported to the structure files
the world generator uses:

```bash
python3 tools/blueprint.py to-nbt src/main/resources/data/heroesevolved/blueprints/ruins_bennet_house.blueprint --out src/main/resources/data/heroesevolved/structure/ruins/bennet_house.nbt
```

Photograph all four in-game with:

```bash
./gradlew runClient --init-script tools/ruins-preview.init.gradle
```
