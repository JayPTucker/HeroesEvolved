# The Last Signal

You wake in the Company’s Odessa stasis bunker 103 years after the city burned. You were there: on [the Last Day](the-last-day.md) you ran for this bunker when the light appeared, and Noah Bennet sealed you in a pod for your own good. At 6:14 PM Ted Sprague lost control downtown, and the stasis pods kept their occupants alive. The [Space-Time Manipulation awakening](time-manipulation-intro.md) lets its owner and nearby companions stand on the avenue, witness the explosion, and see the city run backwards before returning home.

## Follow the signal

1. Search the Overworld for the rare Company relay ruins. For a bearing, use the receiver in the bunker's records room after the lights come back. It records the live distress call and coordinates on **The Last Signal** page of the Journal (**J**).
2. Make your way to the relay ruins and follow the yellow markings. Activate the relay console to open the way to the stasis annex.
3. Use the isolator, then the coolant control. Once life support is stable, use **Mara** and **Ivo** to have them follow you, or sneak-use to carry them. Bring both from their pods to the extraction platform. Gabriel Gray approaches through the service route.
4. Use the extraction console once both sleepers reach the platform. Walk out of the station and return to the bunker. Speak with Mara and Ivo in the infirmary to hear their account and record their testimony in the Journal.

Primatech relay stations generate far apart in rings around the world origin, like strongholds. The campaign can be played with or without an awakened power, and you can find a station by exploring even if the bunker is disabled. The receiver's Journal entry shows the **X** and **Z** coordinates it found. After opening the annex, an electrical cast aimed near the isolator can cut the feed, and a cold cast aimed near the coolant valve can stabilize life support.

**Records**, **Dreams**, **The Way Back**, and **The Watchmaker** track optional discoveries, telepathic fragments, temporal milestones, and Sylar encounters. The first time the bunker's receiver records the call, it also hands over Isaac Mendez's canvas of The Last Signal: a shareable painting of the sleepers that changes when the rescue succeeds.

## Existing worlds

Existing bunkers receive the receiver and recovery rooms where the original fixtures are still in place. Look for relay stations in newly generated terrain. Previously earned story books, advancements, discovery rewards, and past outcomes remain in the save. Books from the earlier bunker layout and retired Hourglass Annex, Avenue at 6:14, and Night Three episodes remain valid discoveries when found. The game recovers players whose saves still place them in one of those visits.

## The first five minutes

New players first live [the Last Day](the-last-day.md), which ends with them sealed in a pod under Primatech. Then:

1. **STASIS FAILURE.** A new player wakes sealed inside a Cryochamber. A new bunker is on emergency
   power: every lamp is dark, and only the alarm beacons on its walls and ceilings turn, sweeping red
   beams across the hall. The facility alarm whoops in time with the red pulse, muffled through the
   lid, while the pod's own warning beeps quicken toward release.
2. **37,620 days.** The pod's counter: 103 years. Frost on the glass. The pod holds you while the revival protocol runs.
3. **You are awake.** The pod chimes and hisses open (about 5.5 seconds in) and you earn **Wake Up**.
4. **The power comes back.** The alarm holds until you step out of the pod. Then it cuts off, a breaker
   slams, and the bunker's lights click on in a wave from the generator, down the hall from its door to
   its far wall. The beacons keep turning.

Every new player wakes this way, in the pod assigned to them (the same number they were sealed in on the Last Day; never Pod 14, Claire Bennet's, which stands open and empty with her note inside), whose nameplate now carries their name
("SUBJECT 03 / name / REVIVAL IN PROGRESS", then "REVIVED"). Their waking cuts the main power again: the
lights go out, and everyone already in the bunker hears the alarm until the last sleeper is out of their
pod, when the power comes back for all of them.

A lectern in the middle of the chamber holds the orientation log. The receiver in the records room gives directions to the relay ruins.

| Room | What is in it |
| --- | --- |
| **Stasis hall** | Fourteen Cryochambers under copper galleries, alarm beacons, the orientation log, and Isaac Mendez's painting of the explosion on the far wall. Glow berries light the end where a cave broke through the ceiling |
| **Records room** | The signal receiver on the east desk, Noah Bennet's lockdown order, filing cabinets, and a chest with bread, apples and torches |
| **Lab** (east of the corridor) | A Centrifuge, a Synthesis Station, the Helixite assay log, and a chest with a Helixite Shard, Empty Syringes, bottles and a donated blood sample |
| **Infirmary** (west of the corridor) | Two cots, medical storage and a screened recovery area. Mara and Ivo recover here after the rescue |
| **Generator hall** (east of records) | A cold reactor, fourteen power cells and a catwalk |
| **Security** (west of records) | The checkpoint, lockers, emergency gear and a marked maintenance hatch |
| **Maintenance bay** | Down the ladder from security are a workbench, a map desk, a cot and storage |
| **Shaft** | A ladder up to a spruce trapdoor on the surface, directly at world spawn |

The bunker is built once, about 32 blocks below world spawn, the first time any player joins. Its books give more context about the Company, the sleepers, and Odessa.

**Existing worlds:** players who have already played more than two minutes, or who already have an
awakened power, are not put in a pod. They get a chat message saying where the bunker is instead. An earlier bunker receives compatible additions the first time someone comes near the stasis hall.
Disable the spawn bunker with `bunker.enabled = false` in the common config.

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

Isaac Mendez's paintings of the explosion and the black sun hang in his studio ruins. The painting of the explosion also hangs in the bunker as Exhibit 1. They are data-driven painting variants (`data/heroesevolved/painting_variant`) placed by blueprint markers. Older bunkers can still have exhibits from their original layout.

## Ruins on the surface

Up top, the world is scattered with what is left of the houses. They generate naturally in plains,
forests, savannas, taigas, snowy plains and deserts, and each has a chest to scavenge.

| Ruin | How common | What happened | What's inside |
| --- | --- | --- | --- |
| **Burnt house** | common | A timber-frame home that burned from the kitchen outward. Only the charred frame stands | Scavenged supplies, sometimes a survivor's note |
| **Collapsed house** | common | A brick house the blast wave pushed over. The west wall and chimney still stand | Scavenged supplies, sometimes a survivor's note |
| **The Bennet house** | rare | The fire took the east wing; a young oak grows there now. Claire's room survived upstairs, and the burnt-out car is still in the driveway | Claire's note for her dad, and his spare glasses |
| **Isaac Mendez's studio** | rare | A brick loft with a collapsed corner. His painting of the man who was the bomb hangs on the wall, with a study of the black sun opposite | Isaac's note about the painting that came true |
| **Primatech relay station** | stronghold-like rarity | A ruined Company relay above a buried stasis annex | The Last Signal rescue, starting at the relay console |

The survivor notes and the two story notes point back toward Odessa and the bunker. Find one with
`/locate structure heroesevolved:ruins_bennet_house` (or `ruins_isaac_studio`, `ruins_burnt_house`,
`ruins_collapsed_house`).

Ruins are drawn as blueprints (`blueprints/ruins_*.blueprint`) and exported to the structure files
the world generator uses:

```bash
python3 tools/blueprint.py to-nbt src/main/resources/data/heroesevolved/blueprints/ruins_bennet_house.blueprint --out src/main/resources/data/heroesevolved/structure/ruins/bennet_house.nbt
```

Photograph the four house and studio ruins in-game with:

```bash
./gradlew runClient --init-script tools/ruins-preview.init.gradle
```

The relay station has its own preview, `tools/signal-site-preview.init.gradle`.
